package com.example.yuewen.data.rss

import android.content.Context
import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.text.RegexOption

/**
 * 探测结果：一次请求同时得到「是否可用 + 源名称 + 条目数 + 格式 + 最新几条标题」。
 *
 * @param samples 最新 3 条的标题，用于加源前先「试读」这个源靠不靠谱
 * @param kind    订阅源格式（RSS 2.0 / Atom / RSS 1.0）
 */
data class FeedProbe(
    val title: String,
    val count: Int,
    val error: String?,
    val samples: List<String> = emptyList(),
    val kind: String = ""
)

/**
 * 用 OkHttp 拉取 RSS XML，再交给 RssParser 解析。
 * HTTP 客户端统一走 [Http.client]（共享连接池 / 线程池）。
 */
class RssFetcher(@Suppress("UNUSED_PARAMETER") private val context: Context) {

    suspend fun fetch(source: FeedSource): List<RawItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(source.url)
                .header("User-Agent", Http.UA_FEED)
                .header("Accept", "application/rss+xml,application/atom+xml,application/xml,text/xml,*/*")
                .build()
            val xml = Http.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: return@withContext emptyList()
            }
            RssParser.parse(xml, source)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 校验一个 RSS/Atom 地址是否可用，并顺手把「源名称」取回来。
     *
     * 以前这里只返回错误信息，用户还得自己手填名称；现在一次请求拿齐三样东西，
     * 加源界面就能「粘贴地址 → 自动显示名称」。
     * error == null 表示可用；title 为空时调用方可用域名兜底。
     */
    suspend fun probe(url: String): FeedProbe = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", Http.UA_FEED)
                .header("Accept", "application/rss+xml,application/atom+xml,application/xml,text/xml,*/*")
                .build()
            val xml = Http.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext FeedProbe("", 0, "服务器返回 HTTP ${resp.code}（地址可能失效或需要登录）")
                }
                resp.body?.string() ?: ""
            }
            if (xml.isBlank()) return@withContext FeedProbe("", 0, "打开成功，但返回内容为空")

            // 常见误操作：把「网站首页」当成订阅地址粘贴了。
            // 服务器返回 200，但内容是 HTML 页面而不是订阅 XML —— 直接说清楚怎么修。
            val head = xml.take(600).lowercase()
            if (!head.contains("<rss") && !head.contains("<feed") && !head.contains("<rdf") &&
                (head.contains("<!doctype html") || head.contains("<html"))
            ) {
                return@withContext FeedProbe(
                    "", 0,
                    "这个地址返回的是网页而不是订阅源。点下面的「🔍 自动找订阅地址」，或者在网站里找「RSS / 订阅」链接再粘贴。"
                )
            }

            val items = RssParser.parse(xml, FeedSource(id = "", url = url, name = "", category = ""))
            if (items.isEmpty()) {
                return@withContext FeedProbe(
                    "", 0,
                    "能打开，但里面没有文章条目——这可能不是标准 RSS/Atom 源（请填 .xml / feed / rss 结尾的订阅地址）"
                )
            }
            val title = RssParser.parseTitle(xml).ifBlank { hostOf(url) }
            FeedProbe(
                title = title,
                count = items.size,
                error = null,
                samples = items.take(3).map { it.title },
                kind = RssParser.parseFeedKind(xml)
            )
        } catch (e: Exception) {
            FeedProbe("", 0, "无法连接：${e.message ?: e.javaClass.simpleName}（检查地址是否正确、手机是否能联网）")
        }
    }

    /** 兼容旧调用：只关心「能不能用」。 */
    suspend fun test(url: String): String? = probe(url).error

    /**
     * 自动发现：给定一个网站首页，抓取 HTML 后解析 <link rel="alternate" type="application/rss+xml">
     * 等标准订阅标签，返回可订阅的 Feed 候选列表（已补全相对地址）。
     */
    suspend fun discoverFeeds(homeUrl: String): List<FeedCandidate> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(homeUrl)
                .header("User-Agent", Http.UA_BROWSER)
                .build()
            val html = Http.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: return@withContext emptyList()
            }
            val out = mutableListOf<FeedCandidate>()
            val linkRegex = Regex("""<link\b[^>]*>""", setOf(RegexOption.IGNORE_CASE))
            linkRegex.findAll(html).forEach { m ->
                val tag = m.value
                val rel = attr(tag, "rel") ?: return@forEach
                if (!rel.contains("alternate", ignoreCase = true)) return@forEach
                val href = attr(tag, "href") ?: return@forEach
                val type = (attr(tag, "type") ?: "").lowercase()
                val looksFeed = type.contains("rss") || type.contains("atom") || type.contains("json") ||
                        href.contains("feed", ignoreCase = true) || href.contains("rss", ignoreCase = true) ||
                        href.contains(".xml", ignoreCase = true)
                if (looksFeed) {
                    out.add(FeedCandidate(resolve(href, homeUrl), attr(tag, "title") ?: "", type))
                }
            }
            out.distinctBy { it.url }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun hostOf(url: String): String =
        try {
            java.net.URI(url).host?.removePrefix("www.") ?: url
        } catch (_: Exception) {
            url
        }

    private fun attr(tag: String, name: String): String? {
        val m = Regex("""$name\s*=\s*["']([^"']*)["']""", setOf(RegexOption.IGNORE_CASE)).find(tag)
        return m?.groupValues?.getOrNull(1)
    }

    private fun resolve(href: String, base: String): String {
        return try {
            java.net.URL(java.net.URL(base), href).toString()
        } catch (_: Exception) {
            href
        }
    }
}

data class FeedCandidate(val url: String, val title: String, val type: String)
