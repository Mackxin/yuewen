package com.example.yuewen.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yuewen.YuewenApplication
import com.example.yuewen.ui.components.YuewenBottomBar
import com.example.yuewen.ui.navigation.Screen
import com.example.yuewen.ui.screens.AboutScreen
import com.example.yuewen.ui.screens.AddSourceScreen
import com.example.yuewen.ui.screens.DetailScreen
import com.example.yuewen.ui.screens.GuideScreen
import com.example.yuewen.ui.screens.HomeScreen
import com.example.yuewen.ui.screens.RssHubScreen
import com.example.yuewen.ui.screens.SearchPane
import com.example.yuewen.ui.screens.SettingsScreen
import com.example.yuewen.ui.screens.SourcesScreen
import com.example.yuewen.ui.screens.StatsScreen
import com.example.yuewen.ui.screens.StorageScreen
import com.example.yuewen.ui.screens.WenjianScreen
import com.example.yuewen.ui.util.rememberImeDismiss
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 底部胶囊栏自身占用的高度（**不含系统导航栏**）。
 *
 * v1.9：胶囊本体 ≈ 34dp + 上下各 7dp 外边距 ≈ 48dp，BAR_INSET 从 70dp 收到 58dp。
 * v2.0：朗读悬浮条会临时叠在底栏之上，[MainScreen] 会在这个基础上再加一段高度。
 * v2.6：底栏底部外边距 7 → 13dp（用户要求「整体再往上走一点」），58 → 64dp。
 *
 * ⚠️ 沉浸式之后，页面底部的实际留白 = `BAR_INSET + 导航栏 inset`，
 * 所以要留空的地方一律用 [MainScreen] 里算好的 `contentInset`，别直接写这个常量。
 */
private val BAR_INSET = 64.dp

/** 「正在朗读」悬浮条自身的高度（含上下留白），用于动态加大页面底部空隙。 */
private val TTS_BAR_HEIGHT = 54.dp

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as YuewenApplication
    // 记住列表实例：原来每次重组都新建一份，白白带着底栏一起重组
    val tabs = remember { Screen.bottomTabs() }

    // v2.7：「液态玻璃」整套设计语言已按用户要求撤掉（Glass / GlassSurface 两个文件一并删除），
    // 这里不再有 glass 开关，顶栏与闻件标签栏都退回实色。

    /**
     * v2.6：系统导航栏的高度（键盘弹起时算 0）。
     *
     * 沉浸式之后窗口是「铺满整屏」的，底栏会被手势条压住，所以：
     * ① 底栏自己让开这一段；
     * ② 页面内容底部要留出的空档也得跟着加上，否则最后一条会被底栏盖掉。
     *
     * ⚠️ 为什么是 `exclude(WindowInsets.ime)`：根节点已经套了 `imePadding()`，
     * 键盘弹起时整块内容已经按键盘高度上移过一次了；而导航栏此时正躲在键盘后面，
     * 系统却仍然会报出它的高度。不排除掉的话，底栏会**在键盘上方再多浮一截**
     * （手势条 24~48dp），看着像没对齐。
     */
    val navBarInset = WindowInsets.navigationBars
        .exclude(WindowInsets.ime)
        .asPaddingValues()
        .calculateBottomPadding()
    var detailLink by remember { mutableStateOf<String?>(null) }
    var showAddSource by remember { mutableStateOf(false) }
    var showRssHub by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    // v2.4：首页顶栏放大镜进来的全屏搜索浮层
    var showSearch by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    /**
     * 当前 Tab —— **从 Pager 派生**，不再单独存一份 state（v2.5）。
     *
     * 以前是「两个状态互相回写」：`selectedTab` 变了去滚 pager，pager 停下又回写
     * `selectedTab`。这套双向同步在跳页动画**被打断**时会振荡 —— 用户点「设置」，
     * 动画途经「阅源」时 pager 停下并回写 `selectedTab = 阅源`，于是又发起一次
     * 从阅源出发的跳转，看到的就是「先跳到阅源、再弹到设置」。
     *
     * 现在只有一份真相（Pager 自己），`selectedTab` 是它的投影，振荡从结构上消失。
     */
    val selectedTab: Screen by remember {
        derivedStateOf { tabs.getOrNull(pagerState.currentPage) ?: Screen.Home }
    }

    /**
     * 切到某个 Tab。
     *
     * ⚠️ **跨页用瞬切、不用滑动动画**（v2.5 修卡顿）。原因：
     * `animateScrollToPage(3)` 是连续滚动，会真的把内容从第 0 页一路推过第 1、2 页；
     * 而 pager 设了 `beyondViewportPageCount = 0`（只组合视口内的页）——
     * 于是途经的每一页都会被**现场组合**：各自 `viewModel(...)` 新建实例、订阅 Room 流、
     * 跑一遍排序，全在主线程上。既让人看到「唰地路过阅源」，又明显掉帧。
     * 相邻页只差一步，动画便宜也好看，保留。
     */
    val goTo: (Screen) -> Unit = { tab ->
        val from = pagerState.currentPage
        val idx = tabs.indexOf(tab)
        if (idx >= 0 && idx != from) {
            scope.launch {
                if (abs(idx - from) > 1) pagerState.scrollToPage(idx)
                else pagerState.animateScrollToPage(idx)
            }
        }
    }

    // 收键盘：打开文章、切底栏 Tab 之前先收掉，否则输入法会跟着飘到新页面上
    val dismissIme = rememberImeDismiss()

    /**
     * 打开某篇文章。
     *
     * v2.4：顺手收掉输入法。搜索浮层点结果、「闻件」里点结果都会走这里 ——
     * 但详情页本身不带输入框，键盘跟过去只会挡住正文。
     */
    val openArticle: (String) -> Unit = { link ->
        dismissIme()
        detailLink = link
    }

    // 底部导航的未读徽标：直接订阅未读数，不去打扰各个页面的 ViewModel
    val unreadFlow = remember { app.newsRepository.observeUnreadCount() }
    val unread by unreadFlow.collectAsStateWithLifecycle(0)

    // v1.6.1：徽标默认关（数字会把图标顶偏、底栏显吵），设置里可开
    val showUnreadBadge by app.settingsRepository.unreadBadgeFlow
        .collectAsStateWithLifecycle(false)

    // 朗读状态：点底栏离开文章时用它决定要不要提示「还在后台念」
    val ttsSpeaking by app.tts.speaking.collectAsStateWithLifecycle()
    val ttsPaused by app.tts.paused.collectAsStateWithLifecycle()
    val ttsTitle by app.tts.title.collectAsStateWithLifecycle()
    val ttsLink by app.tts.link.collectAsStateWithLifecycle()

    // v1.5：阅读文章时是否仍然显示底栏（设置里可关，默认显示）
    val showBarInReader by app.settingsRepository.showBarInReaderFlow.collectAsStateWithLifecycle(true)
    val reading = detailLink != null
    val barVisible = !reading || showBarInReader

    /**
     * v2.0：后台朗读时给一条「一键回到文章」的悬浮条。
     *
     * 判据是「正在朗读」且「当前没有在看那一篇」——
     * 人已经在文章里的时候不需要这条，白占地方。
     */
    val showTtsBar = ttsSpeaking && !ttsLink.isNullOrBlank() && detailLink != ttsLink
    val contentInset = BAR_INSET + navBarInset + if (showTtsBar) TTS_BAR_HEIGHT else 0.dp

    /**
     * 关闭阅读浮层。
     *
     * 朗读**刻意不停**：引擎挂在 Application 上（v1.8），离开文章后声音继续念，
     * 用户才能边听边去别的 Tab 办事。给一句 toast 让他知道去哪停，不然会一头雾水。
     * （v2.0 起还可以直接点悬浮条回到文章 / 点通知栏的按钮控制。）
     */
    val closeReader: () -> Unit = {
        detailLink = null
        if (ttsSpeaking) {
            Toast.makeText(context, "朗读继续在后台播放，顶部可一键回到文章", Toast.LENGTH_SHORT).show()
        }
    }

    // 通知栏 / 悬浮条要求打开某篇文章
    val pendingOpenLink by app.pendingOpenLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenLink) {
        val link = pendingOpenLink
        if (!link.isNullOrBlank()) {
            detailLink = link
            app.consumeOpenArticle()
        }
    }

    // 拦截系统返回（边缘手势 / 返回键）：在 App 内逐级返回，而不是直接退出
    BackHandler(
        enabled = reading || showAddSource || showRssHub || showAbout || showStats || showStorage || showGuide ||
                showSearch || selectedTab != Screen.Home
    ) {
        when {
            // ⚠️ 顺序 = 「谁画在最上面谁先关」。
            // [reading]（详情浮层）永远盖在所有东西之上，所以放在最前面 ——
            // v2.4 起搜索浮层是可以和它共存的：从搜索结果点进文章，详情压在搜索上面，
            // 返回键应该先退文章、再退搜索，而不是反过来。
            reading -> closeReader()                               // 详情浮层 → 关
            showRssHub -> showRssHub = false                         // RSSHub 订阅页 → 关
            showGuide -> showGuide = false                        // 新手指南 → 关
            showStorage -> showStorage = false                     // 缓存管理 → 关
            showStats -> showStats = false                         // 阅读统计 → 关
            showAbout -> showAbout = false                         // 关于页 → 关
            showAddSource -> showAddSource = false                 // 加源页 → 关
            showSearch -> showSearch = false                        // 搜索浮层 → 关
            else -> goTo(Screen.Home)                              // 非首页 Tab → 回首页
        }
    }

    // 注：这里**不再需要**「pager ↔ selectedTab 双向同步」那两段 LaunchedEffect。
    // 底栏的**视觉位置**本来就是把 pagerState 直接交给底栏、由滑动进度连续驱动的
    // （拖动时跟手，不会等停下来才跳）；而「当前在哪一页」现在也直接派生自 pagerState。
    // 谁都不需要回写谁，就没有动画被打断时来回弹跳的余地了。

    /**
     * v2.6：沉浸式（edge-to-edge）。
     *
     * - `background(...)` 写在 `imePadding()` **之前**：修饰符是从左往右包，
     *   背景属于「外层」，所以它铺满的是整屏（含状态栏 / 导航栏区域）；
     *   而 padding 只影响里层内容。这样一条链同时做到「背景铺满」和「键盘弹起时内容上移」，
     *   不用再套一层 Box、也就不必把下面一百多行重新缩进。
     * - 系统栏在 MainActivity 里被设成透明，那两条区域透出来的就是这一层背景色 ——
     *   这也是「底部白条」的根治：以前那里没有任何东西覆盖，露的是**窗口底色**。
     */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 不再同时预组合左右两页：以前 3 个页面各自挂着 Room 查询 + 长列表，
            // 白耗 CPU 又抢主线程。现在只组合看得见的页面，滑动更跟手。
            beyondViewportPageCount = 0,
            // ⚠️ key 必须是「可存进 Bundle 的类型」（String/Int/Parcelable/Serializable）。
            // 之前写成 key = { tabs[it] }，传的是 Screen 对象（Home/Search…），
            // Pager 底层会拿它去注册 SaveableStateProvider → 抛
            // IllegalArgumentException: Type of the key ... is not supported → 一打开就闪退。
            // 用 route（"home"/"wenjian"/…）这个稳定唯一字符串即可。
            key = { page -> tabs.getOrNull(page)?.route ?: "page_$page" }
        ) { page ->
            // v2.6：首页与闻件**自己**处理顶部留白 —— 它们要做「列表从状态栏下面穿过去」
            // 的沉浸效果，所以不能在这里统一往下压。
            // 其余页面（阅源 / 设置）在窗口里补一层状态栏留白：沉浸式之后窗口不再自动避让，
            // 不补的话标题会被状态栏压住。
            val immersive = tabs[page] == Screen.Home || tabs[page] == Screen.Wenjian

            /**
             * v2.7：这一页的内容要不要「画到屏幕最底边、从底栏后面穿过去」。
             *
             * 底栏只在**四个按钮那一行**铺了一层与页面同色的实底（v2.7.2），
             * 上下留白是透的，所以列表穿过底栏时能看见内容 ——
             * 但**只有列表里出现白色卡片时**才看得出差别 —— 卡片是 `surface`（纯白），
             * 底栏那条底是 `background`，两者相接就是一道明显的分界。
             *
             * 所以：
             * - 首页 / 闻件 —— 列表是白色卡片（ArticleCard），必须穿透。它们不能在这里压
             *   `padding(bottom)`（压了就穿不过去），改由各自列表的 `contentPadding` 留白，
             *   见各页的 `bottomInset` 参数。少了那一步，最后一张卡片会被底栏压住。
             * - 阅源 / 设置 —— 整页都是 `background` 底色、没有卡片，底栏区域和内容本来就同色，
             *   看不出分界。保持原来的 `padding` 即可：改动最小，也顺带不会漏掉哪条列表。
             */
            val penetrate = tabs[page] == Screen.Home || tabs[page] == Screen.Wenjian

            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (penetrate) Modifier else Modifier.padding(bottom = contentInset))
                    .then(if (immersive) Modifier else Modifier.statusBarsPadding())
            ) {
                when (tabs[page]) {
                    Screen.Home -> HomeScreen(
                        app = app,
                        onOpenArticle = openArticle,
                        // v2.4：顶栏放大镜 → 全屏搜索浮层
                        onOpenSearch = { showSearch = true },
                        bottomInset = contentInset
                    )
                    Screen.Wenjian -> WenjianScreen(
                        app = app,
                        onOpenArticle = openArticle,
                        bottomInset = contentInset
                    )
                    Screen.Sources -> SourcesScreen(
                        app = app,
                        onOpenAddSource = { showAddSource = true },
                        onOpenRssHub = { showRssHub = true }
                    )
                    Screen.Settings -> SettingsScreen(
                        app = app,
                        onOpenAddSource = { showAddSource = true },
                        onOpenAbout = { showAbout = true },
                        onOpenStats = { showStats = true },
                        onOpenStorage = { showStorage = true },
                        onOpenGuide = { showGuide = true },
                        // 「我的阅源」入口：直接切到「阅源」Tab（不新开页面，
                        // 因为那个页面本来就是底部第三个 Tab，再叠一层浮层反而怪）
                        onOpenSources = { goTo(Screen.Sources) }
                    )
                    else -> Unit
                }
            }
        }

        // ---------------- v2.4：首页放大镜进来的全屏搜索 ----------------
        //
        // 刻意画在 Pager **之上、详情浮层之下**，而且底栏保持可见：
        // 这样「搜索 → 点结果看文章 → 返回」能退回搜索结果继续翻，
        // 同时底栏还在，用户随时可以点别的 Tab 走人（点 Tab 会顺手关掉它）。
        if (showSearch) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(bottom = contentInset)
            ) {
                SearchPane(
                    app = app,
                    onOpenArticle = openArticle,
                    onBack = { showSearch = false },
                    // 用户点放大镜就是来打字的，直接把键盘弹出来
                    autoFocus = true
                )
            }
        }

        // 详情浮层：底栏若保持显示，正文底部要留出同样的 inset，别被胶囊压住
        if (reading) {
            DetailScreen(
                app = app,
                initialLink = detailLink!!,
                onBack = { detailLink = null },
                bottomInset = if (barVisible) contentInset else 0.dp
            )
        }

        // ---------------- v2.0：后台朗读悬浮条 ----------------
        if (showTtsBar) {
            TtsNowPlayingBar(
                title = ttsTitle,
                paused = ttsPaused,
                onToggle = { app.tts.toggle() },
                onOpen = { ttsLink?.let { detailLink = it } },
                onStop = { app.tts.stop() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = BAR_INSET + navBarInset)
            )
        }

        // 底栏画在详情之上，所以「阅读时显示底栏」才真的点得到
        if (barVisible) {
            YuewenBottomBar(
                items = tabs,
                pagerState = pagerState,
                onSelect = { tab ->
                    // v1.8：阅读时点底栏 = 离开文章。
                    // 以前这里只改 selectedTab，而详情浮层还严严实实盖在最上面，
                    // 用户看到的就是「点了没反应」。现在顺手把浮层关掉，底栏才真的可用。
                    if (reading) closeReader()
                    // v2.4：搜索浮层同理 —— 它盖在 Pager 上，不关掉的话点底栏也像「没反应」
                    if (showSearch) showSearch = false
                    dismissIme()
                    goTo(tab)
                },
                unreadCount = if (showUnreadBadge) unread else 0,
                // v2.6.0：底栏不再收 `glass` —— 它按用户要求做成了「没有自己的底」，
                // 玻璃开关从此只管首页顶栏和闻件标签栏。
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 全屏浮层：关于 / 添加阅源 / 阅读统计 / 缓存管理 / 新手指南（覆盖底栏，独立于 Pager）
        //
        // v2.6：每层都套一个 `systemBarsPadding()` 的 Box —— 沉浸式之后窗口不再自动避让系统栏，
        // 不补这一层的话浮层标题会被状态栏压住、底部的按钮会被手势条压住。
        // 这些页面的底色本来就是 `colorScheme.background`，和根容器完全一致，
        // 所以上下两条空出来的地方看不出接缝。
        if (showStats) Box(Modifier.systemBarsPadding()) { StatsScreen(app = app, onBack = { showStats = false }) }
        if (showStorage) Box(Modifier.systemBarsPadding()) { StorageScreen(app = app, onBack = { showStorage = false }) }
        if (showAbout) Box(Modifier.systemBarsPadding()) { AboutScreen(app = app, onBack = { showAbout = false }) }
        if (showGuide) Box(Modifier.systemBarsPadding()) { GuideScreen(app = app, onBack = { showGuide = false }) }
        if (showAddSource) Box(Modifier.systemBarsPadding()) { AddSourceScreen(app = app, onBack = { showAddSource = false }) }
        // 放在最后 = 画在最上层；从「添加阅源」页里也能进 RSSHub 而不被盖住
        if (showRssHub) Box(Modifier.systemBarsPadding()) { RssHubScreen(app = app, onBack = { showRssHub = false }) }
    }
}

/**
 * 「正在朗读」悬浮条。
 *
 * 解决的具体痛点：朗读可以在后台继续，但用户去别的 Tab 转一圈之后，
 * 想回到那篇文章得自己再找一遍——很烦。
 * 这条常驻显示「正在念什么」，点一下直接跳回去；顺手把暂停/继续/停止也放在这里，
 * 不用为了停一段声音专门跑回文章页。
 */
@Composable
private fun TtsNowPlayingBar(
    title: String,
    paused: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.primary,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = cs.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onOpen)
            ) {
                Text(
                    if (paused) "已暂停" else "正在朗读",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onPrimary.copy(alpha = 0.8f)
                )
                Text(
                    title.ifBlank { "（未命名文章）" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = cs.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (paused) "继续朗读" else "暂停朗读",
                    tint = cs.onPrimary
                )
            }
            IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                Text(
                    "回到\n文章",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onPrimary
                )
            }
            IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "停止朗读", tint = cs.onPrimary)
            }
        }
    }
}
