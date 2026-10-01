package com.example.yuewen.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.yuewen.data.model.FeedCatalog
import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.model.sanitizeSources
import com.example.yuewen.data.util.DEFAULT_HOME_KEYWORDS
import com.example.yuewen.data.util.sanitizeKeywords
import com.example.yuewen.ui.theme.DEFAULT_CUSTOM_HUE
import com.example.yuewen.ui.theme.DEFAULT_CUSTOM_SAT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 设置层：所有偏好都存在 DataStore 里。
 * - theme: system / light / dark
 * - font: small / standard / large
 * - category: 当前选中的首页分类
 * - sources: 新闻源列表（自定义序列化，避免引入额外序列化库）
 * - refreshMinutes: 周期刷新频率（0=关闭）
 * - autoread: 打开文章自动标为已读
 * - notify: 新内容提醒
 * - recent: 最近搜索词
 * - seeded: 是否已写入默认源（避免用户删空源后被重新播种）
 * - blockedSources: 屏蔽的源（按 sourceName）
 * - blockedKeywords: 屏蔽的关键词
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_FONT = stringPreferencesKey("font")
    private val KEY_CATEGORY = stringPreferencesKey("category")
    private val KEY_SOURCES = stringPreferencesKey("sources")
    private val KEY_REFRESH = intPreferencesKey("refresh_minutes")
    private val KEY_AUTOREAD = booleanPreferencesKey("autoread")
    private val KEY_NOTIFY = booleanPreferencesKey("notify")
    private val KEY_RECENT = stringPreferencesKey("recent")
    private val KEY_SEEDED = booleanPreferencesKey("seeded")
    private val KEY_BLOCKED_SOURCES = stringPreferencesKey("blocked_sources")
    private val KEY_BLOCKED_KEYWORDS = stringPreferencesKey("blocked_keywords")
    private val KEY_LAST_REFRESH = longPreferencesKey("last_refresh")

    // ---- 文章列表与阅读器偏好（v1.4 新增）----
    private val KEY_LIST_MODE = stringPreferencesKey("list_mode")
    private val KEY_READER_THEME = stringPreferencesKey("reader_theme")
    private val KEY_READER_FONT = stringPreferencesKey("reader_font")
    private val KEY_READER_SPACING = stringPreferencesKey("reader_spacing")
    private val KEY_READER_SIZE = intPreferencesKey("reader_size")

    // ---- v1.5 新增 ----
    /** 阅读文章时是否仍然显示底部导航栏。 */
    private val KEY_SHOW_BAR_IN_READER = booleanPreferencesKey("show_bar_in_reader")
    /** 打开 App 时是否自动刷新一次。 */
    private val KEY_REFRESH_ON_LAUNCH = booleanPreferencesKey("refresh_on_launch")

    /**
     * v1.6.1：底栏「首页」图标上是否显示未读数字。
     *
     * 默认 **false** —— 数字徽标在胶囊上会把图标顶偏、也让底栏显得吵；
     * 想要的人可以在设置里自己打开。
     */
    private val KEY_UNREAD_BADGE = booleanPreferencesKey("unread_badge")

    /**
     * v1.6：收藏夹的显示顺序。
     *
     * 收藏夹本身不是独立表，只是文章上的 folder 字段；
     * 「排序」这种纯展示偏好没必要为它建表，存一行顺序字符串就够。
     * 库里查出来的、但这里没记录的夹会自动追加到末尾（见 BookmarksViewModel）。
     */
    private val KEY_FOLDER_ORDER = stringPreferencesKey("folder_order")

    /** v1.7：分享卡片右下角的自定义署名（空 = 用 ShareCard.DEFAULT_FOOTER）。 */
    private val KEY_SHARE_FOOTER = stringPreferencesKey("share_footer")

    /**
     * v1.8：首页顶部是否显示「仅看未读」「全部标为已读」两个图标。
     *
     * 默认 **true** —— 正好是「默认显示全部（不筛选）」的意思：
     * 图标在，但筛选状态是「全部」。不需要的人可以在设置里整组藏掉，顶栏更清爽。
     */
    private val KEY_HOME_FILTER_ICONS = booleanPreferencesKey("home_filter_icons")

    /**
     * v1.9：离线阅读 —— 刷新后是否自动把文章正文抓下来缓存到本地。
     *
     * 默认 **false**：这是拿流量换「离线可读」的取舍，不该替用户默认打开。
     * 打开后，网络不好或完全没网时，文章也能读到完整的正文而不是只有一句摘要。
     */
    private val KEY_PRELOAD_AUTO = booleanPreferencesKey("preload_auto")

    /**
     * v1.9：仅在 Wi-Fi 下预加载。
     *
     * 默认 **true** —— 抓正文是逐篇请求第三方网页，比抓 RSS 重得多，
     * 不该在流量上偷跑。
     */
    private val KEY_PRELOAD_WIFI_ONLY = booleanPreferencesKey("preload_wifi_only")

    /**
     * v1.9：朗读语速档位（0 慢 / 1 标准 / 2 快 / 3 很快）。
     * 存档位序号而不是浮点数：以后要调具体数值时，用户的选择不会跑偏。
     */
    private val KEY_TTS_RATE = intPreferencesKey("tts_rate")

    // ==================== v2.0：个性化 / 首页 / 朗读通知 ====================

    /**
     * 应用在**界面里**显示的名字（首页标题、关于页、分享卡片默认署名）。
     *
     * 默认空 = 用内置名「阅闻」。桌面图标上的名字是另一回事
     * （那个受系统限制，只能切预设，见 README / 设置页说明）。
     */
    private val KEY_APP_TITLE = stringPreferencesKey("app_title")

    /** 桌面图标名称的预设下标（对应 `IconNames` 列表，靠 activity-alias 切换）。 */
    private val KEY_ICON_NAME = intPreferencesKey("icon_name_idx")

    /** 首页顶栏「布局切换」按钮是否显示（默认显示）。 */
    private val KEY_HOME_SHOW_LAYOUT = booleanPreferencesKey("home_show_layout")

    /** 首页顶栏「刷新」按钮是否显示（默认显示）。 */
    private val KEY_HOME_SHOW_REFRESH = booleanPreferencesKey("home_show_refresh")

    /** 首页顶栏是否显示副标题（「更新于 xx · N 篇未读」，默认显示）。 */
    private val KEY_HOME_SHOW_SUBTITLE = booleanPreferencesKey("home_show_subtitle")

    /** 「闻件」页上次停留的子页（0 搜索 / 1 收藏 / 2 历史 / 3 笔记）。 */
    private val KEY_WENJIAN_TAB = intPreferencesKey("wenjian_tab")

    /** 朗读时是否发通知（锁屏 / 通知栏可暂停、停止、回到文章），默认开。 */
    private val KEY_TTS_NOTIFY = booleanPreferencesKey("tts_notify")

    /** 新手指南是否已读过（首次进来可以在设置里高亮提醒一下）。 */
    private val KEY_GUIDE_SEEN = booleanPreferencesKey("guide_seen")

    // ==================== v2.0.2：首页筛选（分类 / 阅源） ====================

    /**
     * 首页顶栏胶囊用哪种维度筛选：`category` 分类 / `source` 阅源 / `both` 两行都显示。
     *
     * 默认 `category` —— 和以前完全一样，不打开这个设置的人感觉不到变化。
     */
    private val KEY_HOME_CHIP_MODE = stringPreferencesKey("home_chip_mode")

    /** 打开 App 时默认停在哪个分类（`推荐` = 不限分类）。 */
    private val KEY_HOME_DEFAULT_CATEGORY = stringPreferencesKey("home_default_category")

    /** 打开 App 时默认只看哪个阅源（空 = 全部阅源）。 */
    private val KEY_HOME_DEFAULT_SOURCE = stringPreferencesKey("home_default_source")

    /** 默认源播种版本：用于给老用户增量补种新推荐的源，而不动他手动加的那些。 */
    private val KEY_SEED_VERSION = intPreferencesKey("seed_version")

    /**
     * RSSHub 实例地址（v2.1）。
     *
     * **存空串 = 用官方默认实例**（`https://rsshub.app`）。这样默认实例以后要是换了，
     * 没手动设过的人会自动跟着走，而不是被一个写死的历史值钉住。
     * 官方公共实例是限流的，所以这里做成可改：用户以后自建了实例，填一行就切过去。
     */
    private val KEY_RSSHUB_INSTANCE = stringPreferencesKey("rsshub_instance")

    // ==================== v2.2：打开原文用哪个浏览器 ====================

    /**
     * 用哪个浏览器打开原文（v2.2）。
     *
     * **存包名**（如 `com.android.chrome`），不是显示名 —— 显示名会被系统语言翻译，
     * 也可能两个浏览器重名。空串 = 「跟随系统」，也就是交给 Android 自己决定
     * （和加这个设置之前的行为完全一致）。
     *
     * 只存一个包名，不存标签：**标签是随时可以从 PackageManager 查出来的**，
     * 存一份就多一份可能过期的数据（用户换了语言 / 卸载重装后标签就旧了）。
     */
    private val KEY_BROWSER_PKG = stringPreferencesKey("browser_pkg")

    // ==================== v2.2：首页文章排序 ====================

    /**
     * 首页文章的排序方式（v2.2）。
     *
     * `time_desc`（默认，最新的在最前）/ `time_asc` / `random`（随机）/
     * `source`（按阅源名分组）/ `title`（按标题）。
     *
     * 默认 `time_desc` —— 和以前一样；老用户升级后看到的列表不会突然换个样子。
     */
    private val KEY_HOME_SORT = stringPreferencesKey("home_sort")

    /** 随机排序的「洗牌」种子：用户点一次「换一批」就换一个，列表才会真的重排。 */
    private val KEY_SHUFFLE_SEED = longPreferencesKey("shuffle_seed")

    // ==================== v2.3：配色方案 ====================

    /**
     * 选中的配色档位（v2.3）。
     *
     * 存的是 `ThemePalette.key`（如 `emerald` / `ocean` / `custom`）。
     * 默认 `emerald`（青绿）—— 和加这个功能之前的外观完全一致。
     */
    private val KEY_THEME_PALETTE = stringPreferencesKey("theme_palette")

    /** 自定义配色的色相（0..360）。只有 [KEY_THEME_PALETTE] = `custom` 时才起作用。 */
    private val KEY_CUSTOM_HUE = intPreferencesKey("custom_hue")

    /** 自定义配色的鲜艳度（0..100，100 = 最艳）。 */
    private val KEY_CUSTOM_SAT = intPreferencesKey("custom_sat")

    // ==================== v2.4：首页关键词胶囊 ====================

    /**
     * 首页顶栏那排「关键词胶囊」用的词（v2.4）。
     *
     * 存 `:::` 拼接串，和屏蔽列表 / 最近搜索一个格式。
     *
     * ⚠️ **「键不存在」和「键是空串」是两件不同的事**：
     * 前者 = 用户从来没碰过 → 用内置的 [DEFAULT_HOME_KEYWORDS]；
     * 后者 = 用户主动把词删光了 → 就是空列表（那一行不显示），
     * 不能再给他弹回默认词，否则会变成「删了又自己长出来」。
     * 所以读取时用的是 `prefs[KEY] ?: 默认值`，而不是 `isNullOrBlank()` 判断。
     */
    private val KEY_HOME_KEYWORDS = stringPreferencesKey("home_keywords")

    /** 首页是否显示关键词那一行（默认显示；一个词都没有时无论如何都不显示）。 */
    private val KEY_HOME_SHOW_KEYWORDS = booleanPreferencesKey("home_show_keywords")

    val themeFlow: Flow<String> = dataStore.data.map { it[KEY_THEME] ?: "system" }
    val fontFlow: Flow<String> = dataStore.data.map { it[KEY_FONT] ?: "standard" }
    val categoryFlow: Flow<String> = dataStore.data.map { it[KEY_CATEGORY] ?: "推荐" }
    val autoreadFlow: Flow<Boolean> = dataStore.data.map { it[KEY_AUTOREAD] ?: true }
    val notifyFlow: Flow<Boolean> = dataStore.data.map { it[KEY_NOTIFY] ?: true }
    val refreshMinutesFlow: Flow<Int> = dataStore.data.map { it[KEY_REFRESH] ?: 30 }
    val sourcesFlow: Flow<List<FeedSource>> = dataStore.data.map { decodeSources(it[KEY_SOURCES]) }
    val recentSearchesFlow: Flow<List<String>> = dataStore.data.map { decodeRecent(it[KEY_RECENT]) }
    val blockedSourcesFlow: Flow<List<String>> = dataStore.data.map { decodeList(it[KEY_BLOCKED_SOURCES]) }
    val blockedKeywordsFlow: Flow<List<String>> = dataStore.data.map { decodeList(it[KEY_BLOCKED_KEYWORDS]) }

    /** 上一次成功刷新的时间戳（0 = 从未刷新）。 */
    val lastRefreshFlow: Flow<Long> = dataStore.data.map { it[KEY_LAST_REFRESH] ?: 0L }

    // ---- 文章列表布局：compact（紧凑）/ card（卡片）/ magazine（杂志）----
    val listModeFlow: Flow<String> = dataStore.data.map { it[KEY_LIST_MODE] ?: "card" }

    // ---- 阅读器：底色 / 字体 / 行距 / 字号 ----
    val readerThemeFlow: Flow<String> = dataStore.data.map { it[KEY_READER_THEME] ?: "auto" }
    val readerFontFlow: Flow<String> = dataStore.data.map { it[KEY_READER_FONT] ?: "sans" }
    val readerSpacingFlow: Flow<String> = dataStore.data.map { it[KEY_READER_SPACING] ?: "normal" }
    val readerSizeFlow: Flow<Int> = dataStore.data.map { it[KEY_READER_SIZE] ?: 1 }

    // ---- v1.5：默认都打开（阅读时看得到底栏、每次打开自动刷新）----
    val showBarInReaderFlow: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_BAR_IN_READER] ?: true }
    val refreshOnLaunchFlow: Flow<Boolean> = dataStore.data.map { it[KEY_REFRESH_ON_LAUNCH] ?: true }

    // ---- v1.6.1：未读数字徽标，默认关闭 ----
    val unreadBadgeFlow: Flow<Boolean> = dataStore.data.map { it[KEY_UNREAD_BADGE] ?: false }

    // ---- v1.8：首页顶部筛选图标，默认显示 ----
    val homeFilterIconsFlow: Flow<Boolean> = dataStore.data.map { it[KEY_HOME_FILTER_ICONS] ?: true }

    // ---- v1.9：离线预加载（默认关，且默认只在 Wi-Fi 下跑）/ 朗读语速 ----
    val preloadAutoFlow: Flow<Boolean> = dataStore.data.map { it[KEY_PRELOAD_AUTO] ?: false }
    val preloadWifiOnlyFlow: Flow<Boolean> = dataStore.data.map { it[KEY_PRELOAD_WIFI_ONLY] ?: true }
    val ttsRateFlow: Flow<Int> = dataStore.data.map { it[KEY_TTS_RATE] ?: 1 }

    suspend fun setPreloadAuto(v: Boolean) = dataStore.edit { it[KEY_PRELOAD_AUTO] = v }
    suspend fun setPreloadWifiOnly(v: Boolean) = dataStore.edit { it[KEY_PRELOAD_WIFI_ONLY] = v }
    suspend fun setTtsRate(v: Int) = dataStore.edit { it[KEY_TTS_RATE] = v }

    // ==================== v2.0 ====================

    /** 界面里显示的应用名（空 = 用内置「阅闻」）。 */
    val appTitleFlow: Flow<String> = dataStore.data.map { it[KEY_APP_TITLE] ?: "" }

    val iconNameFlow: Flow<Int> = dataStore.data.map { it[KEY_ICON_NAME] ?: 0 }
    val homeShowLayoutFlow: Flow<Boolean> = dataStore.data.map { it[KEY_HOME_SHOW_LAYOUT] ?: true }
    val homeShowRefreshFlow: Flow<Boolean> = dataStore.data.map { it[KEY_HOME_SHOW_REFRESH] ?: true }
    val homeShowSubtitleFlow: Flow<Boolean> = dataStore.data.map { it[KEY_HOME_SHOW_SUBTITLE] ?: true }
    val wenjianTabFlow: Flow<Int> = dataStore.data.map { it[KEY_WENJIAN_TAB] ?: 0 }
    val ttsNotifyFlow: Flow<Boolean> = dataStore.data.map { it[KEY_TTS_NOTIFY] ?: true }
    val guideSeenFlow: Flow<Boolean> = dataStore.data.map { it[KEY_GUIDE_SEEN] ?: false }

    // ---- v2.0.2：首页筛选 ----
    val homeChipModeFlow: Flow<String> = dataStore.data.map { it[KEY_HOME_CHIP_MODE] ?: "category" }
    val homeDefaultCategoryFlow: Flow<String> = dataStore.data.map { it[KEY_HOME_DEFAULT_CATEGORY] ?: "推荐" }
    val homeDefaultSourceFlow: Flow<String> = dataStore.data.map { it[KEY_HOME_DEFAULT_SOURCE] ?: "" }

    /** RSSHub 实例地址；空串表示「用官方默认实例」。 */
    val rssHubInstanceFlow: Flow<String> = dataStore.data.map { it[KEY_RSSHUB_INSTANCE] ?: "" }

    suspend fun setRssHubInstance(v: String) = dataStore.edit { it[KEY_RSSHUB_INSTANCE] = v.trim() }

    /** 打开原文用哪个浏览器（包名）；空串 = 跟随系统。 */
    val browserPkgFlow: Flow<String> = dataStore.data.map { it[KEY_BROWSER_PKG] ?: "" }

    suspend fun setBrowserPkg(v: String) = dataStore.edit { it[KEY_BROWSER_PKG] = v.trim() }

    /**
     * 首页文章排序方式（见 [KEY_HOME_SORT]）。
     *
     * ⚠️ 用 `Eagerly` 语义读它 —— `HomeViewModel` 里要靠同步值算排序，
     * 别再犯 `WhileSubscribed` 那个「没人订阅就停在初值」的错。
     */
    val homeSortFlow: Flow<String> = dataStore.data.map { it[KEY_HOME_SORT] ?: "time_desc" }

    /** 随机排序的洗牌种子。 */
    val shuffleSeedFlow: Flow<Long> = dataStore.data.map { it[KEY_SHUFFLE_SEED] ?: 0L }

    suspend fun setHomeSort(v: String) = dataStore.edit { it[KEY_HOME_SORT] = v }

    /** 换一个种子 = 重新洗一次牌（「换一批」按钮）。 */
    suspend fun reshuffle() = dataStore.edit { it[KEY_SHUFFLE_SEED] = System.currentTimeMillis() }

    // ---- v2.3：配色方案 ----
    val themePaletteFlow: Flow<String> = dataStore.data.map { it[KEY_THEME_PALETTE] ?: "emerald" }

    /**
     * 自定义色相（0..360）。
     *
     * 越界值在这里就夹好：UI 上万一拖出范围，也不该把生成器喂成奇怪的颜色。
     */
    val customHueFlow: Flow<Int> = dataStore.data.map { (it[KEY_CUSTOM_HUE] ?: DEFAULT_CUSTOM_HUE).coerceIn(0, 360) }

    val customSatFlow: Flow<Int> = dataStore.data.map { (it[KEY_CUSTOM_SAT] ?: DEFAULT_CUSTOM_SAT).coerceIn(0, 100) }

    suspend fun setThemePalette(v: String) = dataStore.edit { it[KEY_THEME_PALETTE] = v }

    suspend fun setCustomHue(v: Int) = dataStore.edit { it[KEY_CUSTOM_HUE] = v.coerceIn(0, 360) }

    suspend fun setCustomSat(v: Int) = dataStore.edit { it[KEY_CUSTOM_SAT] = v.coerceIn(0, 100) }

    // ---- v2.4：首页关键词 ----

    /**
     * 首页关键词胶囊。
     *
     * ⚠️ 默认值只能挂在 `prefs[KEY]` 的 **null** 上（见 [KEY_HOME_KEYWORDS] 的注释）：
     * 用户删光之后存的是空串，那时必须老实返回空列表 —— 于是首页那一行不显示。
     */
    val homeKeywordsFlow: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_HOME_KEYWORDS] ?: return@map DEFAULT_HOME_KEYWORDS
        sanitizeKeywords(decodeList(raw))
    }

    val homeShowKeywordsFlow: Flow<Boolean> = dataStore.data.map { it[KEY_HOME_SHOW_KEYWORDS] ?: true }

    /**
     * 加一个首页关键词。
     *
     * 读和写都在 `edit` 回调内部（v2.3 起的老规矩：分开写会丢更新）。
     * 键还没写过时**以内置默认词为起点** —— 否则用户加第一个词时，
     * 屏幕上那几个默认词会莫名其妙一起消失。
     */
    suspend fun addHomeKeyword(kw: String) {
        if (kw.isBlank()) return
        dataStore.edit { prefs ->
            val base = prefs[KEY_HOME_KEYWORDS]?.let(::decodeList) ?: DEFAULT_HOME_KEYWORDS
            prefs[KEY_HOME_KEYWORDS] = sanitizeKeywords(base + kw).joinToString(":::")
        }
    }

    /** 删一个首页关键词（忽略大小写匹配，和 [sanitizeKeywords] 的去重规则一致）。 */
    suspend fun removeHomeKeyword(kw: String) {
        dataStore.edit { prefs ->
            val base = prefs[KEY_HOME_KEYWORDS]?.let(::decodeList) ?: DEFAULT_HOME_KEYWORDS
            prefs[KEY_HOME_KEYWORDS] = base.filter { !it.equals(kw, true) }.joinToString(":::")
        }
    }

    suspend fun setHomeShowKeywords(v: Boolean) = dataStore.edit { it[KEY_HOME_SHOW_KEYWORDS] = v }

    suspend fun setHomeChipMode(v: String) = dataStore.edit { it[KEY_HOME_CHIP_MODE] = v }
    suspend fun setHomeDefaultCategory(v: String) = dataStore.edit { it[KEY_HOME_DEFAULT_CATEGORY] = v }
    suspend fun setHomeDefaultSource(v: String) = dataStore.edit { it[KEY_HOME_DEFAULT_SOURCE] = v }

    suspend fun setAppTitle(v: String) = dataStore.edit { it[KEY_APP_TITLE] = v.trim().take(12) }
    suspend fun setIconNameIndex(v: Int) = dataStore.edit { it[KEY_ICON_NAME] = v }
    suspend fun setHomeShowLayout(v: Boolean) = dataStore.edit { it[KEY_HOME_SHOW_LAYOUT] = v }
    suspend fun setHomeShowRefresh(v: Boolean) = dataStore.edit { it[KEY_HOME_SHOW_REFRESH] = v }
    suspend fun setHomeShowSubtitle(v: Boolean) = dataStore.edit { it[KEY_HOME_SHOW_SUBTITLE] = v }
    suspend fun setWenjianTab(v: Int) = dataStore.edit { it[KEY_WENJIAN_TAB] = v }
    suspend fun setTtsNotify(v: Boolean) = dataStore.edit { it[KEY_TTS_NOTIFY] = v }
    suspend fun setGuideSeen(v: Boolean) = dataStore.edit { it[KEY_GUIDE_SEEN] = v }

    suspend fun setShowBarInReader(v: Boolean) = dataStore.edit { it[KEY_SHOW_BAR_IN_READER] = v }
    suspend fun setRefreshOnLaunch(v: Boolean) = dataStore.edit { it[KEY_REFRESH_ON_LAUNCH] = v }
    suspend fun setUnreadBadge(v: Boolean) = dataStore.edit { it[KEY_UNREAD_BADGE] = v }
    suspend fun setHomeFilterIcons(v: Boolean) = dataStore.edit { it[KEY_HOME_FILTER_ICONS] = v }

    // ---- 收藏夹顺序 ----
    val folderOrderFlow: Flow<List<String>> = dataStore.data.map { decodeList(it[KEY_FOLDER_ORDER]) }

    suspend fun setFolderOrder(list: List<String>) =
        dataStore.edit { it[KEY_FOLDER_ORDER] = list.joinToString(":::") }

    // ---- 分享卡片署名 ----
    val shareFooterFlow: Flow<String> = dataStore.data.map { it[KEY_SHARE_FOOTER] ?: "" }

    suspend fun setShareFooter(v: String) = dataStore.edit { it[KEY_SHARE_FOOTER] = v }

    suspend fun setLastRefresh(ts: Long) = dataStore.edit { it[KEY_LAST_REFRESH] = ts }
    suspend fun setListMode(v: String) = dataStore.edit { it[KEY_LIST_MODE] = v }
    suspend fun setReaderTheme(v: String) = dataStore.edit { it[KEY_READER_THEME] = v }
    suspend fun setReaderFont(v: String) = dataStore.edit { it[KEY_READER_FONT] = v }
    suspend fun setReaderSpacing(v: String) = dataStore.edit { it[KEY_READER_SPACING] = v }
    suspend fun setReaderSize(v: Int) = dataStore.edit { it[KEY_READER_SIZE] = v }

    suspend fun setTheme(v: String) = dataStore.edit { it[KEY_THEME] = v }
    suspend fun setFont(v: String) = dataStore.edit { it[KEY_FONT] = v }
    suspend fun setCategory(v: String) = dataStore.edit { it[KEY_CATEGORY] = v }
    suspend fun setNotify(v: Boolean) = dataStore.edit { it[KEY_NOTIFY] = v }
    suspend fun setRefreshMinutes(v: Int) = dataStore.edit { it[KEY_REFRESH] = v }
    suspend fun setAutoRead(v: Boolean) = dataStore.edit { it[KEY_AUTOREAD] = v }

    // ---- 屏蔽管理 ----
    //
    // v2.3：这几个方法以前是「先 flow.first() 读出来 → 改 → 再单独 edit 写回去」，
    // 读和写被拆成了两段，中间没有任何互斥。连点两次「屏蔽此来源」时，
    // 第二次会基于第一次还没写回去的旧列表计算，后写覆盖先写 → 少一个屏蔽项，
    // 用户看到「明明屏蔽了却还在显示」。
    //
    // 现在把「读」挪进 edit 回调内部：DataStore 的 edit 本身是原子的，
    // 读改写落在同一个事务里，就不存在这个窗口了。

    private suspend fun editList(key: Preferences.Key<String>, transform: (List<String>) -> List<String>) {
        dataStore.edit { prefs ->
            val cur = decodeList(prefs[key])
            prefs[key] = transform(cur).joinToString(":::")
        }
    }

    suspend fun addBlockedSource(name: String) {
        if (name.isBlank()) return
        editList(KEY_BLOCKED_SOURCES) { if (it.contains(name)) it else it + name }
    }

    suspend fun removeBlockedSource(name: String) {
        editList(KEY_BLOCKED_SOURCES) { it.filter { s -> s != name } }
    }

    suspend fun addBlockedKeyword(kw: String) {
        if (kw.isBlank()) return
        editList(KEY_BLOCKED_KEYWORDS) { if (it.contains(kw)) it else it + kw }
    }

    suspend fun removeBlockedKeyword(kw: String) {
        editList(KEY_BLOCKED_KEYWORDS) { it.filter { s -> s != kw } }
    }

    /**
     * 订阅源列表的写锁（v2.3）。
     *
     * 订阅列表是典型的「读出来 → 改 → 写回去」，而 DataStore 的读写都是异步的：
     * 两条路径同时改（比如「加源页点添加」撞上「后台恢复备份」），
     * 双方都基于同一份旧列表计算，后写的一方会把另一方新增的源**整体覆盖掉**，
     * 用户看到的现象是「刚订阅的源凭空消失」，而且没有任何报错。
     *
     * 锁挂在实例上而不是 companion 里：`SettingsRepository` 全 App 只有一个实例
     * （在 Application 里建好，所有 ViewModel 共用），所以设置页 / 阅源页 / RSSHub 浮层 /
     * 备份恢复 / OPML 导入这五条路径天然共用同一把锁 —— 不需要再各自维护。
     *
     * v2.3 之前只有 `SourcesViewModel` 内部加锁，`SettingsViewModel` 的三条路径是裸的，
     * 属于「一半上锁等于没上锁」。现在统一收敛到 [mutateSources]。
     */
    private val sourcesLock = Mutex()

    /**
     * 原子地改订阅源列表：**读 → 改 → 写** 全程持锁，写完顺带跑一遍 [sanitizeSources]
     * 去掉重复 id / 重复地址。
     *
     * @param edit 在锁内修改这个可变列表（加 / 删 / 改都行）
     */
    suspend fun mutateSources(edit: (MutableList<FeedSource>) -> Unit) {
        sourcesLock.withLock {
            val list = getSources().toMutableList()
            edit(list)
            setSources(sanitizeSources(list))
        }
    }

    suspend fun getSources(): List<FeedSource> =
        decodeSources(dataStore.data.first()[KEY_SOURCES])

    suspend fun setSources(list: List<FeedSource>) =
        dataStore.edit { it[KEY_SOURCES] = encodeSources(list) }

    /**
     * 首次启动写入默认免费新闻源（用 seeded 标志，用户删空源后不会被重新播种）。
     *
     * v2.0 起默认源不再写死在这，而是取 [FeedCatalog.starter]：
     * 12 个中文、国内可直连、更新勤的源，覆盖科技 / 财经 / 国际 / 开发 / 设计 ——
     * 装好下拉一次就有内容，不会对着一张白纸发愣。
     *
     * 同时给**老用户**做一次增量补种：早期版本只有 6 个默认源，
     * 如果用户基本还是原样（源很少），就补上新的推荐源；
     * 如果他已经自己配了一堆（≥ 8 个），说明他有自己的用法，不打扰。
     */
    suspend fun ensureSeeded() {
        val prefs = dataStore.data.first()
        val seeded = prefs[KEY_SEEDED] ?: false
        val seedVersion = prefs[KEY_SEED_VERSION] ?: 0

        if (!seeded) {
            dataStore.edit {
                it[KEY_SOURCES] = encodeSources(FeedCatalog.starter())
                it[KEY_SEEDED] = true
                it[KEY_SEED_VERSION] = CURRENT_SEED_VERSION
            }
            return
        }

        if (seedVersion < CURRENT_SEED_VERSION) {
            val current = decodeSources(prefs[KEY_SOURCES])
            val additions = if (current.size < 8) FeedCatalog.upgradeAdditions(current) else emptyList()
            dataStore.edit {
                if (additions.isNotEmpty()) it[KEY_SOURCES] = encodeSources(current + additions)
                it[KEY_SEED_VERSION] = CURRENT_SEED_VERSION
            }
        }
    }

    // ==================== v2.0：备份用设置快照 ====================

    /**
     * 取一份「可备份设置」快照（键名 → 字符串值）。
     *
     * 刻意用**白名单**而不是把整个 DataStore 倒出来：
     * 内部标志（seeded / seed_version / last_refresh）混在备份里，
     * 恢复到另一台机器上会带来莫名其妙的副作用。
     */
    suspend fun snapshotSettings(): Map<String, String> {
        val p = dataStore.data.first()
        val out = LinkedHashMap<String, String>()
        fun put(k: Preferences.Key<*>) {
            val v = p[k] ?: return
            // 集合类型（屏蔽列表 / 收藏夹顺序）统一转成 ":::" 拼接串，和存储格式一致
            out[k.name] = when (v) {
                is Set<*> -> v.filterIsInstance<String>().joinToString(":::")
                else -> v.toString()
            }
        }
        BACKUP_KEYS.forEach { put(it) }
        return out
    }

    /**
     * 把备份里的设置写回本机。**只认识白名单内的键**，其余一律忽略，
     * 免得一个手改过的备份文件把 App 的内部状态搞坏。
     */
    suspend fun applySettings(map: Map<String, String>) {
        if (map.isEmpty()) return
        dataStore.edit { prefs ->
            map.forEach { (name, value) ->
                when (name) {
                    KEY_THEME.name -> prefs[KEY_THEME] = value
                    KEY_FONT.name -> prefs[KEY_FONT] = value
                    KEY_LIST_MODE.name -> prefs[KEY_LIST_MODE] = value
                    KEY_READER_THEME.name -> prefs[KEY_READER_THEME] = value
                    KEY_READER_FONT.name -> prefs[KEY_READER_FONT] = value
                    KEY_READER_SPACING.name -> prefs[KEY_READER_SPACING] = value
                    KEY_READER_SIZE.name -> value.toIntOrNull()?.let { prefs[KEY_READER_SIZE] = it }
                    KEY_APP_TITLE.name -> prefs[KEY_APP_TITLE] = value
                    KEY_ICON_NAME.name -> value.toIntOrNull()?.let { prefs[KEY_ICON_NAME] = it }
                    KEY_SHARE_FOOTER.name -> prefs[KEY_SHARE_FOOTER] = value
                    KEY_FOLDER_ORDER.name -> prefs[KEY_FOLDER_ORDER] = value
                    KEY_RECENT.name -> prefs[KEY_RECENT] = value
                    KEY_BLOCKED_SOURCES.name -> prefs[KEY_BLOCKED_SOURCES] = value
                    KEY_BLOCKED_KEYWORDS.name -> prefs[KEY_BLOCKED_KEYWORDS] = value
                    KEY_UNREAD_BADGE.name -> prefs[KEY_UNREAD_BADGE] = asBool(value)
                    KEY_HOME_FILTER_ICONS.name -> prefs[KEY_HOME_FILTER_ICONS] = asBool(value)
                    KEY_HOME_SHOW_LAYOUT.name -> prefs[KEY_HOME_SHOW_LAYOUT] = asBool(value)
                    KEY_HOME_SHOW_REFRESH.name -> prefs[KEY_HOME_SHOW_REFRESH] = asBool(value)
                    KEY_HOME_SHOW_SUBTITLE.name -> prefs[KEY_HOME_SHOW_SUBTITLE] = asBool(value)
                    KEY_SHOW_BAR_IN_READER.name -> prefs[KEY_SHOW_BAR_IN_READER] = asBool(value)
                    KEY_REFRESH_ON_LAUNCH.name -> prefs[KEY_REFRESH_ON_LAUNCH] = asBool(value)
                    KEY_HOME_CHIP_MODE.name -> prefs[KEY_HOME_CHIP_MODE] = value
                    KEY_HOME_DEFAULT_CATEGORY.name -> prefs[KEY_HOME_DEFAULT_CATEGORY] = value
                    KEY_HOME_DEFAULT_SOURCE.name -> prefs[KEY_HOME_DEFAULT_SOURCE] = value
                    KEY_RSSHUB_INSTANCE.name -> prefs[KEY_RSSHUB_INSTANCE] = value
                    KEY_AUTOREAD.name -> prefs[KEY_AUTOREAD] = asBool(value)
                    KEY_NOTIFY.name -> prefs[KEY_NOTIFY] = asBool(value)
                    KEY_PRELOAD_AUTO.name -> prefs[KEY_PRELOAD_AUTO] = asBool(value)
                    KEY_PRELOAD_WIFI_ONLY.name -> prefs[KEY_PRELOAD_WIFI_ONLY] = asBool(value)
                    KEY_TTS_RATE.name -> value.toIntOrNull()?.let { prefs[KEY_TTS_RATE] = it }
                    KEY_TTS_NOTIFY.name -> prefs[KEY_TTS_NOTIFY] = asBool(value)
                    // v2.3：配色方案跟着备份走 —— 换手机时外观习惯不该丢
                    KEY_THEME_PALETTE.name -> prefs[KEY_THEME_PALETTE] = value
                    KEY_CUSTOM_HUE.name -> value.toIntOrNull()?.let { prefs[KEY_CUSTOM_HUE] = it.coerceIn(0, 360) }
                    KEY_CUSTOM_SAT.name -> value.toIntOrNull()?.let { prefs[KEY_CUSTOM_SAT] = it.coerceIn(0, 100) }
                    // v2.4：首页关键词。恢复时先过一遍清洗，手改过的备份也进不来脏数据。
                    KEY_HOME_KEYWORDS.name -> prefs[KEY_HOME_KEYWORDS] =
                        sanitizeKeywords(decodeList(value)).joinToString(":::")
                    KEY_HOME_SHOW_KEYWORDS.name -> prefs[KEY_HOME_SHOW_KEYWORDS] = asBool(value)
                    KEY_REFRESH.name -> value.toIntOrNull()?.let { prefs[KEY_REFRESH] = it }
                }
            }
        }
    }

    suspend fun addRecentSearch(q: String) {
        if (q.isBlank()) return
        // 同样把「读」挪进 edit 里：连着搜两次时，第二次不会把第一次挤掉。
        dataStore.edit { prefs ->
            val list = decodeRecent(prefs[KEY_RECENT]).toMutableList()
            list.remove(q)
            list.add(0, q)
            prefs[KEY_RECENT] = list.take(8).joinToString(":::")
        }
    }

    /** 兼容旧调用：默认源现在统一来自 [FeedCatalog.starter]。 */
    fun defaultSources(): List<FeedSource> = FeedCatalog.starter()

    private fun encodeSources(list: List<FeedSource>): String =
        list.joinToString("\n") { "${it.id}:::${it.name}:::${it.url}:::${it.category}:::${it.enabled}" }

    private fun decodeSources(s: String?): List<FeedSource> {
        if (s.isNullOrBlank()) return emptyList()
        val raw = s.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split(":::")
            if (p.size >= 4) {
                FeedSource(
                    id = p[0],
                    name = p[1],
                    url = p[2],
                    category = p[3],
                    enabled = p.getOrElse(4) { "true" } != "false"
                )
            } else null
        }
        // v2.0.2：读的时候就顺手把历史脏数据修掉（id 重复 / 地址重复）。
        // 放在这里而不是只在写入时修：老用户一打开 App 就已经是干净的了。
        return sanitizeSources(raw)
    }

    private fun decodeRecent(s: String?): List<String> {
        if (s.isNullOrBlank()) return emptyList()
        return s.split(":::").filter { it.isNotBlank() }
    }

    private fun decodeList(s: String?): List<String> {
        if (s.isNullOrBlank()) return emptyList()
        return s.split(":::").filter { it.isNotBlank() }
    }

    /**
     * 宽松解析备份里的布尔值（v2.3）。
     *
     * 为什么不用 `String.toBoolean()`：那个只认忽略大小写的 `"true"`，
     * 于是备份里写成 `"1"` / `"yes"` / `"true "`（带空格）时会被判成 false ——
     * 恢复完用户会发现「未读红点 / 首页布局按钮 / 自动刷新 / 朗读通知栏」几个开关
     * **静默变成关闭**，还以为备份没恢复成功。
     *
     * 和 `Backup.kt` 的 `asBooleanOr` 保持一致的语义：只有明确的否定词才算 false，
     * 其余非空值（含 "1" / "yes" / "on"）都当 true；空串当 false。
     */
    internal fun asBool(v: String?): Boolean {
        val s = v?.trim()?.lowercase() ?: return false
        if (s.isEmpty()) return false
        return s !in setOf("false", "0", "no", "off", "null")
    }

    /** 参与备份的设置项白名单（顺序即备份文件里的顺序）。 */
    private val BACKUP_KEYS: List<Preferences.Key<*>> = listOf(
        KEY_THEME, KEY_FONT, KEY_CATEGORY, KEY_LIST_MODE,
        KEY_READER_THEME, KEY_READER_FONT, KEY_READER_SPACING, KEY_READER_SIZE,
        KEY_APP_TITLE, KEY_ICON_NAME, KEY_SHARE_FOOTER,
        KEY_FOLDER_ORDER, KEY_RECENT, KEY_BLOCKED_SOURCES, KEY_BLOCKED_KEYWORDS,
        KEY_UNREAD_BADGE, KEY_HOME_FILTER_ICONS,
        KEY_HOME_SHOW_LAYOUT, KEY_HOME_SHOW_REFRESH, KEY_HOME_SHOW_SUBTITLE,
        KEY_SHOW_BAR_IN_READER, KEY_REFRESH_ON_LAUNCH, KEY_AUTOREAD, KEY_NOTIFY,
        KEY_PRELOAD_AUTO, KEY_PRELOAD_WIFI_ONLY, KEY_TTS_RATE, KEY_TTS_NOTIFY, KEY_REFRESH,
        // v2.0.2：首页筛选也算「个性化设置」，跟着备份走
        KEY_HOME_CHIP_MODE, KEY_HOME_DEFAULT_CATEGORY, KEY_HOME_DEFAULT_SOURCE,
        // v2.1：RSSHub 实例。自建实例的地址是用户自己搭出来的东西，
        // 换手机时不跟过去会让人以为「自建的那个丢了」，所以进备份。
        KEY_RSSHUB_INSTANCE,
        // v2.2：默认浏览器 + 首页排序。都是「个性化设置」，跟着备份走。
        // ⚠️ **洗牌种子（KEY_SHUFFLE_SEED）刻意不进备份** —— 它只是个随机数，
        // 恢复备份的人没道理被继承别人的随机序列；落到默认 0 会重新洗一次牌。
        KEY_BROWSER_PKG, KEY_HOME_SORT,
        // v2.3：配色方案（含自定义色相/鲜艳度）。这是最典型的「个性化」，
        // 换设备丢掉的话用户会立刻发现外观变了。
        KEY_THEME_PALETTE, KEY_CUSTOM_HUE, KEY_CUSTOM_SAT,
        // v2.4：首页关键词。用户一个个敲进去的词，换手机不该重敲一遍。
        KEY_HOME_KEYWORDS, KEY_HOME_SHOW_KEYWORDS
    )

    private companion object {
        /** 默认源播种版本。加了新的推荐源就 +1（老用户会自动增量补种）。 */
        const val CURRENT_SEED_VERSION = 2
    }
}
