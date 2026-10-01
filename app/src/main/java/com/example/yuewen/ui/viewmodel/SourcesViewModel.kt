package com.example.yuewen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.SettingsRepository
import com.example.yuewen.data.model.CatalogFeed
import com.example.yuewen.data.model.FeedCatalog
import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.model.feedUrlKey
import com.example.yuewen.data.model.sourceIdOf
import com.example.yuewen.data.repository.NewsRepository
import com.example.yuewen.data.rss.FeedProbe
import com.example.yuewen.data.rss.FeedSearch
import com.example.yuewen.data.rss.RssHubCatalog
import com.example.yuewen.data.rss.RemoteFeed
import com.example.yuewen.data.rss.normalizeRssHubInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 某个阅源的行内状态：测试结果直接显示在它那一行下面。 */
data class SourceStatus(
    val busy: Boolean = false,
    val ok: Boolean? = null,
    val message: String = ""
)

/**
 * 「阅源」页（v2.0）：订阅源管理 + 发现推荐 + 在线搜索。
 *
 * 用户原话是「让用户可以拿到 app 不至于什么都没有」。
 * 所以这里的主线不是「管理」（那是设置页的活），而是**发现**：
 * 打开就有分好类的推荐源，一键订阅一整组；找不到想看的还能按关键词订阅一个专属信息流。
 */
class SourcesViewModel(
    private val settings: SettingsRepository,
    private val repo: NewsRepository
) : ViewModel() {

    /** 我订阅的源。 */
    val sources: StateFlow<List<FeedSource>> = settings.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 发现页当前选中的分类（null = 全部）。 */
    private val _group = MutableStateFlow<String?>(null)
    val group: StateFlow<String?> = _group

    fun selectGroup(name: String?) { _group.value = name }

    /** 在线搜索关键词。 */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private val _results = MutableStateFlow<List<RemoteFeed>>(emptyList())
    val results: StateFlow<List<RemoteFeed>> = _results

    /** 搜索失败时的提示（比如没有网络）。 */
    private val _searchError = MutableStateFlow("")
    val searchError: StateFlow<String> = _searchError

    /** 最近一次操作的结果提示（「已订阅 8 个源」这种）。 */
    private val _notice = MutableStateFlow("")
    val notice: StateFlow<String> = _notice

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    // v2.3：这里原有一把 `listLock`（v2.1 起还特意做成了进程级共享）已被移除。
    // 原因是「一半上锁等于没上锁」—— 设置页的加源 / OPML 导入 / 备份恢复三条路径
    // 各自直接读写 settings，根本不经过这把锁，撞上照样丢数据。
    // 现在锁统一收进 `SettingsRepository.sourcesLock`（仓库全 App 单实例），
    // 所有改列表的地方都走 `settings.mutateSources { }`，没有第二条路可走。


    /** 每个阅源的行内状态（排队中 / 测试中 / 可用 / 失败）。 */
    private val _status = MutableStateFlow<Map<String, SourceStatus>>(emptyMap())
    val statusMap: StateFlow<Map<String, SourceStatus>> = _status

    private fun setStatus(id: String, st: SourceStatus) {
        _status.value = _status.value + (id to st)
    }

    /** 「测试全部」是否正在跑。 */
    private val _testingAll = MutableStateFlow(false)
    val testingAll: StateFlow<Boolean> = _testingAll

    /**
     * 「测试全部」的结论。
     *
     * 和 [notice] 分开：notice 三秒就走，而「27 个可用、2 个失败」这种结论
     * 用户往往要对照着列表一行行看 —— 得一直挂在列表顶部，直到下次测试。
     */
    private val _testSummary = MutableStateFlow("")
    val testSummary: StateFlow<String> = _testSummary

    fun setQuery(q: String) {
        _query.value = q
        if (q.isBlank()) {
            _results.value = emptyList()
            _searchError.value = ""
        }
    }

    fun clearNotice() { _notice.value = "" }

    /** 某个地址是否已经订阅过（推荐列表用它显示「已订阅」）。 */
    fun isSubscribed(url: String): Boolean {
        val key = url.trim().trimEnd('/').lowercase()
        return sources.value.any { it.url.trim().trimEnd('/').lowercase() == key }
    }

    /** 按分类过滤内置推荐库。 */
    fun catalogFor(groupName: String?): List<CatalogFeed> {
        val g = FeedCatalog.findGroup(groupName.orEmpty())
        return g?.feeds ?: FeedCatalog.all()
    }

    /** 「编辑精选」那一组（跨分类挑出来的最稳的源）。 */
    fun featured(): List<CatalogFeed> = FeedCatalog.featured()

    // ------------------------------------------------------------ RSSHub（v2.1）

    /**
     * 当前 RSSHub 实例地址。空串表示「用官方默认实例」。
     *
     * ⚠️ 这里必须用 [SharingStarted.Eagerly]：界面要在**同步**代码里
     * `rssHubInstance.value` 拼地址，而 `WhileSubscribed` 的属性在没人订阅时
     * 上游根本不收集，`.value` 会永远停在初始值 —— 表现就是「改了实例地址但拼出来的
     * 还是老地址」。这条坑在 v2.0.2 的首页阅源行上踩过一次。
     */
    val rssHubInstance: StateFlow<String> = settings.rssHubInstanceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * 保存实例地址。**写空串等于用默认**：
     * 用户把地址改回官方实例时，我们存空串而不是把 `https://rsshub.app` 写死进去 ——
     * 这样以后官方换域名 / 换默认实例，没手动改过的人会自动跟上。
     */
    suspend fun setRssHubInstance(raw: String) {
        val normalized = normalizeRssHubInstance(raw)
        settings.setRssHubInstance(if (normalized == RssHubCatalog.DEFAULT_INSTANCE) "" else normalized)
    }

    suspend fun resetRssHubInstance() = settings.setRssHubInstance("")

    /** 探一次地址，给 RSSHub 页做「订阅前先看看有什么」。网络异常时返回 null。 */
    suspend fun probeUrl(url: String): FeedProbe? = runCatching { repo.probeSource(url) }.getOrNull()

    /**
     * 订阅一个 RSSHub 生成的地址。
     *
     * 走的是和「粘贴地址添加」完全一样的链路（[addInternal] + `refreshSource`），
     * 所以分类、正文抽取、朗读、离线缓存这些后续能力自动都有。
     *
     * @return 拉到的新文章数；`-1` 表示这个地址已经订过了（界面据此提示，不重复加）。
     */
    suspend fun addRssHubSource(name: String, url: String, category: String): Int {
        if (isSubscribed(url)) {
            _notice.value = "这个地址已经在你的阅源里了"
            return -1
        }
        _busy.value = true
        val id = addInternal(name, url, category)
        val n = runCatching { repo.refreshSource(id) }.getOrDefault(0)
        settings.setLastRefresh(System.currentTimeMillis())
        _busy.value = false
        _notice.value = "已订阅「$name」" +
                if (n > 0) "，拉到 $n 篇文章" else "（暂时没拉到内容，这条路由可能已经失效）"
        return n
    }

    // ---------------------------------------------------------------- 订阅

    /** 订阅一个源；已经订阅过就跳过。返回新增的文章数（-1 表示未订阅）。 */
    fun subscribe(feed: CatalogFeed) {
        viewModelScope.launch {
            if (isSubscribed(feed.url)) {
                _notice.value = "「${feed.name}」已经在你的阅源里了"
                return@launch
            }
            _busy.value = true
            val added = addInternal(feed.name, feed.url, feed.category)
            val n = runCatching { repo.refreshSource(added) }.getOrDefault(0)
            _busy.value = false
            _notice.value = "已订阅「${feed.name}」" + if (n > 0) "，拉到 $n 篇文章" else "，暂时没拉到内容"
        }
    }

    /** 一键订阅一整组（并发抓取交给仓库的 refreshAll）。 */
    fun subscribeGroup(groupName: String) {
        val g = FeedCatalog.findGroup(groupName) ?: return
        val pending = g.feeds.filter { !isSubscribed(it.url) }
        if (pending.isEmpty()) {
            _notice.value = "「${groupName}」这一组已经全部订阅过了"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            settings.mutateSources { list ->
                val have = list.map { feedUrlKey(it.url) }.toHashSet()
                pending.forEach { f ->
                    // 锁里再查一次重：看到的是最新列表，顺手保证 id 唯一
                    if (have.add(feedUrlKey(f.url))) {
                        list.add(FeedSource(sourceIdOf(f.url), f.name, f.url, f.category, true))
                    }
                }
            }
            _notice.value = "已订阅「$groupName」整组（${pending.size} 个源），正在拉取内容…"
            runCatching { repo.refreshAll() }
            settings.setLastRefresh(System.currentTimeMillis())
            _busy.value = false
            _notice.value = "「$groupName」整组订阅完成，共 ${pending.size} 个源"
        }
    }

    /** 订阅搜索结果里的一个源。 */
    fun subscribeRemote(feed: RemoteFeed) {
        viewModelScope.launch {
            if (isSubscribed(feed.url)) {
                _notice.value = "「${feed.title}」已经在你的阅源里了"
                return@launch
            }
            _busy.value = true
            // 在线搜索回来的源没有分类，先归到「订阅」里，用户之后可以在「我的阅源」里改
            val id = addInternal(feed.title, feed.url, "订阅")
            val n = runCatching { repo.refreshSource(id) }.getOrDefault(0)
            _busy.value = false
            _notice.value = "已订阅「${feed.title}」" + if (n > 0) "，拉到 $n 篇文章" else "，暂时没拉到内容（可能是地址失效）"
        }
    }

    private suspend fun addInternal(name: String, url: String, category: String): String {
        // v2.0.2：id 由地址派生，保证唯一且稳定（老写法用时间戳，重复添加会串行）
        val id = sourceIdOf(url)
        // v2.3：改走 settings.mutateSources —— 锁收在仓库里，
        // 设置页 / 加源页 / 备份恢复 / OPML 导入 与本页共用同一把，
        // 不会再出现「两处同时改列表，后写的把先写的覆盖掉」。
        settings.mutateSources { list ->
            list.add(FeedSource(id, name.trim(), url.trim(), category.trim().ifBlank { "订阅" }, true))
        }
        return id
    }

    // ------------------------------------------------------------------ 搜索

    /**
     * 搜订阅源。
     *
     * 两个来源一起给：
     * 1. **本地推荐库**（离线可用，按名字匹配）—— 没网也能找到「少数派」这种常见源；
     * 2. **在线目录**（Feedly 公开搜索）—— 覆盖长尾的小众站点。
     * 另外永远附上「按关键词订阅」两条（必应 / Google 新闻搜索流），
     * 保证哪怕什么都搜不到，用户也有能订阅的东西。
     */
    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _searching.value = true
            _searchError.value = ""
            _results.value = emptyList()

            val local = FeedCatalog.all()
                .filter { it.name.contains(q, true) || it.desc.contains(q, true) || it.category.contains(q, true) }
                .map { RemoteFeed(it.name, it.url, description = it.desc, provider = "内置推荐") }

            val keyword = FeedSearch.keywordFeeds(q)

            val remote = withContext(Dispatchers.IO) { FeedSearch.searchFeeds(q) }
            _searching.value = false

            val remoteList = remote.getOrElse { emptyList() }
            if (remote.isFailure && local.isEmpty()) {
                _searchError.value = "在线目录连不上（可能没有网络）。下面这些仍然可以直接订阅："
            }

            _results.value = (local + remoteList + keyword)
                .distinctBy { it.url.trim().trimEnd('/').lowercase() }
                .take(40)
        }
    }

    // ------------------------------------------------------------------ 测试

    /**
     * 测一个源：能不能连上、是什么格式、有多少篇文章。
     *
     * 和「刷新」不同 —— 刷新是真的去抓文章写库，测试只取一份 feed 看看健康度，
     * 便宜得多，所以「全部测一遍」才敢一次测几十个。
     */
    fun testOne(id: String, url: String) {
        viewModelScope.launch {
            setStatus(id, SourceStatus(busy = true, message = "正在测试…"))
            val probe = runCatching { repo.probeSource(url) }.getOrNull()
            val st = when {
                probe == null -> SourceStatus(ok = false, message = "测试失败：网络异常或地址无法访问")
                probe.error == null -> SourceStatus(ok = true, message = "可用 · ${probe.kind} · ${probe.count} 篇文章")
                else -> SourceStatus(ok = false, message = probe.error)
            }
            setStatus(id, st)
        }
    }

    /**
     * 一键测试全部阅源。
     *
     * 并发但限流（同时最多 4 个）：串行测二十几个源要等一两分钟；
     * 全部一起冲又容易把手机网络打爆，反而测出一堆"失败"。
     * 每测完一个就结算一行，用户能看着结果一个个冒出来。
     */
    fun testAll() {
        if (_testingAll.value) return
        val list = sources.value
        if (list.isEmpty()) {
            _notice.value = "还没有阅源可以测试"
            return
        }
        viewModelScope.launch {
            _testingAll.value = true
            _testSummary.value = ""
            _notice.value = "正在测试 ${list.size} 个阅源…"
            // 先全部标成「排队中」，点下去马上有反馈，不会像点空了
            _status.value = _status.value +
                    list.associate { it.id to SourceStatus(busy = true, message = "排队中…") }

            try {
                val gate = Semaphore(4)
                val results = coroutineScope {
                    list.map { src ->
                        async {
                            gate.withPermit {
                                setStatus(src.id, SourceStatus(busy = true, message = "正在测试…"))
                                val probe = runCatching { repo.probeSource(src.url) }.getOrNull()
                                val st = when {
                                    probe == null -> SourceStatus(ok = false, message = "测试失败：网络异常或地址无法访问")
                                    probe.error == null -> SourceStatus(
                                        ok = true,
                                        message = "可用 · ${probe.kind} · ${probe.count} 篇文章"
                                    )
                                    else -> SourceStatus(ok = false, message = probe.error)
                                }
                                setStatus(src.id, st)
                                src.name to (probe?.error == null)
                            }
                        }
                    }.awaitAll()
                }
                val okCount = results.count { it.second }
                val bad = results.filterNot { it.second }.map { it.first }
                _testSummary.value = when {
                    bad.isEmpty() -> "全部 ${okCount} 个阅源都可用"
                    okCount == 0 -> "全部 ${bad.size} 个都失败了：${bad.take(3).joinToString("、")}"
                    else -> "$okCount 个可用，${bad.size} 个失败：${bad.take(3).joinToString("、")}"
                }
                _notice.value = ""
            } finally {
                _testingAll.value = false
            }
        }
    }

    // ------------------------------------------------------------------ 管理

    fun toggleSource(id: String, enabled: Boolean) {
        viewModelScope.launch {
            settings.mutateSources { list ->
                val i = list.indexOfFirst { it.id == id }
                if (i >= 0) list[i] = list[i].copy(enabled = enabled)
            }
        }
    }

    fun removeSource(id: String) {
        viewModelScope.launch {
            // 先记下名字：删完源之后列表里就查不到它了，而清缓存文章要按名字兜底
            val name = sources.value.firstOrNull { it.id == id }?.name.orEmpty()
            settings.mutateSources { list -> list.removeAll { it.id == id } }
            // 行内状态跟着源一起清掉：留着的话，下次某个新源拿到同一个 id 会「继承」这条旧结果
            _status.value = _status.value - id
            runCatching { repo.deleteBySource(id, name) }
        }
    }

    fun updateSource(id: String, name: String, url: String, category: String) {
        viewModelScope.launch {
            settings.mutateSources { list ->
                val i = list.indexOfFirst { it.id == id }
                if (i >= 0) {
                    list[i] = list[i].copy(
                        name = name.trim(),
                        url = url.trim(),
                        category = category.trim().ifBlank { "订阅" }
                    )
                }
            }
        }
    }

    /**
     * 刷新单个阅源，并把结果**落在这一行下面**（v2.0.2）。
     *
     * 以前这里只更新一条全局提示条，用户点完某一行之后，提示条挂在页面顶部，
     * 很容易看成「结果跑到别的源上了」。现在改成两件事一起做：
     * 1. 先抓一遍新文章（`refreshSource`）；
     * 2. 再探一次源本身（`probeSource`），这样即使「新增 0 篇」也能分清到底是
     *    「源正常、只是没新内容」还是「源已经挂了、所以拉不到东西」。
     *
     * 两条结果都写进 [statusMap]，渲染在对应行下面，点哪行就只动哪行。
     */
    fun refreshSource(id: String) {
        viewModelScope.launch {
            val src = sources.value.firstOrNull { it.id == id } ?: return@launch
            _busy.value = true
            setStatus(id, SourceStatus(busy = true, message = "正在检查并刷新…"))
            val added = runCatching { repo.refreshSource(id) }.getOrDefault(0)
            val probe = runCatching { repo.probeSource(src.url) }.getOrNull()
            val st = when {
                probe == null -> SourceStatus(ok = false, message = "刷新失败：网络异常或地址无法访问")
                probe.error != null -> SourceStatus(ok = false, message = probe.error)
                added > 0 -> SourceStatus(ok = true, message = "可用 · ${probe.kind} · ${probe.count} 篇（新增 $added 篇）")
                else -> SourceStatus(ok = true, message = "可用 · ${probe.kind} · ${probe.count} 篇（暂无新内容）")
            }
            setStatus(id, st)
            settings.setLastRefresh(System.currentTimeMillis())
            _busy.value = false
        }
    }

    /** 「一键补齐全部推荐」：把推荐库里还没订阅的都加上（分批，别一次几百个）。 */
    fun subscribeAllFeatured() {
        val pending = FeedCatalog.featured().filter { !isSubscribed(it.url) }
        if (pending.isEmpty()) {
            _notice.value = "编辑精选里的源都已经订阅过了"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            settings.mutateSources { list ->
                val have = list.map { feedUrlKey(it.url) }.toHashSet()
                pending.forEach { f ->
                    if (have.add(feedUrlKey(f.url))) {
                        list.add(FeedSource(sourceIdOf(f.url), f.name, f.url, f.category, true))
                    }
                }
            }
            _notice.value = "已订阅编辑精选 ${pending.size} 个源，正在拉取内容…"
            runCatching { repo.refreshAll() }
            settings.setLastRefresh(System.currentTimeMillis())
            _busy.value = false
            _notice.value = "编辑精选订阅完成（${pending.size} 个源）"
        }
    }

    companion object {
        fun provide(application: YuewenApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SourcesViewModel(application.settingsRepository, application.newsRepository) as T
            }
    }
}
