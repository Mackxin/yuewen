import com.example.yuewen.data.backup.Backup
import com.example.yuewen.data.backup.Remote
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
import com.example.yuewen.data.util.DailyDigest
import com.example.yuewen.data.util.BackupSpan
import com.example.yuewen.data.util.Json
import com.example.yuewen.data.util.DEFAULT_HOME_KEYWORDS
import com.example.yuewen.data.util.HOME_KEYWORD_LIMIT
import com.example.yuewen.data.util.HOME_KEYWORD_MAX_LEN
import com.example.yuewen.data.util.HomeRows
import com.example.yuewen.data.util.LIKE_ESCAPE_CHAR
import com.example.yuewen.data.util.WenjianTabs
import com.example.yuewen.data.util.WidgetSnapshot
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
import com.example.yuewen.ui.util.FindInArticle
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

    // 26. 液态玻璃数值层（v2.6 加，v2.7 删）
    // 用户看过实机效果后要求撤掉整套「液态玻璃」，Glass.kt 与这里的 14 条断言一并移除，
    // 断言总数 301 → 287。别再把这组加回来 —— 除非玻璃设计重新启用。

    // ========================================================
    // 27. 「闻件」页子栏下标换算（v2.8）
    //
    // v2.8 在「搜索」后面插了「稍后读」，后面三栏下标整体 +1。
    // 这段换算是**升级路径**：只有「老用户带着旧偏好装上新版本」才会走到，
    // 而开发时反复重装、偏好都是空的，本地怎么点都复现不出来。
    // 所以只能靠这里钉死 —— 换算错了的表现是「升级后一打开闻件就停在别的栏上」，
    // 而且只在老用户身上出现，事后极难定位。
    // ========================================================
    println()
    println("== 27. 闻件页子栏换算（v2.8）==")

    // ---- 老用户：只有旧键（新键 = null）----
    check("27.01 旧值 0（搜索）原地不动", WenjianTabs.resolve(null, 0) == WenjianTabs.SEARCH)
    check("27.02 旧值 1（原收藏）→ 落到新收藏，不是稍后读",
        WenjianTabs.resolve(null, 1) == WenjianTabs.BOOKMARKS)
    check("27.03 旧值 2（原历史）→ 落到新历史", WenjianTabs.resolve(null, 2) == WenjianTabs.HISTORY)
    check("27.04 旧值 3（原笔记）→ 落到新笔记", WenjianTabs.resolve(null, 3) == WenjianTabs.NOTES)
    // 这条是整段换算的核心风险点：插栏之后 1 是「稍后读」，
    // 如果换算写成「原样返回」，老用户的收藏栏会变成空的稍后读栏。
    check("27.05 任何旧值都不该被送到「稍后读」（那一栏对老用户是全新的）",
        listOf(0, 1, 2, 3).none { WenjianTabs.resolve(null, it) == WenjianTabs.QUEUE })

    // ---- 全新安装：两个键都没写过 ----
    check("27.06 两个键都没有 → 搜索栏", WenjianTabs.resolve(null, null) == WenjianTabs.SEARCH)

    // ---- 新键存在就盖过旧键（老偏好不再参与）----
    check("27.07 新键写了就用新键，不看旧键",
        WenjianTabs.resolve(WenjianTabs.NOTES, 0) == WenjianTabs.NOTES)
    check("27.08 新键是 0 也算「写过」—— 不能因为像默认值就回退去读旧键",
        WenjianTabs.resolve(0, 3) == WenjianTabs.SEARCH)

    // ---- 脏数据兜底：越界一律夹回范围，绝不落到别的分支 ----
    check("27.09 新键越界（99）夹到最后一栏", WenjianTabs.resolve(99, null) == WenjianTabs.NOTES)
    check("27.10 新键是负数夹到第 0 栏", WenjianTabs.resolve(-3, null) == WenjianTabs.SEARCH)
    check("27.11 旧键越界（99）也夹得住", WenjianTabs.resolve(null, 99) == WenjianTabs.NOTES)
    check("27.12 旧键是负数夹到第 0 栏", WenjianTabs.resolve(null, -3) == WenjianTabs.SEARCH)

    // ---- 常量与「标签表长度」的一致性 ----
    // WenjianScreen.WENJIAN_TABS 是私有表、测试拿不到，所以退一步钉住数字本身。
    // 以后再加栏时，这两条会和代码里的 COUNT 一起被改，改错就先在这里红掉。
    check("27.13 COUNT = 5（与闻件页标签表长度一致）", WenjianTabs.COUNT == 5)
    check("27.14 五个下标常量互不相同且都落在 0..COUNT-1",
        setOf(
            WenjianTabs.SEARCH, WenjianTabs.QUEUE, WenjianTabs.BOOKMARKS,
            WenjianTabs.HISTORY, WenjianTabs.NOTES
        ) == setOf(0, 1, 2, 3, 4))
    check("27.15 默认栏位仍是最靠前的那个（常量顺序没被颠倒）",
        WenjianTabs.SEARCH == 0 && WenjianTabs.NOTES == WenjianTabs.COUNT - 1)

    // ========================================================
    // 28. 备份里的「收藏 / 稍后读」（v2.8）
    //
    // `bookmarks` 这个数组从 v2.8 起装的是**收藏 ∪ 稍后读队列**。
    // 名字没改（改名会让老版本读不了新文件），靠每条里显式写出的
    // `isBookmarked` / `queuedAt` 两个字段来区分。
    // 漏写任何一个字段 → 用户换机后「队列全变收藏、队列本身空了」，
    // 而本机重装是复现不出来的（库是空的）。所以必须在这里钉住。
    // ========================================================
    println()
    println("== 28. 备份的收藏 / 稍后读字段（v2.8）==")

    val queuedOnly = Article(
        link = "https://example.com/q1", title = "只在队列里", sourceName = "某源",
        isBookmarked = false, queuedAt = 1_700_000_000_123L
    )
    val markedOnly = Article(
        link = "https://example.com/b1", title = "只在收藏里", sourceName = "某源",
        isBookmarked = true, queuedAt = 0L
    )
    val both = Article(
        link = "https://example.com/bq", title = "既收藏又稍后读", sourceName = "某源",
        isBookmarked = true, queuedAt = 1_600_000_000_000L
    )
    val bkRaw = Backup.export(
        sources = emptyList(),
        bookmarks = listOf(queuedOnly, markedOnly, both),
        notes = emptyList(),
        settings = emptyMap(),
        now = 1L
    )

    // 「先确认字段真的写进了 JSON 文本」——
    // 只测还原是不够的：导出端把 queuedAt 写成常量 0（或干脆不写）时，
    // 还原端读到 0 也「能过」，两条一起错就互相掩盖了。
    check("28.01 导出的 JSON 文本里有 isBookmarked 字段", bkRaw.contains("\"isBookmarked\""))
    check("28.02 导出的 JSON 文本里有 queuedAt 字段", bkRaw.contains("\"queuedAt\""))
    check("28.03 队列时间戳真的写进了文本里", bkRaw.contains("1700000000123"))

    val bkBack = Backup.parse(bkRaw)
    check("28.04 导出→导入能解析出 3 条", bkBack?.bookmarks?.size == 3, "实际 ${bkBack?.bookmarks?.size}")
    val byLink = bkBack?.bookmarks.orEmpty().associateBy { it.link }
    check("28.05 只入队的：queuedAt 保住、isBookmarked 仍是 false",
        byLink["https://example.com/q1"]?.queuedAt == 1_700_000_000_123L &&
            byLink["https://example.com/q1"]?.isBookmarked == false)
    check("28.06 只收藏的：isBookmarked 保住、queuedAt 仍是 0",
        byLink["https://example.com/b1"]?.isBookmarked == true &&
            byLink["https://example.com/b1"]?.queuedAt == 0L)
    check("28.07 既收藏又入队的：两个字段各自独立还原，不会被压成一个",
        byLink["https://example.com/bq"]?.isBookmarked == true &&
            byLink["https://example.com/bq"]?.queuedAt == 1_600_000_000_000L)

    // ---- 老备份文件（v2.8 之前导出的：数组里只有收藏，两个新字段都不存在）----
    // 期望：当成收藏（isBookmarked 默认 true），且不在队列里（queuedAt = 0）。
    // 这两条是这个字段默认值的**唯一理由**，写反了老用户恢复备份会得到一队空收藏。
    val legacyRaw = """
        {"app":"yuewen","version":1,"exportedAt":1,
         "sources":[],
         "bookmarks":[{"link":"https://example.com/old","title":"老备份里的收藏"}],
         "notes":[],"settings":{}}
    """.trimIndent()
    val legacyBundle = Backup.parse(legacyRaw)
    check("28.08 老备份（没有 isBookmarked 键）→ 当作收藏",
        legacyBundle?.bookmarks?.firstOrNull()?.isBookmarked == true)
    check("28.09 老备份（没有 queuedAt 键）→ 不在队列里",
        legacyBundle?.bookmarks?.firstOrNull()?.queuedAt == 0L)

    // 显式写了 false 的，不能被「默认 true」的兜底顺手掰回 true
    val explicitFalse = Backup.parse(
        """{"app":"yuewen","version":1,"exportedAt":1,"sources":[],
            "bookmarks":[{"link":"https://example.com/x","isBookmarked":false,"queuedAt":5}],
            "notes":[],"settings":{}}""".trimIndent()
    )
    check("28.10 显式的 false 不会被兜底掰成 true",
        explicitFalse?.bookmarks?.firstOrNull()?.isBookmarked == false &&
            explicitFalse?.bookmarks?.firstOrNull()?.queuedAt == 5L)

    // ========================================================
    // 29. 文内查找（v2.8）
    //
    // 这一组钉的是**段号**与**下标运算**，它们是「搜索 → 高亮 + 跳转」三步共用的那把尺子：
    // 段号算错 → 点「下一处」跳到别的段落；下标不环绕 → 到最后一条按钮像坏了。
    // 纯字符串 / 纯数字，正好能离线跑完。
    // ========================================================
    println()
    println("== 29. 文内查找（v2.8）==")

    // 段号体系：0 = 标题，1.. = 正文文本段（图片不占号）
    val paras = listOf(
        "手机影像的下半场",          // 0 标题
        "这几年手机拍照进步很快。",   // 1
        "但是手机影像也有瓶颈。",     // 2
        "瓶颈主要在传感器尺寸。",     // 3
        "另一件事是算法。"            // 4
    )

    check("29.01 空查询返回空（不是命中全部）", FindInArticle.hits(paras, "").isEmpty())
    check("29.02 只有空格也算空查询", FindInArticle.hits(paras, "   ").isEmpty())
    check("29.03 命中的段号就是 speakParas 的下标（顺序 = 文档顺序）",
        FindInArticle.hits(paras, "瓶颈") == listOf(2, 3))
    check("29.04 标题也要能被搜到（标题是第 0 段）",
        FindInArticle.hits(paras, "下半场") == listOf(0))
    check("29.05 搜不到就是空，不是「第一个」", FindInArticle.hits(paras, "量子计算").isEmpty())
    check("29.06 一段里出现多次只算一条命中（否则要按「第几处」跳同一段好几遍）",
        FindInArticle.hits(listOf("手机和手机之间"), "手机") == listOf(0))
    check("29.07 英文大小写不敏感",
        FindInArticle.hits(listOf("iPhone 与 IPHONE"), "iphone") == listOf(0))
    check("29.08 查询词两头的空格会被 trim（用户误按空格不该搜不到）",
        FindInArticle.hits(paras, " 瓶颈 ") == listOf(2, 3))

    // ---- 段内划线区间 ----
    check("29.09 一段里的多处命中都划出来", FindInArticle.ranges("手机和手机", "手机") == listOf(0..1, 3..4))
    check("29.10 区间含首含尾（endInclusive），不是半开区间",
        FindInArticle.ranges("abcabc", "abc") == listOf(0..2, 3..5))
    check("29.11 不重叠：不会在原地反复命中",
        FindInArticle.ranges("aaaa", "aa") == listOf(0..1, 2..3))
    check("29.12 没有命中就是空", FindInArticle.ranges("手机", "电脑").isEmpty())
    check("29.13 空查询不产生区间（否则会画出整段的底色）", FindInArticle.ranges("手机", "").isEmpty())
    check("29.14 大小写不敏感但要按原文切出来",
        FindInArticle.ranges("Hello World", "world") == listOf(6..10))
    // ⚠️ 这条是越界防护：查询词比正文长时，Naive 的 indexOf 循环会退化成负数长度 substring
    check("29.15 查询词比整段还长 → 空，不炸",
        FindInArticle.ranges("短", "很长的一段查询词").isEmpty())

    // ---- 环形步进 ----
    check("29.16 正常 +1", FindInArticle.step(0, 1, 5) == 1)
    check("29.17 正常 -1", FindInArticle.step(3, -1, 5) == 2)
    check("29.18 末尾 +1 绕回开头", FindInArticle.step(4, 1, 5) == 0)
    check("29.19 开头 -1 绕到末尾（不是原地不动 —— 那看起来像按钮坏了）",
        FindInArticle.step(0, -1, 5) == 4)
    check("29.20 只有一条时怎么按都在原地", 
        FindInArticle.step(0, 1, 1) == 0 && FindInArticle.step(0, -1, 1) == 0)
    check("29.21 空结果集返回 0（调用方靠 hits 是否为空来决定按钮能不能点）",
        FindInArticle.step(3, 1, 0) == 0)
    check("29.22 越界入参也夹得住（脏状态兜底）", FindInArticle.step(99, 1, 5) == 0)

    // ========================================================
    // 30. 每日摘要（v2.8）
    //
    // 这一组要钉的是两件「只有第二天早上才会暴露」的事：
    // ① 下一次该在什么时候跑 —— 算错的表现是「设了 8 点，第二天 9 点才响」
    //    或者更糟：算成 0 毫秒 → Worker 立刻跑 → 又排下一次 → 又是一瞬间，
    //    变成一秒内跑几十次的死循环（把电和流量烧光）；
    // ② 这条通知该不该发 —— 算错的表现是每天准时响一声，点开发现什么都没有。
    //
    // 时区固定成 Asia/Shanghai（+08:00，中国无夏令时）以保证断言在任何机器上结果一致；
    // 单独再补几条跨时区 / 跨月的用例，因为「今天」和「明天」都是按时区算的。
    // ========================================================
    println()
    println("== 30. 每日摘要（v2.8）==")

    val sh = ZoneId.of("Asia/Shanghai")

    /** 造一个上海本地时间的时间戳，纯为了下面读起来像人话。 */
    fun shAt(month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(2026, month, day, hour, minute, second, 0, sh).toInstant().toEpochMilli()

    // ---- 30.1 时间文案 ----
    check("30.01 补零成两位", DailyDigest.format(8, 0) == "08:00")
    check("30.02 分也要补零", DailyDigest.format(21, 5) == "21:05")
    check("30.03 越界的时/分会被夹回来（25:70 → 23:59）", DailyDigest.format(25, 70) == "23:59")
    check("30.04 负数也夹得住（-1:-1 → 00:00）", DailyDigest.format(-1, -1) == "00:00")
    check("30.05 夹取函数本身", DailyDigest.sanitizeHour(24) == 23 && DailyDigest.sanitizeMinute(-3) == 0)

    // ---- 30.2 「今天 00:00」 ----
    check("30.06 下午 15:30 的今天从当天 00:00 起",
        DailyDigest.todayStart(shAt(10, 5, 15, 30), sh) == shAt(10, 5, 0, 0))
    check("30.07 正好 00:00 就是自己", DailyDigest.todayStart(shAt(10, 5, 0, 0), sh) == shAt(10, 5, 0, 0))
    check("30.08 00:00:00.001 还属于当天（不是前一天）",
        DailyDigest.todayStart(shAt(10, 5, 0, 0) + 1, sh) == shAt(10, 5, 0, 0))
    check("30.09 23:59 也属于当天", DailyDigest.todayStart(shAt(10, 5, 23, 59), sh) == shAt(10, 5, 0, 0))
    check("30.10 换月没影响", DailyDigest.todayStart(shAt(11, 1, 3, 0), sh) == shAt(11, 1, 0, 0))
    // 同一个绝对时刻，在 UTC 下「今天」的起点跟在上海下不是一回事 —— 这条证明
    // todayStart 真的按传入时区算，而不是写死了 +08:00
    check("30.11 时区是活的：纽约的今天起点 ≠ 上海的",
        DailyDigest.todayStart(shAt(10, 5, 15, 30), sh) !=
                DailyDigest.todayStart(shAt(10, 5, 15, 30), ZoneId.of("America/New_York")))

    // ---- 30.3 下一次触发还有多久 ----
    val hourMs = 3_600_000L
    val dayMs = 24 * hourMs

    check("30.12 7:00 时问 8:00 → 还有 1 小时",
        DailyDigest.nextDelayMillis(shAt(10, 5, 7, 0), 8, 0, sh) == hourMs)
    check("30.13 5:00 时问 8:00 → 还有 3 小时",
        DailyDigest.nextDelayMillis(shAt(10, 5, 5, 0), 8, 0, sh) == 3 * hourMs)
    check("30.14 9:00 时问 8:00 → 已经过了，算明天（23 小时）",
        DailyDigest.nextDelayMillis(shAt(10, 5, 9, 0), 8, 0, sh) == 23 * hourMs)
    // ⚠️ 这条是全组最要紧的一条：正好卡在目标时刻上必须给「明天」，
    // 给 0 的话 Worker 会立刻跑 → 又排下一次 → 又是 0，一秒内转上百圈。
    check("30.15 正好在目标时刻上 → 24 小时后，不是 0（防死循环）",
        DailyDigest.nextDelayMillis(shAt(10, 5, 8, 0), 8, 0, sh) == dayMs)
    check("30.16 过了目标 1 毫秒 → 还差 24 小时差 1 毫秒，不是 0",
        DailyDigest.nextDelayMillis(shAt(10, 5, 8, 0) + 1, 8, 0, sh) == dayMs - 1)
    check("30.17 23:00 时问 8:00 → 跨天，还有 9 小时",
        DailyDigest.nextDelayMillis(shAt(10, 5, 23, 0), 8, 0, sh) == 9 * hourMs)
    check("30.18 跨月：10-31 23:00 问 7:00 → 8 小时（落到 11-01）",
        DailyDigest.nextDelayMillis(shAt(10, 31, 23, 0), 7, 0, sh) == 8 * hourMs)
    check("30.19 跨年：12-31 23:00 问 8:00 → 9 小时（落到次年 1-1）",
        DailyDigest.nextDelayMillis(shAt(12, 31, 23, 0), 8, 0, sh) == 9 * hourMs)
    check("30.20 分钟也参与计算：7:30 问 8:15 → 45 分钟",
        DailyDigest.nextDelayMillis(shAt(10, 5, 7, 30), 8, 15, sh) == 45 * 60_000L)
    // ⚠️ 明天那一班必须是「本地日历 +1 天」再取时刻，不能用「今天那一班 + 24 小时」——
    // 后者在有夏令时的时区会偏整整一个小时。中国没有夏令时，所以借纽约钉一下：
    // 2026-03-08 是美国的夏令时切换日（凌晨 2 点直接跳到 3 点），
    // 3-07 23:00 → 3-08 08:00 的真实间隔是 **8** 小时；
    // 写成「todayAt + 24h」会算出 9 小时。
    val ny = ZoneId.of("America/New_York")
    val dstNow = ZonedDateTime.of(2026, 3, 7, 23, 0, 0, 0, ny).toInstant().toEpochMilli()
    check("30.21 跨夏令时切换：3-07 23:00 问 08:00 的真实间隔是 8 小时（不是写死 +24h）",
        DailyDigest.nextDelayMillis(dstNow, 8, 0, ny) == 8 * hourMs)
    check("30.22 越界的目标时刻先被夹住再算（25:00 → 23:00）",
        DailyDigest.nextDelayMillis(shAt(10, 5, 22, 0), 25, 0, sh) == hourMs)
    check("30.23 结果恒为正（任何入参下都不会给出 0 或负数）",
        listOf(
            shAt(10, 5, 0, 0), shAt(10, 5, 8, 0), shAt(10, 5, 8, 0) + 1,
            shAt(10, 5, 12, 0), shAt(10, 5, 23, 59)
        ).all { DailyDigest.nextDelayMillis(it, 8, 0, sh) > 0L })

    // ---- 30.4 预设时刻表 ----
    check("30.24 预设有选项（设置页那排胶囊不能是空的）", DailyDigest.PRESETS.isNotEmpty())
    check("30.25 预设按时间从早到晚排好序（界面上是顺的）",
        DailyDigest.PRESETS == DailyDigest.PRESETS.sortedBy { (h, m) -> h * 60 + m })
    // ⚠️ 必须加括号：`to` 的优先级比 `in` 低，不写括号会变成
    // `HOUR to (MINUTE in PRESETS)`，类型对不上，直接编译失败。
    check("30.26 默认时刻就在预设里（否则打开设置时一个胶囊都不亮，像坏了）",
        (DailyDigest.DEFAULT_HOUR to DailyDigest.DEFAULT_MINUTE) in DailyDigest.PRESETS)
    check("30.27 预设值合法且 format 可逆（不会出现 24:00 这种，也不会补零补错）",
        DailyDigest.PRESETS.all { (h, m) ->
            h in 0..23 && m in 0..59 &&
                    DailyDigest.format(h, m).let { s ->
                        s.length == 5 && s[2] == ':' &&
                                s.substring(0, 2).toInt() == h && s.substring(3, 5).toInt() == m
                    }
        })

    // ---- 30.5 该不该推 / 推什么 ----
    fun facts(today: Int, unread: Int = 0, queued: Int = 0, titles: List<String> = emptyList()) =
        DailyDigest.DigestFacts(today, unread, queued, titles)

    check("30.28 今天没新内容、队列也空 → 不推（null）",
        DailyDigest.buildContent(facts(0, unread = 0, queued = 0)) == null)
    // ⚠️ 这条是刻意的产品决定：光有未读不算理由。库里躺着 200 篇没读是常态，
    // 拿它当推送理由等于每天固定骚扰一次。
    check("30.29 只有未读一堆、今天没新的 → 照样不推",
        DailyDigest.buildContent(facts(0, unread = 240, queued = 0)) == null)
    check("30.30 今天有新内容 → 推，并带上未读数",
        DailyDigest.buildContent(facts(12, unread = 30))?.text == "今天 12 条新内容 · 未读 30 条")
    check("30.31 今天有新内容且队列非空 → 队列优先播报（那才是要用户动手的事）",
        DailyDigest.buildContent(facts(12, unread = 30, queued = 4))?.text ==
                "今天 12 条新内容 · 队列里 4 篇待读")
    check("30.32 今天没新的但队列压着 → 推，且说明今天没新的",
        DailyDigest.buildContent(facts(0, unread = 5, queued = 3))?.text ==
                "今天暂无新内容 · 队列里 3 篇待读")
    check("30.33 标题固定是「今日摘要」",
        DailyDigest.buildContent(facts(1))?.title == "今日摘要")

    // 展开态
    val withTitles = DailyDigest.buildContent(
        facts(3, unread = 3, titles = listOf("标题甲", "标题乙", "标题丙"))
    )
    check("30.34 展开态里先说事、再列标题（换行分开）",
        withTitles?.bigText == "今天 3 条新内容 · 未读 3 条\n\n· 标题甲\n· 标题乙\n· 标题丙")
    check("30.35 折叠态只有那一行摘要，不带标题",
        withTitles?.text == "今天 3 条新内容 · 未读 3 条")
    check("30.36 没有标题时展开态就等于折叠态（不多出空行）",
        DailyDigest.buildContent(facts(3, unread = 3))?.bigText == "今天 3 条新内容 · 未读 3 条")
    check("30.37 空白标题会被丢掉，不留一行「· 」",
        DailyDigest.buildContent(facts(1, titles = listOf("  ", "", "真标题")))?.bigText ==
                "今天 1 条新内容 · 未读 0 条\n\n· 真标题")
    check("30.38 标题两头的空格会 trim（RSS 里很常见）",
        DailyDigest.buildContent(facts(1, titles = listOf("  带空格的标题  ")))?.bigText ==
                "今天 1 条新内容 · 未读 0 条\n\n· 带空格的标题")
    // 脏数据兜底：负数不是「不推」的理由，夹成 0 之后按正常分支走
    check("30.39 负数入参不炸也不会算出「今天 -3 条」",
        DailyDigest.buildContent(facts(-3, unread = -1, queued = 2))?.text ==
                "今天暂无新内容 · 队列里 2 篇待读")

    println("========== 31. 桌面小组件：数字写法与文案 ==========")

    fun wf(
        unread: Int = 0,
        today: Int = 0,
        queued: Int = 0,
        items: List<WidgetSnapshot.Item> = emptyList()
    ) = WidgetSnapshot.Facts(unread, today, queued, items)

    fun item(t: String, l: String) = WidgetSnapshot.Item(t, l)

    // ---- 31.1 大数字怎么写 ----
    // 卡片宽度就那么点，五位数的处理方式是这块最容易「看起来没坏、其实很丑」的地方，
    // 而桌面上很难把每种数量级都试一遍，所以全靠这里钉住。
    check("31.01 三位数原样显示", WidgetSnapshot.count(176) == "176")
    check("31.02 四位数还不到收成「万」的程度", WidgetSnapshot.count(9999) == "9999")
    check("31.03 刚好一万从 10000 变成 1.0万", WidgetSnapshot.count(10_000) == "1.0万")
    check("31.04 五位数保留一位小数", WidgetSnapshot.count(12_345) == "1.2万")
    check("31.05 四舍五入到一位小数", WidgetSnapshot.count(19_960) == "2.0万")
    check("31.06 到「百万」级别就丢掉小数（再留一位太长）", WidgetSnapshot.count(1_230_000) == "123万")
    check("31.07 先四舍五入再决定写法，不会出现「100.0万」",
        WidgetSnapshot.count(999_999) == "100万")
    check("31.08 0 显示成 0，不是空字符串", WidgetSnapshot.count(0) == "0")
    check("31.09 负数夹成 0（脏数据不该让卡片显示「-3」）", WidgetSnapshot.count(-5) == "0")
    // ⚠️ 小数点必须是英文句点：`%.1f` 跟着系统 Locale 走的话，
    // 某些语言环境下会写成「1,2万」，中文里读不通。
    check("31.10 万位的小数点不受系统语言影响",
        WidgetSnapshot.count(18_000) == "1.8万" && !WidgetSnapshot.count(18_000).contains(','))

    // ---- 31.2 一行摘要 ----
    check("31.11 今天有新的 + 队列有货 → 两个都报",
        WidgetSnapshot.summary(wf(today = 3, queued = 5)) == "今日 3 条 · 队列 5 篇")
    check("31.12 只有今天有新的", WidgetSnapshot.summary(wf(today = 3)) == "今日 3 条新内容")
    check("31.13 只有队列有货", WidgetSnapshot.summary(wf(queued = 5)) == "队列 5 篇待读")
    check("31.14 今天没新的但还有未读 → 明说今天没新的，而不是显示 0",
        WidgetSnapshot.summary(wf(unread = 30)) == "今日暂无新内容")
    check("31.15 一条未读都没有 → 说「都读完了」", WidgetSnapshot.summary(wf()) == "都读完了")
    check("31.16 三个数全是负数也不会写出「今日 -2 条」",
        WidgetSnapshot.summary(wf(unread = -1, today = -2, queued = -3)) == "都读完了")

    // ---- 31.3 2×2 ----
    val sm = WidgetSnapshot.small(wf(unread = 12_345, today = 3, queued = 5))
    check("31.17 2×2 的大数字走同一套写法", sm.unreadText == "1.2万")
    // 「阅闻」「未读」这两个固定文案在布局里（@string/widget_*），不归这里管 ——
    // 所以这里只断言真正动态的那两行。
    check("31.18 2×2 的副行就是摘要", sm.sub == "今日 3 条 · 队列 5 篇")
    check("31.19 空库时 2×2 显示 0 而不是空白",
        WidgetSnapshot.small(wf()).let { it.unreadText == "0" && it.sub == "都读完了" })

    // ---- 31.4 4×2 ----
    val wd = WidgetSnapshot.wide(
        wf(
            unread = 7,
            items = listOf(item("  甲  ", "https://a"), item("乙", "https://b"), item("丙", "https://c"))
        )
    )
    check("31.20 4×2 只取两条标题（第三行显示不下）",
        wd.title1 == "甲" && wd.title2 == "乙")
    check("31.21 标题两头的空格会 trim（RSS 里很常见）", wd.title1 == "甲")
    check("31.22 两行都显示", wd.showTitle1 && wd.showTitle2)
    check("31.23 每行标题带着自己的链接（点哪条进哪条）",
        wd.link1 == "https://a" && wd.link2 == "https://b")
    check("31.24 被裁掉的第三条不会漏进任何字段", wd.title1 != "丙" && wd.title2 != "丙")

    val onlyOne = WidgetSnapshot.wide(wf(items = listOf(item("唯一一条", "https://x"))))
    check("31.25 只有一条时第二行要隐藏，而不是留一行空白", onlyOne.showTitle1 && !onlyOne.showTitle2)
    check("31.26 隐藏那行的文案是空串（配合 GONE，不能留出「null」）", onlyOne.title2 == "")
    check("31.27 只有一条时它的链接照样带上", onlyOne.link1 == "https://x")

    val none = WidgetSnapshot.wide(wf())
    check("31.28 一条标题都没有时给引导语，而不是留白（留白看着像加载失败）",
        none.title1 == WidgetSnapshot.EMPTY_HINT && none.showTitle1)
    check("31.29 引导语不对应任何文章 → 没有链接（那一行只能打开 App）",
        none.link1 == null && none.link2 == null)
    check("31.30 没有条目时第二行同样隐藏", !none.showTitle2)

    val blanks = WidgetSnapshot.wide(
        wf(items = listOf(item("   ", "https://blank"), item("真标题", "https://real")))
    )
    check("31.31 空白标题被过滤掉，真标题顶上第一行",
        blanks.title1 == "真标题" && blanks.link1 == "https://real")
    check("31.32 过滤后只剩一条 → 第二行隐藏", !blanks.showTitle2)
    check("31.33 取数上限就是 2（和布局里画的行数一致）", WidgetSnapshot.TITLE_LIMIT == 2)

    // ==================================================================
    println("========== 32. 云备份：文件名规则与两个远程通道 ==========")
    // ==================================================================

    val z = ZoneId.systemDefault()
    fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, s, 0, z).toInstant().toEpochMilli()

    // ---- 32.1 文件命名 ----

    val t0 = at(2026, 10, 5, 23, 10, 45)
    check("32.01 时间戳固定 15 个字符（字典序能当时间序用的前提）",
        BackupSpan.stamp(t0) == "20261005-231045")
    check("32.02 文件名 = 前缀 + 时间戳 + .json",
        BackupSpan.autoName(t0) == "yuewen-20261005-231045.json")
    check("32.03 认得出自己的文件名",
        BackupSpan.isOurs(BackupSpan.autoName(t0)))
    // ⚠️ 这条是「本地文件夹」通道的保命断言：目录是用户自己选的，
    // 光看前缀就认的话，用户存的 `yuewen-笔记.json` 会被当成过期备份删掉。
    check("32.04 只有前缀对、格式不对的不算我们的（用户自己的文件不能被删）",
        !BackupSpan.isOurs("yuewen-笔记.json") &&
                !BackupSpan.isOurs("yuewen-备份.json") &&
                !BackupSpan.isOurs("yuewen-.json"))
    check("32.05 别的 App 的文件不算我们的",
        !BackupSpan.isOurs("readwise-20261005.json") && !BackupSpan.isOurs("notes.txt"))
    check("32.06 后缀必须正好是 .json",
        !BackupSpan.isOurs("yuewen-20261005-231045.json.bak"))
    check("32.07 取不出时间戳时返回 null", BackupSpan.stampOf("随便一个文件") == null)
    check("32.08 展示用的时间去掉秒，只到分钟",
        BackupSpan.displayOf("yuewen-20261005-231045.json") == "2026-10-05 23:10")
    check("32.09 不是备份的名字没有展示时间",
        BackupSpan.displayOf("yuewen-笔记.json") == null)

    val n1 = BackupSpan.autoName(at(2026, 10, 5, 23, 10, 45))
    val n2 = BackupSpan.autoName(at(2026, 10, 4, 9, 0, 0))
    val n3 = BackupSpan.autoName(at(2026, 10, 6, 8, 30, 0))
    check("32.10 列表按时间从新到旧",
        BackupSpan.newestFirst(listOf(n2, n1, n3)) == listOf(n3, n1, n2))
    // 跨月 / 跨年时字符串比较最容易和真实时间顺序分家 —— 这条专门钉住它。
    val dec = BackupSpan.autoName(at(2026, 12, 31, 23, 59, 59))
    val jan = BackupSpan.autoName(at(2027, 1, 1, 0, 0, 0))
    check("32.11 跨年时字典序仍然等于时间序",
        BackupSpan.stamp(at(2026, 12, 31, 23, 59, 59)) <
                BackupSpan.stamp(at(2027, 1, 1, 0, 0, 0)))
    check("32.12 跨年时排序也对", BackupSpan.newestFirst(listOf(dec, jan)) == listOf(jan, dec))
    check("32.13 排序时会把非备份的文件筛掉",
        BackupSpan.newestFirst(listOf("yuewen-笔记.json", n1, "a.txt")) == listOf(n1))
    // 同一个秒里备份两次会生成同名文件；列表里真出现两份时不能多数掉一个名额。
    check("32.14 重复出现的同一个名字只算一份", BackupSpan.newestFirst(listOf(n1, n1)).size == 1)

    check("32.15 多于保留份数时，最旧的被挑出来删",
        BackupSpan.toPrune(listOf(n3, n1, n2), keep = 2) == listOf(n2))
    check("32.16 文件还不够保留份数时一个都不删",
        BackupSpan.toPrune(listOf(n3, n1), keep = 5).isEmpty())
    check("32.17 删的名单里绝不会出现用户的文件",
        BackupSpan.toPrune(listOf("yuewen-笔记.json", n3, n1, n2), keep = 1)
            .none { it == "yuewen-笔记.json" })
    // 「保留 0 份」= 备份完立刻删掉自己，是一定要拦住的组合。
    check("32.18 keep=0 被夹成 1，不会把最后一份也删掉",
        BackupSpan.toPrune(listOf(n3, n1, n2), keep = 0) == listOf(n1, n2))
    check("32.19 负数 keep 同样夹成 1",
        BackupSpan.toPrune(listOf(n3, n1), keep = -3).size == 1)
    check("32.20 keep 超过上限夹到 30", BackupSpan.sanitizeKeep(999) == BackupSpan.KEEP_MAX)
    check("32.21 合法的 keep 原样返回", BackupSpan.sanitizeKeep(7) == 7)
    check("32.22 keep 下限是 1", BackupSpan.KEEP_MIN == 1 && BackupSpan.KEEP_MAX == 30)
    check("32.23 默认保留 5 份", BackupSpan.KEEP_DEFAULT == 5)
    // 秒级精度：同一分钟内点两次备份，不能生成同一个名字（否则后一次静默覆盖前一次）。
    check("32.24 同一分钟内的两次备份名字不同",
        BackupSpan.autoName(at(2026, 10, 5, 23, 10, 0)) !=
                BackupSpan.autoName(at(2026, 10, 5, 23, 10, 30)))
    // 文件名要进 URL（WebDAV）和 JSON 键（Gist），非 ASCII 会逼着两边都去处理编码。
    check("32.25 文件名全是 ASCII（进 URL / JSON 都不用再转义）",
        BackupSpan.autoName(t0).all { it.code < 128 })

    // ---- 32.2 认证 ----

    val auth = Remote.basicAuth("me@example.com", "app-pass")
    check("32.26 Basic 认证头以 'Basic ' 开头", auth.startsWith("Basic "))
    check("32.27 认证头里没有明文密码", !auth.contains("app-pass"))
    check("32.28 解码回来就是 用户名:密码",
        String(
            java.util.Base64.getDecoder().decode(auth.removePrefix("Basic ")),
            Charsets.UTF_8
        ) == "me@example.com:app-pass")
    // ⚠️ 中文用户名/密码：如果按平台默认编码取字节，服务端按 UTF-8 解就会对不上，
    // 而报错是 401 —— 看起来完全像密码敲错了。
    val cnAuth = Remote.basicAuth("小明", "密码123")
    check("32.29 中文账号密码也按 UTF-8 编（否则会莫名 401）",
        String(
            java.util.Base64.getDecoder().decode(cnAuth.removePrefix("Basic ")),
            Charsets.UTF_8
        ) == "小明:密码123")

    check("32.30 令牌两头的空白会被去掉（复制粘贴最常见的脏东西）",
        Remote.normalizeToken("  ghp_abc\n") == "ghp_abc")
    check("32.31 连在一起的 'Bearer ' 也会去掉",
        Remote.normalizeToken("Bearer ghp_abc") == "ghp_abc" &&
                Remote.normalizeToken("bearer ghp_abc") == "ghp_abc")
    check("32.32 干净的值原样不动", Remote.normalizeToken("ghp_abc") == "ghp_abc")

    // ---- 32.3 地址拼接 ----

    check("32.33 没写协议时补 https://",
        Remote.normalizeBase("dav.example.com/dav") == "https://dav.example.com/dav")
    // 局域网里的 NAS 经常没有证书，替用户「升级」成 https 会直接连不上。
    check("32.34 用户明确写了 http:// 就保留",
        Remote.normalizeBase("http://192.168.1.9:5005/dav") == "http://192.168.1.9:5005/dav")
    check("32.35 末尾的斜杠会被去掉",
        Remote.normalizeBase("https://dav.example.com/dav/") == "https://dav.example.com/dav")
    check("32.36 两头空白会被去掉",
        Remote.normalizeBase("  https://dav.example.com/dav  ") == "https://dav.example.com/dav")
    check("32.37 空串判为非法", Remote.normalizeBase("   ") == null)
    check("32.38 只有协议没有主机判为非法",
        Remote.normalizeBase("https://") == null && Remote.normalizeBase("://x") == null)
    check("32.39 地址里有空格判为非法（否则会攒出一个连不上的地址）",
        Remote.normalizeBase("https://dav example.com") == null)
    check("32.40 拼出来的 URL 不会出现双斜杠",
        Remote.joinUrl("https://d.example.com/dav/", "a.json") ==
                "https://d.example.com/dav/a.json")
    check("32.41 路径段里的中文会被百分号编码",
        Remote.joinUrl("https://d.example.com/dav", "阅闻备份.json") ==
                "https://d.example.com/dav/%E9%98%85%E9%97%BB%E5%A4%87%E4%BB%BD.json")
    check("32.42 - . _ ~ 不用编码（RFC 3986 的未保留字符）",
        Remote.encodePathSegment("a-b_c.d~e") == "a-b_c.d~e")
    check("32.43 编码再解码能回到原文",
        Remote.decodePathSegment(Remote.encodePathSegment("中文 空格/斜杠")) == "中文 空格/斜杠")
    check("32.44 畸形的转义原样保留，不会把列表搅乱",
        Remote.decodePathSegment("%ZZx") == "%ZZx" && Remote.decodePathSegment("abc%") == "abc%")
    check("32.45 备份文件名编码后原样不动（所以它才必须全 ASCII）",
        Remote.encodePathSegment(BackupSpan.autoName(t0)) == BackupSpan.autoName(t0))

    // ---- 32.4 GitHub Gist ----

    val create = Remote.gistCreateBody("yuewen-20261005-231045.json", "{\"a\":1}")
    check("32.46 新建的 Gist 是私密的（备份里有全部订阅源和笔记）",
        create.contains("\"public\":false"))
    check("32.47 新建请求里带上了文件名", create.contains("yuewen-20261005-231045.json"))
    // 用我们自己的解析器读回来 —— 顺带证明转义（换行 / 引号）是对的。
    val tricky = "第一行\n第二行 \"带引号\" \\反斜杠\\ \u0001控制符"
    val trickyBody = Remote.gistCreateBody("n.json", tricky)
    check("32.48 正文里的换行和引号会被正确转义（用自家解析器读回来验证）",
        Remote.gistFileContent(trickyBody, "n.json") == tricky)
    check("32.49 往已有 Gist 加文件时只带 files 段",
        Remote.gistAddFileBody("n.json", "x").startsWith("{\"files\":{") &&
                !Remote.gistAddFileBody("n.json", "x").contains("public"))
    // GitHub 删文件的约定是「把值置成 null」，不是传一个标记 —— 写错会变成空操作：
    // 返回 200 但文件还在，于是「保留最近 N 份」永远不生效。
    check("32.50 删文件用的是「值置为 null」这个约定",
        Remote.gistDeleteFilesBody(listOf("a.json", "b.json")) ==
                "{\"files\":{\"a.json\":null,\"b.json\":null}}")
    check("32.51 没有要删的文件时也不炸",
        Remote.gistDeleteFilesBody(emptyList()) == "{\"files\":{}}")

    val gistJson = """
        {"id":"abc123","files":{
          "yuewen-20261005-231045.json":{"content":"{\"v\":1}"},
          "yuewen-20261004-090000.json":{"content":"{\"v\":0}"},
          "说明.md":{"content":"hello"}}}
    """.trimIndent()
    check("32.52 能列出 Gist 里的所有文件名（含非备份文件）",
        Remote.gistFileNames(gistJson).size == 3)
    check("32.53 挑「最新的一份」时会跳过非备份文件，也不会挑错那份",
        BackupSpan.newestFirst(Remote.gistFileNames(gistJson)).firstOrNull() ==
                "yuewen-20261005-231045.json")
    check("32.54 能取出指定文件的内容",
        Remote.gistFileContent(gistJson, "yuewen-20261004-090000.json") == "{\"v\":0}")
    check("32.55 取不存在的文件返回 null（而不是抛异常）",
        Remote.gistFileContent(gistJson, "没有这个.json") == null)
    check("32.56 能从新建响应里拿到 Gist 标识", Remote.gistIdOf(gistJson) == "abc123")
    check("32.57 响应不是 JSON 时不炸", Remote.gistIdOf("not json") == null)
    check("32.58 响应里没有 files 时返回空列表", Remote.gistFileNames("{}").isEmpty())

    // ---- 32.5 WebDAV（PROPFIND 响应解析）----

    val dav = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>/dav/yuewen/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
          </d:response>
          <d:response>
            <d:href>/dav/yuewen/yuewen-20261005-231045.json</d:href>
            <d:propstat><d:prop><d:getcontentlength>1024</d:getcontentlength></d:prop></d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()
    val davNames = Remote.davFileNames(dav)
    check("32.59 从 PROPFIND 响应里取出文件名（只留最后一段）",
        davNames.contains("yuewen-20261005-231045.json") &&
                davNames.none { it.contains("/") })
    check("32.60 目录自身也在响应里，但它不是「我们的备份」会被筛掉",
        BackupSpan.newestFirst(davNames) == listOf("yuewen-20261005-231045.json"))
    // 命名空间前缀写死成 d: 的话，换一个网盘（返回 <D:href> 或干脆 <href>）就一个文件都列不出来。
    val davNoPrefix = "<multistatus><response><href>/dav/a/yuewen-20261005-231045.json</href>" +
            "</response></multistatus>"
    check("32.61 不带命名空间前缀的响应也认",
        Remote.davFileNames(davNoPrefix) == listOf("yuewen-20261005-231045.json"))
    check("32.62 大写前缀 D: 也认",
        Remote.davFileNames("<D:href>/yuewen-20261005-231045.json</D:href>") ==
                listOf("yuewen-20261005-231045.json"))
    check("32.63 百分号编码的 href 会被解码",
        Remote.davFileNames("<d:href>/dav/%E9%98%85%E9%97%BB.json</d:href>") ==
                listOf("阅闻.json"))
    check("32.64 服务端回完整 URL 形式时也能取到最后一段",
        Remote.davFileNames(
            "<d:href>https://dav.example.com/dav/yuewen-20261005-231045.json</d:href>"
        ) == listOf("yuewen-20261005-231045.json"))
    check("32.65 响应里没有 href 时返回空列表",
        Remote.davFileNames("<?xml version=\"1.0\"?><d:multistatus/>").isEmpty())
    check("32.66 空响应不炸", Remote.davFileNames("").isEmpty())

    println()
    println("==========================================")
    println("通过 $pass 项，失败 $fail 项")
    if (fail > 0) throw RuntimeException("有 $fail 项断言失败")
}