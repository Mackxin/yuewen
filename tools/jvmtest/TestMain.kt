import com.example.yuewen.data.backup.Backup
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.model.BodyBlock
import com.example.yuewen.data.model.FeedCatalog
import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.model.Note
import com.example.yuewen.data.model.ReadStats
import com.example.yuewen.data.model.ReadStatsCalc
import com.example.yuewen.data.model.sanitizeSources
import com.example.yuewen.data.model.sourceIdOf
import com.example.yuewen.data.opml.Opml
import com.example.yuewen.data.reader.BodyBlocks
import com.example.yuewen.data.rss.FeedSearchParser
import com.example.yuewen.data.rss.RssHubCatalog
import com.example.yuewen.data.rss.RssParser
import com.example.yuewen.data.rss.buildRssHubUrl
import com.example.yuewen.data.rss.isUsableRssHubInstance
import com.example.yuewen.data.rss.normalizeRssHubInstance
import com.example.yuewen.data.rss.normalizeRssHubPath
import com.example.yuewen.data.rss.rssHubDefaultName
import com.example.yuewen.data.rss.rssHubEncode
import com.example.yuewen.data.rss.rssHubHost
import com.example.yuewen.data.util.Json
import com.example.yuewen.data.util.DEFAULT_HOME_KEYWORDS
import com.example.yuewen.data.util.HOME_KEYWORD_LIMIT
import com.example.yuewen.data.util.HOME_KEYWORD_MAX_LEN
import com.example.yuewen.data.util.HomeRows
import com.example.yuewen.data.util.LIKE_ESCAPE_CHAR
import com.example.yuewen.data.util.asArray
import com.example.yuewen.data.util.asBooleanOr
import com.example.yuewen.data.util.asLongOr
import com.example.yuewen.data.util.asObject
import com.example.yuewen.data.util.asString
import com.example.yuewen.data.util.likePattern
import com.example.yuewen.data.util.matchesKeyword
import com.example.yuewen.data.util.sanitizeKeywords
import com.example.yuewen.ui.theme.ThemePalette
import com.example.yuewen.ui.theme.contrastRatio
import com.example.yuewen.ui.theme.generateScheme
import com.example.yuewen.ui.theme.relativeLuminance
import com.example.yuewen.ui.theme.rgbToHsl
import com.example.yuewen.ui.theme.seedFor
import com.example.yuewen.ui.theme.Glass
import com.example.yuewen.ui.util.HomeSortMode
import com.example.yuewen.ui.util.TtsChunker
import com.example.yuewen.ui.util.sortArticles
import java.time.ZoneId
import java.time.ZonedDateTime

var pass = 0
var fail = 0

fun check(name: String, cond: Boolean, extra: String = "") {
    if (cond) {
        pass++
        println("[PASS] $name")
    } else {
        fail++
        println("[FAIL] $name" + if (extra.isNotEmpty()) "  -> $extra" else "")
    }
}

fun main() {
    println("========== 1. 导出 -> 导入 往返 ==========")
    val src = listOf(
        FeedSource("a", "少数派", "https://sspai.com/feed", "科技"),
        FeedSource("b", "阮一峰的网络日志", "https://www.ruanyifeng.com/blog/atom.xml", "科技"),
        FeedSource("c", "豆瓣电影", "https://www.douban.com/feed/review/movie", "娱乐")
    )
    val xml = Opml.export(src)
    println("---- 导出的 OPML 原文 ----")
    println(xml)
    println("---- 解析结果 ----")
    val back = Opml.parse(xml)
    back.forEach { println("  name=${it.name} url=${it.url} category=${it.category} id=${it.id}") }
    check("往返数量一致(=3)", back.size == 3, "实际 ${back.size}")
    check("分类保留", back.map { it.category }.toSet() == setOf("科技", "娱乐"), back.map { it.category }.toString())
    check("名称保留", back.map { it.name }.toSet() == src.map { it.name }.toSet(), back.map { it.name }.toString())
    check("地址保留", back.map { it.url }.toSet() == src.map { it.url }.toSet(), back.map { it.url }.toString())
    // v2.0.2：id 改成「由地址派生」，不再是 opml_<hash>。
    // 这样导入同一个地址永远得到同一个 id，重复导入不会造出两条看着一样、点起来却分不清的源。
    check("id 由地址派生", back.all { it.id == sourceIdOf(it.url) }, back.map { it.id }.toString())
    check("导入的 id 互不重复", back.map { it.id }.distinct().size == back.size)

    println()
    println("========== 2. 扁平写法 + category=\"/Tech\" (Feeder 风格) ==========")
    val flat = """
        <?xml version="1.0" encoding="UTF-8"?>
        <opml version="1.0">
          <body>
            <outline type="rss" text="爱范儿" title="爱范儿" xmlUrl="https://www.ifanr.com/feed" htmlUrl="https://www.ifanr.com" category="/Tech"/>
            <outline type="rss" text="虎嗅" xmlUrl="https://www.huxiu.com/rss/0.xml" category="/商业"/>
          </body>
        </opml>
    """.trimIndent()
    val flatOut = Opml.parse(flat)
    flatOut.forEach { println("  name=${it.name} category=${it.category}") }
    check("扁平写法解析 2 条", flatOut.size == 2, "实际 ${flatOut.size}")
    check("category 属性取末段 => Tech/商业",
        flatOut.map { it.category }.toSet() == setOf("Tech", "商业"), flatOut.map { it.category }.toString())

    println()
    println("========== 3. 嵌套分组（父 outline 即分类） ==========")
    val nested = """
        <?xml version="1.0"?>
        <opml version="2.0"><body>
          <outline text="新闻">
            <outline type="rss" text="澎湃" xmlUrl="https://www.thepaper.cn/feed"/>
            <outline type="rss" text="界面" xmlUrl="https://a.jiemian.com/index.php?m=article&amp;a=rss"/>
          </outline>
          <outline text="博客">
            <outline type="rss" text="酷壳" xmlUrl="https://coolshell.cn/feed"/>
          </outline>
        </body></opml>
    """.trimIndent()
    val nestedOut = Opml.parse(nested)
    nestedOut.forEach { println("  name=${it.name} url=${it.url} category=${it.category}") }
    check("嵌套解析 3 条", nestedOut.size == 3, "实际 ${nestedOut.size}")
    check("父节点当分类", nestedOut.map { it.category }.toSet() == setOf("新闻", "博客"), nestedOut.map { it.category }.toString())
    check("HTML 实体 &amp; 已还原", nestedOut.any { it.url.contains("&") }, nestedOut.map { it.url }.toString())

    println()
    println("========== 4. 重复地址去重 ==========")
    val dup = """
        <opml version="2.0"><body>
          <outline type="rss" text="A" xmlUrl="https://same.com/feed"/>
          <outline type="rss" text="A2" xmlUrl="https://same.com/feed"/>
        </body></opml>
    """.trimIndent()
    check("同地址只留 1 条", Opml.parse(dup).size == 1, "实际 ${Opml.parse(dup).size}")

    println()
    println("========== 5. 非 http 协议 / 无 xmlUrl 的 outline 应跳过 ==========")
    val weird = """
        <opml version="2.0"><body>
          <outline text="我的分组">
          <outline type="rss" text="feed 协议" xmlUrl="feed://example.com/rss"/>
          <outline type="rss" text="只有网页地址" htmlUrl="https://example.com"/>
          <outline type="rss" text="正常源" xmlUrl="https://ok.com/feed"/>
          </outline>
        </body></opml>
    """.trimIndent()
    val weirdOut = Opml.parse(weird)
    weirdOut.forEach { println("  name=${it.name} url=${it.url}") }
    check("只保留 1 条合法 http 源", weirdOut.size == 1 && weirdOut[0].url == "https://ok.com/feed",
        weirdOut.map { it.url }.toString())

    println()
    println("========== 6. 导入上限 300 ==========")
    val big = buildString {
        append("<opml version=\"2.0\"><body>")
        repeat(305) { i -> append("<outline type=\"rss\" text=\"S$i\" xmlUrl=\"https://s$i.com/feed\"/>") }
        append("</body></opml>")
    }
    check("305 条被截断到 300", Opml.parse(big).size == 300, "实际 ${Opml.parse(big).size}")

    println()
    println("========== 7. parseFeedKind 格式识别 ==========")
    val atom = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><title>T</title></feed>"""
    val rss20 = """<?xml version="1.0"?><rss version="2.0"><channel><title>T</title></channel></rss>"""
    val rss10 = """<?xml version="1.0"?><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><channel/></rdf:RDF>"""
    val html = """<!DOCTYPE html><html><head><title>一个普通网页</title></head><body>hi</body></html>"""
    check("Atom 识别", RssParser.parseFeedKind(atom) == "Atom", RssParser.parseFeedKind(atom))
    check("RSS 2.0 识别", RssParser.parseFeedKind(rss20) == "RSS 2.0", RssParser.parseFeedKind(rss20))
    check("RSS 1.0 (RDF) 识别", RssParser.parseFeedKind(rss10) == "RSS 1.0", RssParser.parseFeedKind(rss10))
    check("HTML 页面识别为未知格式", RssParser.parseFeedKind(html) == "未知格式", RssParser.parseFeedKind(html))

    println()
    println("========== 8. RssParser 真实抓取解析（RSS2.0 + Atom） ==========")
    val realRss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
          <title>示例源</title>
          <item>
            <title>第一篇</title>
            <link>https://example.com/1</link>
            <description><![CDATA[<p>摘要一 <img src="https://example.com/a.jpg"></p>]]></description>
            <pubDate>Mon, 28 Sep 2026 10:00:00 GMT</pubDate>
          </item>
          <item>
            <title>第二篇</title>
            <link>https://example.com/2</link>
            <description>摘要二</description>
            <pubDate>Sun, 27 Sep 2026 10:00:00 GMT</pubDate>
          </item>
        </channel></rss>
    """.trimIndent()
    val items = RssParser.parse(realRss, FeedSource("x", "示例源", "https://example.com/feed"))
    items.forEach { println("  title=${it.title} link=${it.link} img=${it.imageUrl} pub=${it.pubDate}") }
    check("RSS2.0 解析出 2 篇", items.size == 2, "实际 ${items.size}")
    check("标题正确", items.map { it.title } == listOf("第一篇", "第二篇"), items.map { it.title }.toString())
    check("从 description 兜底抽到图片", items[0].imageUrl == "https://example.com/a.jpg", "${items[0].imageUrl}")
    check("pubDate 解析非 0", items[0].pubDate > 0L, "${items[0].pubDate}")

    val realAtom = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Atom 源</title>
          <entry>
            <title>Atom 第一篇</title>
            <link rel="alternate" type="text/html" href="https://atom.example.com/1"/>
            <summary>摘要</summary>
            <updated>2026-09-28T10:00:00Z</updated>
          </entry>
        </feed>
    """.trimIndent()
    val atomItems = RssParser.parse(realAtom, FeedSource("y", "Atom 源", "https://atom.example.com/feed"))
    atomItems.forEach { println("  title=${it.title} link=${it.link} pub=${it.pubDate}") }
    check("Atom 解析出 1 篇", atomItems.size == 1, "实际 ${atomItems.size}")
    check("Atom link 取 href", atomItems[0].link == "https://atom.example.com/1", atomItems[0].link)
    check("Atom updated 解析非 0", atomItems[0].pubDate > 0L, "${atomItems[0].pubDate}")

    println()
    println("========== 9. 命名空间前缀字段（content:encoded / media:thumbnail / dc:date） ==========")
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
    val wp = RssParser.parse(wordpress, FeedSource("z", "WordPress 源", "https://wp.example.com/feed"))
    check("命名空间源解析出 1 篇", wp.size == 1, "实际 ${wp.size}")
    if (wp.isNotEmpty()) {
        val it0 = wp[0]
        check("content:encoded 取到全文", it0.content.contains("这是完整正文"), it0.content)
        check("media:thumbnail 取到缩略图", it0.imageUrl == "https://wp.example.com/thumb.jpg", "${it0.imageUrl}")
        check("dc:date 取到发布时间", it0.pubDate == 1790589600000L, "${it0.pubDate}")
        check("summary 仍是短摘要", it0.summary == "短摘要", it0.summary)
    }

    println()
    println("========== 10. 图片地址抽取（引号 / 懒加载 / 相对路径） ==========")
    fun feedWith(id: String, link: String, desc: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>T</title>
          <item>
            <title>$id</title>
            <link>$link</link>
            <description><![CDATA[$desc]]></description>
            <pubDate>Mon, 28 Sep 2026 10:00:00 GMT</pubDate>
          </item>
        </channel></rss>
    """.trimIndent()

    fun imgOf(xml: String): String? =
        RssParser.parse(xml, FeedSource("t", "T", "https://t.com/feed")).firstOrNull()?.imageUrl

    check(
        "双引号 src",
        imgOf(feedWith("a", "https://s.com/1", """<p>x</p><img src="https://cdn.com/a.jpg">""")) == "https://cdn.com/a.jpg",
        "${imgOf(feedWith("a", "https://s.com/1", """<img src="https://cdn.com/a.jpg">"""))}"
    )
    check(
        "单引号 src",
        imgOf(feedWith("b", "https://s.com/2", "<img src='https://cdn.com/b.jpg'>")) == "https://cdn.com/b.jpg",
        "${imgOf(feedWith("b", "https://s.com/2", "<img src='https://cdn.com/b.jpg'>"))}"
    )
    check(
        "无引号 src",
        imgOf(feedWith("c", "https://s.com/3", "<img src=https://cdn.com/c.jpg>")) == "https://cdn.com/c.jpg",
        "${imgOf(feedWith("c", "https://s.com/3", "<img src=https://cdn.com/c.jpg>"))}"
    )
    check(
        "懒加载 data-src 优先于 base64 占位 src",
        imgOf(
            feedWith(
                "d", "https://s.com/4",
                """<img src="data:image/gif;base64,R0lGOD" data-src="https://cdn.com/d.jpg">"""
            )
        ) == "https://cdn.com/d.jpg",
        "${imgOf(feedWith("d", "https://s.com/4", """<img src="data:image/gif;base64,R0lGOD" data-src="https://cdn.com/d.jpg">"""))}"
    )
    check(
        "相对路径按文章链接补全",
        imgOf(feedWith("e", "https://s.com/post/5", """<img src="/img/e.jpg">""")) == "https://s.com/img/e.jpg",
        "${imgOf(feedWith("e", "https://s.com/post/5", """<img src="/img/e.jpg">"""))}"
    )
    check(
        "协议相对地址补 https",
        imgOf(feedWith("f", "https://s.com/6", """<img src="//cdn.com/f.jpg">""")) == "https://cdn.com/f.jpg",
        "${imgOf(feedWith("f", "https://s.com/6", """<img src="//cdn.com/f.jpg">"""))}"
    )
    check(
        "没有图片时返回 null",
        imgOf(feedWith("g", "https://s.com/7", "<p>纯文字，无图</p>")) == null,
        "${imgOf(feedWith("g", "https://s.com/7", "<p>纯文字，无图</p>"))}"
    )

    // media:thumbnail 应优先于正文里的图
    val priority = """
        <?xml version="1.0"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>T</title>
          <item>
            <title>优先级</title>
            <link>https://s.com/8</link>
            <description><![CDATA[<img src="https://cdn.com/inner.jpg">]]></description>
            <media:thumbnail url="https://cdn.com/thumb.jpg" />
          </item>
        </channel></rss>
    """.trimIndent()
    check(
        "media:thumbnail 优先级高于正文内图片",
        imgOf(priority) == "https://cdn.com/thumb.jpg", "${imgOf(priority)}"
    )

    println()
    println("========== 12. 朗读切块（TTS 单次输入上限保护） ==========")
    check("空白文本切出 0 块", TtsChunker.split("   \n  ").isEmpty())
    val one = "今天天气不错，适合出门走走。"
    check("短文本只有一块", TtsChunker.split(one).size == 1, "${TtsChunker.split(one)}")
    check("短文本内容不变", TtsChunker.split(one).first() == one)

    // 60 句 × 16 字 ≈ 960 字，按 200 字上限切
    val longText = (1..60).joinToString("") { "第${it}句测试内容用来凑够长度。" }
    val pieces = TtsChunker.split(longText, 200)
    println("  长文 ${longText.length} 字 -> 切成 ${pieces.size} 块，前两块：${pieces.take(2).joinToString(" | ")}")
    check("长文切成多块", pieces.size > 1, "${pieces.size}")
    check("每块都不超上限", pieces.all { it.length <= 200 }, pieces.maxOfOrNull { it.length }.toString())
    check(
        "每块都在句末标点处断开",
        pieces.all { it.last() in "。！？；…!?;\n" },
        pieces.map { it.last() }.toString()
    )
    check(
        "拼回去不丢字",
        pieces.joinToString("") == longText,
        "拼接后 ${pieces.joinToString("").length} 字 vs 原文 ${longText.length} 字"
    )

    check("标题拼接自动补句号", TtsChunker.compose("标题", "正文") == "标题。正文", TtsChunker.compose("标题", "正文"))
    check("标题已带句号不重复补", TtsChunker.compose("标题。", "正文") == "标题。正文")
    check("没有正文时只读标题", TtsChunker.compose("标题", "   ") == "标题")
    check("没有标题时只读正文", TtsChunker.compose("  ", "正文") == "正文")

    println()
    println("========== 13. 阅读统计分桶（日期口径） ==========")
    // 固定时区：否则测试机时区不同会让「今天」的判定漂移，断言随机挂掉
    val zone = ZoneId.of("Asia/Shanghai")

    fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    val now = at(2026, 9, 28, 14)
    check(
        "startOfDay 落在当天 00:00",
        ReadStatsCalc.startOfDay(now, zone) == at(2026, 9, 28, 0),
        "${ReadStatsCalc.startOfDay(now, zone)} vs ${at(2026, 9, 28, 0)}"
    )
    check(
        "weekStart 落在 6 天前的 00:00",
        ReadStatsCalc.weekStart(now, zone) == at(2026, 9, 22, 0),
        "${ReadStatsCalc.weekStart(now, zone)} vs ${at(2026, 9, 22, 0)}"
    )

    val stamps = listOf(
        at(2026, 9, 28, 9), at(2026, 9, 28, 13), // 今天 2 篇
        at(2026, 9, 27, 8),                      // 昨天 1 篇
        at(2026, 9, 22, 10),                     // 6 天前 1 篇（桶的最左边界）
        at(2026, 9, 1, 10)                       // 太久远，不该计入
    )
    val buckets = ReadStatsCalc.last7Buckets(stamps, now, zone)
    println("  分桶：" + buckets.joinToString(" ") { "${it.label}=${it.count}" })
    check("固定 7 个桶", buckets.size == 7, "${buckets.size}")
    check("最后一桶标记为今天", buckets.last().isToday)
    check("今天计 2 篇", buckets.last().count == 2, "${buckets.last().count}")
    check("昨天计 1 篇", buckets[5].count == 1, "${buckets[5].count}")
    check("6 天前计 1 篇（边界包含）", buckets.first().count == 1, "${buckets.first().count}")
    check(
        "超出 7 天的旧记录不计入",
        ReadStatsCalc.last7Buckets(listOf(at(2026, 9, 1, 10)), now, zone).sumOf { it.count } == 0
    )

    val s3 = listOf(at(2026, 9, 28, 9), at(2026, 9, 27, 9), at(2026, 9, 26, 9))
    check("连续天数：今天+昨天+前天 = 3", ReadStatsCalc.streak(s3, now, zone) == 3, "${ReadStatsCalc.streak(s3, now, zone)}")
    val s2 = listOf(at(2026, 9, 27, 9), at(2026, 9, 26, 9))
    check("连续天数：今天还没读，从昨天数起 = 2", ReadStatsCalc.streak(s2, now, zone) == 2, "${ReadStatsCalc.streak(s2, now, zone)}")
    val broken = listOf(at(2026, 9, 28, 9), at(2026, 9, 26, 9))
    check("连续天数：中间断档只算到断点 = 1", ReadStatsCalc.streak(broken, now, zone) == 1, "${ReadStatsCalc.streak(broken, now, zone)}")
    check("连续天数：没有任何记录 = 0", ReadStatsCalc.streak(emptyList(), now, zone) == 0)
    val crossMonth = listOf(at(2026, 10, 1, 9), at(2026, 9, 30, 9), at(2026, 9, 29, 9))
    val oct1 = at(2026, 10, 1, 12)
    check("连续天数：跨月也能数对 = 3", ReadStatsCalc.streak(crossMonth, oct1, zone) == 3, "${ReadStatsCalc.streak(crossMonth, oct1, zone)}")

    check("估算时长 = 字数 ÷ 300", ReadStats(chars = 900).minutes == 3, "${ReadStats(chars = 900).minutes}")
    check("读过就至少算 1 分钟", ReadStats(chars = 10).minutes == 1, "${ReadStats(chars = 10).minutes}")
    check("没读过算 0 分钟", ReadStats().minutes == 0, "${ReadStats().minutes}")
    check("全 0 时柱状图峰值兜底为 1（避免除零）", ReadStats().peak == 1, "${ReadStats().peak}")

    println()
    println("========== 14. 正文块解析（v1.7 富文本：段落 + 图片） ==========")

    // 14.1 网页 HTML：懒加载属性优先、相对路径补全、data:URI 过滤
    val webHtml = """
        <div class="article">
          <script>evil()</script>
          <p>第一段文字</p>
          <p><img src="data:image/gif;base64,AAAA"/><img data-src="/img/lazy1.jpg" src="data:image/png;base64,BBBB"/>配图说明</p>
          <div class="content"><img data-original="https://cdn.example.com/pic2.png"/><p>容器里的段落</p></div>
          <img src="https://tracker.example.com/1x1.gif" width="1" height="1"/>
          <h3>小标题</h3>
          <p>第二段文字 <img srcset="/img/a.jpg 640w, /img/b.jpg 1280w"/></p>
          <img src="//static.example.com/proto.jpg"/>
        </div>
    """.trimIndent()
    val webBlocks = BodyBlocks.parse(webHtml, "https://news.example.com/post/1")
    webBlocks.forEach { println("  $it") }
    check("14.1 图片单独成块", webBlocks.filterIsInstance<BodyBlock.Image>().size == 4,
        webBlocks.filterIsInstance<BodyBlock.Image>().toString())
    check("14.1 data:URI 被过滤，懒加载属性顶上",
        webBlocks.filterIsInstance<BodyBlock.Image>().any { it.url == "https://news.example.com/img/lazy1.jpg" })
    check("14.1 相对路径补全成绝对地址",
        webBlocks.filterIsInstance<BodyBlock.Image>().any { it.url == "https://cdn.example.com/pic2.png" })
    check("14.1 1x1 追踪像素被过滤",
        webBlocks.filterIsInstance<BodyBlock.Image>().none { it.url.contains("tracker") })
    check("14.1 srcset 取第一张并补全",
        webBlocks.filterIsInstance<BodyBlock.Image>().any { it.url == "https://news.example.com/img/a.jpg" })
    check("14.1 协议相对地址补 https",
        webBlocks.filterIsInstance<BodyBlock.Image>().any { it.url == "https://static.example.com/proto.jpg" })
    check("14.1 h3 成了 Heading",
        webBlocks.filterIsInstance<BodyBlock.Heading>().map { it.text } == listOf("小标题"))
    check("14.1 段落文本保留（li 前缀不误伤普通 p）",
        webBlocks.filterIsInstance<BodyBlock.Paragraph>().any { it.text.contains("第一段文字") })
    check("14.1 script 内容不进正文", webBlocks.none { BodyBlocks.textOf(it)?.contains("evil") == true })

    // 14.2 轻量 HTML 往返：toLightHtml -> parse
    val round = listOf(
        BodyBlock.Paragraph("A & B"),
        BodyBlock.Image("https://x.com/i.png"),
        BodyBlock.Heading("标题<hello>")
    )
    val light = BodyBlocks.toLightHtml(round)
    val back2 = BodyBlocks.parse(light)
    check("14.2 轻量 HTML 往返无损", back2 == round, "$back2")

    // 14.3 旧版纯文本兼容（v1.6 及以前存的 fullText）
    val legacy = "第一段\n\n第二段\n· 列表行\n第三段"
    val legacyBlocks = BodyBlocks.parse(legacy)
    check("14.3 纯文本按行拆段", legacyBlocks.size == 4, legacyBlocks.toString())
    check("14.3 纯文本全是 Paragraph", legacyBlocks.all { it is BodyBlock.Paragraph })

    // 14.4 文本长度统计不含图片
    check("14.4 textLength 不计图片", BodyBlocks.textLength(round) == "A & B".length + "标题<hello>".length)

    println()
    println("========== 15. Json（v2.0 自建序列化，备份 / 搜索解析都用它） ==========")
    val j1 = Json.obj(
        "s" to "he said \"hi\"\n换行",
        "n" to 42,
        "d" to 3.5,
        "b" to true,
        "nil" to null,
        "arr" to Json.Raw(Json.array(listOf(1, 2, 3))),
        "raw" to Json.Raw(Json.obj("k" to "v"))
    )
    println("  $j1")
    val p1 = Json.parse(j1).asObject()
    check("15.1 顶层是对象", p1.isNotEmpty())
    check("15.2 引号 / 换行转义往返", p1["s"].asString() == "he said \"hi\"\n换行", p1["s"].asString())
    check("15.3 整数解析", p1["n"].asLongOr(0L) == 42L)
    check("15.4 小数转 Long", p1["d"].asLongOr(0L) == 3L)
    check("15.5 布尔解析", p1["b"].asBooleanOr(false))
    check("15.6 null 值不炸", p1["nil"].asString() == "")
    check("15.7 内嵌数组", p1["arr"].asArray().size == 3)
    check("15.8 Raw 对象原样内联", p1["raw"].asObject()["k"].asString() == "v")
    check("15.9 坏 JSON 返回 null 而不是抛异常", Json.parse("{oops") == null)
    check("15.10 顶层数组便捷入口", Json.parseArray("""[{"a":1},{"a":2}]""").size == 2)
    check("15.11 \\uXXXX 转义还原",
        Json.parse("""{"x":"\u4e2d\u6587"}""").asObject()["x"].asString() == "中文")
    check("15.12 空对象 / 空数组",
        Json.parse("{}").asObject().isEmpty() && Json.parse("[]").asArray().isEmpty())

    println()
    println("========== 16. 内置阅源库 FeedCatalog（v2.0，保证「装完就有内容」） ==========")
    val allFeeds = FeedCatalog.all()
    check("16.1 至少 6 个分类", FeedCatalog.groups.size >= 6, "${FeedCatalog.groups.size}")
    check("16.2 收录源 >= 30 个", allFeeds.size >= 30, "${allFeeds.size}")
    check("16.3 每个地址都是 http(s)", allFeeds.all { it.url.startsWith("http") })
    check("16.4 地址不重复", allFeeds.map { it.url }.distinct().size == allFeeds.size)
    check("16.5 名称不重复", allFeeds.map { it.name }.distinct().size == allFeeds.size)
    check("16.6 每个源都有名字 / 分类 / 简介", allFeeds.all { it.name.isNotBlank() && it.category.isNotBlank() && it.desc.isNotBlank() })
    check("16.7 分类组都有 emoji", FeedCatalog.groups.all { it.emoji.isNotBlank() && it.feeds.isNotEmpty() })

    val starter = FeedCatalog.starter()
    check("16.8 新用户默认 12 个源", starter.size == 12, "${starter.size}")
    check("16.9 默认源 id 唯一且由地址派生（v2.0.2 起不再用位置编号）",
        starter.map { it.id }.distinct().size == starter.size && starter.all { it.id == sourceIdOf(it.url) })
    check("16.10 默认源全部启用", starter.all { it.enabled })
    check("16.11 默认源都来自推荐库", starter.all { s -> allFeeds.any { it.url == s.url } })
    check("16.12 featured() 非空且都在库里",
        FeedCatalog.featured().isNotEmpty() && FeedCatalog.featured().all { f -> allFeeds.any { it.url == f.url } })
    check("16.13 findGroup 命中 / 落空",
        FeedCatalog.findGroup(FeedCatalog.groups.first().name) != null && FeedCatalog.findGroup("不存在") == null)
    // 老用户增量补种：已有的源不该被重复塞进来
    val already = listOf(starter.first(), starter[1])
    val additions = FeedCatalog.upgradeAdditions(already)
    check("16.14 upgradeAdditions 不重复补种",
        additions.size == starter.size - 2 && additions.none { a -> already.any { it.url == a.url } },
        "additions=${additions.size}")
    check("16.15 upgradeAdditions 对空列表 = 全集",
        FeedCatalog.upgradeAdditions(emptyList()).size == starter.size)
    // v2.0.2 的坑：默认源编号是 s0/s1…，补种进来的源如果也沿用这套编号，
    // 就会和用户已有的源撞 id（→ 点一个源的操作落到另一个源上）。这里钉死「补种不会撞」。
    val legacySources = listOf(
        FeedSource("s0", "老默认源 A", "https://a.example.com/rss"),
        FeedSource("s1", "老默认源 B", "https://b.example.com/rss")
    )
    val patched = legacySources + FeedCatalog.upgradeAdditions(legacySources)
    check("16.16 补种后 id 全局唯一（不再和 s0/s1 撞车）",
        patched.map { it.id }.distinct().size == patched.size,
        patched.map { it.id }.toString())
    check("16.17 补种后的源，id 也由地址派生",
        patched.drop(2).all { it.id == sourceIdOf(it.url) })

    println()
    println("========== 17. 阅源搜索解析 FeedSearchParser（v2.0） ==========")
    val feedlyJson = """
        [
          {"title":"少数派","feedId":"feed/https://sspai.com/feed","website":"https://sspai.com","description":"高效工作，品质生活","subscribers":1000},
          {"title":"","feedId":"feed/https://a.example.com/rss","subscribers":2},
          {"title":"坏数据","feedId":"notahttp","subscribers":1},
          {"title":"少数派","feedId":"feed/https://sspai.com/feed","subscribers":9}
        ]
    """.trimIndent()
    val found = FeedSearchParser.parseFeedly(feedlyJson)
    found.forEach { println("  ${it.title}  ${it.url}  subs=${it.subscribers}") }
    check("17.1 去掉 feed/ 前缀", found.any { it.url == "https://sspai.com/feed" })
    check("17.2 非 http 地址被丢掉", found.none { it.url == "notahttp" })
    check("17.3 同地址去重", found.count { it.url == "https://sspai.com/feed" } == 1)
    check("17.4 没标题时用域名兜底",
        found.any { it.url.endsWith("a.example.com/rss") && it.title == "a.example.com" },
        found.joinToString { it.title })
    check("17.5 provider 标成 Feedly", found.all { it.provider == "Feedly" })
    check("17.6 订阅人数解析", found.first { it.url.contains("sspai") }.subscribers == 1000)
    check("17.7 简介截断到 160 字以内", found.all { it.description.length <= 160 })

    val bingXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
          <title>必应新闻</title>
          <item><title>关键词新闻一</title><link>https://news.example.com/1</link><description>摘要一</description></item>
          <item><title>关键词新闻二</title><link>https://news.example.com/2</link><description>摘要二</description></item>
        </channel></rss>
    """.trimIndent()
    val bingFeeds = FeedSearchParser.parseBingNews(bingXml)
    check("17.8 Bing 新闻解析出 2 条", bingFeeds.size == 2, "${bingFeeds.size}")
    check("17.9 Bing provider 正确", bingFeeds.all { it.provider == "Bing 新闻" })
    check("17.10 Bing 标题 / 链接取到", bingFeeds.first().title == "关键词新闻一" && bingFeeds.first().url.endsWith("/1"))

    val kw = FeedSearchParser.keywordFeeds("新能源 汽车")
    check("17.11 关键词生成 2 条专属信息流", kw.size == 2, "${kw.size}")
    check("17.12 关键词已 URL 编码（空格成 %20，不留裸空格）",
        kw.all { it.second.contains("%20") && !it.second.contains(' ') })
    check("17.13 空关键词不生成", FeedSearchParser.keywordFeeds("   ").isEmpty())

    println()
    println("========== 18. 备份 / 恢复 Backup（v2.0，JSON） ==========")
    val bsources = listOf(
        FeedSource("a", "少数派", "https://sspai.com/feed", "科技"),
        FeedSource("b", "跳跳", "https://x.example.com/rss", "未分类", false)
    )
    val bmarks = listOf(
        Article(
            link = "https://sspai.com/1", title = "标题", summary = "摘要",
            sourceName = "少数派", category = "科技", pubDate = 5L,
            isBookmarked = true, folder = "默认", readProgress = 300
        )
    )
    val bnotes = listOf(
        Note(
            id = "n1", link = "https://sspai.com/1", articleTitle = "标题", sourceName = "少数派",
            quote = "他说：\"你好\"", note = "记一笔\n换行", createdAt = 111L
        )
    )
    val backupJson = Backup.export(bsources, bmarks, bnotes, mapOf("theme" to "dark", "list_mode" to "card"), now = 1000L)
    println("  ${backupJson.take(500)}")
    val bundle = Backup.parse(backupJson)
    check("18.1 能解析回 Bundle", bundle != null)
    check("18.2 app 标记为 yuewen", bundle?.app == Backup.APP_TAG)
    check("18.3 版本号", bundle?.version == Backup.VERSION)
    check("18.4 导出时间", bundle?.exportedAt == 1000L)
    check("18.5 源数量与字段", bundle?.sources?.size == 2 && bundle.sources[0].name == "少数派")
    check("18.6 源的 enabled 保留（含 false）", bundle?.sources?.get(1)?.enabled == false)
    check("18.7 收藏恢复时强制 isBookmarked", bundle?.bookmarks?.firstOrNull()?.isBookmarked == true)
    check("18.8 阅读进度保留", bundle?.bookmarks?.firstOrNull()?.readProgress == 300)
    check("18.9 笔记的引号 / 换行往返无损",
        bundle?.notes?.firstOrNull()?.quote == "他说：\"你好\"" && bundle?.notes?.firstOrNull()?.note == "记一笔\n换行")
    check("18.10 设置往返", bundle?.settings?.get("theme") == "dark")
    check("18.11 备份不含正文缓存（体积才小）", !backupJson.contains("fullText"))
    check("18.12 总计 = 源 + 收藏 + 笔记", bundle?.total == 4, "${bundle?.total}")
    check("18.13 别人的 JSON 不认", Backup.parse("""{"hello":1}""") == null)
    check("18.14 坏 JSON 不认（不抛异常）", Backup.parse("{oops") == null)
    check("18.15 quote / note 都空的笔记被丢掉",
        Backup.parse("""{"app":"yuewen","notes":[{"link":"a","quote":"","note":""},{"link":"b","quote":"q"}]}""")
            ?.notes?.size == 1)
    check("18.16 备份文件名带日期", Backup.fileName(0L).startsWith("阅闻备份-") && Backup.fileName(0L).endsWith(".json"))
    check("18.17 空备份也能解析", Backup.parse(Backup.export(emptyList(), emptyList(), emptyList(), emptyMap()))?.total == 0)

    println()
    println("========== 19. 阅源 id 与去重 sanitizeSources（v2.0.2，修「测试跑到别的源上」） ==========")
    // 同一地址永远同一个 id —— 这是「换手机恢复备份后 id 还认得出来」的前提
    check("19.1 同地址同 id", sourceIdOf("https://a.com/feed") == sourceIdOf("https://a.com/feed"))
    // 大小写 / 结尾斜杠的写法差异，算同一个地址（去重也按这个口径）
    check("19.2 忽略大小写与结尾斜杠",
        sourceIdOf("https://A.com/feed/") == sourceIdOf("https://a.com/feed"))
    check("19.3 不同地址不同 id", sourceIdOf("https://a.com/feed") != sourceIdOf("https://b.com/feed"))
    check("19.4 长度不同的地址也不同 id",
        sourceIdOf("https://a.com/x") != sourceIdOf("https://a.com/y"))
    check("19.5 id 里带长度前缀，便于人眼排查", sourceIdOf("https://a.com/feed").startsWith("s"))

    // id 重复 → 就地修：第二条按地址重新派生，两条不再共用 id
    val dupId = listOf(
        FeedSource("s0", "少数派", "https://sspai.com/feed"),
        FeedSource("s0", "36氪", "https://36kr.com/feed")
    )
    val fixed = sanitizeSources(dupId)
    check("19.6 重复 id 被修掉", fixed.size == 2 && fixed[0].id != fixed[1].id, fixed.map { it.id }.toString())
    check("19.7 第一条保持原 id 不动（尽量少改）", fixed[0].id == "s0")
    check("19.8 被改的那条按地址派生", fixed[1].id == sourceIdOf("https://36kr.com/feed"))

    // 空 id 也要补上
    val blankId = sanitizeSources(listOf(FeedSource("", "x", "https://x.com/feed")))
    check("19.9 空 id 自动补", blankId.single().id == sourceIdOf("https://x.com/feed"))

    // 同地址只留一条：OPML 导入 + 手动添加很容易凑出两条一模一样的源
    val dupUrl = sanitizeSources(
        listOf(
            FeedSource("a", "少数派", "https://sspai.com/feed"),
            FeedSource("b", "少数派（重复）", "https://SSPAI.com/feed/")
        )
    )
    check("19.10 同地址只留第一条", dupUrl.size == 1 && dupUrl[0].id == "a", "${dupUrl.size}")

    check("19.11 干净数据原样返回（不制造无谓改动）",
        sanitizeSources(starter) == starter)
    check("19.12 空列表不炸", sanitizeSources(emptyList()).isEmpty())
    check("19.13 三条同 id 也能全部修成唯一",
        sanitizeSources((1..3).map { FeedSource("d", "n$it", "https://d$it.com/feed") })
            .map { it.id }.distinct().size == 3)

    println()
    println("========== 20. RSSHub 地址拼接（v2.1） ==========")
    // ---- 实例地址规范化 ----
    check("20.1 实例留空 = 用官方默认", normalizeRssHubInstance("") == RssHubCatalog.DEFAULT_INSTANCE)
    check("20.2 没写协议自动补 https", normalizeRssHubInstance("my.host.com") == "https://my.host.com")
    check("20.3 去掉结尾斜杠（否则会拼出 //）",
        normalizeRssHubInstance("https://my.host.com/") == "https://my.host.com")
    check("20.4 保留自建实例的子路径", normalizeRssHubInstance("https://my.host.com/rsshub/") == "https://my.host.com/rsshub")
    check("20.5 去掉查询串与锚点",
        normalizeRssHubInstance("https://a.com/base?x=1#y") == "https://a.com/base")
    check("20.6 显示形式去掉协议", rssHubHost("https://rsshub.app") == "rsshub.app")
    check("20.7 默认实例可用", isUsableRssHubInstance(""))
    check("20.8 没有点的假域名不可用", !isUsableRssHubInstance("abc"))
    check("20.9 只有协议也不可用", !isUsableRssHubInstance("https://"))

    // ---- 路径片段编码 ----
    check("20.10 中文按 UTF-8 百分号编码", rssHubEncode("编程") == "%E7%BC%96%E7%A8%8B")
    check("20.11 斜杠保留（两段式参数要用）", rssHubEncode("DIYgod/RSSHub") == "DIYgod/RSSHub")
    check("20.12 空格编成 %20（不是 +）", rssHubEncode("a b") == "a%20b")
    check("20.13 URL 安全字符原样保留", rssHubEncode("a-b_c.d~e") == "a-b_c.d~e")
    check("20.14 # 与 & 会被编码", rssHubEncode("a#b&c") == "a%23b%26c")

    // ---- 路由路径规范化 ----
    check("20.15 补前导斜杠", normalizeRssHubPath("weibo/user/123") == "/weibo/user/123")
    check("20.16 粘整条地址也能用（砍掉实例部分）",
        normalizeRssHubPath("https://rsshub.app/weibo/user/123") == "/weibo/user/123")
    check("20.17 没写协议的域名前缀也认",
        normalizeRssHubPath("rsshub.app/zhihu/hot") == "/zhihu/hot")
    check("20.18 查询串保留（很多路由靠它调输出）",
        normalizeRssHubPath("/zhihu/hot?limit=20") == "/zhihu/hot?limit=20")
    check("20.19 折叠重复斜杠并去尾斜杠", normalizeRssHubPath("/weibo//user/123/") == "/weibo/user/123")
    check("20.20 空路径落到根", normalizeRssHubPath("   ") == "/")
    check("20.21 只有域名时落到根", normalizeRssHubPath("https://rsshub.app") == "/")

    // ---- 拼地址 ----
    check("20.22 实例 + 路由",
        buildRssHubUrl("", "/weibo/user/{q}", "1234567") == "https://rsshub.app/weibo/user/1234567")
    check("20.23 参数首尾空格自动去掉",
        buildRssHubUrl("", "/weibo/user/{q}", "  1234567  ") == "https://rsshub.app/weibo/user/1234567")
    check("20.24 中文关键词被编码",
        buildRssHubUrl("", "/weibo/keyword/{q}", "编程") == "https://rsshub.app/weibo/keyword/%E7%BC%96%E7%A8%8B")
    check("20.25 两段式参数（作者/仓库名）不被拆散",
        buildRssHubUrl("", "/github/issue/{q}", "DIYgod/RSSHub") == "https://rsshub.app/github/issue/DIYgod/RSSHub")
    check("20.26 自建实例（带子路径）",
        buildRssHubUrl("my.host.com/rsshub", "/zhihu/hot") == "https://my.host.com/rsshub/zhihu/hot")
    check("20.27 参数留空不会拼出双斜杠",
        buildRssHubUrl("", "/xiaohongshu/user/{q}/notes", "") == "https://rsshub.app/xiaohongshu/user/notes")
    check("20.28 参数两端斜杠被去掉",
        buildRssHubUrl("", "/weibo/user/{q}", "/123/") == "https://rsshub.app/weibo/user/123")
    check("20.29 无参数路由直接用",
        buildRssHubUrl("", "/zhihu/hot") == "https://rsshub.app/zhihu/hot")
    check("20.30 自定义路由（整条地址粘进来）",
        buildRssHubUrl("", "https://rsshub.app/v2ex/topics/latest") == "https://rsshub.app/v2ex/topics/latest")
    check("20.31 路由末尾查询串不会丢",
        buildRssHubUrl("", "/v2ex/topics/latest?limit=20") == "https://rsshub.app/v2ex/topics/latest?limit=20")

    // ---- 默认源名 ----
    check("20.32 有参数时用「平台 · 参数」当名字",
        rssHubDefaultName(RssHubCatalog.find("weibo-user")!!, "1642909335") == "微博 · 1642909335")
    check("20.33 榜单类用「平台 · 标题」",
        rssHubDefaultName(RssHubCatalog.find("zhihu-hot")!!, "") == "知乎 · 热榜")

    // ---- 模板表自身的体检（这几条是「防呆」：新增模板时忘了写提示会当场报错） ----
    check("20.34 模板 id 唯一", RssHubCatalog.routes.map { it.id }.distinct().size == RssHubCatalog.routes.size)
    check("20.35 路径都以 / 开头且不带尾斜杠",
        RssHubCatalog.routes.all { it.path.startsWith("/") && !it.path.endsWith("/") })
    check("20.36 路径都是多段（没有半截路由）",
        RssHubCatalog.routes.all { it.path.count { c -> c == '/' } >= 2 })
    check("20.37 路径里不含协议头", RssHubCatalog.routes.none { it.path.contains("://") })
    check("20.38 要参数的模板必须写清「去哪找这个 ID」",
        RssHubCatalog.routes.filter { it.needsParam }.all { it.paramLabel.isNotBlank() && it.paramHint.isNotBlank() })
    check("20.39 要参数的模板要有示例值（输入框占位靠它）",
        RssHubCatalog.routes.filter { it.needsParam }.all { it.paramExample.isNotBlank() })
    check("20.40 平台分组够用", RssHubCatalog.platforms.size >= 6)
    check("20.41 每条模板都能拼出干净地址（无 //、无尾斜杠）",
        RssHubCatalog.routes.all {
            val u = buildRssHubUrl("", it.path, it.paramExample)
            !u.removePrefix("https://").removePrefix("http://").contains("//") && !u.endsWith("/")
        })
    check("20.42 每条模板拼出来的地址都以实例开头",
        RssHubCatalog.routes.all {
            buildRssHubUrl("", it.path, it.paramExample).startsWith("https://rsshub.app/")
        })
    check("20.43 空关键词返回全部（清空搜索框即有全部）",
        RssHubCatalog.search("").size == RssHubCatalog.routes.size)
    check("20.44 搜英文平台名能命中（bilibili -> B站）",
        RssHubCatalog.search("bilibili").isNotEmpty() &&
                RssHubCatalog.search("bilibili").all { it.platform == "B站" })
    check("20.45 搜不存在的词返回空", RssHubCatalog.search("zzz不存在的词zzz").isEmpty())
    check("20.46 按平台过滤正确",
        RssHubCatalog.byPlatform("微博").isNotEmpty() && RssHubCatalog.byPlatform("微博").all { it.platform == "微博" })
    check("20.47 能按 id 取到模板", RssHubCatalog.find("weibo-user")?.path == "/weibo/user/{q}")
    check("20.48 取不到的 id 返回 null", RssHubCatalog.find("nope") == null)

    // ==================== 21. 首页排序（v2.2） ====================
    println()
    println("========== 21. 首页排序 ==========")

    /** 造一篇文章：只关心排序用得到的字段。 */
    fun art(link: String, src: String, title: String, ts: Long) =
        Article(link = link, title = title, sourceName = src, pubDate = ts)

    val bag = listOf(
        art("L1", "少数派", "b 苹果发布会", 300),
        art("L2", "阮一峰", "d 每周分享", 100),
        art("L3", "少数派", "a 安卓技巧", 500),
        art("L4", "阮一峰", "c 科技爱好者周刊", 200),
        art("L5", "豆瓣", "e 影评", 400)
    )

    // ---- 最新在前 ----
    val desc = sortArticles(bag, HomeSortMode.TimeDesc, 0)
    check("21.01 最新在前：时间从大到小", desc.map { it.pubDate } == listOf(500L, 400L, 300L, 200L, 100L))
    check("21.02 最新在前：第一条是最新的", desc.first().link == "L3")
    // ---- 最早在前 ----
    val asc = sortArticles(bag, HomeSortMode.TimeAsc, 0)
    check("21.03 最早在前：时间从小到大", asc.map { it.pubDate } == listOf(100L, 200L, 300L, 400L, 500L))
    check("21.04 正序是倒序的逆", asc.map { it.link } == desc.map { it.link }.reversed())
    // ---- 按阅源 ----
    val bySrc = sortArticles(bag, HomeSortMode.Source, 0)
    check("21.05 按阅源：同一个源凑在一起",
        (bySrc.map { it.sourceName }.zipWithNext().count { (a, b) -> a != b }) == 2,
        bySrc.map { "${it.sourceName}/${it.link}" }.toString())
    check("21.06 按阅源：源名有序", bySrc.map { it.sourceName } == bySrc.map { it.sourceName }.sorted())
    check("21.07 按阅源：组内仍按时间倒序",
        bySrc.filter { it.sourceName == "少数派" }.map { it.pubDate } == listOf(500L, 300L))
    // ---- 按标题 ----
    val byTitle = sortArticles(bag, HomeSortMode.Title, 0)
    check("21.08 按标题：字典序", byTitle.map { it.title.first() } == listOf('a', 'b', 'c', 'd', 'e'))
    check("21.09 按标题：忽略大小写（大写标题不该排到最后）",
        sortArticles(
            listOf(art("z1", "s", "Zoo", 1), art("a1", "s", "apple", 2)),
            HomeSortMode.Title, 0
        ).map { it.title } == listOf("apple", "Zoo"))
    // ---- 随机 ----
    val r1 = sortArticles(bag, HomeSortMode.Random, 42)
    val r2 = sortArticles(bag, HomeSortMode.Random, 42)
    val r3 = sortArticles(bag, HomeSortMode.Random, 99)
    check("21.10 随机：元素一个不多一个不少", r1.map { it.link }.sorted() == bag.map { it.link }.sorted())
    check("21.11 随机：同一个种子结果完全一致（同 seed 必可复现）", r1.map { it.link } == r2.map { it.link })
    check("21.12 随机：换种子结果会变（否则「换一批」等于没换）", r1.map { it.link } != r3.map { it.link })
    check("21.13 随机：打乱了顺序（不是原样返回）", r1.map { it.link } != bag.map { it.link })
    check("21.14 随机：输入顺序不同、同种子结果相同（先按 link 归一化过）",
        sortArticles(bag.reversed(), HomeSortMode.Random, 42).map { it.link } == r1.map { it.link })
    // ---- 边界 ----
    check("21.15 空列表不炸", sortArticles(emptyList(), HomeSortMode.Random, 1).isEmpty())
    check("21.16 单条列表原样返回", sortArticles(listOf(bag[0]), HomeSortMode.Random, 7).size == 1)
    check("21.17 单条列表返回同一条", sortArticles(listOf(bag[0]), HomeSortMode.Random, 7).first().link == "L1")
    // ---- 枚举与分组开关 ----
    check("21.18 默认档位是最新在前（老用户升级后列表不变）", HomeSortMode.of(null) == HomeSortMode.TimeDesc)
    check("21.19 未知 key 回落默认", HomeSortMode.of("nonsense") == HomeSortMode.TimeDesc)
    check("21.20 每个档位的 key 都能被解析回来",
        HomeSortMode.entries.all { HomeSortMode.of(it.key) == it })
    check("21.21 key 互不重复", HomeSortMode.entries.map { it.key }.distinct().size == HomeSortMode.entries.size)
    check("21.22 label 互不重复", HomeSortMode.entries.map { it.label }.distinct().size == HomeSortMode.entries.size)
    check("21.23 只有按时间的两档才切日期分组",
        HomeSortMode.entries.filter { it.groupedByDay }.toSet() ==
                setOf(HomeSortMode.TimeDesc, HomeSortMode.TimeAsc))
    check("21.24 排序不会丢文章（每种模式都保留全部元素）",
        HomeSortMode.entries.all { sortArticles(bag, it, 5).size == bag.size })

    // ==================================================================
    // 22. 配色生成器（v2.3）
    // ==================================================================
    println()
    println("-- 22. 配色生成器 --")

    val presets = ThemePalette.presets
    check("22.01 至少内置 6 套配色（太少就不叫「可选」了）", presets.size >= 6, "实际 ${presets.size} 套")
    check("22.02 档位 key 互不重复", ThemePalette.entries.map { it.key }.distinct().size == ThemePalette.entries.size)
    check("22.03 档位 label 互不重复", ThemePalette.entries.map { it.label }.distinct().size == ThemePalette.entries.size)
    check("22.04 「自定义」不出现在内置列表里", presets.none { it == ThemePalette.Custom })
    check("22.05 默认档是青绿（老用户升级后外观不变）", ThemePalette.of("emerald") == ThemePalette.Emerald)
    check("22.06 未知 key 回落默认档", ThemePalette.of("nonsense") == ThemePalette.Emerald)
    check("22.07 每个档位的 key 都能解析回来", ThemePalette.entries.all { ThemePalette.of(it.key) == it })

    // 所有档位 × 深浅两套，逐个槽位检查
    val allSchemes = ThemePalette.entries.flatMap { p ->
        listOf(false, true).map { dark -> Triple(p, dark, generateScheme(seedFor(p, 200, 80), dark)) }
    }
    val opaque = allSchemes.all { (_, _, s) ->
        s.all().all { (_, v) -> (v shr 24) and 0xFFL == 0xFFL }
    }
    check("22.08 生成的每个槽位都是不透明色（没有漏写 alpha 的透明槽）", opaque)

    val allSlotsFilled = allSchemes.all { (_, _, s) -> s.all().all { (_, v) -> v and 0xFFFFFFL != 0L } }
    check("22.09 没有纯黑槽位（漏赋值的话会落到 0）", allSlotsFilled)

    // 色相环全扫描：这是「自定义」能拖到任意角度的前提
    val hueScan = (0 until 360 step 15).map { h ->
        generateScheme(seedFor(ThemePalette.Custom, h, 80), false) to
                generateScheme(seedFor(ThemePalette.Custom, h, 80), true)
    }
    check("22.10 色相环每 15° 都能生成（拖到哪都不炸）", hueScan.size == 24)
    check("22.11 鲜艳度 0~100 全区间都能生成合法不透明色",
        (0..100 step 10).all { sat ->
            generateScheme(seedFor(ThemePalette.Custom, 210, sat), false)
                .all().all { (_, v) -> (v shr 24) and 0xFFL == 0xFFL }
        })

    // ---- 对比度：这是「光看 hex 看不出问题」的核心校验 ----
    // 每条给一个门槛，返回「最差的那条差多少」（≥0 即全过）
    fun worstMargin(pairs: List<Triple<String, String, Double>>, dark: Boolean): Pair<Double, String> {
        var worst = Double.MAX_VALUE
        var who = ""
        allSchemes.filter { it.second == dark }.forEach { (p, _, s) ->
            val m = s.all().toMap()
            pairs.forEach { (a, b, min) ->
                val r = contrastRatio(m.getValue(a), m.getValue(b))
                if (r - min < worst) {
                    worst = r - min
                    who = "${p.label} $a/$b = ${"%.2f".format(r)}（要求 $min）"
                }
            }
        }
        return worst to who
    }

    // 前景/背景成对出现的地方，AA 正文标准是 4.5
    val textPairs = listOf(
        Triple("primary", "onPrimary", 4.5),
        Triple("primaryContainer", "onPrimaryContainer", 4.5),
        Triple("secondary", "onSecondary", 4.5),
        Triple("secondaryContainer", "onSecondaryContainer", 4.5),
        Triple("tertiary", "onTertiary", 4.5),
        Triple("tertiaryContainer", "onTertiaryContainer", 4.5),
        Triple("error", "onError", 4.5),
        Triple("errorContainer", "onErrorContainer", 4.5),
        Triple("inverseSurface", "inverseOnSurface", 4.5),
        Triple("inverseSurface", "inversePrimary", 4.5)
    )
    val (mLight, whoLight) = worstMargin(textPairs, dark = false)
    check("22.12 浅色：主色/二级/三级/错误等前景背景对对比度 ≥ 4.5（WCAG AA）", mLight >= 0, "最差 $whoLight")
    val (mDark, whoDark) = worstMargin(textPairs, dark = true)
    check("22.13 深色：同上 ≥ 4.5", mDark >= 0, "最差 $whoDark")

    // 正文级别的对比度要求更高（阅读类 App，长期盯着看）
    val bodyPairs = listOf(
        Triple("background", "onBackground", 10.0),
        Triple("surface", "onSurface", 10.0),
        // 次要文字（来源名、时间）用 AA 的 4.5 就够，卡到 7 连手工调过的默认配色都不达标
        Triple("surface", "onSurfaceVariant", 4.5)
    )
    val (mL, whoL) = worstMargin(bodyPairs, dark = false)
    check("22.14 浅色：正文对比度 ≥ 10、次要文字 ≥ 4.5", mL >= 0, "最差 $whoL")
    val (mD, whoD) = worstMargin(bodyPairs, dark = true)
    check("22.15 深色：正文对比度 ≥ 10、次要文字 ≥ 4.5", mD >= 0, "最差 $whoD")

    // ---- 方向不变量 ----
    check("22.16 浅色方案里「背景比前景亮」（isDark 判定靠的就是这个）",
        allSchemes.filter { !it.second }.all { (_, _, s) ->
            relativeLuminance(s.background) > relativeLuminance(s.onBackground)
        })
    check("22.17 深色方案里「背景比前景暗」",
        allSchemes.filter { it.second }.all { (_, _, s) ->
            relativeLuminance(s.background) < relativeLuminance(s.onBackground)
        })

    // ---- 错误色必须永远是红的 ----
    fun isReddish(c: Long): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return r > g && r > b
    }
    check("22.18 错误色在各种配色下都是红色系（不能跟着主题变成紫/绿）",
        allSchemes.all { (_, _, s) -> isReddish(s.error) })
    check("22.19 错误容器色也是红色系", allSchemes.all { (_, _, s) -> isReddish(s.errorContainer) })

    // ---- 不同档位确实长得不一样 ----
    val primariesLight = presets.map { generateScheme(it.seed, false).primary }
    check("22.20 每套预设的浅色主色都不同（不是复制粘贴出来的摆设）",
        primariesLight.distinct().size == presets.size)

    // ---- 色相保留 ----
    val hueKept = listOf(30, 120, 210, 300).all { h ->
        val generated = generateScheme(seedFor(ThemePalette.Custom, h, 90), false).primary
        val got = rgbToHsl(generated)[0]
        // 环形距离：0° 和 359° 其实是挨着的，直接相减会得到 359 这种假差距
        val diff = ((got - h + 540f) % 360f) - 180f
        Math.abs(diff) < 12f
    }
    check("22.21 自定义色相会被保留（拖到红就是红，不会跑成蓝）", hueKept)

    val satsDiffer = generateScheme(seedFor(ThemePalette.Custom, 200, 90), false).primary !=
            generateScheme(seedFor(ThemePalette.Custom, 200, 30), false).primary
    check("22.22 鲜艳度不同 → 主色不同（滑块真的有用）", satsDiffer)

    // ---- 纯函数：同输入必同输出（否则界面会每帧轻微变色） ----
    check("22.23 生成器是纯函数：同种子同模式两次结果完全一致",
        generateScheme(0xFF0A76BEL, false).all() == generateScheme(0xFF0A76BEL, false).all())
    check("22.24 各档位种子互不相同", presets.map { it.seed }.distinct().size == presets.size)
    check("22.25 深浅两套方案不会一模一样",
        ThemePalette.entries.all { p ->
            val s = seedFor(p, 200, 80)
            generateScheme(s, false).all() != generateScheme(s, true).all()
        })

    // ==================================================================
    // 23. 搜索关键词转义（v2.3）
    // ==================================================================
    println()
    println("-- 23. 搜索关键词转义 --")

    check("23.01 普通词两侧补 %", likePattern("天气") == "%天气%")
    check("23.02 空串变成 %%（匹配一切，等于没筛）", likePattern("") == "%%")
    check("23.03 % 会被转义（否则 LIKE '%%%' 命中全库 → 搜索失灵）",
        likePattern("50%") == "%50\\%%")
    check("23.04 _ 会被转义", likePattern("a_b") == "%a\\_b%")
    check("23.05 反斜杠本身会被转义", likePattern("a\\b") == "%a\\\\b%")
    // 关键顺序问题：先转义反斜杠，再转义 %
    check("23.06 输入里已有 \\% 时，两个字符各转各的（先处理反斜杠的顺序不能反）",
        likePattern("\\%") == "%\\\\\\%%")
    check("23.07 只有通配符的输入也被完全转义（%_% 不再命中一切）",
        likePattern("%%__%%") == "%\\%\\%\\_\\_\\%\\%%")
    check("23.08 转义后的串里没有「裸露」的 % 或 _",
        likePattern("100%_test").let { p ->
            val body = p.substring(1, p.length - 1) // 去掉两侧自己加的 %
            var i = 0
            var bare = false
            while (i < body.length) {
                val c = body[i]
                if (c == '\\') { i += 2; continue }
                if (c == '%' || c == '_') { bare = true; break }
                i++
            }
            !bare
        })
    check("23.09 中文与空格不受影响", likePattern("新 闻") == "%新 闻%")
    check("23.10 转义符常量就是反斜杠（和 DAO 里的 ESCAPE '\\' 必须一致）", LIKE_ESCAPE_CHAR == '\\')

    // ==================================================================
    // 24. 首页关键词（v2.4）
    // ==================================================================
    println()
    println("-- 24. 首页关键词 --")

    check("24.01 空列表清洗后还是空", sanitizeKeywords(emptyList()).isEmpty())
    check("24.02 去掉首尾空格", sanitizeKeywords(listOf("  手机  ")) == listOf("手机"))
    check("24.03 纯空白的词被丢掉（否则首页会出现一个透明的空胶囊）",
        sanitizeKeywords(listOf("手机", "   ", "", "汽车")) == listOf("手机", "汽车"))
    // 关键：忽略大小写去重。AI / ai 在搜索里筛出来的是同一批文章
    check("24.04 AI 与 ai 只保留一个（保留先出现的写法）",
        sanitizeKeywords(listOf("AI", "ai", "Ai")) == listOf("AI"))
    check("24.05 过长的词被截断到 $HOME_KEYWORD_MAX_LEN 个字",
        sanitizeKeywords(listOf("0123456789")) == listOf("01234567"))
    check("24.06 最多只留 $HOME_KEYWORD_LIMIT 个（首页那一行再多就只有滚动条了）",
        sanitizeKeywords((1..15).map { "w$it" }).size == HOME_KEYWORD_LIMIT)
    check("24.07 清洗不乱动顺序（顺序 = 首页胶囊的排列顺序）",
        sanitizeKeywords(listOf("汽车", "手机", "AI")) == listOf("汽车", "手机", "AI"))
    check("24.08 内置默认词本身是干净的（跑一遍清洗不会变）",
        sanitizeKeywords(DEFAULT_HOME_KEYWORDS) == DEFAULT_HOME_KEYWORDS)
    check("24.09 默认词没超过上限", DEFAULT_HOME_KEYWORDS.size <= HOME_KEYWORD_LIMIT)

    // ---- 匹配规则 ----
    val kwTitle = "小米发布新手机"
    val kwSummary = "续航提升明显"
    val kwBody = "文中提到了汽车行业的反应"
    check("24.10 关键词为空 = 不筛（直接算命中，调用方不用再套 if）",
        matchesKeyword(kwTitle, kwSummary, kwBody, ""))
    check("24.11 空白的关键词同样算不筛",
        matchesKeyword(kwTitle, kwSummary, kwBody, "   "))
    check("24.12 命中标题", matchesKeyword(kwTitle, kwSummary, kwBody, "手机"))
    check("24.13 命中摘要", matchesKeyword(kwTitle, kwSummary, kwBody, "续航"))
    // 这条最关键：只匹配标题的话，「汽车」这种常出现在正文里的词会一篇都筛不出来
    check("24.14 命中正文（只匹配标题 = 用户会觉得关键词失灵）",
        matchesKeyword(kwTitle, kwSummary, kwBody, "汽车"))
    check("24.15 忽略大小写", matchesKeyword("OpenAI 发布", "", "", "openai"))
    check("24.16 哪个字段都不含 → 不命中", !matchesKeyword(kwTitle, kwSummary, kwBody, "财经"))
    check("24.17 关键词自带空格也能命中（先 trim 再比）",
        matchesKeyword(kwTitle, kwSummary, kwBody, " 手机 "))

    // ========================================================
    // 25. 首页两行的显隐规则（v2.5）
    //
    // v2.5 把「首页筛选」那把三档开关（category / source / both）拆成了两个独立开关。
    // 拆的时候**没有做迁移写盘**，而是「新键没写过就按旧键现算」——
    // 好处是升级瞬间外观不变、老备份文件也能恢复对；
    // 代价是这段映射必须长期稳定。所以每条组合都在这里钉死。
    // ========================================================
    println()
    println("== 25. 首页筛选行显隐（v2.5） ==")

    // ---- 老用户：只有旧键（新键 = null）----
    // 这三条是「升级后外观一个像素不变」的保证
    check("25.01 旧档 category → 只显示分类行（旧默认值；升级后不能凭空多出一行）",
        HomeRows.showCategoryRow(null, HomeRows.CHIP_MODE_CATEGORY) &&
            !HomeRows.showSourceRow(null, HomeRows.CHIP_MODE_CATEGORY))
    check("25.02 旧档 source → 只显示阅源行",
        !HomeRows.showCategoryRow(null, HomeRows.CHIP_MODE_SOURCE) &&
            HomeRows.showSourceRow(null, HomeRows.CHIP_MODE_SOURCE))
    check("25.03 旧档 both → 两行都显示",
        HomeRows.showCategoryRow(null, HomeRows.CHIP_MODE_BOTH) &&
            HomeRows.showSourceRow(null, HomeRows.CHIP_MODE_BOTH))

    // ---- 全新安装：两个键都没写过 ----
    check("25.04 两个键都没有 → 等价于旧默认值（只显示分类行）",
        HomeRows.showCategoryRow(null, null) && !HomeRows.showSourceRow(null, null))

    // ---- 新键优先级更高 ----
    check("25.05 新键写了就盖过旧档（分类行）", !HomeRows.showCategoryRow(false, HomeRows.CHIP_MODE_BOTH))
    check("25.06 新键写了就盖过旧档（阅源行）", HomeRows.showSourceRow(true, HomeRows.CHIP_MODE_CATEGORY))
    check("25.07 两行都关掉是合法状态，不该被任何兜底掰回来",
        !HomeRows.showCategoryRow(false, HomeRows.CHIP_MODE_BOTH) &&
            !HomeRows.showSourceRow(false, HomeRows.CHIP_MODE_BOTH))
    check("25.08 新键 true 能把旧档的 source 掰回「分类行也显示」",
        HomeRows.showCategoryRow(true, HomeRows.CHIP_MODE_SOURCE))

    // ---- 脏数据兜底 ----
    // 关键：大小写不同 / 拼错的值如果原样拿去 != 比较，两个判断会**同时成立** → 两行一起冒出来
    check("25.09 旧键认不出来（大小写不对）→ 回落默认，不会两行一起冒出来",
        HomeRows.showCategoryRow(null, "Category") && !HomeRows.showSourceRow(null, "Category"))
    check("25.10 旧键是乱码同样回落默认",
        HomeRows.showCategoryRow(null, "??") && !HomeRows.showSourceRow(null, "??"))
    check("25.11 旧键是空串同样回落默认（当成没设过）",
        HomeRows.showCategoryRow(null, "") && !HomeRows.showSourceRow(null, ""))
    check("25.12 默认档常量就是 category（和加这个功能之前的默认一致）",
        HomeRows.DEFAULT_CHIP_MODE == HomeRows.CHIP_MODE_CATEGORY)

    // ==================== 26. 液态玻璃数值层（v2.6） ====================
    println()
    println("---- 26. 液态玻璃（Glass）----")

    // ---- 透明度必须「透得有分寸」----
    // 全透明 = 看不见底栏轮廓；太实 = 看不出玻璃。两头都是事故，所以钉住区间。
    check("26.01 深色填充透明度在 0..1 之间（不能全透也不能全实）",
        Glass.fillAlpha(true) > 0f && Glass.fillAlpha(true) < 1f)
    check("26.02 浅色填充透明度也在 0..1 之间",
        Glass.fillAlpha(false) > 0f && Glass.fillAlpha(false) < 1f)
    check("26.03 深色比浅色更透（深底上透出来的层次更明显，同 alpha 会糊成一块灰）",
        Glass.fillAlpha(true) < Glass.fillAlpha(false))
    check("26.04 描边透明度在 0..1 之间",
        Glass.strokeAlpha(true) in 0f..1f && Glass.strokeAlpha(false) in 0f..1f)
    check("26.05 浅色底的描边必须比深色底更实，否则白底上看不见轮廓",
        Glass.strokeAlpha(false) > Glass.strokeAlpha(true))
    check("26.06 受光面高度比例落在 0..1（否则会画到框外或盖满整块）",
        Glass.SHEEN_HEIGHT_RATIO > 0f && Glass.SHEEN_HEIGHT_RATIO < 1f)

    // ---- withAlpha：只许动 alpha，绝不能碰 RGB ----
    // 这是本组最关键的一条：alpha 用 shl 24 拼回去，一旦没夹取，
    // 负数 / 大于 1 的输入会**溢出到相邻通道**，算出一种随机的颜色（而不是全透 / 全实）。
    val baseArgb = 0xFF0E9F76L
    check("26.07 withAlpha 保留 RGB 三个通道",
        (Glass.withAlpha(baseArgb, 0.5f) and 0x00FFFFFFL) == 0x000E9F76L)
    check("26.08 withAlpha 0.5 → alpha 通道 128",
        Glass.alphaOf(Glass.withAlpha(baseArgb, 0.5f)) in 127..128)
    check("26.09 withAlpha 1.0 → 完全不透明", Glass.alphaOf(Glass.withAlpha(baseArgb, 1f)) == 255)
    check("26.10 withAlpha 0 → 完全透明", Glass.alphaOf(Glass.withAlpha(baseArgb, 0f)) == 0)
    check("26.11 传负数被夹成 0，不会溢出到 RGB",
        Glass.withAlpha(baseArgb, -3f) == (baseArgb and 0x00FFFFFFL))
    check("26.12 传大于 1 被夹成 255，不会溢出到 RGB",
        Glass.withAlpha(baseArgb, 9f) == (baseArgb or 0xFF000000L))

    // ---- 白色高光 ----
    check("26.13 white() = 纯白 + 给定 alpha",
        (Glass.white(0.16f) and 0x00FFFFFFL) == 0x00FFFFFFL && Glass.alphaOf(Glass.white(0.16f)) == 41)
    check("26.14 alphaOf 能还原出写进去的透明度", Glass.alphaOf(Glass.withAlpha(0xFF123456L, 0.62f)) == 158)

    println()
    println("==========================================")
    println("通过 $pass 项，失败 $fail 项")
    if (fail > 0) throw RuntimeException("有 $fail 项断言失败")
}