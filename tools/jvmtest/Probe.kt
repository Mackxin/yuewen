import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.rss.RssParser

fun main() {
    // 典型的 WordPress 源：content:encoded 里有全文 + media:thumbnail 缩略图 + dc:date
    val wordpress = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"
             xmlns:content="http://purl.org/rss/1.0/modules/content/"
             xmlns:dc="http://purl.org/dc/elements/1.1/"
             xmlns:media="http://search.yahoo.com/mrss/">
          <channel>
            <title>WordPress 源</title>
            <item>
              <title>带命名空间的一篇</title>
              <link>https://wp.example.com/1</link>
              <description>短摘要</description>
              <content:encoded><![CDATA[<p>这是完整正文，比摘要长得多。</p>]]></content:encoded>
              <dc:date>2026-09-28T10:00:00Z</dc:date>
              <media:thumbnail url="https://wp.example.com/thumb.jpg" />
            </item>
          </channel>
        </rss>
    """.trimIndent()

    val items = RssParser.parse(wordpress, FeedSource("z", "WordPress 源", "https://wp.example.com/feed"))
    println("解析条数 = ${items.size}")
    items.forEach {
        println("  title  = ${it.title}")
        println("  link   = ${it.link}")
        println("  summary= ${it.summary}")
        println("  content= ${it.content}")
        println("  image  = ${it.imageUrl}")
        println("  pubDate= ${it.pubDate}")
    }

    println()
    println("--- 期望 ---")
    println("  content  应为「这是完整正文，比摘要长得多。」(来自 content:encoded)")
    println("  image    应为 https://wp.example.com/thumb.jpg (来自 media:thumbnail)")
    println("  pubDate  应为 1790589600000 (来自 dc:date)")
}
