package com.example.yuewen.data.model

/**
 * 「阅源」里的一条推荐订阅源（v2.0）。
 *
 * 和 [FeedSource] 的区别：[FeedSource] 是**用户已订阅的源**（存 DataStore），
 * [CatalogFeed] 是**我们内置的推荐库条目**，用户还没订阅它。
 * 两者刻意分开：推荐库随版本更新，用户列表只装他自己点了订阅的那些。
 */
data class CatalogFeed(
    val name: String,
    val url: String,
    val category: String,
    val desc: String,
    val lang: String = "中文",
    /** 是否进「编辑精选」那一栏（挑最稳、更新最勤的）。 */
    val featured: Boolean = false
)

/** 推荐库里的一组（按内容方向分）。 */
data class CatalogGroup(val name: String, val emoji: String, val feeds: List<CatalogFeed>)

/**
 * 内置精选订阅源库。
 *
 * 设计意图（用户原话：*让用户拿到 app 不至于什么都没有*）：
 * 一个 RSS 阅读器最劝退的时刻，就是装好后打开是一张白纸。
 * 这里内置一批**长期稳定、更新勤、免费**的源，分好类，
 * 用户可以在「阅源 → 发现」里一键订阅整组，不用去网上到处找地址。
 *
 * 选源原则：
 * 1. 优先后**国内可直连**的中文源（英文源另设一组，注明可能需要网络环境）；
 * 2. 优先官方提供的 RSS（不是第三方抓取生成的），长期可用性高；
 * 3. 每条都带一句人话说明它适合谁看，用户不必点开才知道是什么。
 */
object FeedCatalog {

    private fun f(
        name: String,
        url: String,
        category: String,
        desc: String,
        lang: String = "中文",
        featured: Boolean = false
    ) = CatalogFeed(name, url, category, desc, lang, featured)

    // ---------------- 中文科技 ----------------

    private val cnTech = listOf(
        f("少数派", "https://sspai.com/feed", "科技", "效率工具、App 评测与数码生活，中文科技媒体里更新最勤的一家", featured = true),
        f("36氪", "https://36kr.com/feed", "科技", "创投与商业科技资讯，适合关心行业动向", featured = true),
        f("爱范儿", "https://www.ifanr.com/feed", "科技", "消费电子与科技生活方式"),
        f("品玩 PingWest", "https://www.pingwest.com/feed", "科技", "科技产业观察与深度报道"),
        f("Solidot 奇客", "https://www.solidot.org/index.rss", "科技", "极简版科技新闻，一条一句话，扫读效率极高", featured = true),
        f("V2EX", "https://www.v2ex.com/index.xml", "科技", "程序员社区热帖，看真实的技术圈在聊什么", featured = true),
        f("IT之家", "https://www.ithome.com/rss/", "科技", "国内科技数码新闻，更新极快"),
        f("cnBeta 业界资讯", "https://www.cnbeta.com.tw/backend.php", "科技", "国内外科技业界资讯汇总")
    )

    // ---------------- 中文财经 ----------------

    private val cnBiz = listOf(
        f("华尔街见闻", "https://dedicated.wallstreetcn.com/rss.xml", "财经", "全球市场与宏观资讯，做投资的人看得比较多", featured = true),
        f("界面新闻", "https://a.jiemian.com/index.php?m=article&a=rss", "财经", "商业与财经深度报道"),
        f("虎嗅", "https://www.huxiu.com/rss/0.xml", "财经", "商业评论与互联网观察")
    )

    // ---------------- 国际视野 ----------------

    private val world = listOf(
        f("BBC 中文", "https://feeds.bbci.co.uk/zhongwen/simp/rss.xml", "国际", "BBC 中文网的简中报道", featured = true),
        f("纽约时报中文网", "https://cn.nytimes.com/rss/", "国际", "纽时中文版，国际新闻与评论"),
        f("The Guardian World", "https://www.theguardian.com/world/rss", "国际", "卫报国际新闻，英文", lang = "英文"),
        f("Reuters World", "https://feeds.reuters.com/reuters/worldNews", "国际", "路透社国际新闻，英文", lang = "英文"),
        f("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "国际", "半岛电视台，英文", lang = "英文")
    )

    // ---------------- 开发技术 ----------------

    private val dev = listOf(
        f("阮一峰的网络日志", "https://www.ruanyifeng.com/blog/atom.xml", "开发", "每周科技爱好者周刊，中文技术写作标杆", featured = true),
        f("酷 壳 CoolShell", "https://coolshell.cn/feed", "开发", "陈皓的技术博客，架构与工程文化"),
        f("Hacker News", "https://hnrss.org/frontpage", "开发", "硅谷技术圈头条聚合，英文", lang = "英文", featured = true),
        f("InfoQ 中文", "https://www.infoq.cn/feed", "开发", "架构、AI 与工程实践"),
        f("开源中国资讯", "https://www.oschina.net/news/rss", "开发", "国内开源与开发资讯"),
        f("Dev.to", "https://dev.to/feed", "开发", "开发者社区文章流，英文", lang = "英文"),
        f("GitHub Blog", "https://github.blog/feed/", "开发", "GitHub 官方博客，英文", lang = "英文"),
        f("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "开发", "技术深度报道，英文", lang = "英文")
    )

    // ---------------- 设计创意 ----------------

    private val design = listOf(
        f("优设 UISDC", "https://www.uisdc.com/feed", "设计", "中文设计教程与灵感", featured = true),
        f("Smashing Magazine", "https://www.smashingmagazine.com/feed/", "设计", "前端与设计经典站点，英文", lang = "英文"),
        f("CSS-Tricks", "https://css-tricks.com/feed/", "设计", "Web 设计与前端技巧，英文", lang = "英文"),
        f("Designer News", "https://www.designernews.co/?format=rss", "设计", "设计师社区讨论，英文", lang = "英文")
    )

    // ---------------- 科学探索 ----------------

    private val science = listOf(
        f("Nature 要闻", "https://www.nature.com/nature.rss", "科学", "《自然》周刊要闻，英文", lang = "英文", featured = true),
        f("ScienceDaily", "https://www.sciencedaily.com/rss/all.xml", "科学", "各领域科研进展通俗摘要，英文", lang = "英文"),
        f("Phys.org", "https://phys.org/rss-feed/", "科学", "物理与科技前沿，英文", lang = "英文"),
        f("NASA Breaking News", "https://www.nasa.gov/news-release/feed/", "科学", "NASA 官方新闻稿，英文", lang = "英文")
    )

    // ---------------- 生活文化 ----------------

    private val life = listOf(
        f("豆瓣电影影评", "https://www.douban.com/feed/review/movie", "生活", "豆瓣热门影评更新"),
        f("果壳网", "https://www.guokr.com/rss/", "生活", "有意思的科学与生活解读"),
        f("The Verge", "https://www.theverge.com/rss/index.xml", "生活", "科技与流行文化，英文", lang = "英文"),
        f("Wired", "https://www.wired.com/feed/rss", "生活", "科技文化长文，英文", lang = "英文")
    )

    /** 全部分组，顺序就是「发现」页里的展示顺序。 */
    val groups: List<CatalogGroup> = listOf(
        CatalogGroup("中文科技", "📱", cnTech),
        CatalogGroup("财经商业", "📈", cnBiz),
        CatalogGroup("开发技术", "💻", dev),
        CatalogGroup("设计创意", "🎨", design),
        CatalogGroup("国际视野", "🌍", world),
        CatalogGroup("科学探索", "🔬", science),
        CatalogGroup("生活文化", "☕", life)
    )

    fun all(): List<CatalogFeed> = groups.flatMap { it.feeds }

    fun featured(): List<CatalogFeed> = all().filter { it.featured }

    /** 按名字模糊找一组（大小写不敏感，`中文科技` / `tech` 都能命中）。 */
    fun findGroup(name: String): CatalogGroup? =
        groups.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * 首次安装时的默认订阅（12 个，覆盖 5 个方向）。
     *
     * 挑的都是中文、国内可直连、更新勤的源 —— 保证用户第一次打开 App
     * 下拉一下就有内容看，而不是对着一张空列表发呆。
     */
    fun starter(): List<FeedSource> = listOf(
        f("少数派", "https://sspai.com/feed", "科技", "", featured = true),
        f("36氪", "https://36kr.com/feed", "科技", "", featured = true),
        f("爱范儿", "https://www.ifanr.com/feed", "科技", ""),
        f("Solidot 奇客", "https://www.solidot.org/index.rss", "科技", ""),
        f("V2EX", "https://www.v2ex.com/index.xml", "科技", ""),
        f("IT之家", "https://www.ithome.com/rss/", "科技", ""),
        f("华尔街见闻", "https://dedicated.wallstreetcn.com/rss.xml", "财经", ""),
        f("界面新闻", "https://a.jiemian.com/index.php?m=article&a=rss", "财经", ""),
        f("BBC 中文", "https://feeds.bbci.co.uk/zhongwen/simp/rss.xml", "国际", ""),
        f("纽约时报中文网", "https://cn.nytimes.com/rss/", "国际", ""),
        f("阮一峰的网络日志", "https://www.ruanyifeng.com/blog/atom.xml", "开发", ""),
        f("优设 UISDC", "https://www.uisdc.com/feed", "设计", "")
        // v2.0.2：id 从「按位置编号」改成「按地址派生」。
        // 老写法 s0/s1… 在「给老用户增量补种」时会和已有源的编号撞车 → 两个源同一个 id，
        // 于是点某个源的测试/编辑会作用到另一个源上。地址派生天然唯一，也不会随顺序变。
    ).map { c -> FeedSource(sourceIdOf(c.url), c.name, c.url, c.category, true) }

    /**
     * 老用户升级时补种的源（v2.0 从 6 个默认源升级到 12 个时用）。
     * 只补「用户列表里没有的」，不会覆盖用户自己的增删。
     */
    fun upgradeAdditions(existing: List<FeedSource>): List<FeedSource> {
        val have = existing.map { it.url.trim().trimEnd('/').lowercase() }.toHashSet()
        return starter().filter { it.url.trim().trimEnd('/').lowercase() !in have }
    }
}
