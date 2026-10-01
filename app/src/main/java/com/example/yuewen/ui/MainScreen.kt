package com.example.yuewen.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.example.yuewen.ui.screens.SettingsScreen
import com.example.yuewen.ui.screens.SourcesScreen
import com.example.yuewen.ui.screens.StatsScreen
import com.example.yuewen.ui.screens.StorageScreen
import com.example.yuewen.ui.screens.WenjianScreen

/**
 * 底部胶囊栏占用的高度。页面内容与阅读正文都要按这个留出空档，别被压住。
 *
 * v1.9：胶囊本体 ≈ 34dp + 上下各 7dp 外边距 ≈ 48dp，BAR_INSET 从 70dp 收到 58dp。
 * v2.0：朗读悬浮条会临时叠在底栏之上，[MainScreen] 会在这个基础上再加一段高度。
 */
private val BAR_INSET = 58.dp

/** 「正在朗读」悬浮条自身的高度（含上下留白），用于动态加大页面底部空隙。 */
private val TTS_BAR_HEIGHT = 54.dp

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as YuewenApplication
    // 记住列表实例：原来每次重组都新建一份，白白带着底栏一起重组
    val tabs = remember { Screen.bottomTabs() }
    var selectedTab by remember { mutableStateOf<Screen>(Screen.Home) }
    var detailLink by remember { mutableStateOf<String?>(null) }
    var showAddSource by remember { mutableStateOf(false) }
    var showRssHub by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })

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
    val contentInset = BAR_INSET + if (showTtsBar) TTS_BAR_HEIGHT else 0.dp

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
                selectedTab != Screen.Home
    ) {
        when {
            showRssHub -> showRssHub = false                         // RSSHub 订阅页 → 关
            showGuide -> showGuide = false                        // 新手指南 → 关
            showStorage -> showStorage = false                     // 缓存管理 → 关
            showStats -> showStats = false                         // 阅读统计 → 关
            showAbout -> showAbout = false                         // 关于页 → 关
            showAddSource -> showAddSource = false                 // 加源页 → 关
            reading -> closeReader()                               // 详情浮层 → 关
            else -> selectedTab = Screen.Home                      // 非首页 Tab → 回首页
        }
    }

    // 滑动 → 更新「当前在哪一页」这个逻辑状态。
    // 注意：底栏的**视觉位置**不靠这里，而是把 pagerState 直接交给底栏，
    // 由 Pager 的滑动进度连续驱动 —— 所以拖动过程中底栏是跟着手指走的，不会等停下来才跳。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (page, scrolling) ->
                if (!scrolling) tabs.getOrNull(page)?.let { if (it != selectedTab) selectedTab = it }
            }
    }
    // 点底栏 → 翻到对应页
    LaunchedEffect(selectedTab) {
        val idx = tabs.indexOf(selectedTab)
        if (idx >= 0 && idx != pagerState.currentPage) pagerState.animateScrollToPage(idx)
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            Box(Modifier.fillMaxSize().padding(bottom = contentInset)) {
                when (tabs[page]) {
                    Screen.Home -> HomeScreen(app = app, onOpenArticle = { detailLink = it })
                    Screen.Wenjian -> WenjianScreen(app = app, onOpenArticle = { detailLink = it })
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
                        onOpenSources = { selectedTab = Screen.Sources }
                    )
                    else -> Unit
                }
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
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = BAR_INSET)
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
                    selectedTab = tab
                },
                unreadCount = if (showUnreadBadge) unread else 0,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 全屏浮层：关于 / 添加阅源 / 阅读统计 / 缓存管理 / 新手指南（覆盖底栏，独立于 Pager）
        if (showStats) {
            StatsScreen(app = app, onBack = { showStats = false })
        }
        if (showStorage) {
            StorageScreen(app = app, onBack = { showStorage = false })
        }
        if (showAbout) {
            AboutScreen(app = app, onBack = { showAbout = false })
        }
        if (showGuide) {
            GuideScreen(app = app, onBack = { showGuide = false })
        }
        if (showAddSource) {
            AddSourceScreen(app = app, onBack = { showAddSource = false })
        }
        // 放在最后 = 画在最上层；从「添加阅源」页里也能进 RSSHub 而不被盖住
        if (showRssHub) {
            RssHubScreen(app = app, onBack = { showRssHub = false })
        }
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
