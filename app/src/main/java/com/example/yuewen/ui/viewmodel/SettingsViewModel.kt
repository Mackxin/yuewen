package com.example.yuewen.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.SettingsRepository
import com.example.yuewen.data.backup.Backup
import com.example.yuewen.data.backup.BackupException
import com.example.yuewen.data.backup.BackupTarget
import com.example.yuewen.data.backup.LocalBackup
import com.example.yuewen.data.backup.Remote
import com.example.yuewen.data.backup.RemoteBackup
import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.model.feedUrlKey
import com.example.yuewen.data.model.sourceIdOf
import com.example.yuewen.data.opml.Opml
import com.example.yuewen.data.preload.PreloadManager
import com.example.yuewen.data.preload.PreloadProgress
import com.example.yuewen.data.repository.NewsRepository
import com.example.yuewen.data.rss.DailyDigestScheduler
import com.example.yuewen.data.rss.FeedProbe
import com.example.yuewen.data.rss.RefreshScheduler
import com.example.yuewen.data.util.BackupSpan
import com.example.yuewen.data.util.DailyDigest
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
import kotlinx.coroutines.flow.first
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
    /**
     * v2.8：读完自动移出稍后读。
     *
     * 只有一个开关，没有第二个 —— 「最新在前」那个排序**只在稍后读页面自己身上**
     * （顶部的两枚 FilterChip）。同一个东西在设置页再摆一份，除了多一处要同步的
     * 界面之外没有任何好处，而用户是在队列列表里翻的时候才想改排序的。
     */
    val queueAutoDequeue: StateFlow<Boolean> = settings.queueAutoDequeueFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ---- v2.8：每日摘要 ----
    val digestEnabled: StateFlow<Boolean> = settings.digestEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val digestHour: StateFlow<Int> = settings.digestHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyDigest.DEFAULT_HOUR)
    val digestMinute: StateFlow<Int> = settings.digestMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyDigest.DEFAULT_MINUTE)

    /**
     * 改摘要开关。
     *
     * ⚠️ 为什么这里要显式重排（而不是交给 Application 那个订阅）：
     * Application 用的是 `ensure`（KEEP），它只会「补排一条本来就没有的」，
     * 而**刚刚被打开的**这个动作在它眼里和「早就开着」没有区别，
     * 于是不会真的排下一次。用户刚点开开关，当晚却什么都没收到，就是这么来的。
     */
    fun setDigestEnabled(v: Boolean) {
        viewModelScope.launch {
            settings.setDigestEnabled(v)
            // 写完再读：这样重排用的时刻和界面显示的是同一份（用户在同一个动作里
            // 先改时间再打开开关时，不会用上旧时间排期）
            val h = settings.digestHourFlow.first()
            val m = settings.digestMinuteFlow.first()
            DailyDigestScheduler.replace(context, v, h, m)
        }
    }

    /** 改推送时刻。改动本身就必须重排 —— 旧的那班还等在昨天算出来的时刻上。 */
    fun setDigestTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settings.setDigestTime(hour, minute)
            val enabled = settings.digestEnabledFlow.first()
            DailyDigestScheduler.replace(context, enabled, hour, minute)
        }
    }
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

    /**
     * 当前订阅源列表。
     *
     * ⚠️ v2.5：从 `WhileSubscribed` 改成 `Eagerly`。
     * 原因：[isDuplicateUrl] 是**同步**读它的（加源页要在组合阶段判断「这个源加过没有」），
     * 而 `WhileSubscribed` 在没人订阅时 `.value` 会停在初始的**空列表** ——
     * 那时 `isDuplicateUrl` 会恒为 false，已经加过的源被当成新源重复添加。
     * 以前之所以没暴露，纯粹是因为 `AddSourceScreen` 恰好也订阅了这个流，
     * 「正确性靠调用方凑巧订阅维持」是脆的，不能留。
     * （同一个坑 v2.0.2 在 `HomeViewModel.sources` 上踩过一次。）
     */
    val sources: StateFlow<List<FeedSource>> = settings.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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

    // ==================== v2.0.2 / v2.5：首页筛选 ====================
    //
    // v2.5：原来那把三档开关（分类 / 阅源 / 都显示）拆成了两个独立开关。
    // 三档本身就是在描述「两行各自要不要显示」，拆开之后四种组合都能表达，
    // 而且和旁边的「显示副标题」「显示关键词行」是同一套心智模型。
    // 老数据的兼容在 [com.example.yuewen.data.util.HomeRows] 里，有离线测试钉着。

    /** 首页显不显示「分类」那一行。 */
    val homeShowCategoryRow: StateFlow<Boolean> = settings.homeShowCategoryRowFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 首页显不显示「阅源名称」那一行。 */
    val homeShowSourceRow: StateFlow<Boolean> = settings.homeShowSourceRowFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 打开 App 时默认停在的分类（「推荐」= 全部）。 */
    val homeDefaultCategory: StateFlow<String> = settings.homeDefaultCategoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "推荐")

    /** 打开 App 时默认只看哪个阅源（空 = 全部阅源）。 */
    val homeDefaultSource: StateFlow<String> = settings.homeDefaultSourceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setHomeShowCategoryRow(v: Boolean) = viewModelScope.launch { settings.setHomeShowCategoryRow(v) }
    fun setHomeShowSourceRow(v: Boolean) = viewModelScope.launch { settings.setHomeShowSourceRow(v) }
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
    fun setQueueAutoDequeue(v: Boolean) = viewModelScope.launch { settings.setQueueAutoDequeue(v) }
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
     * 导出完整备份：订阅源 + （收藏 ∪ 稍后读）+ 笔记 + 个性化设置，写成一个 JSON 文件。
     * 返回 (源数, 收藏与队列总数, 笔记数)。
     *
     * ⚠️ v2.8：这里取的是 `allSaved()` 而不是以前的 `allBookmarks()`。
     * 队列项里有一部分并没有被收藏，只查收藏会把它们整个漏掉（换机后才看得出来）。
     */
    suspend fun writeBackup(uri: Uri): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        val payload = buildBackupPayload()
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(payload.json.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        payload.counts
    }

    /**
     * 渲染一份备份。
     *
     * v2.9：**四条写入路径（文件 / Gist / WebDAV / 本地文件夹）共用这一段**。
     * 各自再抄一份的话，早晚会出现「导出的文件里有队列内容、云备份漏了」这种不一致 ——
     * 而那种问题只有等用户换机恢复之后才会发现，那时本地数据可能已经没了。
     */
    private suspend fun buildBackupPayload(): BackupPayload {
        val sources = settings.getSources()
        val saved = repo.allSaved()
        val notes = repo.allNotes()
        return BackupPayload(
            json = Backup.export(sources, saved, notes, settings.snapshotSettings()),
            counts = Triple(sources.size, saved.size, notes.size)
        )
    }

    private class BackupPayload(val json: String, val counts: Triple<Int, Int, Int>)

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
        applyBundle(bundle)
    }

    /**
     * 把一份已经解析好的备份并进本机。**只加不减**。
     *
     * v2.9：文件和三条云通道共用这一段 —— 「怎么合并」这件事只能有一份实现，
     * 否则「从文件恢复」和「从网盘恢复」会慢慢长成两套行为，
     * 用户会遇到「同样的备份，从文件导进来是对的，从 Gist 拉下来少了东西」。
     */
    private suspend fun applyBundle(bundle: Backup.Bundle): Triple<Int, Int, Int> {
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
        return Triple(addedSources, bundle.bookmarks.size, bundle.notes.size)
    }

    /**
     * 备份用：一句话描述本机现在有多少东西（导出前展示）。
     *
     * v2.8：收藏和稍后读分开报数 —— 两条本来就不等长，
     * 合成一个「N 篇收藏」会让用户以为队列里的也算进收藏了。
     */
    suspend fun backupSummary(): String = withContext(Dispatchers.IO) {
        val s = settings.getSources().size
        val b = runCatching { repo.observeBookmarks().first().size }.getOrDefault(0)
        val q = runCatching { repo.allQueued().size }.getOrDefault(0)
        val n = runCatching { repo.noteCount() }.getOrDefault(0)
        "$s 个阅源 · $b 篇收藏 · $q 篇稍后读 · $n 条笔记"
    }

    // ==================== v2.9：云备份（三条通道） ====================

    /**
     * 备份到指定通道。
     *
     * @return 给用户看的一句话
     * @throws BackupException 失败时抛出一句**能直接展示的中文**
     */
    suspend fun cloudBackup(target: BackupTarget): String = withContext(Dispatchers.IO) {
        val payload = buildBackupPayload()
        val (s, b, n) = payload.counts
        if (s + b + n == 0) throw BackupException("本机还没有可备份的内容")

        val name = BackupSpan.autoName(System.currentTimeMillis())
        val keep = settings.backupKeepFlow.first()

        when (target) {
            BackupTarget.GIST -> {
                val token = settings.backupGistTokenFlow.first()
                if (token.isBlank()) throw BackupException("还没有填 GitHub 令牌")
                // 第一次备份会自动新建 Gist，拿回来的 id **必须存下来** ——
                // 不存的话下次又会新建一个，用户 GitHub 上会攒出一串没人管的 Gist。
                val id = RemoteBackup.gistBackup(
                    token, settings.backupGistIdFlow.first(), name, payload.json
                )
                settings.setBackupGistId(id)
            }

            BackupTarget.WEBDAV -> {
                val (base, user, pass) = davConfig()
                RemoteBackup.davBackup(base, user, pass, name, payload.json)
            }

            BackupTarget.LOCAL -> {
                LocalBackup.write(context.contentResolver, localTreeUri(), name, payload.json)
            }
        }

        // 清理旧的。
        // ⚠️ 清不掉**不算这次备份失败**：数据已经安全上传了，
        // 因为「删旧文件没删掉」而报一句「备份失败」，用户会以为东西没上去、
        // 然后再备份一次 —— 那才是真的把目录堆乱。
        val pruned = runCatching { pruneOld(target, keep) }.getOrDefault(0)

        settings.setBackupLastAt(System.currentTimeMillis())
        "已备份到${target.label}：$s 个阅源、$b 篇收藏、$n 条笔记" +
                if (pruned > 0) "（顺带清掉 $pruned 份旧的）" else ""
    }

    /**
     * 从指定通道恢复**最新的一份**备份。
     *
     * 只恢复最新那份是刻意的：界面上不给「选哪一份」是减少一处要维护的选择界面，
     * 而用户真正会后悔的场景（「今天把收藏删空了」）要的是最近一次，
     * 不是三周前那次。真要回更早的，把文件下载下来用「从文件恢复」。
     */
    suspend fun cloudRestore(target: BackupTarget): String = withContext(Dispatchers.IO) {
        val text = when (target) {
            BackupTarget.GIST -> {
                val token = settings.backupGistTokenFlow.first()
                if (token.isBlank()) throw BackupException("还没有填 GitHub 令牌")
                val id = settings.backupGistIdFlow.first()
                if (id.isBlank()) throw BackupException("还没有备份过，没有可恢复的内容")
                RemoteBackup.gistFetchLatest(token, id)?.second
            }

            BackupTarget.WEBDAV -> {
                val (base, user, pass) = davConfig()
                val newest = BackupSpan.newestFirst(RemoteBackup.davNames(base, user, pass)).firstOrNull()
                newest?.let { RemoteBackup.davFetch(base, user, pass, it) }
            }

            BackupTarget.LOCAL -> {
                val tree = localTreeUri()
                val newest = BackupSpan.newestFirst(LocalBackup.listNames(context.contentResolver, tree))
                    .firstOrNull()
                newest?.let { LocalBackup.read(context.contentResolver, tree, it) }
            }
        } ?: throw BackupException("${target.label}上还没有备份")

        val bundle = Backup.parse(text)
            ?: throw BackupException("那份文件不是阅闻的备份（可能被别的程序覆盖过）")
        val (s, b, n) = applyBundle(bundle)
        if (s + b + n == 0) "备份是空的，本机内容没有变化"
        else "已恢复：新增 $s 个阅源、$b 篇收藏、$n 条笔记"
    }

    /**
     * 「这条通道现在什么情况」——设置页那一行下面的小字。
     *
     * **绝不抛异常**：它只是个展示用的状态，网络不通时显示一句「读不出来」就好，
     * 不该让整个设置页跟着炸。
     */
    suspend fun cloudStatus(target: BackupTarget): String = withContext(Dispatchers.IO) {
        runCatching {
            when (target) {
                BackupTarget.GIST -> {
                    if (settings.backupGistTokenFlow.first().isBlank()) return@runCatching "未配置"
                    val id = settings.backupGistIdFlow.first()
                    if (id.isBlank()) return@runCatching "已填令牌，首次备份会自动新建 Gist"
                    describeRemote(RemoteBackup.gistNames(settings.backupGistTokenFlow.first(), id))
                }

                BackupTarget.WEBDAV -> {
                    if (settings.backupDavUrlFlow.first().isBlank()) return@runCatching "未配置"
                    val (base, user, pass) = davConfig()
                    describeRemote(RemoteBackup.davNames(base, user, pass))
                }

                BackupTarget.LOCAL -> {
                    val raw = settings.backupLocalTreeFlow.first()
                    if (raw.isBlank()) return@runCatching "未配置"
                    describeRemote(
                        LocalBackup.listNames(context.contentResolver, Uri.parse(raw))
                    )
                }
            }
        }.getOrElse { "读不出来：" + (it.message ?: "网络或权限问题") }
    }

    private fun describeRemote(names: List<String>): String {
        val mine = BackupSpan.newestFirst(names)
        if (mine.isEmpty()) return "还没有备份"
        val whenText = BackupSpan.displayOf(mine.first()) ?: return "已有 ${mine.size} 份"
        return "已有 ${mine.size} 份，最近 $whenText"
    }

    /** 按保留份数清掉最旧的那几份。返回清掉的数量。 */
    private suspend fun pruneOld(target: BackupTarget, keep: Int): Int {
        val doomed = when (target) {
            BackupTarget.GIST -> {
                val token = settings.backupGistTokenFlow.first()
                val id = settings.backupGistIdFlow.first()
                val list = BackupSpan.toPrune(RemoteBackup.gistNames(token, id), keep)
                RemoteBackup.gistDelete(token, id, list)
                list
            }

            BackupTarget.WEBDAV -> {
                val (base, user, pass) = davConfig()
                val list = BackupSpan.toPrune(RemoteBackup.davNames(base, user, pass), keep)
                RemoteBackup.davDelete(base, user, pass, list)
                list
            }

            BackupTarget.LOCAL -> {
                val tree = localTreeUri()
                val list = BackupSpan.toPrune(LocalBackup.listNames(context.contentResolver, tree), keep)
                LocalBackup.delete(context.contentResolver, tree, list)
                list
            }
        }
        return doomed.size
    }

    /** 读 WebDAV 配置，顺手把地址收拾干净（地址不合法就直接报中文，不留到网络层）。 */
    private suspend fun davConfig(): Triple<String, String, String> {
        val raw = settings.backupDavUrlFlow.first()
        if (raw.isBlank()) throw BackupException("还没有填 WebDAV 地址")
        val base = Remote.normalizeBase(raw)
            ?: throw BackupException("WebDAV 地址格式不对，例如 https://dav.jianguoyun.com/dav/yuewen")
        return Triple(base, settings.backupDavUserFlow.first(), settings.backupDavPassFlow.first())
    }

    private suspend fun localTreeUri(): Uri {
        val raw = settings.backupLocalTreeFlow.first()
        if (raw.isBlank()) throw BackupException("还没有选备份文件夹")
        return Uri.parse(raw)
    }

    /** 记住用户选的本地备份目录，并把授权**长期化**（不然重启后就失效了）。 */
    suspend fun setLocalBackupTree(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        settings.setBackupLocalTree(uri.toString())
    }

    // ---- v2.9：云备份的界面状态 ----

    val backupKeep: StateFlow<Int> = settings.backupKeepFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupSpan.KEEP_DEFAULT)

    val backupGistToken: StateFlow<String> = settings.backupGistTokenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val backupGistId: StateFlow<String> = settings.backupGistIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val backupDavUrl: StateFlow<String> = settings.backupDavUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val backupDavUser: StateFlow<String> = settings.backupDavUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val backupDavPass: StateFlow<String> = settings.backupDavPassFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** 三条通道的状态一次性读出来（设置页展开云备份面板时调用）。 */
    suspend fun cloudStatuses(): Map<BackupTarget, String> =
        BackupTarget.values().associateWith { cloudStatus(it) }

    fun setBackupKeep(v: Int) {
        viewModelScope.launch { settings.setBackupKeep(v) }
    }

    /**
     * 存 Gist 配置。
     *
     * ⚠️ 刻意做成 `suspend` 而不是「自己起一个协程就返回」：
     * 调用方（设置页）在保存后要立刻重新拉一次三通道状态去刷新那一行小字。
     * 如果这里 fire-and-forget，那次「重新拉」会和这里的写库赛跑 ——
     * 读到的是写之前的旧值，界面就一直显示「未配置」，用户以为没保存上。
     */
    suspend fun saveGistConfig(token: String, gistId: String) {
        settings.setBackupGistToken(token)
        settings.setBackupGistId(gistId)
    }

    /** 存 WebDAV 配置。`suspend` 的理由同 [saveGistConfig]。 */
    suspend fun saveDavConfig(url: String, user: String, pass: String) {
        settings.setBackupDavUrl(url)
        settings.setBackupDavUser(user)
        settings.setBackupDavPass(pass)
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
