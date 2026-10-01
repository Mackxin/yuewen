package com.example.yuewen.data.rss

import com.example.yuewen.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 「阅源 → 发现」里的在线订阅源搜索（v2.0）。
 *
 * 走 Feedly 的公开搜索接口：它本身是个 RSS 阅读器服务，
 * `/v3/search/feeds` 不需要登录就能查，覆盖了绝大多数公开订阅源，
 * 返回里还带订阅人数（[RemoteFeed.subscribers]）—— 正好可以当作「这个源靠不靠谱」的参考。
 *
 * 失败不抛异常，统一返回 [Result]，让界面能优雅地退回「只用内置推荐」。
 */
object FeedSearch {

    private const val ENDPOINT = "https://cloud.feedly.com/v3/search/feeds"

    suspend fun searchFeeds(query: String, limit: Int = 20): Result<List<RemoteFeed>> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext Result.success(emptyList())
            runCatching {
                val url = "$ENDPOINT?query=${java.net.URLEncoder.encode(q, "UTF-8")}&count=$limit"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", Http.UA_BROWSER)
                    .header("Accept", "application/json")
                    .build()
                Http.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    FeedSearchParser.parseFeedly(resp.body?.string().orEmpty())
                }
            }
        }

    /** 按关键词订阅：生成的其实是「必应新闻 / Google 新闻」的搜索结果流，永远有内容。 */
    fun keywordFeeds(query: String): List<RemoteFeed> =
        FeedSearchParser.keywordFeeds(query).map { (name, url) ->
            RemoteFeed(title = name, url = url, provider = "关键词订阅")
        }
}
