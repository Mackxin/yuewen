package com.example.yuewen.data.update

import com.example.yuewen.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * 「从 GitHub 拉新版 APK」这条链路（v2.14）。
 *
 * 只做两件事：**问一下有没有新版本**、**把那个 apk 下载到本地**。
 * 装不装、什么时候装、要不要先申请权限，全都不归这里管 —— 那是 UI 层的事
 * （见 `ui/screens/UpdateDialog.kt` 与 `ApkInstaller`）。
 * 这样拆的好处是：这个文件没有任何 Android 上下文，出问题时好单独验证。
 *
 * ## 为什么是 GitHub Releases，而不是自己搭个接口
 *
 * 这个 App 本来就在 GitHub 上开发（仓库 `Mackxin/yuewen`），每次发版打一个 tag
 * 就是天然的发布记录：版本号、更新说明、apk 文件都在同一个地方，不用额外维护服务器。
 * `releases/latest` 这个接口**不需要 token**（公开仓库的只读接口），
 * 也就是说 App 里不需要塞任何密钥。
 *
 * ## 版本号怎么比
 *
 * GitHub 上的 tag 是给人看的 **versionName**（`v2.13.2`），而系统判断「能不能覆盖安装」
 * 看的是 **versionCode**（39、40…）。两者不是同一个东西，所以这里**按 versionName
 * 分段比较**：`2.13.2 > 2.13.1 > 2.9.0`（逐段比数值，缺的段按 0 算）。
 *
 * ⚠️ 别用字符串比：`"2.9.0" > "2.13.1"` 在字典序下是成立的，会把新版本判成旧版本。
 */
object UpdateChecker {

    /** 仓库的「最新发布」接口。公开仓库，无需鉴权。 */
    private const val API_LATEST =
        "https://api.github.com/repos/Mackxin/yuewen/releases/latest"

    /**
     * 下载目录（在 `cacheDir` 下面）。
     *
     * 放 cache 而不是 files：apk 装完就没用了，让系统在空间紧张时能自己回收。
     * 「装完立刻删」也有做（见 [downloadTo]），但万一用户中途退出就轮不到删 ——
     * 那时靠系统的缓存清理兜底。
     */
    private const val DIR_NAME = "updates"

    /**
     * 查最新版本。
     *
     * @param currentVersionName 当前装的版本，通常传 `BuildConfig.VERSION_NAME`
     * @return `Result.success(null)` 表示**已经是最新**；
     *         `Result.success(info)` 表示有新版本；
     *         `Result.failure` 表示查不了（没网 / 限流 / 该版本没附 apk）。
     *
     * ⚠️ 用 `Result` 而不是抛异常：调用方一定要在 UI 上把「查失败」和「已是最新」
     * 分开显示，否则用户会以为「点了没反应」。
     */
    suspend fun fetchLatest(currentVersionName: String): Result<UpdateInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(API_LATEST)
                    .header("Accept", "application/vnd.github+json")
                    // GitHub 的接口**必须带 User-Agent**，不带直接 403。
                    // 复用 App 自己的 UA，方便在 GitHub 的访问日志里认出是谁在请求。
                    .header("User-Agent", Http.UA_FEED)
                    .build()

                Http.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        // 403 基本都是「按 IP 限流」（未登录时每小时 60 次），
                        // 说人话比丢一个 HTTP 码有用
                        val why = when (resp.code) {
                            403, 429 -> "GitHub 限流了（每小时 60 次），过一会儿再试"
                            404 -> "仓库或发布页不存在"
                            else -> "GitHub 返回 HTTP ${resp.code}"
                        }
                        error(why)
                    }
                    val body = resp.body?.string().orEmpty()
                    parseLatest(body, currentVersionName)
                }
            }
        }

    /**
     * 解析 `releases/latest` 的响应。
     *
     * 抽成独立函数是为了**能被单测直接喂字符串**，不用起网络。
     */
    fun parseLatest(json: String, currentVersionName: String): UpdateInfo? {
        val root = JSONObject(json)
        val tag = root.optString("tag_name").trim()
        val version = normalizeVersion(tag)
        if (version.isBlank()) error("发布标签里读不出版本号：$tag")

        val assets = root.optJSONArray("assets") ?: JSONArray()
        val apk = pickApkAsset(assets, version) ?: error("这个版本没有附带 .apk 文件")

        // 不比当前版本新 → 什么都不返回（调用方据此显示「已是最新」）
        if (compareVersion(version, currentVersionName) <= 0) return null

        return UpdateInfo(
            versionName = version,
            tagName = tag,
            notes = root.optString("body").trim(),
            sizeBytes = apk.second,
            downloadUrl = apk.first,
            publishedAt = root.optString("published_at").trim()
        )
    }

    /**
     * 从资产列表里挑出要下载的那个 apk。
     *
     * 一个 release 里可能同时挂 debug 包和正式包（本仓库就是这样）。
     * 优先级：
     * 1. 名字正好是 `yuewen-v<版本>.apk`（正式包的标准命名）；
     * 2. 随便一个 apk，但**名字里不能有 debug**；
     * 3. 兜底：第一个 apk（哪怕叫 debug —— 总比下不了强）。
     *
     * @return `downloadUrl to sizeBytes`，找不到 apk 时返回 null
     */
    private fun pickApkAsset(assets: JSONArray, version: String): Pair<String, Long>? {
        data class Cand(val name: String, val url: String, val size: Long)

        val all = ArrayList<Cand>(assets.length())
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            // browser_download_url 是给浏览器点的地址，会 302 到 CDN；
            // OkHttp 默认跟随重定向，直接用就行
            val url = a.optString("browser_download_url")
            if (url.isBlank()) continue
            all += Cand(name, url, a.optLong("size"))
        }
        if (all.isEmpty()) return null

        val exact = all.firstOrNull { it.name.equals("yuewen-v$version.apk", ignoreCase = true) }
        val release = all.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
        val chosen = exact ?: release ?: all.first()
        return chosen.url to chosen.size
    }

    /**
     * 把下载好的 apk 读进 `cacheDir/updates/`。
     *
     * ⚠️ 下载前**先清掉同目录的旧文件**：每次更新都往 cache 里塞一个几十 MB 的包，
     * 攒几次就能吃掉几百 MB，而用户完全不知道是「更新」留下的。
     *
     * @param onProgress 0..100。`contentLength` 不明时（服务端没给 Content-Length）
     *   会一直回报 0，UI 那边就显示不确定进度 —— 不假装自己知道。
     */
    suspend fun downloadTo(
        context: android.content.Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        // 清场：上一次下载的残留（同目录下的所有 apk）
        dir.listFiles()?.forEach { if (it.isFile) it.delete() }

        val name = "yuewen-v${info.versionName}.apk"
        val target = File(dir, name)
        val tmp = File(dir, "$name.part")

        val req = Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", Http.UA_FEED)
            .header("Accept", "application/octet-stream")
            .build()

        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("下载失败：HTTP ${resp.code}")
            val body = resp.body ?: error("下载失败：响应是空的")
            val total = body.contentLength()          // -1 = 服务端没告诉长度
            var done = 0L

            tmp.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        // 用户点「取消」时协程会被 cancel，这里及时退出，
                        // 否则会一直下到天荒地老
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            if (total > 0 && done < total) error("下载不完整（${done}/${total} 字节）")
        }

        // 先写 .part 再改名：中途失败/被杀时，目录里不会留一个「看起来完整」的半截 apk
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) error("下载完了但存不下来（存储空间不足？）")
        onProgress(100)
        target
    }

    /**
     * 把 tag / 版本字符串归一成 `2.13.2` 这种纯数字点的形式。
     *
     * 兼容 `v2.13.2`、`V2.13.2`、`2.13.2`、`release-2.13.2`。
     * 取**第一个**形如 `数字(.数字)*` 的片段，后面跟的 `-beta`、`+20261106` 之类一律丢掉。
     */
    fun normalizeVersion(raw: String): String {
        val m = Regex("""\d+(?:\.\d+)*""").find(raw.trim()) ?: return ""
        return m.value.trimEnd('.')
    }

    /**
     * 比较两个版本号。
     *
     * @return 正数表示 [a] 新，负数表示 [b] 新，0 表示一样。
     *
     * 逐段比**数值**，缺的段按 0 补：`2.13` == `2.13.0`，`2.13.2` > `2.13.1`，
     * `2.9.0` < `2.13.0`（这就是为什么不能用字符串比）。
     */
    fun compareVersion(a: String, b: String): Int {
        val pa = normalizeVersion(a).split('.').filter { it.isNotBlank() }
        val pb = normalizeVersion(b).split('.').filter { it.isNotBlank() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return x - y
        }
        return 0
    }
}

/**
 * 一个可安装的新版本。
 *
 * @param versionName 归一后的版本号（`2.13.2`），用于显示和比较
 * @param tagName     GitHub 上的原始 tag（`v2.13.2`），只用于排查问题
 * @param notes       发布说明（release body），直接显示给用户看改了什么
 * @param sizeBytes   apk 大小，提前告诉用户「要下 3.9MB」比下完才知道更友好
 * @param downloadUrl 资产的下载地址（会重定向到 CDN，OkHttp 自动跟）
 * @param publishedAt 发布时间，ISO8601 字符串
 */
data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val notes: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val publishedAt: String
) {
    /** 人类可读的大小，例如 `3.9 MB`。 */
    fun sizeText(): String = when {
        sizeBytes <= 0L -> "未知大小"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
        else -> String.format("%.1f MB", sizeBytes / 1024.0 / 1024.0)
    }
}
