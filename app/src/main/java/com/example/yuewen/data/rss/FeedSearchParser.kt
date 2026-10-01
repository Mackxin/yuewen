package com.example.yuewen.data.rss

import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.util.Json
import com.example.yuewen.data.util.asArray
import com.example.yuewen.data.util.asLongOr
import com.example.yuewen.data.util.asObject
import com.example.yuewen.data.util.asString

/**
 * 从网络搜到的一个订阅源（v2.0）。**纯数据，不依赖 Android**，方便离线单测。
 */
data class RemoteFeed(
    val title: String,
    val url: String,
    val site: String = "",
    val description: String = "",
    val subscribers: Int = 0,
    /** 来自哪个目录（Feedly / Bing / 关键词），界面上标一下来源更可信。 */
    val provider: String = ""
)

/**
 * 订阅源搜索结果的解析（纯 Kotlin）。
 *
 * 单拎出来的原因和 [com.example.yuewen.data.util.Json] 一样：
 * 这是唯一的纯逻辑部分，抽出来就能进 `tools/jvmtest` 离线回归；
 * 真正发请求的 [FeedSearch] 反而没什么可测的。
 *
 * - [parseFeedly]：Feedly 公开搜索接口 `POST/GET /v3/search/feeds` 的返回
 * - [parseBingNews]：Bing 新闻 RSS（按关键词生成）的返回
 */
object FeedSearchParser {

    /**
     * 解析 Bing 新闻 RSS 时用的占位 FeedSource。
     *
     * [RssParser.parse] 的签名要求传一个 source（留给将来「按源定制解析」用），
     * 这里只是走个过场，不影响解析结果。
     */
    private val BING_PLACEHOLDER = FeedSource("bing-news", "必应新闻", "", "推荐")

    /** Feedly 的 `results[]` 数组。字段：title / feedId("feed/https://…") / website / description / subscribers。 */
    fun parseFeedly(json: String): List<RemoteFeed> =
        Json.parseArray(json).mapNotNull { o ->
            val feedId = o["feedId"]?.asString().orEmpty()
            // feedId 形如 "feed/https://example.com/rss"，去掉前缀就是真实地址
            val url = feedId.removePrefix("feed/").trim()
            if (!url.startsWith("http")) return@mapNotNull null
            val title = o["title"]?.asString().orEmpty().ifBlank { hostOf(url) }
            RemoteFeed(
                title = title,
                url = url,
                site = o["website"]?.asString().orEmpty(),
                description = o["description"]?.asString().orEmpty().take(160),
                subscribers = o["subscribers"]?.asLongOr(0L)?.toInt() ?: 0,
                provider = "Feedly"
            )
        }.distinctBy { it.url.lowercase() }

    /** Bing 新闻 RSS 的 `channel.item[]`（XML，用 [RssParser] 解析后转过来）。 */
    fun parseBingNews(xml: String): List<RemoteFeed> =
        RssParser.parse(xml, BING_PLACEHOLDER).map { item ->
            RemoteFeed(
                title = item.title,
                url = item.link,
                site = "",
                description = item.summary.take(160),
                provider = "Bing 新闻"
            )
        }.distinctBy { it.url.lowercase() }.take(30)

    /**
     * 按关键词生成「订阅」地址（不用先搜到具体站点，也能有一条自己的专属信息流）。
     *
     * 返回 (显示名, 地址) 列表，按推荐顺序排。用户点一下就直接订阅 ——
     * 这是保证「刚装上就有内容」的另一条兜底路径。
     */
    fun keywordFeeds(query: String): List<Pair<String, String>> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8").replace("+", "%20")
        return listOf(
            "「$q」· 必应新闻" to "https://www.bing.com/news/search?q=$encoded&format=RSS",
            "「$q」· Google 新闻" to "https://news.google.com/rss/search?q=$encoded&hl=zh-CN&gl=CN&ceid=CN:zh-Hans"
        )
    }

    private fun hostOf(url: String): String =
        url.substringAfter("//").substringBefore('/').removePrefix("www.")
}
