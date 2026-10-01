package com.example.yuewen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.SettingsRepository
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.preload.PreloadManager
import com.example.yuewen.data.repository.NewsRepository
import com.example.yuewen.data.util.DEFAULT_HOME_KEYWORDS
import com.example.yuewen.data.util.matchesKeyword
import com.example.yuewen.ui.components.ArticleListMode
import com.example.yuewen.ui.util.HomeSortMode
import com.example.yuewen.ui.util.sortArticles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// v2.5 删掉了 [HomeChipMode] 这个三档枚举。
// 它描述的是「分类行 / 阅源行各自要不要显示」这件事，用一把三档开关表达反而绕
// （「按分类」到底在说筛选口径还是这一行？）。现在拆成两个独立的布尔开关：
// `showCategoryRow` / `showSourceRow`，语义一眼可见，四种组合都能表达。
// 老数据（`home_chip_mode`）的兼容规则在 data/util/HomeRows.kt，有离线测试钉着。

/** 首页列表的一行：要么是日期分组的标题，要么是一篇文章。 */
sealed interface HomeRow {
    val key: String

    data class Header(val label: String, val count: Int) : HomeRow {
        override val key: String get() = "header_$label"
    }

    data class Item(val article: Article) : HomeRow {
        override val key: String get() = article.link
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repo: NewsRepository,
    private val settings: SettingsRepository,
    /** v1.9：刷新完之后顺手把「离线预加载」跑一轮（开关关着 / 不在 Wi-Fi 时它自己会跳过）。 */
    private val preloader: PreloadManager
) : ViewModel() {

    /**
     * 全部已订阅的阅源。
     *
     * ⚠️ 这里是 `WhileSubscribed`：**没有订阅者时上游根本不会被收集**，`.value` 会一直停在
     * 初始的空列表。v2.0.2 就踩过这个坑 —— 阅源筛选行读 `sources.value` 拼胶囊，但首页
     * 当时没订阅它（订阅它的都是别的页面的其它 ViewModel），结果那一行永远只有
     * 「全部阅源」一个胶囊，源名一个都列不出来。
     *
     * 所以：**首页必须订阅它**（`HomeScreen` 里 `collectAsStateWithLifecycle`），
     * 而且 `sourceNamesFor()` 要显式接收列表、不要自己去读 `.value`。
     */
    val sources: StateFlow<List<FeedSource>> = settings.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 顶栏分类行的取值。
     *
     * 从 [sources] 派生（而不是再订阅一次 DataStore）—— 顺带让「订阅 sources = 分类也跟着活」，
     * 少一份上游订阅，也保证两行胶囊看到的是同一份数据快照。
     * 固定带一个「推荐」（= 不限分类），其余从已订阅的源里抽，源删了分类自然消失。
     */
    val categories: StateFlow<List<String>> = sources.map { srcs ->
        listOf("推荐") + srcs.map { it.category }.distinct().filter { it.isNotBlank() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("推荐"))

    /**
     * 首页显不显示「分类」那一行（v2.5，设置里可关）。
     *
     * 刻意用 `Eagerly`（而不是别处惯用的 `WhileSubscribed`）：这个值会被**同步读**
     * （`showCategoryRow.value`，见 init 里「别处切分类时首页跟不跟」那段）。用
     * `WhileSubscribed` 的话，首页不在前台就没人订阅它，`.value` 会停在初始值 ——
     * 用户在设置页关掉分类行，回到首页时会被过时判断误导，把「分类」这一级悄悄设上，
     * 列表被一个界面上根本看不见的条件筛成空的。
     */
    val showCategoryRow: StateFlow<Boolean> = settings.homeShowCategoryRowFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 首页显不显示「阅源名称」那一行（v2.5，设置里可关）。同样 `Eagerly`，理由同上。 */
    val showSourceRow: StateFlow<Boolean> = settings.homeShowSourceRowFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * 当前筛选的两个维度：
     * - [category] 「推荐」= 不限分类；
     * - [source]   空 = 不限阅源。
     *
     * 做成两个独立的 state 而不是一个字符串，是因为「分类 + 阅源」是可以叠加的两级筛选，
     * 拼成一个 key 容易在解析时出错。
     */
    private val _category = MutableStateFlow("推荐")
    val category: StateFlow<String> = _category

    private val _source = MutableStateFlow("")
    val source: StateFlow<String> = _source

    /**
     * 当前选中的**关键词胶囊**（v2.4）。空 = 不按关键词筛。
     *
     * 和 [category] / [source] 一样是个独立的维度：它不参与数据库查询，
     * 而是在内存里对「分类 × 阅源」的结果再筛一道（见 [articles]）。
     * 这样「手机」「汽车」这类词才能匹配到正文，而不是只匹配标题。
     */
    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword

    /**
     * 首页关键词那一行显示哪些词（设置里可增删）。
     *
     * 用 `Eagerly`：首页要拿它渲染胶囊，`init` 里还要订阅它做「词被删了就把筛选也撤掉」，
     * 一旦用 `WhileSubscribed`，退到后台再回来时会短暂拿到默认词，
     * 那一行会闪一下才变成用户自己的词。
     */
    val homeKeywords: StateFlow<List<String>> = settings.homeKeywordsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_HOME_KEYWORDS)

    /**
     * 某个分类下能选的阅源名。
     * 分类为「推荐」时返回全部；否则只给这个分类里的源 —— 换了分类，源行跟着变，不会点出空列表。
     *
     * [list] 由调用方传进来（首页传它订阅到的那份），**不要**在这里读 `sources.value`：
     * 那是个 `WhileSubscribed` 的 StateFlow，读取方没订阅时拿到的会是初始空列表。
     */
    fun sourceNamesFor(cat: String, list: List<FeedSource>): List<String> =
        list.filter { it.enabled && (cat == "推荐" || it.category == cat) }
            .map { it.name }
            .distinct()

    /** 当前列表布局（紧凑 / 卡片 / 杂志）。 */
    val listMode: StateFlow<ArticleListMode> = settings.listModeFlow
        .map { ArticleListMode.of(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArticleListMode.Card)

    /**
     * 首页文章排序方式（v2.2）。
     *
     * 用 `Eagerly`：这个值要在 [articles] 这条链里参与计算，而且「随机」那一档还要
     * 配合洗牌种子一起读。若用 `WhileSubscribed`，首页一退到后台上游就断，
     * 再回来时链路会用初值重算一次 —— 列表会闪一下才排好。
     */
    val sortMode: StateFlow<HomeSortMode> = settings.homeSortFlow
        .map { HomeSortMode.of(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeSortMode.TimeDesc)

    /** 随机排序的种子。换一个 = 重新洗牌。 */
    private val shuffleSeed: StateFlow<Long> = settings.shuffleSeedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _unreadOnly = MutableStateFlow(false)
    val unreadOnly: StateFlow<Boolean> = _unreadOnly

    fun setUnreadOnly(v: Boolean) { _unreadOnly.value = v }

    /** 两级筛选（分类 × 阅源）→ 数据库查询。 */
    private val feed: Flow<List<Article>> =
        combine(_category, _source) { c, s -> c to s }
            .flatMapLatest { (c, s) -> repo.observeHomeFeed(c, s) }

    /**
     * 排序（模式 + 种子）打包成一个流。
     *
     * 拆出来是因为 `combine` 的「带类型参数」重载最多只到 5 个上游；
     * 六个以上只能走 vararg 版本，拿到的是 `Array<Any?>`，每个值都得手写转型 ——
     * 一旦上游顺序被改动（比如插了一个新条件），转型就会在运行时炸 ClassCastException，
     * 而且编译器完全不会提醒。这里先把两个排序相关的上游合成一个 Pair，
     * 剩下的链路就还是「最多 5 个具名上游」，类型全程有保障。
     */
    private val sortSpec: Flow<Pair<HomeSortMode, Long>> =
        combine(sortMode, shuffleSeed) { mode, seed -> mode to seed }

    /**
     * 「关键词 + 仅看未读」打包成一个流。
     *
     * 和 [sortSpec] 同一个理由：`combine` 的具名重载**最多 5 个上游**。
     * v2.4 加了关键词筛选之后正好要多一个（feed / 关键词 / 未读 / 屏蔽源 / 屏蔽词 / 排序 = 6），
     * 与其退回 vararg 版本去手写 `Array<Any?>` 转型（顺序一改就运行时 CCE），
     * 不如先把这两项合成一个 Pair，链条仍然是「5 个具名上游」。
     */
    private val quickFilters: Flow<Pair<String, Boolean>> =
        combine(_keyword, _unreadOnly) { kw, unread -> kw to unread }

    /** 过滤后的文章（屏蔽来源 / 关键词 / 仅看未读 / 排序都在这里生效）。 */
    private val articles: StateFlow<List<Article>> =
        combine(
            feed,
            quickFilters,
            settings.blockedSourcesFlow,
            settings.blockedKeywordsFlow,
            sortSpec
        ) { list, (keyword, unread), blockedSources, blockedKeywords, (sort, seed) ->
            val filtered = list.filter { a ->
                if (unread && a.isRead) return@filter false
                // 关键词匹配 标题 / 摘要 / 正文，和「闻件 → 搜索」的口径一致
                if (!matchesKeyword(a.title, a.summary, a.fullText, keyword)) return@filter false
                if (blockedSources.contains(a.sourceName)) return@filter false
                if (blockedKeywords.any { kw -> a.title.contains(kw, true) || a.summary.contains(kw, true) }) return@filter false
                true
            }
            sortArticles(filtered, sort, seed)
        }
            // 过滤 + 排序是 O(n log n) 的纯计算：放到 Default 线程，别占主线程，
            // 避免列表滚动/刷新时掉帧
            .flowOn(Dispatchers.Default)
            // ⚠️ v2.3：这里从 WhileSubscribed 改成 Eagerly。
            // 原因：`currentLinks()`（多选「全选」用）要**同步**读它。
            // WhileSubscribed 的情况下，下游 5 秒内全取消订阅后 `.value` 会停在上一次的旧值，
            // 于是「全选」可能选中一批早就不在列表里的链接 —— 正确性不能依赖
            // 「恰好有别人订阅着」。首页本来就是启动页，一直算着也不亏。
            // （同一个坑 v2.0.2 在 `sources` 上踩过一次。）
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 分好组的行列表，直接喂给 LazyColumn。
     *
     * ⚠️ **只有按时间排的两种模式才切日期分组**（今天 / 昨天 / 本周 / 更早）。
     * 随机 / 按阅源 / 按标题下面，文章的日期是乱的，硬切出「今天」「昨天」几个小标题
     * 反而矛盾（同一组里会混着半年前的文章）。那几种模式就直接平铺，
     * 日期已经写在每张卡片的 meta 行里，不会丢信息。
     */
    val rows: StateFlow<List<HomeRow>> = combine(articles, sortMode) { list, sort ->
        if (sort.groupedByDay) groupByDay(list) else list.map { HomeRow.Item(it) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前分类下的未读数（顶部显示）。 */
    val unreadCount: StateFlow<Int> = articles
        .map { list -> list.count { !it.isRead } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 已有的收藏夹名（长按菜单里的「移动到收藏夹」用），排除默认分组。 */
    val folders: StateFlow<List<String>> = repo.observeBookmarkFolders()
        .map { list -> list.filter { it != "默认" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** 上次成功刷新时间（0 = 从未）。 */
    val lastRefresh: StateFlow<Long> = settings.lastRefreshFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    init {
        viewModelScope.launch {
            settings.ensureSeeded()
            applyDefaultFilter()
            // 某一行被关掉时，要顺手清掉「看不见的那一级筛选」——
            // 否则会出现「界面上根本没有这个条件，列表却被它筛着」的灵异现象，
            // 而且用户找不到任何可以取消的地方（v2.0.2 的老规矩）。
            // 这里 collect 的是已经解析好的 [showCategoryRow] / [showSourceRow]
            // （Eagerly，见属性上的注释），顺带保证别处同步读 `.value` 拿到的一定是最新值。
            launch {
                showCategoryRow.collect { if (!it) _category.value = "推荐" }
            }
            launch {
                showSourceRow.collect { if (!it) _source.value = "" }
            }
        }
        // 别处要求换分类时（加完阅源自动切到它所在的分类、恢复备份）首页跟着走
        viewModelScope.launch {
            settings.categoryFlow.drop(1).collect { cat ->
                if (cat.isNotBlank() && showCategoryRow.value) _category.value = cat
            }
        }
        // v2.4：关键词被删掉时，如果首页正好还筛着那个词，必须主动把筛选撤掉。
        // 否则那块胶囊跟着词一起从界面上消失了，列表却还被它筛着 ——
        // 用户看到的是「首页突然一篇都没有了，而且找不到任何可以取消的按钮」。
        viewModelScope.launch {
            homeKeywords.collect { list ->
                val cur = _keyword.value
                if (cur.isNotBlank() && list.none { it.equals(cur, true) }) _keyword.value = ""
            }
        }
        viewModelScope.launch { refreshOnLaunchIfNeeded() }
    }

    /**
     * 把设置里的「打开时默认停在」落到列表上（v2.0.2）。
     *
     * 全部做了兜底：指定的阅源可能已经被删掉，也可能不在指定的分类里。
     * 宁可放宽条件（回到推荐 / 全部阅源），也不要让用户一打开就是一片空白。
     * 另外，那一行没显示的话它就不参与筛选（否则用户看不到条件却被筛着）。
     */
    private suspend fun applyDefaultFilter() {
        val showCat = settings.homeShowCategoryRowFlow.first()
        val showSrc = settings.homeShowSourceRowFlow.first()
        val cat = settings.homeDefaultCategoryFlow.first().ifBlank { "推荐" }
        val src = settings.homeDefaultSourceFlow.first()
        val enabled = settings.sourcesFlow.first().filter { it.enabled }
        val target = enabled.firstOrNull { it.name == src }
        _category.value = when {
            !showCat -> "推荐"
            target == null -> cat
            cat == "推荐" || target.category == cat -> cat
            else -> "推荐"
        }
        _source.value = if (!showSrc) "" else target?.name.orEmpty()
    }

    /**
     * 「打开 App 自动刷新」（默认开启，可在设置里关掉）。
     *
     * 加了两道保险，避免变成「每次切回首页都在联网」：
     * 1. 用户关掉开关就直接返回；
     * 2. 距上次刷新不到 5 分钟就跳过 —— 冷启动、旋屏、从后台切回来都只算一次。
     */
    private suspend fun refreshOnLaunchIfNeeded() {
        if (!settings.refreshOnLaunchFlow.first()) return
        val last = settings.lastRefreshFlow.first()
        if (System.currentTimeMillis() - last < LAUNCH_REFRESH_MIN_GAP_MS) return
        _isRefreshing.value = true
        try {
            repo.refreshAll()
            settings.setLastRefresh(System.currentTimeMillis())
        } catch (_: Exception) {
            // 网络异常不该影响首页展示本地缓存
        } finally {
            _isRefreshing.value = false
        }
        preloader.maybeAutoStart()
    }

    fun selectCategory(cat: String) {
        _category.value = cat
        // 顺手记进设置：一来加完阅源「自动切到新分类」那条路径要靠它，
        // 二来换手机恢复备份时分类能跟着回来。
        // 注意它**不会**覆盖「打开时默认停在」——那个设置启动时优先级更高。
        viewModelScope.launch { settings.setCategory(cat) }
        // 换分类之后，如果当前选中的阅源不属于这个分类，就退回「全部阅源」——
        // 否则会变成「分类 × 源」双重条件筛出一个空列表，看起来像出了 bug。
        // 这里现读一次最新的源列表：不能读 sources.value（那是 WhileSubscribed，
        // 首页没订阅时它还是空的，这个保护就形同虚设）。
        val cur = _source.value
        if (cur.isBlank()) return
        viewModelScope.launch {
            val names = sourceNamesFor(cat, settings.sourcesFlow.first())
            if (cur !in names) _source.value = ""
        }
    }

    /** 只看某个阅源；传空字符串 = 全部阅源。 */
    fun selectSource(name: String) {
        _source.value = name
    }

    /**
     * 点关键词胶囊：再点一次同一个词就取消筛选（传空串同理 = 回到全部）。
     *
     * 做成「可点掉」而不是必须去点「全部」那个胶囊，是因为那一行本身可以横向滚动 ——
     * 词多的时候「全部」容易被滚出屏幕外。
     */
    fun selectKeyword(kw: String) {
        val cur = _keyword.value
        _keyword.value = if (kw.isBlank() || kw.equals(cur, true)) "" else kw
    }

    fun setListMode(mode: ArticleListMode) {
        viewModelScope.launch { settings.setListMode(mode.key) }
    }

    fun setSortMode(mode: HomeSortMode) {
        viewModelScope.launch { settings.setHomeSort(mode.key) }
    }

    /** 「换一批」：只在随机排序下有实际效果 —— 换一个种子，列表重新洗一遍。 */
    fun reshuffle() {
        viewModelScope.launch { settings.reshuffle() }
    }

    /** 点一下布局按钮就在 紧凑 → 卡片 → 杂志 之间循环。 */
    fun cycleListMode() {
        viewModelScope.launch { settings.setListMode(listMode.value.next().key) }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repo.refreshAll()
                settings.setLastRefresh(System.currentTimeMillis())
            } finally {
                _isRefreshing.value = false
            }
            // 刷新完再预加载：这样刚拿到的「新文章」也在缓存范围内。
            // 放到 finally 之后，预加载慢不会拖着刷新转圈不放。
            preloader.maybeAutoStart()
        }
    }

    fun markAllRead() {
        viewModelScope.launch { repo.markAllRead(_category.value, _source.value) }
    }

    fun toggleBookmark(link: String, value: Boolean) {
        viewModelScope.launch { repo.setBookmarked(link, value) }
    }

    /** 手动标已读 / 未读（长按菜单用）。 */
    fun toggleRead(link: String, value: Boolean) {
        viewModelScope.launch { repo.setRead(link, value) }
    }

    /** 移动到收藏夹（会自动顺带收藏，否则「移动」没有意义）。 */
    fun setFolder(link: String, folder: String, bookmark: Boolean = true) {
        viewModelScope.launch {
            repo.setFolder(link, folder)
            if (bookmark) repo.setBookmarked(link, true)
        }
    }

    // ==================== v2.0：批量管理 ====================

    fun setReadBatch(links: List<String>, value: Boolean) {
        if (links.isEmpty()) return
        viewModelScope.launch { repo.setReadBatch(links, value) }
    }

    fun setBookmarkedBatch(links: List<String>, value: Boolean) {
        if (links.isEmpty()) return
        viewModelScope.launch { repo.setBookmarkedBatch(links, value) }
    }

    fun setFolderBatch(links: List<String>, folder: String, bookmark: Boolean = true) {
        if (links.isEmpty()) return
        viewModelScope.launch { repo.setFolderBatch(links, folder, bookmark) }
    }

    fun removeBatch(links: List<String>) {
        if (links.isEmpty()) return
        viewModelScope.launch { repo.deleteByLinks(links) }
    }

    /**
     * 当前筛选下全部文章的链接（多选「全选」用）。
     *
     * v2.3：从「`articles.value.map { it.link }` 的函数」改成 StateFlow。
     * 以前那个写法是同步读一个 WhileSubscribed 流的 `.value`，属于脆弱耦合；
     * 现在做成 StateFlow 由 UI 订阅，值一定是最新的。
     */
    val currentLinks: StateFlow<List<String>> = articles
        .map { list -> list.map { it.link } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    companion object {
        /** 启动刷新的节流窗口：5 分钟。 */
        private const val LAUNCH_REFRESH_MIN_GAP_MS = 5 * 60_000L

        fun provide(application: YuewenApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(
                        application.newsRepository,
                        application.settingsRepository,
                        application.preloader
                    ) as T
            }
    }
}

// ---------------- 日期分组 ----------------

private fun groupByDay(list: List<Article>): List<HomeRow> {
    if (list.isEmpty()) return emptyList()
    val today = LocalDate.now()
    // list 已按 pubDate 倒序，LinkedHashMap 天然保持「今天 → 昨天 → 本周 → 更早」的顺序
    val grouped = LinkedHashMap<String, MutableList<Article>>()
    for (a in list) {
        grouped.getOrPut(dayLabel(a.pubDate, today)) { mutableListOf() }.add(a)
    }
    val rows = ArrayList<HomeRow>(list.size + grouped.size)
    grouped.forEach { (label, items) ->
        rows.add(HomeRow.Header(label, items.size))
        items.forEach { rows.add(HomeRow.Item(it)) }
    }
    return rows
}

private fun dayLabel(ts: Long, today: LocalDate): String {
    if (ts <= 0L) return "更早"
    val date = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0L -> "今天"
        days == 1L -> "昨天"
        days < 7L -> "本周"
        else -> "更早"
    }
}
