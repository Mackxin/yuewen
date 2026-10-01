package com.example.yuewen.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.SettingsRepository
import com.example.yuewen.data.backup.Backup
import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.model.feedUrlKey
import com.example.yuewen.data.model.sourceIdOf
import com.example.yuewen.data.opml.Opml
import com.example.yuewen.data.preload.PreloadManager
import com.example.yuewen.data.preload.PreloadProgress
import com.example.yuewen.data.repository.NewsRepository
import com.example.yuewen.data.rss.FeedProbe
import com.example.yuewen.data.rss.RefreshScheduler
import com.example.yuewen.data.util.DEFAULT_HOME_KEYWORDS
import com.example.yuewen.ui.components.ArticleListMode
import com.example.yuewen.ui.theme.DEFAULT_CUSTOM_HUE
import com.example.yuewen.ui.theme.DEFAULT_CUSTOM_SAT
import com.example.yuewen.ui.theme.ThemePalette
import com.example.yuewen.ui.util.HomeSortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 缓存用量快照。
 *
 * 单位注意：Coil 的 `DiskCache.size` 是 **Long 字节**，
 * 而 `MemoryCache.size` 是 **Int 字节**（两者签名不同，别混用）。
 */
data class CacheInfo(
    val imageDisk: Long = 0L,
    val imageDiskMax: Long = 0L,
    val imageMem: Int = 0,
    val imageMemMax: Int = 0,
    val fullTextChars: Int = 0,
    val fullTextCount: Int = 0,
    val articles: Int = 0,
    val unbookmarked: Int = 0,
    /** v1.9：还有多少篇「能抓但还没抓」的正文（离线阅读里显示待缓存数量）。 */
    val pendingPreload: Int = 0,
    val loading: Boolean = false
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repo: NewsRepository,
    private val context: Context,
    private val preloader: PreloadManager
) : ViewModel() {

    val theme: StateFlow<String> = settings.themeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")
    val font: StateFlow<String> = settings.fontFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "standard")
    val autoread: StateFlow<Boolean> = settings.autoreadFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notify: StateFlow<Boolean> = settings.notifyFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val refreshMinutes: StateFlow<Int> = settings.refreshMinutesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    // ---- 列表布局（紧凑 / 卡片 / 杂志）----
    val listMode: StateFlow<ArticleListMode> = settings.listModeFlow
        .map { ArticleListMode.of(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArticleListMode.Card)

    // ---- 阅读器排版 ----
    val readerTheme: StateFlow<String> = settings.readerThemeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")
    val readerFont: StateFlow<String> = settings.readerFontFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "sans")
    val readerSpacing: StateFlow<String> = settings.readerSpacingFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "normal")
    val readerSize: StateFlow<Int> = settings.readerSizeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val sources: StateFlow<List<FeedSource>> = settings.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val blockedSources: StateFlow<List<String>> = settings.blockedSourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val blockedKeywords: StateFlow<List<String>> = settings.blockedKeywordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- v1.5：阅读时显示底栏 / 启动自动刷新 ----
    val showBarInReader: StateFlow<Boolean> = settings.showBarInReaderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val refreshOnLaunch: StateFlow<Boolean> = settings.refreshOnLaunchFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ---- v1.6.1：底栏未读数字（默认关闭）----
    val unreadBadge: StateFlow<Boolean> = settings.unreadBadgeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ---- v1.8：首页顶部筛选图标（默认显示）----
    val homeFilterIcons: StateFlow<Boolean> = settings.homeFilterIconsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ==================== v2.0：个性化 ====================

    /** 应用内显示名（空 = 用默认「阅闻」）。 */
    val appTitle: StateFlow<String> = settings.appTitleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** 桌面图标名称的预设下标。 */
    val iconNameIndex: StateFlow<Int> = settings.iconNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val homeShowLayout: StateFlow<Boolean> = settings.homeShowLayoutFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val homeShowRefresh: StateFlow<Boolean> = settings.homeShowRefreshFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val homeShowSubtitle: StateFlow<Boolean> = settings.homeShowSubtitleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 朗读时是否挂通知（锁屏 / 通知栏控制条）。 */
    val ttsNotify: StateFlow<Boolean> = settings.ttsNotifyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 是否读过新手指南（没读过就给设置项加个提示）。 */
    val guideSeen: StateFlow<Boolean> = settings.guideSeenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ==================== v2.0.2：首页筛选（分类 / 阅源） ====================

    /** 首页顶栏胶囊显示哪种维度。 */
    val homeChipMode: StateFlow<HomeChipMode> = settings.homeChipModeFlow
        .map { HomeChipMode.of(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeChipMode.Category)

    /** 打开 App 时默认停在的分类（「推荐」= 全部）。 */
    val homeDefaultCategory: StateFlow<String> = settings.homeDefaultCategoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "推荐")

    /** 打开 App 时默认只看哪个阅源（空 = 全部阅源）。 */
    val homeDefaultSource: StateFlow<String> = settings.homeDefaultSourceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setHomeChipMode(v: HomeChipMode) = viewModelScope.launch { settings.setHomeChipMode(v.key) }
    fun setHomeDefaultCategory(v: String) = viewModelScope.launch { settings.setHomeDefaultCategory(v) }
    fun setHomeDefaultSource(v: String) = viewModelScope.launch { settings.setHomeDefaultSource(v) }

    // ==================== v2.4：首页关键词胶囊 ====================

    /**
     * 设置里那份关键词列表。
     *
     * ⚠️ 用 `Eagerly`：`HomeViewModel` 也要用同一份数据来判断「当前筛着的词是不是被删了」。
     * 用 `WhileSubscribed` 的话，设置页一关上游就断，首页那边会拿到默认词、
     * 把用户自己填的词判成「已删除」，筛选状态被莫名清掉。
     */
    val homeKeywords: StateFlow<List<String>> = settings.homeKeywordsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_HOME_KEYWORDS)

    val homeShowKeywords: StateFlow<Boolean> = settings.homeShowKeywordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun addHomeKeyword(kw: String) = viewModelScope.launch { settings.addHomeKeyword(kw) }
    fun removeHomeKeyword(kw: String) = viewModelScope.launch { settings.removeHomeKeyword(kw) }
    fun setHomeShowKeywords(v: Boolean) = viewModelScope.launch { settings.setHomeShowKeywords(v) }

    // ==================== v2.2：首页排序 / 默认浏览器 ====================

    /** 首页文章排序方式（最新在前 / 随机 / 按阅源 …）。 */
    val homeSort: StateFlow<HomeSortMode> = settings.homeSortFlow
        .map { HomeSortMode.of(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeSortMode.TimeDesc)

    fun setHomeSort(v: HomeSortMode) = viewModelScope.launch { settings.setHomeSort(v.key) }

    /** 「换一批」：只对随机排序有意义，换一个洗牌种子。 */
    fun reshuffle() = viewModelScope.launch { settings.reshuffle() }

    /**
     * 打开原文用哪个浏览器（**包名**，空串 = 跟随系统）。
     *
     * `WhileSubscribed` 就够：只有设置页会读它，设置页在的时候必然有订阅者。
     */
    val browserPkg: StateFlow<String> = settings.browserPkgFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setBrowserPkg(v: String) = viewModelScope.launch { settings.setBrowserPkg(v) }

    // ==================== v2.3：配色方案 ====================

    /**
     * 当前配色档位。
     *
     * ⚠️ 用 `Eagerly` 而不是 `WhileSubscribed`：这个值也被 `MainActivity` 直接订阅
     * （决定整个 App 的配色），设置页关掉之后它还得继续是活的 ——
     * 而且以后若有人要同步读 `.value`，`WhileSubscribed` 会停在初值上（老坑了）。
     */
    val themePalette: StateFlow<ThemePalette> = settings.themePaletteFlow
        .map { ThemePalette.of(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePalette.Emerald)

    val customHue: StateFlow<Int> = settings.customHueFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_CUSTOM_HUE)

    val customSat: StateFlow<Int> = settings.customSatFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_CUSTOM_SAT)

    fun setThemePalette(v: ThemePalette) = viewModelScope.launch { settings.setThemePalette(v.key) }
    fun setCustomHue(v: Int) = viewModelScope.launch { settings.setCustomHue(v) }
    fun setCustomSat(v: Int) = viewModelScope.launch { settings.setCustomSat(v) }

    fun setAppTitle(v: String) = viewModelScope.launch { settings.setAppTitle(v) }
    fun setHomeShowLayout(v: Boolean) = viewModelScope.launch { settings.setHomeShowLayout(v) }
    fun setHomeShowRefresh(v: Boolean) = viewModelScope.launch { settings.setHomeShowRefresh(v) }
    fun setHomeShowSubtitle(v: Boolean) = viewModelScope.launch { settings.setHomeShowSubtitle(v) }
    fun setTtsNotify(v: Boolean) = viewModelScope.launch { settings.setTtsNotify(v) }

    /**
     * 切换桌面图标名称。
     *
     * 这是本轮唯一一处「会影响桌面」的操作：本质是启用另一个 activity-alias。
     * 用 DONT_KILL_APP，所以 App 不会被杀；但桌面图标要等启动器自己刷新（一般 1~2 秒）。
     * 失败（个别 ROM 会拒绝）时返回 false，由界面提示用户。
     */
    fun setIconNameIndex(v: Int): Boolean {
        val ok = com.example.yuewen.ui.util.applyIconName(context, v)
        viewModelScope.launch { settings.setIconNameIndex(v) }
        return ok
    }

    fun setShowBarInReader(v: Boolean) = viewModelScope.launch { settings.setShowBarInReader(v) }
    fun setRefreshOnLaunch(v: Boolean) = viewModelScope.launch { settings.setRefreshOnLaunch(v) }
    fun setUnreadBadge(v: Boolean) = viewModelScope.launch { settings.setUnreadBadge(v) }
    fun setHomeFilterIcons(v: Boolean) = viewModelScope.launch { settings.setHomeFilterIcons(v) }

    // ==================== v1.9：离线阅读 ====================

    val preloadAuto: StateFlow<Boolean> = settings.preloadAutoFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val preloadWifiOnly: StateFlow<Boolean> = settings.preloadWifiOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 朗读语速档位（0 慢 / 1 标准 / 2 快 / 3 很快）。 */
    val ttsRate: StateFlow<Int> = settings.ttsRateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    /** 预加载进度（和 Application 上那份是同一个 StateFlow）。 */
    val preloadProgress: StateFlow<PreloadProgress> = preloader.progress

    fun setPreloadAuto(v: Boolean) = viewModelScope.launch { settings.setPreloadAuto(v) }
    fun setPreloadWifiOnly(v: Boolean) = viewModelScope.launch { settings.setPreloadWifiOnly(v) }
    fun setTtsRate(v: Int) = viewModelScope.launch { settings.setTtsRate(v) }

    /** 手动「立即缓存全部正文」。 */
    fun startPreload() = preloader.startNow()

    fun cancelPreload() = preloader.cancel()

    /** 当前网络是不是 Wi-Fi（用来提示「已跳过：不在 Wi-Fi」）。 */
    fun isOnWifi(): Boolean = preloader.isOnWifi()

    fun setTheme(v: String) = viewModelScope.launch { settings.setTheme(v) }
    fun setFont(v: String) = viewModelScope.launch { settings.setFont(v) }
    fun setAutoRead(v: Boolean) = viewModelScope.launch { settings.setAutoRead(v) }
    fun setNotify(v: Boolean) = viewModelScope.launch { settings.setNotify(v) }
    fun setListMode(mode: ArticleListMode) = viewModelScope.launch { settings.setListMode(mode.key) }
    fun setReaderTheme(v: String) = viewModelScope.launch { settings.setReaderTheme(v) }
    fun setReaderFont(v: String) = viewModelScope.launch { settings.setReaderFont(v) }
    fun setReaderSpacing(v: String) = viewModelScope.launch { settings.setReaderSpacing(v) }
    fun setReaderSize(v: Int) = viewModelScope.launch { settings.setReaderSize(v) }

    fun setRefreshMinutes(v: Int) {
        viewModelScope.launch { settings.setRefreshMinutes(v) }
        RefreshScheduler.schedule(context, v)
    }

    // v2.0.2：原来这里还有 toggleSource / removeSource / updateSource / addSource 四个方法，
    // 是「阅源管理还在设置页」时代留下的。现在源管理统一在「阅源」页（SourcesViewModel），
    // 两份实现并存只会让行为慢慢走偏，所以整块删掉。

    suspend fun testSource(url: String): String? = repo.testSource(url)

    /** 探测地址：一次拿到「能不能用 + 源名称 + 文章数 + 格式 + 最新几条标题」。 */
    suspend fun probeSource(url: String): FeedProbe = repo.probeSource(url)

    suspend fun discoverFeeds(url: String): List<com.example.yuewen.data.rss.FeedCandidate> = repo.discoverFeeds(url)

    /** 当前已存在的源地址（加源页用来提示「这个源已经加过了」）。 */
    fun isDuplicateUrl(url: String): Boolean =
        sources.value.any { feedUrlKey(it.url) == feedUrlKey(url) }

    /**
     * 添加阅源后：写入设置 → 自动切到该分类 → 立即刷新拉取文章。
     * 分类留空时归入「未分类」，之后可以在「阅源 → 我的阅源」里统一改。
     */
    suspend fun addSourceAndRefresh(name: String, url: String, category: String = "未分类") {
        // v2.3：改走 settings.mutateSources（仓库级的锁）。
        // 以前这里是裸的「读 → 改 → 写」，和阅源页 / 备份恢复撞在一起会丢源。
        val cat = category.trim().ifBlank { "未分类" }
        settings.mutateSources { list ->
            list.add(FeedSource(sourceIdOf(url), name.trim(), url.trim(), cat, true))
        }
        // 切到新源所在的分类：首页订阅 categoryFlow 的变化会跟着跳过去
        settings.setCategory(cat)
        repo.refreshAll()
        settings.setLastRefresh(System.currentTimeMillis())
    }

    // ---- OPML 导入 / 导出 ----

    /** 把当前所有源写成 OPML 到用户选定的文件。返回写入的源数量。 */
    suspend fun writeOpml(uri: Uri): Int = withContext(Dispatchers.IO) {
        val list = settings.getSources()
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(Opml.export(list).toByteArray(Charsets.UTF_8))
            out.flush()
        }
        list.size
    }

    // ==================== v2.0：完整备份 / 恢复（JSON） ====================

    /**
     * 导出完整备份：订阅源 + 收藏 + 笔记 + 个性化设置，写成一个 JSON 文件。
     * 返回 (源数, 收藏数, 笔记数)。
     */
    suspend fun writeBackup(uri: Uri): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        val sources = settings.getSources()
        val bookmarks = repo.allBookmarks()
        val notes = repo.allNotes()
        val json = Backup.export(sources, bookmarks, notes, settings.snapshotSettings())
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        Triple(sources.size, bookmarks.size, notes.size)
    }

    /** 备份文件的默认名（带日期）。 */
    fun backupFileName(): String = Backup.fileName()

    /**
     * 从备份文件恢复。**只加不减**：导入的源按地址去重后追加，
     * 收藏用 REPLACE 覆盖同链接的那条，笔记同理 —— 不会把本机已有的东西删掉。
     *
     * @return (新增源数, 恢复收藏数, 恢复笔记数)；解析失败返回 null
     */
    suspend fun readBackup(uri: Uri): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            .orEmpty()
        val bundle = Backup.parse(text) ?: return@withContext null

        // 订阅源：去重后追加。
        // v2.3 两处修正：
        //   ① id 改回 `sourceIdOf(url)`（以前是 `"b" + 时间戳`，破坏了「id 由地址派生」
        //      这条全库约定 —— 那样 id 会随导入时刻变化，跨设备/重复导入都对不上）；
        //   ② 去重键统一用 `feedUrlKey()`，和 OPML 导入 / 阅源页保持完全一致。
        var addedSources = 0
        settings.mutateSources { list ->
            val existing = list.map { feedUrlKey(it.url) }.toHashSet()
            bundle.sources.forEach { s ->
                if (existing.add(feedUrlKey(s.url))) {
                    list.add(s.copy(id = sourceIdOf(s.url)))
                    addedSources++
                }
            }
        }

        // 收藏 + 笔记
        repo.restore(bundle.bookmarks, bundle.notes)

        // 设置：只覆盖备份里带的键
        settings.applySettings(bundle.settings)

        if (addedSources > 0) {
            runCatching { repo.refreshAll() }
            settings.setLastRefresh(System.currentTimeMillis())
        }
        Triple(addedSources, bundle.bookmarks.size, bundle.notes.size)
    }

    /** 备份用：一句话描述本机现在有多少东西（导出前展示）。 */
    suspend fun backupSummary(): String = withContext(Dispatchers.IO) {
        val s = settings.getSources().size
        val b = runCatching { repo.allBookmarks().size }.getOrDefault(0)
        val n = runCatching { repo.noteCount() }.getOrDefault(0)
        "$s 个阅源 · $b 篇收藏 · $n 条笔记"
    }

    /**
     * 从 OPML 文件导入。返回 (新增数, 跳过数)。
     * 同一个地址已存在就跳过，不会产生重复源。
     */
    suspend fun readOpml(uri: Uri): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            .orEmpty()
        val incoming = Opml.parse(text)
        if (incoming.isEmpty()) return@withContext 0 to 0

        // v2.3：① 走仓库级原子改法（防并发丢源）；② 去重键与备份恢复统一用 feedUrlKey
        // （以前这里少了一步 lowercase()，`https://x.com/Feed` 和 `.../feed` 会被当成两个源）。
        var added = 0
        var skipped = 0
        settings.mutateSources { list ->
            val existing = list.map { feedUrlKey(it.url) }.toHashSet()
            incoming.forEach { s ->
                if (existing.add(feedUrlKey(s.url))) {
                    list.add(s.copy(id = sourceIdOf(s.url)))
                    added++
                } else {
                    skipped++
                }
            }
        }
        if (added > 0) {
            repo.refreshAll()
            settings.setLastRefresh(System.currentTimeMillis())
        }
        added to skipped
    }

    fun clearCache(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val n = repo.clearCache()
            withContext(Dispatchers.Main) { onResult(n) }
        }
    }

    // ==================== v1.6：缓存管理 ====================

    private val _cacheInfo = MutableStateFlow(CacheInfo())
    val cacheInfo: StateFlow<CacheInfo> = _cacheInfo

    /**
     * 读一次缓存用量：图片缓存走 Coil，其余走数据库。
     *
     * `ImageLoader.diskCache` / `memoryCache` 在 Coil 2.6 上仍标着 ExperimentalCoilApi，
     * 需要显式 opt-in 才不会报警告。这里只用读取与 clear()，行为是稳定的。
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun loadCacheInfo() {
        viewModelScope.launch {
            _cacheInfo.value = _cacheInfo.value.copy(loading = true)

            // Coil 的图片缓存。YuewenApplication 实现了 ImageLoaderFactory，
            // 所以这里拿到的就是 App 自己在用的那个实例（含磁盘缓存配置）。
            val loader = runCatching { coil.Coil.imageLoader(context) }.getOrNull()
            val disk = loader?.diskCache
            val mem = loader?.memoryCache

            val (total, unbookmarked) = runCatching { repo.articleCounts() }.getOrDefault(0 to 0)
            val (chars, ftCount) = runCatching { repo.fullTextUsage() }.getOrDefault(0 to 0)
            val pending = runCatching { repo.pendingPreloadCount() }.getOrDefault(0)

            _cacheInfo.value = CacheInfo(
                imageDisk = disk?.size ?: 0L,
                imageDiskMax = disk?.maxSize ?: 0L,
                imageMem = mem?.size ?: 0,
                imageMemMax = mem?.maxSize ?: 0,
                fullTextChars = chars,
                fullTextCount = ftCount,
                articles = total,
                unbookmarked = unbookmarked,
                pendingPreload = pending,
                loading = false
            )
        }
    }

    /** 清图片缓存（磁盘 + 内存），返回释放的字节数。 */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearImageCache(onResult: (Long) -> Unit) {
        viewModelScope.launch {
            val loader = runCatching { coil.Coil.imageLoader(context) }.getOrNull()
            val before = loader?.diskCache?.size ?: 0L
            withContext(Dispatchers.IO) {
                runCatching { loader?.memoryCache?.clear() }
                runCatching { loader?.diskCache?.clear() }
            }
            // 清完重新量一次，界面上的数字立刻归零
            loadCacheInfo()
            withContext(Dispatchers.Main) { onResult(before) }
        }
    }

    /** 清未收藏文章，返回清掉的篇数。 */
    fun clearUnbookmarked(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val n = repo.clearCache()
            loadCacheInfo()
            withContext(Dispatchers.Main) { onResult(n) }
        }
    }

    /** 清正文缓存，返回清掉的篇数。 */
    fun clearFullTextCache(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val n = runCatching { repo.clearFullText() }.getOrDefault(0)
            loadCacheInfo()
            withContext(Dispatchers.Main) { onResult(n) }
        }
    }

    // ---- 屏蔽管理 ----
    fun addBlockedSource(name: String) = viewModelScope.launch { settings.addBlockedSource(name) }
    fun removeBlockedSource(name: String) = viewModelScope.launch { settings.removeBlockedSource(name) }
    fun addBlockedKeyword(kw: String) = viewModelScope.launch { settings.addBlockedKeyword(kw) }
    fun removeBlockedKeyword(kw: String) = viewModelScope.launch { settings.removeBlockedKeyword(kw) }

    companion object {
        fun provide(application: YuewenApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(
                        application.settingsRepository,
                        application.newsRepository,
                        application.applicationContext,
                        application.preloader
                    ) as T
            }
    }
}
