package com.example.yuewen.data.backup

import com.example.yuewen.data.net.Http
import com.example.yuewen.data.util.BackupSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 消息可以直接展示给用户看的异常。
 *
 * 网络层把所有失败（HTTP 401 / 连不上 / 超时 / 响应没法解析）都翻译成
 * 一句中文再抛出来 —— 否则界面层只能拿到 `UnknownHostException: Unable to resolve host`
 * 这种字符串，既没法展示也没法让用户知道下一步该改什么。
 */
class BackupException(message: String) : Exception(message)

/**
 * 云备份的网络实现（v2.9）：GitHub Gist + WebDAV。
 *
 * 两条通道的**语义刻意做成一致的**：
 * - `backup(...)` 上传一份，返回「这次用到的远端标识」（Gist 是 gist id，WebDAV 不需要）；
 * - `names(...)` 列出远端已有的备份文件名；
 * - `fetch(...)` 取回最新的那份内容；
 * - `delete(...)` 删掉指定的几份。
 *
 * 「保留最近 N 份」是**调用方**按 [BackupSpan.toPrune] 算好之后调 `delete` 的，
 * 不写在这里：两条通道的目录结构不一样（Gist 是扁平的 files，
 * WebDAV 是真实目录），但「留几份」的规则只应该有一处。
 *
 * 本地文件夹那条通道不在这里 —— 它走 SAF（`content://`）而不是 HTTP，
 * 见 [LocalBackup]。
 */
object RemoteBackup {

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val XML_TYPE = "application/xml; charset=utf-8".toMediaType()

    /** `PROPFIND` 的请求体：只要名字和是不是目录，不要大字段。 */
    private const val PROPFIND_BODY =
        "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
                "<d:resourcetype/><d:getcontentlength/>" +
                "</d:prop></d:propfind>"

    // ================================================================== GitHub Gist

    /**
     * 上传一份备份到 Gist。
     *
     * [gistId] 为空时**新建**一个私密 Gist 并把这次的内容写进去，
     * 返回值是新建出来的 id（调用方必须存下来，否则下次又会新建一个，
     * 用户的 GitHub 上会堆出一串 Gist）。
     * 已有 id 时直接把文件加进去。
     *
     * @return 这次实际写入的 gist id
     */
    suspend fun gistBackup(token: String, gistId: String, name: String, content: String): String {
        val tk = Remote.normalizeToken(token)
        if (tk.isEmpty()) throw BackupException("还没有填 GitHub 令牌")
        return if (gistId.isBlank()) {
            val resp = call(
                Request.Builder()
                    .url("${Remote.GIST_API}/gists")
                    .post(Remote.gistCreateBody(name, content).toRequestBody(JSON_TYPE))
                    .gistHeaders(tk)
                    .build()
            )
            resp.use {
                it.ensureOk("新建 Gist")
                Remote.gistIdOf(it.bodyText())
                    ?: throw BackupException("GitHub 没有返回 Gist 标识，请稍后重试")
            }
        } else {
            val resp = call(
                Request.Builder()
                    .url("${Remote.GIST_API}/gists/$gistId")
                    .patch(Remote.gistAddFileBody(name, content).toRequestBody(JSON_TYPE))
                    .gistHeaders(tk)
                    .build()
            )
            resp.use {
                // 404 单拎出来给一句人话：Gist 被用户在网页上删掉是很常见的事，
                // 这时候应该提示「把 Gist 标识清空重新建一个」，而不是干巴巴的 HTTP 404。
                if (it.code == 404) throw BackupException("原来的 Gist 已经不在了，请清空 Gist 标识后重新备份")
                it.ensureOk("写入 Gist")
            }
            gistId
        }
    }

    /** 列出 Gist 里的备份文件名（含非备份文件，由调用方用 [BackupSpan] 筛）。 */
    suspend fun gistNames(token: String, gistId: String): List<String> {
        val tk = Remote.normalizeToken(token)
        if (tk.isEmpty() || gistId.isBlank()) return emptyList()
        val resp = call(
            Request.Builder()
                .url("${Remote.GIST_API}/gists/$gistId")
                .get()
                .gistHeaders(tk)
                .build()
        )
        resp.use {
            if (it.code == 404) return emptyList()
            it.ensureOk("读取 Gist")
            return Remote.gistFileNames(it.bodyText())
        }
    }

    /** 取回 Gist 上最新的一份备份。返回 (文件名, 内容)；一份都没有返回 null。 */
    suspend fun gistFetchLatest(token: String, gistId: String): Pair<String, String>? {
        val tk = Remote.normalizeToken(token)
        if (tk.isEmpty() || gistId.isBlank()) return null
        val resp = call(
            Request.Builder()
                .url("${Remote.GIST_API}/gists/$gistId")
                .get()
                .gistHeaders(tk)
                .build()
        )
        val json = resp.use {
            if (it.code == 404) throw BackupException("找不到这个 Gist，可能已被删除")
            it.ensureOk("读取 Gist")
            it.bodyText()
        }
        val name = BackupSpan.newestFirst(Remote.gistFileNames(json)).firstOrNull() ?: return null
        val content = Remote.gistFileContent(json, name)
            ?: throw BackupException("Gist 里的备份文件读不出来")
        return name to content
    }

    /** 从 Gist 删掉指定的几个备份文件。 */
    suspend fun gistDelete(token: String, gistId: String, names: List<String>) {
        if (names.isEmpty()) return
        val tk = Remote.normalizeToken(token)
        if (tk.isEmpty() || gistId.isBlank()) return
        val resp = call(
            Request.Builder()
                .url("${Remote.GIST_API}/gists/$gistId")
                .patch(Remote.gistDeleteFilesBody(names).toRequestBody(JSON_TYPE))
                .gistHeaders(tk)
                .build()
        )
        resp.use { it.ensureOk("清理旧的 Gist 备份") }
    }

    private fun Request.Builder.gistHeaders(token: String): Request.Builder = apply {
        header("Authorization", "Bearer $token")
        header("Accept", Remote.GIST_ACCEPT)
        // ⚠️ GitHub 的 API **强制要求** User-Agent，缺了它一律回 403，
        // 而 403 的文案是「权限不足」，会把排查方向完全带偏。
        header("User-Agent", Http.UA_FEED)
    }

    // ================================================================== WebDAV

    /** 确保目录存在（不存在就创建）。已存在时服务器回 405，属正常情况。 */
    suspend fun davEnsureDir(base: String, user: String, pass: String) {
        val resp = call(
            Request.Builder()
                .url(base.trimEnd('/') + "/")
                .method("MKCOL", null)
                .davHeaders(user, pass)
                .build()
        )
        resp.use {
            // 201 = 建好了；405 = 已经存在（多数服务端）；200/204 也有服务端这么回。
            // 409 = 上级目录不存在 —— 这个必须报错，否则后面 PUT 会一路失败。
            when (it.code) {
                201, 200, 204, 405 -> Unit
                409 -> throw BackupException("地址里的上级目录不存在，请检查 WebDAV 地址")
                else -> it.ensureOk("创建备份目录")
            }
        }
    }

    /** 上传一份备份。 */
    suspend fun davBackup(
        base: String,
        user: String,
        pass: String,
        name: String,
        content: String
    ) {
        davEnsureDir(base, user, pass)
        val resp = call(
            Request.Builder()
                .url(Remote.joinUrl(base, name))
                .put(content.toRequestBody(JSON_TYPE))
                .davHeaders(user, pass)
                .build()
        )
        resp.use {
            when (it.code) {
                200, 201, 204 -> Unit
                403 -> throw BackupException("服务器不允许写入这个目录，请换一个地址")
                507 -> throw BackupException("网盘空间不足")
                else -> it.ensureOk("上传到 WebDAV")
            }
        }
    }

    /** 列出目录里的文件名（含目录自身以外的所有条目，由调用方筛）。 */
    suspend fun davNames(base: String, user: String, pass: String): List<String> {
        val resp = call(
            Request.Builder()
                .url(base.trimEnd('/') + "/")
                // Depth: 1 = 只看这一层。缺了它有的服务端默认按 infinity 递归，
                // 目录一深就会拖到超时。
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_TYPE))
                .header("Depth", "1")
                .davHeaders(user, pass)
                .build()
        )
        resp.use {
            if (it.code == 404) return emptyList()
            it.ensureOk("列目录")
            return Remote.davFileNames(it.bodyText())
        }
    }

    /** 取回指定的备份内容。 */
    suspend fun davFetch(
        base: String,
        user: String,
        pass: String,
        name: String
    ): String {
        val resp = call(
            Request.Builder()
                .url(Remote.joinUrl(base, name))
                .get()
                .davHeaders(user, pass)
                .build()
        )
        resp.use {
            if (it.code == 404) throw BackupException("这份备份已经不在了")
            it.ensureOk("下载备份")
            return it.bodyText()
        }
    }

    /** 删掉指定的几份备份。404 当成「已经没了」，不当失败。 */
    suspend fun davDelete(base: String, user: String, pass: String, names: List<String>) {
        names.forEach { name ->
            val resp = call(
                Request.Builder()
                    .url(Remote.joinUrl(base, name))
                    .delete()
                    .davHeaders(user, pass)
                    .build()
            )
            resp.use {
                when (it.code) {
                    200, 204, 404 -> Unit
                    else -> it.ensureOk("清理旧的 WebDAV 备份")
                }
            }
        }
    }

    private fun Request.Builder.davHeaders(user: String, pass: String): Request.Builder = apply {
        if (user.isNotEmpty() || pass.isNotEmpty()) {
            header("Authorization", Remote.basicAuth(user, pass))
        }
        header("User-Agent", Http.UA_FEED)
    }

    // ================================================================== 公共

    private suspend fun call(req: Request): Response = withContext(Dispatchers.IO) {
        try {
            Http.client.newCall(req).execute()
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            // 统一翻译成「人话」：用户看到的应该是一句能据以行动的提示，
            // 而不是 `java.net.UnknownHostException: Unable to resolve host "dav.xxx"`。
            throw BackupException(networkMessage(e))
        }
    }

    private fun networkMessage(e: Exception): String {
        val text = (e.message ?: "").lowercase()
        return when {
            text.contains("unable to resolve host") || text.contains("no address") ->
                "域名解析不了，请检查地址是否写对"
            text.contains("timeout") || text.contains("timed out") ->
                "连接超时，请检查网络后重试"
            text.contains("ssl") || text.contains("certificate") ->
                "HTTPS 证书校验失败，如果这是自建网盘可以改用 http:// 地址"
            text.contains("failed to connect") || text.contains("connection refused") ->
                "连不上服务器，请检查地址和端口"
            else -> "网络请求失败：" + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun Response.bodyText(): String = body?.string().orEmpty()

    /**
     * 非 2xx 时抛出带中文说明的 [BackupException]。
     *
     * ⚠️ `401` 的文案刻意**不区分**「令牌错了」和「密码错了」——
     * 这个方法两个通道共用，无从判断。具体提示由调用方在各自的入口处补。
     */
    private fun Response.ensureOk(action: String) {
        if (isSuccessful) return
        throw BackupException(
            when (code) {
                401 -> "$action 失败：认证没通过（令牌或账号密码不对）"
                403 -> "$action 失败：服务器拒绝了这个请求（权限不足或触发频率限制）"
                404 -> "$action 失败：目标不存在"
                413 -> "$action 失败：备份文件太大，服务器拒收"
                500, 502, 503, 504 -> "$action 失败：服务器出错了（HTTP $code），稍后再试"
                else -> "$action 失败：HTTP $code"
            }
        )
    }
}
