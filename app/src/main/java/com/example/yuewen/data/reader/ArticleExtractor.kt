package com.example.yuewen.data.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import com.example.yuewen.data.net.Http
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * 正文抽取器：进详情页时把「摘要」升级成「全文」，不再跳浏览器。
 *
 * 核心用 **Readability4J**（https://github.com/dankito/Readability4J）——
 * 它是 Mozilla Readability.js 的 Kotlin 移植，也就是 Firefox「阅读模式」背后的同款算法，
 * 会自动剔除广告、导航栏、社交按钮等噪音，只留正文。
 * 抽取失败时退回 jsoup 的启发式（挑文字最密集的容器），保证「能看」。
 */
class ArticleExtractor {

    /** 抓取网页并抽取正文纯文本；失败返回 null。 */
    suspend fun extract(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", Http.UA_BROWSER)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
            val html = Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string() ?: return@withContext null
            }
            extractFromHtml(url, html)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 纯函数版：便于将来做单元测试。
     *
     * v1.7.0 起返回值从「纯文本」升级为**轻量 HTML**（`<p>` 段落 + `<img>` 图片交替）：
     * 以前 toPlainText 把 <img> 全剥掉了，详情页正文一张图都没有；
     * 现在图片在抽取阶段就保留下来（img 单独成块、属性按懒加载优先级挑、相对地址补全）。
     */
    fun extractFromHtml(url: String, html: String): String? {
        // 1) Readability4J —— Firefox 阅读模式同款
        val readable: String? = try {
            val article = Readability4J(url, html).parse()
            val contentHtml = article.content
            val blocks = if (!contentHtml.isNullOrBlank()) {
                BodyBlocks.parse(contentHtml, url)
            } else {
                BodyBlocks.parse(article.textContent.orEmpty(), url)
            }
            // 抽取质量门槛照旧按「有效文本长度」算，图片不计入
            if (BodyBlocks.textLength(blocks) >= 150) BodyBlocks.toLightHtml(blocks) else null
        } catch (_: Throwable) {
            null
        }
        if (!readable.isNullOrBlank()) {
            return readable.trim()
        }

        // 2) 兜底：jsoup 挑正文容器（文字 / 段落密度最高者）。兜底路径拿不到结构，只保文本
        return fallbackExtract(html)
    }

    private fun fallbackExtract(html: String): String? {
        val doc = Jsoup.parse(html)
        doc.select("script,style,nav,header,footer,aside,form,iframe,noscript,svg").remove()
        var best: String? = null
        var bestScore = 0
        for (el in doc.select("article,main,div,section")) {
            val paragraphLen = el.select("p").sumOf { it.text().length }
            val score = maxOf(paragraphLen, el.text().length / 3)
            if (paragraphLen > 80 && score > bestScore) {
                bestScore = score
                best = el.text().trim()
            }
        }
        val result = best ?: doc.body().text().trim()
        return result.takeIf { it.length >= 150 }
    }
}
