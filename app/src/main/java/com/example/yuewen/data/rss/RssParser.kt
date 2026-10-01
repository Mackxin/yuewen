package com.example.yuewen.data.rss

import com.example.yuewen.data.model.FeedSource
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 解析 RSS 2.0 (<item>) 与 Atom (<entry>) 的轻量实现，不依赖第三方库。
 * 兼容常见字段：title / link / description|summary / content|content:encoded /
 * pubDate|published|updated / media:thumbnail / enclosure，并从 HTML 里兜底抽第一张图。
 */
data class RawItem(
    val title: String,
    val link: String,
    val summary: String,
    val content: String,
    val imageUrl: String?,
    val pubDate: Long
)

object RssParser {

    // source 目前只用于未来的「按源定制解析」，保留签名方便调用方统一传参
    @Suppress("UNUSED_PARAMETER")
    fun parse(xml: String, source: FeedSource): List<RawItem> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val items = mutableListOf<RawItem>()
        var event = parser.eventType
        var inItem = false
        var title = ""
        var link = ""
        var summary = ""
        var content = ""
        var imageUrl: String? = null
        var pubDate = 0L

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = qualifiedName(parser)
                    when {
                        name == "item" || name == "entry" -> {
                            inItem = true
                            title = ""; link = ""; summary = ""; content = ""
                            imageUrl = null; pubDate = 0L
                        }
                        inItem && name == "title" -> title = parser.nextText().orEmpty().trim()
                        inItem && name == "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                            link = if (href != null) href else parser.nextText().orEmpty().trim()
                        }
                        inItem && (name == "description" || name == "summary") -> {
                            summary = parser.nextText().orEmpty().trim()
                        }
                        inItem && (name == "content" || name == "content:encoded") -> {
                            content = parser.nextText().orEmpty().trim()
                        }
                        inItem && (name == "pubdate" || name == "published" || name == "updated" || name == "dc:date") -> {
                            pubDate = parseDate(parser.nextText().orEmpty().trim())
                        }
                        inItem && (name == "media:thumbnail" || name == "media:content") -> {
                            if (imageUrl == null) imageUrl = parser.getAttributeValue(null, "url")
                        }
                        inItem && name == "enclosure" -> {
                            if (imageUrl == null) {
                                val type = parser.getAttributeValue(null, "type") ?: ""
                                val url = parser.getAttributeValue(null, "url")
                                if (type.startsWith("image") || url != null) imageUrl = url
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = qualifiedName(parser)
                    if ((name == "item" || name == "entry") && inItem) {
                        inItem = false
                        if (title.isNotBlank() && link.isNotBlank()) {
                            // 图片地址优先取 media/enclosure，其次从摘要 / 正文 HTML 里捞。
                            // 最后务必补全成绝对地址 —— 源里写 "/img/a.jpg" 这种相对路径很常见，
                            // 直接塞给 Coil 是加载不出来的。
                            val rawImg = imageUrl ?: extractImage(summary) ?: extractImage(content)
                            val finalImg = absolutize(rawImg, link)
                            val body = summary.ifBlank { content }
                            items.add(RawItem(title, link, stripHtml(body), content, finalImg, pubDate))
                        }
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    /**
     * 只取订阅源的名称（RSS 2.0 的 <channel><title>，Atom 的 <feed><title>）。
     * 用于「粘贴 RSS 地址后自动显示这个地址的名称」，避免用户手填。
     * 取第一条「不在 item/entry 内部」的 title。
     */
    fun parseTitle(xml: String): String {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var depthInsideItem = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name?.lowercase() ?: ""
                        if (name == "item" || name == "entry") {
                            depthInsideItem = true
                        } else if (name == "title" && !depthInsideItem) {
                            val t = parser.nextText().orEmpty().trim()
                            if (t.isNotBlank()) return t
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name?.lowercase() ?: ""
                        if (name == "item" || name == "entry") depthInsideItem = false
                    }
                }
                event = parser.next()
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 判断订阅源格式：RSS 2.0 / RSS 1.0(RDF) / Atom。
     * 用于加源界面告诉用户「这是个什么格式的源」，也让测试结果更有信息量。
     */
    fun parseFeedKind(xml: String): String {
        val head = xml.take(2000).lowercase()
        return when {
            head.contains("<feed") -> "Atom"
            head.contains("<rdf:rdf") -> "RSS 1.0"
            head.contains("<rss") -> "RSS 2.0"
            else -> "未知格式"
        }
    }

    /**
     * 当前元素的**限定名**（含命名空间前缀），一律小写。
     *
     * ⚠️ 这是个容易踩的坑：解析器开了 `isNamespaceAware = true` 之后，
     * `XmlPullParser.name` 只返回**局部名**——`<content:encoded>` 拿到的是 "encoded"、
     * `<media:thumbnail>` 是 "thumbnail"、`<dc:date>` 是 "date"。
     * 所以直接拿 name 去比 "content:encoded" 永远不成立（死代码）。
     * 结果就是：WordPress 系源（占全网 RSS 很大比例）的**正文、缩略图、发布时间全丢**。
     * 这里把 prefix 拼回来，既保住了原有写法，也让匹配真正生效。
     */
    private fun qualifiedName(parser: XmlPullParser): String {
        val local = parser.name?.lowercase() ?: ""
        val prefix = try {
            parser.prefix?.lowercase()
        } catch (_: Exception) {
            null
        }
        return if (prefix.isNullOrEmpty()) local else "$prefix:$local"
    }

    private fun parseDate(s: String): Long {
        if (s.isBlank()) return 0L
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(s, Instant::from).toEpochMilli()
        } catch (_: Exception) {
        }
        try {
            return Instant.parse(s).toEpochMilli()
        } catch (_: Exception) {
        }
        try {
            return ZonedDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: Exception) {
        }
        return 0L
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 从一段 HTML 里找出第一张「能真正加载」的图片地址。
     *
     * 这里踩过的坑（v1.5.0 修）：
     * 1. 老实现只认 `<img src="...">` 这种**双引号 + src** 的写法。
     *    但实际源里单引号、无引号都很常见，直接漏掉。
     * 2. 大量中文源用**懒加载**：`src` 放的是 1x1 透明占位图，
     *    真图在 `data-src` / `data-original` / `data-lazy-src` / `data-actualsrc` 上。
     *    所以要先看 data-* 再看 src。
     */
    private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val IMG_ATTRS = listOf("data-src", "data-original", "data-lazy-src", "data-actualsrc", "src", "srcset")

    private fun extractImage(html: String): String? {
        if (html.isBlank()) return null
        for (tag in IMG_TAG.findAll(html).map { it.value }) {
            for (attr in IMG_ATTRS) {
                var v = attrValue(tag, attr)?.trim() ?: continue
                if (v.isEmpty()) continue
                // srcset 形如 "a.jpg 1x, b.jpg 2x"，取第一个候选
                if (attr == "srcset") v = v.substringBefore(',').trim().substringBefore(' ')
                // base64 内联图（占位图）没有单独加载的价值
                if (v.startsWith("data:")) continue
                return v
            }
        }
        return null
    }

    /**
     * 读标签属性。用 `(?<![\w-])` 保证「属性名前面不能是字母/数字/连字符」——
     * 否则找 `src` 会误命中 `data-src` 里的 src。
     */
    private fun attrValue(tag: String, name: String): String? {
        val re = Regex(
            "(?<![\\w-])${Regex.escape(name)}\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
            RegexOption.IGNORE_CASE
        )
        val m = re.find(tag) ?: return null
        return m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
    }

    /**
     * 把可能是相对路径的图片地址补成绝对地址。
     * `base` 用文章链接（RSS 里的 link），这是唯一可靠的参照。
     */
    private fun absolutize(url: String?, base: String): String? {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return null
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        if (u.startsWith("data:")) return null
        // 协议相对地址 "//img.example.com/a.jpg"
        if (u.startsWith("//")) return "https:$u"
        if (!base.startsWith("http")) return null
        return try {
            java.net.URI(base).resolve(u).toString()
        } catch (_: Exception) {
            null
        }
    }
}
