package com.example.yuewen.data.backup

import com.example.yuewen.data.util.Json
import com.example.yuewen.data.util.asObject
import com.example.yuewen.data.util.asString
import java.util.Base64

/**
 * 云备份两个远程通道的**纯计算部分**（v2.9）。
 *
 * 这里只做「拼请求」和「拆响应」——不碰网络。
 * 网络那一层在 [RemoteBackup] 里，两边分开的好处是：
 * 认证头、URL 拼接、文件名解析这些**最容易悄悄写错**的地方都能离线跑断言，
 * 不用连真服务器。
 *
 * ## 为什么认证要用 `java.util.Base64` 而不是 `android.util.Base64`
 *
 * 后者是 Android 独有的类，离线断言（纯 JVM）里根本加载不到，
 * 这段逻辑就会变成「只能靠连服务器试」的黑盒。
 * `java.util.Base64` 从 API 26 起在 Android 上也可用，而本 App 的 minSdk 正是 26。
 */
object Remote {

    /**
     * GitHub Gist 的 API 根。写死成常量而不是做成可配：
     * 这是 GitHub 官方的域名，没有「换成自建实例」的正当场景，
     * 多一个可配项就多一处能被填错、填坏的地方。
     */
    const val GIST_API = "https://api.github.com"

    /** Gist 接口要求的 Accept 头。不带你也能用，但带上才不会跟着 GitHub 改默认行为。 */
    const val GIST_ACCEPT = "application/vnd.github+json"

    /** 新建 Gist 时的说明文字——用户在 GitHub 网站上看到的就是这句。 */
    const val GIST_DESCRIPTION = "阅闻备份（由 App 生成，可自行删除）"

    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    private const val HEX = "0123456789ABCDEF"

    private val SCHEME_RE = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")

    /**
     * 主机名（可带端口）的合法形状。
     *
     * ⚠️ 必须有这一道检查，不能只判「主机段非空」：
     * `://x`、`https://` 这类残缺输入在「主机段非空」的标准下都能蒙混过关，
     * 补完协议之后会攒出 `https://://x` 这种地址，
     * 然后被当成合法值存进设置 —— 用户下次来只会看到一句莫名其妙的连接失败。
     */
    private val HOST_RE = Regex("^[A-Za-z0-9]([A-Za-z0-9.\\-]*[A-Za-z0-9])?(:\\d{1,5})?$")

    /**
     * `<d:href>…</d:href>` 这类标签的内容。
     *
     * 命名空间前缀做成可选的：坚果云、Nextcloud 这些服务端返回的
     * 前缀未必是 `d:`，有的干脆不带前缀（`<href>`）。
     * 写死 `d:` 的话，换一个网盘就一个文件都列不出来 ——
     * 表现是「备份成功了但列表永远是空的」。
     */
    private val HREF_RE = Regex(
        "<(?:[A-Za-z_][\\w.\\-]*:)?href\\s*>([^<]*)</(?:[A-Za-z_][\\w.\\-]*:)?href\\s*>",
        RegexOption.IGNORE_CASE
    )

    // ------------------------------------------------------------------ 认证 / URL

    /**
     * 收拾用户粘进来的令牌 / 密码。
     *
     * 做两件事，都是为了让「从网页上复制粘贴」这个最常见的动作不要失败：
     * - 去掉首尾空白（复制时很容易带一个换行或空格，
     *   带着它去请求会得到 401，看起来完全像是令牌本身错了）；
     * - 去掉可能连在一起的 `Bearer ` 前缀（GitHub 的文档里到处是
     *   `Authorization: Bearer <token>`，用户很容易把整段复制进来）。
     */
    fun normalizeToken(raw: String): String =
        raw.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()

    /**
     * HTTP Basic 认证头。
     *
     * ⚠️ 必须显式按 **UTF-8** 取字节：`"$user:$pass".toByteArray()`
     * 用的是平台默认编码，服务端一律按 UTF-8 解，
     * 用户名或密码里有中文时就会认证失败（而且报的是 401，看起来像密码错了）。
     */
    fun basicAuth(user: String, pass: String): String =
        "Basic " + Base64.getEncoder()
            .encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))

    /**
     * 把用户填的地址收拾成一个可用的根地址；填得没法用就返回 null。
     *
     * 规则：
     * - 去首尾空格、去掉末尾的 `/`（`joinUrl` 会自己补，留着会拼出 `//`）；
     * - 没写协议就补 `https://`（绝大多数网盘都是 https）；
     * - **明确写了 `http://` 就保留** —— 局域网里的 NAS 经常没有证书，
     *   替用户「升级」成 https 会直接连不上；
     * - 主机名不合法、或者地址里带空格 → 判为非法，返回 null，
     *   让界面提示「地址格式不对」，而不是等网络层抛一个 `UnknownHostException`。
     *
     * ⚠️ 去尾斜杠必须放在**协议判定之后**：先 `trimEnd('/')` 的话，
     * `https://` 会被削成 `https:`，于是它认不出这是「用户已经写了协议」，
     * 又补一个 `https://` 上去，最后拼出 `https://https:`。
     */
    fun normalizeBase(input: String): String? {
        val t = input.trim()
        if (t.isEmpty()) return null
        if (t.any { it.isWhitespace() }) return null
        val withScheme = if (SCHEME_RE.containsMatchIn(t)) t else "https://$t"
        val rest = withScheme.substringAfter("://")
        val host = rest.substringBefore('/').substringBefore('?')
        if (!HOST_RE.matches(host)) return null
        return withScheme.trimEnd('/')
    }

    /**
     * 拼出一个绝对 URL。
     *
     * [base] 假定已经过 [normalizeBase]（没有尾部斜杠），这里对名字做百分号编码。
     * 我们的备份文件名是纯 ASCII，编码后原样不动；这一步是为了 base 里带中文路径
     * 的自建服务端也能正确工作。
     */
    fun joinUrl(base: String, name: String): String =
        base.trimEnd('/') + "/" + encodePathSegment(name)

    /** 按 RFC 3986 编码一段路径。保留 `-._~` 与字母数字，其余（含中文）转成 `%XX`。 */
    fun encodePathSegment(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            // ⚠️ Kotlin 的 Byte 是**有符号**的，中文的字节是负数，
            // 直接 `b.toChar()` 会得到 `\uFFxx` 这种乱码字符。
            val v = b.toInt() and 0xFF
            val ch = v.toChar()
            if (v < 0x80 && UNRESERVED.indexOf(ch) >= 0) sb.append(ch)
            else sb.append('%').append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /** [encodePathSegment] 的逆运算。畸形转义（`%ZZ`、结尾一个孤零零的 `%`）原样保留。 */
    fun decodePathSegment(s: String): String {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val h = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (h != null) {
                    out.write(h)
                    i += 3
                    continue
                }
            }
            out.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    // ------------------------------------------------------------------ GitHub Gist

    /**
     * 新建一个（私密）Gist 并带上第一份备份。
     *
     * `public = false` 是**刻意的**：备份里装着用户的全部订阅源、收藏和笔记，
     * 公开 Gist 等于把它们挂在网上。私密 Gist 虽然也是「知道链接就能看」，
     * 但至少不会被搜索到、不会被列进别人的时间线。
     */
    fun gistCreateBody(name: String, content: String): String = Json.obj(
        "description" to GIST_DESCRIPTION,
        "public" to false,
        "files" to Json.Raw(oneFile(name, content))
    )

    /** 往已有 Gist 里加一个文件（同名则替换内容）。 */
    fun gistAddFileBody(name: String, content: String): String =
        Json.obj("files" to Json.Raw(oneFile(name, content)))

    /**
     * 删掉 Gist 里指定的文件。GitHub 的约定是**把值置为 null**，
     * 不是传一个 delete 标记 —— 这点和直觉相反，写错了会变成空操作
     * （返回 200 但文件还在，于是「保留 5 份」永远不生效）。
     */
    fun gistDeleteFilesBody(names: List<String>): String {
        val pairs = names.map { Pair<String, Any?>(it, null) }.toTypedArray()
        return Json.obj("files" to Json.Raw(Json.obj(*pairs)))
    }

    private fun oneFile(name: String, content: String): String =
        Json.obj(name to Json.Raw(Json.obj("content" to content)))

    /** 从 Gist 详情响应里取出所有文件名（用来判断「该删哪些」）。 */
    fun gistFileNames(json: String): List<String> =
        (Json.parse(json) as? Json.J.O)?.v?.get("files")?.asObject()?.keys?.toList()
            .orEmpty()

    /** 从 Gist 详情响应里取某个文件的内容；文件不存在返回 null。 */
    fun gistFileContent(json: String, name: String): String? =
        (Json.parse(json) as? Json.J.O)?.v?.get("files")
            ?.asObject()?.get(name)
            ?.asObject()?.get("content")
            ?.asString()

    /** 从「新建 Gist」的响应里取出系统分配的 id。取不到返回 null。 */
    fun gistIdOf(json: String): String? =
        (Json.parse(json) as? Json.J.O)?.v?.get("id")
            ?.asString()
            ?.takeIf { it.isNotBlank() }

    // ------------------------------------------------------------------ WebDAV

    /**
     * 从 `PROPFIND` 的 XML 响应里取出**文件名**列表（只最后一段，已解码）。
     *
     * 返回的是「裸」文件名，是否属于我们的备份由 [com.example.yuewen.data.util.BackupSpan.isOurs]
     * 判断 —— 这里刻意不做过滤：这个函数只管「响应里有哪些条目」，
     * 过滤规则只应该有一份（在 BackupSpan）。
     */
    fun davFileNames(xml: String): List<String> =
        HREF_RE.findAll(xml)
            .map { it.groupValues[1].trim().trimEnd('/') }
            // 目录自身的 href 会以 `/` 结尾，去掉之后最后一段就是目录名 ——
            // 它是「一条 href」，但不是「一个文件」，所以这里不特殊处理，
            // 交给 isOurs 筛掉。
            .map { decodePathSegment(it.substringAfterLast('/')) }
            .filter { it.isNotBlank() }
            .toList()
}
