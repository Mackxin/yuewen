package com.example.yuewen.data.rss

/**
 * RSSHub 支持层（v2.1）。
 *
 * ## 为什么要有这个文件
 *
 * 阅闻本身只能订「已经有 RSS 的网站」。而微博、知乎、B站、小红书这些平台**不提供 RSS** ——
 * 想订它们，生态里通用的做法是用 **RSSHub**：一个开源服务，把「实例地址 + 路由」拼成一个
 * 普通 RSS 地址。它自己不负责阅读，只负责造 feed。
 *
 * 于是阅闻要做的事情很轻：**帮用户把地址拼对**，拼好之后的加源 → 分类 → 刷新 →
 * 正文抽取 → 收藏 → 朗读，全部复用现有链路，一行都不用新写。
 *
 * ## 三个概念
 *
 * - **实例（instance）**：一台跑着 RSSHub 的服务器。默认官方公共实例 `rsshub.app`，
 *   但它是**限流**的（大致每小时 200 次请求 / IP），官方自己都说别长期依赖，
 *   所以实例地址做成可切换，用户以后可以换成自己搭的。
 * - **路由（route）**：`/weibo/user/1234567` 这样的一段路径，决定「要哪个站点的哪份内容」。
 *   RSSHub 有上千条，本文件只挑了 29 条高频的做成**模板**（填一个参数就出地址），
 *   其余的走界面上的「自定义路由」。
 * - **地址**：`实例 + 路由`，就是这个文件里 [buildRssHubUrl] 干的事。
 *
 * ## 为什么把逻辑放这里而不是放界面里
 *
 * 这些函数全是纯函数（不碰 Android），于是能进 `tools/jvmtest` 的离线回归 ——
 * 地址拼接错一个斜杠就是一整条订阅失效，值得有断言盯着。
 */

/** 参数输入框的类型：只影响键盘与提示，不影响拼接结果。 */
enum class RssHubParamKind {
    /** 这个路由不需要参数（榜单类）。 */
    None,

    /** 普通文本（UID / 关键词 / 用户名）。 */
    Text,

    /** 纯数字（多数平台的用户 ID）。 */
    Number
}

/**
 * 一条 RSSHub 路由模板。
 *
 * [path] 里用 `{q}` 表示「要用户填的那一段」。之所以只留**一个**占位符，
 * 是为了界面上只需要一个输入框；像「某个仓库的 Issue」这种天然要「作者/仓库名」两段的，
 * 就靠 [rssHubEncode] **保留斜杠**这一点，让用户在一格里填 `DIYgod/RSSHub` —— 拆开正好两段。
 */
data class RssHubRoute(
    val id: String,
    /** 分组名，界面上按它分类（微博 / 知乎 / B站 …）。 */
    val platform: String,
    /** 这一条是干什么的，如「某个用户的微博」。 */
    val title: String,
    /** 路由路径，如 `/weibo/user/{q}`。 */
    val path: String,
    /** 输入框的标题，如「微博用户 UID」。 */
    val paramLabel: String = "",
    /** 输入框下面的提示：**告诉用户去哪找这个参数**，这是最好用的一条说明。 */
    val paramHint: String = "",
    val paramExample: String = "",
    val kind: RssHubParamKind = RssHubParamKind.Text,
    /** 一句话说明订了能看到什么。 */
    val desc: String = "",
    /** 平台反爬强 / 需要登录态：公共实例大概率失败，标出来让用户有心理准备。 */
    val fragile: Boolean = false
) {
    /** 需要用户填参数吗。 */
    val needsParam: Boolean get() = path.contains("{q}")
}

/**
 * 内置的路由模板表。
 *
 * ⚠️ **这些路径会随平台改版而失效** —— 这是 RSSHub 生态的常态，不是 bug。
 * 所以设计上做了两手：
 * 1. 每条模板在添加前都能「先预览测试」，失效会当场看见，不会悄悄订进来一个空源；
 * 2. 界面上另有「自定义路由」，用户可以按官方文档（[DOCS]）自己填任意路由。
 *
 * 本表只收录**高频且已核对过**的路由；不敢保证的宁可不放（例如 36氪快讯、抖音热搜榜
 * 这两条在 RSSHub 近期版本里已经不在原位，就没写进来）。
 */
object RssHubCatalog {

    /** 官方公共实例。能改，见 [normalizeRssHubInstance]。 */
    const val DEFAULT_INSTANCE = "https://rsshub.app"

    /** 路由大全（可搜索），界面上给用户指路用。 */
    const val DOCS = "https://docs.rsshub.app"

    /** 路由源码仓库，公告里的「哪里能查有哪些源」。 */
    const val REPO = "https://github.com/DIYgod/RSSHub"

    val routes: List<RssHubRoute> = listOf(
        // ---------------------------------------------------------- 微博
        RssHubRoute(
            id = "weibo-hot", platform = "微博", title = "热搜榜",
            path = "/weibo/search/hot", kind = RssHubParamKind.None,
            desc = "实时热搜词，一眼看完今天大家在聊什么。", fragile = true
        ),
        RssHubRoute(
            id = "weibo-user", platform = "微博", title = "某个用户的微博",
            path = "/weibo/user/{q}",
            paramLabel = "微博用户 UID",
            paramHint = "打开 ta 的微博主页，地址 weibo.com/u/ 后面那串数字（如 1642909335）",
            paramExample = "1642909335", kind = RssHubParamKind.Number,
            desc = "只看这个人发的内容，不掺推荐流。", fragile = true
        ),
        RssHubRoute(
            id = "weibo-keyword", platform = "微博", title = "关键词搜索",
            path = "/weibo/keyword/{q}",
            paramLabel = "关键词",
            paramHint = "想追的词，中文英文都行",
            paramExample = "编程",
            desc = "把全站提到这个词的微博收进列表。", fragile = true
        ),

        // ---------------------------------------------------------- 知乎
        RssHubRoute(
            id = "zhihu-hot", platform = "知乎", title = "热榜",
            path = "/zhihu/hot", kind = RssHubParamKind.None,
            desc = "知乎热榜前十条，带问题链接。", fragile = true
        ),
        RssHubRoute(
            id = "zhihu-daily", platform = "知乎", title = "知乎日报",
            path = "/zhihu/daily", kind = RssHubParamKind.None,
            desc = "每天几篇精选长文，适合通勤听。"
        ),
        RssHubRoute(
            id = "zhihu-activities", platform = "知乎", title = "某个用户的动态",
            path = "/zhihu/people/activities/{q}",
            paramLabel = "知乎用户 ID",
            paramHint = "主页地址 zhihu.com/people/ 后面那一段（如 excited-vczh），不是昵称",
            paramExample = "excited-vczh",
            desc = "ta 的回答、想法、赞过的东西。", fragile = true
        ),
        RssHubRoute(
            id = "zhihu-zhuanlan", platform = "知乎", title = "专栏文章",
            path = "/zhihu/zhuanlan/{q}",
            paramLabel = "专栏 ID",
            paramHint = "专栏地址 zhuanlan.zhihu.com/ 后面那一段（如 c_1129398137630167040）",
            paramExample = "c_1129398137630167040",
            desc = "某个专栏的更新。", fragile = true
        ),

        // ---------------------------------------------------------- B站
        RssHubRoute(
            id = "bili-rank", platform = "B站", title = "全站排行榜",
            path = "/bilibili/ranking/all", kind = RssHubParamKind.None,
            desc = "B站全站排行，想知道最近什么火就看它。"
        ),
        RssHubRoute(
            id = "bili-video", platform = "B站", title = "UP 主投稿",
            path = "/bilibili/user/video/{q}",
            paramLabel = "UP 主 UID",
            paramHint = "主页地址 space.bilibili.com/ 后面那串数字（如 2267573）",
            paramExample = "2267573", kind = RssHubParamKind.Number,
            desc = "关注的 UP 主一发新视频就出现在列表里。"
        ),
        RssHubRoute(
            id = "bili-dynamic", platform = "B站", title = "UP 主动态",
            path = "/bilibili/user/dynamic/{q}",
            paramLabel = "UP 主 UID",
            paramHint = "同「UP 主投稿」，还是 space.bilibili.com/ 后面那串数字",
            paramExample = "2267573", kind = RssHubParamKind.Number,
            desc = "投稿之外的碎碎念、转发、图文动态。"
        ),

        // ---------------------------------------------------------- 抖音
        RssHubRoute(
            id = "douyin-user", platform = "抖音", title = "某个用户的作品",
            path = "/douyin/user/{q}",
            paramLabel = "sec_uid",
            paramHint = "抖音主页右上角「分享」→ 复制链接，链接里 sec_uid= 后面那 60 多位字符",
            paramExample = "MS4wLjABAAAA",
            desc = "某个账号发的视频。注意：抖音反爬很强，公共实例经常失败。", fragile = true
        ),
        RssHubRoute(
            id = "douyin-hashtag", platform = "抖音", title = "话题下的作品",
            path = "/douyin/hashtag/{q}",
            paramLabel = "话题名",
            paramHint = "带不带 # 都行",
            paramExample = "美食",
            desc = "某个话题里的视频流。", fragile = true
        ),

        // ---------------------------------------------------------- 小红书
        RssHubRoute(
            id = "xhs-notes", platform = "小红书", title = "用户的笔记",
            path = "/xiaohongshu/user/{q}/notes",
            paramLabel = "用户 ID",
            paramHint = "主页分享链接里 user/profile/ 后面那 24 位（一串字母数字）",
            paramExample = "5ff0e6a0000000000101a1b2",
            desc = "某个博主发的笔记。注意：需要 24 位 ID，且小红书反爬很强。", fragile = true
        ),

        // ---------------------------------------------------------- 豆瓣
        RssHubRoute(
            id = "douban-playing", platform = "豆瓣", title = "正在上映",
            path = "/douban/movie/playing", kind = RssHubParamKind.None,
            desc = "院线正在放的片子，带评分。"
        ),
        RssHubRoute(
            id = "douban-later", platform = "豆瓣", title = "即将上映",
            path = "/douban/movie/later", kind = RssHubParamKind.None,
            desc = "还没上的片子，可以提前留意。"
        ),
        RssHubRoute(
            id = "douban-book", platform = "豆瓣", title = "新书速递",
            path = "/douban/book/latest", kind = RssHubParamKind.None,
            desc = "豆瓣最新上架的书。"
        ),
        RssHubRoute(
            id = "douban-people", platform = "豆瓣", title = "某个用户的广播",
            path = "/douban/people/{q}/status",
            paramLabel = "豆瓣用户 ID",
            paramHint = "主页地址 douban.com/people/ 后面那一段（如 ahbei）",
            paramExample = "ahbei",
            desc = "ta 的广播 / 标记 / 短评。", fragile = true
        ),

        // ---------------------------------------------------------- 资讯
        RssHubRoute(
            id = "sspai-index", platform = "资讯", title = "少数派 · 首页",
            path = "/sspai/index", kind = RssHubParamKind.None,
            desc = "效率工具、数码评测的头部站点。"
        ),
        RssHubRoute(
            id = "sspai-matrix", platform = "资讯", title = "少数派 · Matrix 社区",
            path = "/sspai/matrix", kind = RssHubParamKind.None,
            desc = "用户投稿区，常有好用的长文。"
        ),
        RssHubRoute(
            id = "sspai-author", platform = "资讯", title = "少数派 · 某个作者",
            path = "/sspai/author/{q}",
            paramLabel = "作者 ID",
            paramHint = "作者主页地址 sspai.com/author/ 后面那一段",
            paramExample = "1",
            desc = "只追你喜欢的那个作者。"
        ),
        RssHubRoute(
            id = "v2ex-latest", platform = "资讯", title = "V2EX · 最新主题",
            path = "/v2ex/topics/latest", kind = RssHubParamKind.None,
            desc = "程序员社区的实时新帖。"
        ),
        RssHubRoute(
            id = "v2ex-hot", platform = "资讯", title = "V2EX · 热门主题",
            path = "/v2ex/topics/hot", kind = RssHubParamKind.None,
            desc = "只留讨论最热的那些。"
        ),

        // ---------------------------------------------------------- 开发
        RssHubRoute(
            id = "github-trending", platform = "开发", title = "GitHub · Trending 每日",
            path = "/github/trending/daily", kind = RssHubParamKind.None,
            desc = "当天涨星最快的开源项目。"
        ),
        RssHubRoute(
            id = "github-trending-lang", platform = "开发", title = "GitHub · 某语言的 Trending",
            path = "/github/trending/daily/{q}",
            paramLabel = "语言名",
            paramHint = "英文小写，如 javascript / python / kotlin / rust",
            paramExample = "kotlin",
            desc = "限定一种语言的每日趋势。"
        ),
        RssHubRoute(
            id = "github-issue", platform = "开发", title = "GitHub · 仓库的 Issue",
            path = "/github/issue/{q}",
            paramLabel = "作者/仓库名",
            paramHint = "斜杠分隔，如 DIYgod/RSSHub",
            paramExample = "DIYgod/RSSHub",
            desc = "盯一个项目的 issue 动态。"
        ),
        RssHubRoute(
            id = "github-pulls", platform = "开发", title = "GitHub · 仓库的 Pull Request",
            path = "/github/pulls/{q}",
            paramLabel = "作者/仓库名",
            paramHint = "斜杠分隔，如 square/okhttp",
            paramExample = "square/okhttp",
            desc = "盯一个项目的 PR 动态。"
        ),
        RssHubRoute(
            id = "juejin-trending", platform = "开发", title = "掘金 · 全站热榜",
            path = "/juejin/trending/all/weekly", kind = RssHubParamKind.None,
            desc = "中文技术社区的本周热门。"
        ),
        RssHubRoute(
            id = "juejin-posts", platform = "开发", title = "掘金 · 某个作者的文章",
            path = "/juejin/posts/{q}",
            paramLabel = "掘金用户 ID",
            paramHint = "主页地址 juejin.cn/user/ 后面那串数字",
            paramExample = "2525801196257159", kind = RssHubParamKind.Number,
            desc = "只追某一个作者。"
        ),

        // ---------------------------------------------------------- 社交
        RssHubRoute(
            id = "telegram-channel", platform = "社交", title = "Telegram · 公开频道",
            path = "/telegram/channel/{q}",
            paramLabel = "频道用户名",
            paramHint = "频道链接 t.me/ 后面那一段，不带 @",
            paramExample = "awesomeRSSHub",
            desc = "订阅公开频道，不用装客户端也能追更。"
        )
    )

    /** 界面上「平台」筛选行的取值（按配置顺序去重）。 */
    val platforms: List<String> = routes.map { it.platform }.distinct()

    fun find(id: String): RssHubRoute? = routes.firstOrNull { it.id == id }

    /**
     * 关键词过滤（界面上的搜索框）。
     *
     * 空词返回全部 —— 让「清空搜索框」天然等价于「看全部」，调用方不用额外判断。
     * 匹配范围包含平台的英文/中文名、标题、说明和路径，用户搜 `bilibili` 或 `B站` 都能命中。
     */
    fun search(keyword: String): List<RssHubRoute> {
        val k = keyword.trim()
        if (k.isEmpty()) return routes
        return routes.filter {
            it.title.contains(k, true) ||
                    it.platform.contains(k, true) ||
                    it.desc.contains(k, true) ||
                    it.path.contains(k, true)
        }
    }

    fun byPlatform(platform: String?): List<RssHubRoute> {
        if (platform.isNullOrBlank()) return routes
        return routes.filter { it.platform == platform }
    }
}

// ============================================================ 纯函数：地址拼接

/**
 * 把用户填的实例地址收拾成一个规整的 base。
 *
 * 规则：
 * - 空 → 官方默认实例（设置里存空串就等于「用默认」，这样默认实例升级了老用户也跟着变）；
 * - 没写协议 → 补 `https://`（用户一般只会填 `rsshub.app` 或 `我的域名.com`）；
 * - 去掉结尾斜杠和查询串 —— 否则拼出来会变成 `...//weibo/user/1` 或带上一段废参数。
 */
fun normalizeRssHubInstance(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return RssHubCatalog.DEFAULT_INSTANCE
    if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
    s = s.substringBefore('?').substringBefore('#').trimEnd('/')
    return s.ifBlank { RssHubCatalog.DEFAULT_INSTANCE }
}

/** 实例的显示形式（去掉协议，纯粹为了界面好看）。 */
fun rssHubHost(instance: String): String =
    normalizeRssHubInstance(instance).substringAfter("://").trimEnd('/')

/** 这个实例地址看起来能不能用：至少得有个带点的域名。 */
fun isUsableRssHubInstance(instance: String): Boolean {
    val host = rssHubHost(instance).substringBefore('/')
    return host.length >= 3 && host.contains('.') && !host.startsWith(".") && !host.endsWith(".")
}

/**
 * 路径片段编码：保留 `A-Za-z0-9-_.~` 与 `/`，其余按 UTF-8 百分号编码。
 *
 * 两个关键点：
 * 1. **中文要编码**（`编程` → `%E7%BC%96%E7%A8%8B`）。请求行里塞非 ASCII 字符是碰运气的事，
 *    RSSHub 那边（Express）会自动解码百分号形式，所以编码后是更稳的一边。
 * 2. **斜杠要留着**。这样「作者/仓库名」这种两段式参数才能在一个输入框里填完；
 *    同时也顺便堵住 `../` 这类越界路径被塞进来（`..` 会被原样保留但构不成路径穿越的语义，
 *    因为服务端按路由前缀匹配）。
 */
fun rssHubEncode(s: String): String {
    val keep = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~/"
    val sb = StringBuilder(s.length + 8)
    for (b in s.toByteArray(Charsets.UTF_8)) {
        val c = (b.toInt() and 0xFF).toChar()
        if (c in keep) {
            sb.append(c)
        } else {
            sb.append('%')
            sb.append(HEX[(b.toInt() shr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
    }
    return sb.toString()
}

private val HEX = "0123456789ABCDEF".toCharArray()

/**
 * 把用户随手写的路由收拾成 `/xxx/yyy` 的形式。
 *
 * 支持的「随手写法」（都是用户真会干的事）：
 * - `weibo/user/123` → `/weibo/user/123`（补前导斜杠）；
 * - 直接粘整条地址 `https://rsshub.app/weibo/user/123` → `/weibo/user/123`（砍掉实例部分）；
 * - `rsshub.app/zhihu/hot` → `/zhihu/hot`（没写协议也能认）；
 * - `/zhihu/hot?limit=20` → 保留查询串（RSSHub 的很多路由靠 query 调输出）。
 *
 * 空输入返回 `"/"`，调用方用 [buildRssHubUrl] 拼出来就是实例首页，不会拼出半个地址。
 */
fun normalizeRssHubPath(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return "/"

    val schemeAt = s.indexOf("://")
    if (schemeAt >= 0) {
        // 有协议：丢掉 scheme://host，只留路径
        val rest = s.substring(schemeAt + 3)
        val slash = rest.indexOf('/')
        s = if (slash >= 0) rest.substring(slash) else ""
    } else if (!s.startsWith("/") && s.substringBefore('/').contains('.')) {
        // 没协议但首段像个域名（带点）：同样丢掉
        s = if (s.contains('/')) "/" + s.substringAfter('/') else ""
    }

    val query = s.substringAfter('?', "").substringBefore('#')
    val path = s.substringBefore('?').trim().trim('/').replace(Regex("/{2,}"), "/")
    val out = "/$path"
    return if (query.isBlank()) out else "$out?$query"
}

/**
 * 拼出最终订阅地址：`实例 + 路由`，顺带把 `{q}` 换成用户填的参数。
 *
 * 顺手做了两件防御：
 * - **折叠重复斜杠**：参数留空时 `{q}` 会变成空串，`/weibo/user/` 这种再拼上别的段就成了 `//`；
 * - **去掉结尾斜杠**：RSSHub 对带尾斜杠的路径不一定路由得上。
 *
 * 参数为空时不会瞎造一个畸形地址 —— 返回的就是「去掉参数段」的形式，
 * 但界面上的按钮会拦住空参数（[RssHubRoute.needsParam]），走不到这里。
 */
fun buildRssHubUrl(instance: String, path: String, param: String = ""): String {
    val base = normalizeRssHubInstance(instance)
    val normalized = normalizeRssHubPath(path)
    val filled = if (normalized.contains("{q}")) {
        // 参数里的首尾斜杠去掉，避免出现 //；中间的斜杠保留（两段式参数要用）
        normalized.replace("{q}", rssHubEncode(param.trim().trim('/')))
    } else {
        normalized
    }

    val query = if (filled.contains('?')) filled.substringAfter('?') else ""
    val body = filled.substringBefore('?').replace(Regex("/{2,}"), "/").trimEnd('/')
    return base + body + if (query.isBlank()) "" else "?$query"
}

/**
 * 拼一个默认的源名称（检测出真名之前先用它）。
 *
 * 不直接用「微博」这种平台名：一个用户很可能订十个微博源，
 * 全叫「微博」的话在列表里根本分不清谁是谁。
 */
fun rssHubDefaultName(route: RssHubRoute, param: String): String {
    val p = param.trim()
    return if (route.needsParam && p.isNotBlank()) "${route.platform} · $p" else "${route.platform} · ${route.title}"
}
