package com.example.yuewen.data.opml

import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.model.sourceIdOf
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * OPML 导入 / 导出。
 *
 * OPML 是 RSS 阅读器之间「搬家订阅列表」的事实标准：Feeder、NetNewsWire、FreshRSS、
 * Capy Reader 等都能读写。有了它，用户换手机或换 App 时不用一个个手动重加。
 *
 * 不依赖第三方库：导出直接拼 XML，导入用平台自带的 XmlPullParser（和 RssParser 同一套路）。
 */
object Opml {

    /** 单次导入上限，防止误选超大文件把 App 卡死。 */
    private const val MAX_IMPORT = 300

    // ---------------- 导出 ----------------

    fun export(sources: List<FeedSource>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head>\n")
        sb.append("    <title>阅闻订阅源</title>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")
        // 按分类分组嵌套 outline，和主流阅读器的导出格式保持一致
        val groups = sources.groupBy { it.category.ifBlank { "" } }
        groups.forEach { (category, list) ->
            if (category.isBlank()) {
                list.forEach { sb.append(feedOutline(it, "    ")) }
            } else {
                sb.append("    <outline text=\"${esc(category)}\" title=\"${esc(category)}\">\n")
                list.forEach { sb.append(feedOutline(it, "      ")) }
                sb.append("    </outline>\n")
            }
        }
        sb.append("  </body>\n")
        sb.append("</opml>\n")
        return sb.toString()
    }

    private fun feedOutline(s: FeedSource, indent: String): String {
        val cat = if (s.category.isNotBlank()) " category=\"${esc(s.category)}\"" else ""
        return "$indent<outline type=\"rss\" text=\"${esc(s.name)}\" title=\"${esc(s.name)}\" " +
                "xmlUrl=\"${esc(s.url)}\"$cat />\n"
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    // ---------------- 导入 ----------------

    /**
     * 解析 OPML。兼容两种常见写法：
     * 1. 扁平列表（所有 outline 平铺，靠 `category` 属性分类）
     * 2. 嵌套分组（父 outline 的 text 当作分类）
     * 同一个地址只保留一次；id 由地址推导，重复导入不会产生重复源。
     */
    fun parse(xml: String): List<FeedSource> {
        val out = LinkedHashMap<String, FeedSource>() // key = url，天然去重
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            // 嵌套层级里各层的标题，用于推断分类
            val stack = ArrayDeque<String>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name?.lowercase() == "outline") {
                            val text = attr(parser, "text") ?: attr(parser, "title") ?: ""
                            val xmlUrl = attr(parser, "xmlurl") ?: attr(parser, "url")
                            if (!xmlUrl.isNullOrBlank() && xmlUrl.startsWith("http")) {
                                val catAttr = attr(parser, "category")
                                val category = catAttr?.substringAfterLast('/')?.trim()
                                    ?.takeIf { it.isNotBlank() }
                                    ?: stack.lastOrNull()?.takeIf { it.isNotBlank() }
                                    ?: "未分类"
                                val name = text.ifBlank { hostOf(xmlUrl) }
                                if (out.size < MAX_IMPORT && !out.containsKey(xmlUrl)) {
                                    out[xmlUrl] = FeedSource(
                                        // v2.0.2：统一用地址派生的 id。
                                        // 原来的 opml_<hash> 在「同一个地址导入两次」时会产生同一个 id，
                                        // 现在交给 sourceIdOf 保证唯一（真撞了也还有 sanitizeSources 兜底）。
                                        id = sourceIdOf(xmlUrl),
                                        name = name,
                                        url = xmlUrl,
                                        category = category,
                                        enabled = true
                                    )
                                }
                            }
                            stack.addLast(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name?.lowercase() == "outline") {
                            if (stack.isNotEmpty()) stack.removeLast()
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // 解析失败就返回已读到的部分，不抛给调用方
        }
        return out.values.toList()
    }

    /**
     * 读属性。⚠️ 必须逐个比对并忽略大小写：
     * XML 属性名本来是区分大小写的，但 OPML 的作者们写法五花八门——
     * `xmlUrl`（规范写法）、`xmlurl`、`XMLURL`、`xmlURL` 都真实存在。
     * 之前写成 getAttributeValue(null, "xmlurl") ?: getAttributeValue(null, "XMLURL")，
     * 结果连自家导出的 `xmlUrl` 都匹配不上，导入永远返回 0 条。
     */
    private fun attr(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).equals(name, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun hostOf(url: String): String = try {
        java.net.URI(url).host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }
}
