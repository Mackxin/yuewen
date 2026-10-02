package com.example.yuewen.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.model.Article
import com.example.yuewen.ui.components.ArticleActionsSheet
import com.example.yuewen.ui.components.ArticleCard
import com.example.yuewen.ui.components.ArticleListMode
import com.example.yuewen.ui.components.EmptyState
import com.example.yuewen.ui.components.FolderPickerDialog
import com.example.yuewen.ui.util.BrowserLauncher
import com.example.yuewen.ui.util.formatRelativeTime
import com.example.yuewen.ui.util.titleOrDefault
import com.example.yuewen.ui.viewmodel.HomeRow
import com.example.yuewen.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 首页信息流（v2.0，v2.4 起顶栏多了搜索入口 + 关键词胶囊）。
 *
 * 本轮的变化：
 * 1. 顶部标题用**用户自定义的应用名**（设置 → 个性 → 应用内名称）；
 * 2. 顶栏的「布局」「刷新」按钮可以在设置里单独关掉，副标题也能关；
 * 3. 新增**批量管理**：顶栏最左的勾选图标进入多选，选完在底部操作条上一次处理；
 * 4. v2.4：顶栏最前面加了**放大镜**，点开直接搜索（原来得先切到「闻件」）；
 *    订阅源行下面多了**关键词胶囊**（设置里自己填「手机」「汽车」这类词），
 *    点一下首页就只看含这个词的文章。
 * 5. v2.6：**沉浸式**。顶栏从「占位的一行」变成**悬浮在列表之上的一层玻璃**，
 *    列表从屏幕最顶端开始画、顶部留白 = 状态栏 + 顶栏高度 ——
 *    往上滚的时候文章会从玻璃顶栏和状态栏下面穿过去。
 *    筛选胶囊行顺势搬进列表（变成第一项），跟着一起滚走，
 *    滚回顶部它就在，不再常驻占掉一屏最贵的位置。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    app: YuewenApplication,
    onOpenArticle: (String) -> Unit,
    onOpenSearch: () -> Unit = {},
    bottomInset: Dp = 0.dp
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.provide(app))
    val categories by vm.categories.collectAsStateWithLifecycle()
    // v2.0.2：必须在这里订阅一次 —— sources 是 WhileSubscribed 的 StateFlow，
    // 没人订阅时它的 .value 一直是空列表，阅源筛选行就会一个源名都列不出来。
    val sources by vm.sources.collectAsStateWithLifecycle()
    // v2.5：分类行 / 阅源行各自一个开关（设置 →「外观 → 首页筛选」里改）。
    // 这里订阅一次，是为了 HomeChipRow 渲染时拿得到最新值。
    val showCategoryRow by vm.showCategoryRow.collectAsStateWithLifecycle()
    val showSourceRow by vm.showSourceRow.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val sourceFilter by vm.source.collectAsStateWithLifecycle()
    // v2.4：关键词胶囊
    val keywords by vm.homeKeywords.collectAsStateWithLifecycle()
    val keyword by vm.keyword.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    // v2.3：「全选」的目标链接。以前是同步调 `vm.currentLinks()` 现算，
    // 既每次重组都新建一次列表，又依赖「恰好有别的流订阅着 articles」才拿到新值。
    val allLinks by vm.currentLinks.collectAsStateWithLifecycle()
    val unreadOnly by vm.unreadOnly.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastRefresh.collectAsStateWithLifecycle()
    val mode by vm.listMode.collectAsStateWithLifecycle()
    val unread by vm.unreadCount.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()

    // 顶栏各件的显示开关（设置 → 外观 / 个性）
    val showFilterIcons by app.settingsRepository.homeFilterIconsFlow.collectAsStateWithLifecycle(true)
    val showLayoutBtn by app.settingsRepository.homeShowLayoutFlow.collectAsStateWithLifecycle(true)
    val showRefreshBtn by app.settingsRepository.homeShowRefreshFlow.collectAsStateWithLifecycle(true)
    val showSubtitle by app.settingsRepository.homeShowSubtitleFlow.collectAsStateWithLifecycle(true)
    // v2.4：关键词那一行可以在设置里整行关掉（词不会丢，只是不显示）
    val showKeywords by app.settingsRepository.homeShowKeywordsFlow.collectAsStateWithLifecycle(true)
    val appTitleRaw by app.settingsRepository.appTitleFlow.collectAsStateWithLifecycle("")
    val appTitle = titleOrDefault(appTitleRaw)
    // v2.2：长按菜单的「在浏览器打开原文」也用设置里选的那个浏览器
    val browserPkg by app.settingsRepository.browserPkgFlow.collectAsStateWithLifecycle("")

    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // v1.9：列表状态提到这里，供「双击顶部标题回到顶端」使用
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var actionTarget by remember { mutableStateOf<Article?>(null) }
    var folderTarget by remember { mutableStateOf<Article?>(null) }

    // ---- v2.0：批量多选 ----
    var selectionMode by remember { mutableStateOf(false) }
    var selectedLinks by remember { mutableStateOf<Set<String>>(emptySet()) }
    var batchFolderTarget by remember { mutableStateOf(false) }

    // ---- v2.7：日期分组折叠 ----
    // 存的是**被折叠的分组名**（「今天」「昨天」「本周」「更早」），不是下标 ——
    // 下标会随筛选 / 刷新而变，那样折叠状态会莫名其妙地跑到别的分组上去。
    // 有意只放在内存里：重启后回到「全部展开」是更好预期的行为，也省掉一个设置项。
    var collapsedDays by remember { mutableStateOf<Set<String>>(emptySet()) }

    val exitSelection = {
        selectionMode = false
        selectedLinks = emptySet()
    }

    // 退出多选时顺手清掉选择，避免下次进来还残留
    LaunchedEffect(selectionMode) { if (!selectionMode) selectedLinks = emptySet() }

    Box(modifier = Modifier.fillMaxSize().background(cs.background)) {

        // ---------------- v2.6：沉浸式的尺寸 ----------------
        // 列表顶部要留白多少，取决于「状态栏 + 顶栏」到底多高。
        // 顶栏高度没法写死：标题字数、副标题开不开、右上角挂几个图标，都会改它。
        // 所以布局一帧后量一次（量的是**整条顶栏**，含状态栏留白），
        // 偏差超过半 dp 才回写，避免每帧都触发一次无谓的重组。
        val density = LocalDensity.current
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        var topBarTotal by remember { mutableStateOf(statusBarTop + 70.dp) }
        val listTop = topBarTotal

        // ---------------- 顶栏：悬浮在列表之上的一条同色底 ----------------
        // 形状用矩形而不是胶囊：顶栏是贴边的，带圆角反而会露出底下的内容。
        // `statusBarsPadding` 折进 measure 里（而不是再套一层 Column）：
        // 这样量到的就是「含状态栏」的总高，正好等于列表要留的空白。
        //
        // v2.7：撤掉「液态玻璃」，退回一层**和页面同色**的实底。
        // 必须有不透明底色 —— 列表是从屏幕最顶端开始画的，内容会从它下面穿过，
        // 没有这层底的话标题会和文章文字叠在一起。
        //
        // ⚠️⚠️ v2.6.0 的坑：这个顶栏**必须画在列表之上**（`zIndex(1f)`）。
        // 它是在 Box 里先于列表声明的，而 Compose 的绘制与命中测试都按**声明顺序的逆序**走 ——
        // 于是列表画在了顶栏上面：
        //   ① 往下滚一点，搬进列表第一项的筛选胶囊就**盖住**了「更新于 …前」那行副标题，
        //      看着像「阅闻标题没有置顶了」；
        //   ② 顶栏右侧那排图标（放大镜 / 多选 / 筛选 / 全标已读 / 布局 / 刷新）
        //      的点击**全被列表吃掉**，按了没反应。
        // 而不是把顶栏整段挪到列表后面 —— 那样 diff 太大，加个 `zIndex` 是等效且最小的修法。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .background(cs.background)
        ) {
            val measure = Modifier
                .onGloballyPositioned { coords ->
                    val h = with(density) { coords.size.height.toDp() }
                    // 用 .value 比：Dp 之间没有现成的 abs 重载，别硬套 Float 的那个
                    if (h > 0.dp && abs(h.value - topBarTotal.value) > 0.5f) topBarTotal = h
                }
                .statusBarsPadding()

            // ---------------- 顶部标题栏 ----------------
            if (selectionMode) {
                BatchTopBar(
                    count = selectedLinks.size,
                    total = allLinks.size,
                    allSelected = selectedLinks.size >= allLinks.size && allLinks.isNotEmpty(),
                    onClose = { exitSelection() },
                    onToggleAll = {
                        selectedLinks = if (selectedLinks.size >= allLinks.size) emptySet() else allLinks.toSet()
                    },
                    modifier = measure
                )
            } else {
                Row(
                    modifier = measure.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            // v1.9：双击标题回到列表顶端。
                            // 和「点刷新」不冲突：单击不消费，只是双击才回顶。
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = {
                                    scope.launch {
                                        if (listState.firstVisibleItemIndex > 0 ||
                                            listState.firstVisibleItemScrollOffset > 0
                                        ) {
                                            listState.animateScrollToItem(0)
                                        }
                                    }
                                })
                            }
                    ) {
                        // 标题：最后一个字用主色，做一个简单的双色字标（名字可自定义，所以按长度切）
                        Row(verticalAlignment = Alignment.Bottom) {
                            val head = appTitle.dropLast(1)
                            val tail = appTitle.takeLast(1).ifEmpty { appTitle }
                            if (head.isNotEmpty()) {
                                Text(head, style = MaterialTheme.typography.headlineMedium, color = cs.onBackground)
                            }
                            Text(tail, style = MaterialTheme.typography.headlineMedium, color = cs.primary)
                        }
                        if (showSubtitle) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (lastRefresh > 0) {
                                    // v2.7.2：用户要求「副标题不要换行」。
                                    //
                                    // 但只把 maxLines 改回 1 会退回到 v2.7.0 那个「…」的老问题 ——
                                    // 所以这里**同时把文案压短**，让它一行真的放得下：
                                    //   「更新于 1分钟前 · 379 篇未读」（约 139dp）→ 一行装不下 → 折行
                                    //   「1分钟前 · 379 未读」        （约 105dp）→ 标题区约 136dp，放得下
                                    // 去掉的是「更新于」（顶栏右侧就有 ↻，语义不丢）和量词「篇」。
                                    // 最坏情况「12月31日 · 9999 未读」约 105dp，同样安全。
                                    formatRelativeTime(lastRefresh) + if (unread > 0) " · $unread 未读" else ""
                                } else {
                                    "下拉或点 ↻ 刷新"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                // softWrap = false：宁可按不动也不许折行（用户明确说折行不好看）。
                                // Ellipsis 只是给「字体被系统放大到极端」这类情况兜底，正常用不到。
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // ---- v2.4：搜索入口 ----
                    // 以前想搜东西必须先切到底部的「闻件」，再确认停在「搜索」那一栏，
                    // 两步才能开始打字。这个放大镜把入口提到首页顶栏，点开就是全屏搜索，
                    // 返回键/返回箭头直接回到刚才的首页。
                    // 放在图标组的**最前面**：它是这一排里唯一的「主操作」，
                    // 而且右侧那几枚（布局 / 刷新）位置早就固定了，别去动它们。
                    TopBarIcon(
                        icon = Icons.Filled.Search,
                        contentDescription = "搜索文章",
                        tint = cs.primary,
                        onClick = onOpenSearch
                    )

                    // 批量管理入口
                    TopBarIcon(
                        icon = Icons.Filled.Checklist,
                        contentDescription = "批量管理",
                        tint = cs.onSurfaceVariant,
                        onClick = { selectionMode = true }
                    )

                    // ---- v1.8：把原来那两排「全部 / 仅看未读」胶囊 + 「全部标为已读」按钮，
                    //      收成顶栏里的两个图标（放在布局按钮左边）。整组可在设置里隐藏。 ----
                    if (showFilterIcons) {
                        TopBarIcon(
                            icon = if (unreadOnly) Icons.Filled.FilterAlt else Icons.Outlined.FilterAlt,
                            contentDescription = if (unreadOnly) "当前仅看未读，点击查看全部" else "当前显示全部，点击仅看未读",
                            tint = if (unreadOnly) cs.primary else cs.onSurfaceVariant,
                            onClick = {
                                val next = !unreadOnly
                                vm.setUnreadOnly(next)
                                Toast.makeText(
                                    context,
                                    if (next) "仅看未读" else "显示全部",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        TopBarIcon(
                            icon = Icons.Filled.DoneAll,
                            contentDescription = "全部标为已读",
                            // 没有 Material 的 disabled 着色兜底了，得手动压暗
                            tint = if (unread > 0) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.38f),
                            enabled = unread > 0,
                            onClick = { vm.markAllRead() }
                        )
                    }

                    // 布局切换：紧凑 → 卡片 → 杂志 循环
                    if (showLayoutBtn) {
                        TopBarIcon(
                            icon = mode.icon(),
                            contentDescription = "切换列表布局",
                            tint = cs.onSurfaceVariant,
                            onClick = {
                                vm.cycleListMode()
                                Toast.makeText(context, "布局：${mode.next().label}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    if (showRefreshBtn) {
                        TopBarIcon(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            tint = cs.primary,
                            enabled = !isRefreshing,
                            loading = isRefreshing,
                            onClick = vm::refresh
                        )
                    }
                }
            }
        }

        // ---------------- 列表 ----------------
        // v1.8：下拉刷新只保留「右上角刷新按钮」那一处动效 ——
        // PullToRefreshBox 自带的那个圆形指示器会浮在列表上方（看起来很碍眼），
        // 这里把 indicator 传成空实现。手势本身照旧可用，
        // 转圈提示交给顶栏那个按钮（它和 isRefreshing 是同一份状态）。
        //
        // v2.6：列表从屏幕最顶端开始画（不再被顶栏挤下去），顶部留白改由 contentPadding 出。
        // 这是「沉浸式」的关键一步 —— 内容往上滚时会从玻璃顶栏和状态栏下面穿过去。
        val pullState = rememberPullToRefreshState()

        // v2.7：日期分组折叠 —— 先按折叠状态把 rows 过一遍。
        // `rows` 是「Header + 它名下的一串 Item」的展平列表（见 HomeViewModel.groupByDay），
        // 所以折叠一个分组 = 留下它的 Header、把它后面那段 Item 丢掉，直到遇到下一个 Header 才恢复。
        // 一个都没折叠时直接返回原列表 —— 不白造一遍对象。
        //
        // ⚠️ 必须在这里算，**不能**挪进 `LazyColumn { }` 里面：LazyColumn 的 content 是
        // `LazyListScope.() -> Unit`，不是 @Composable 作用域，`remember` 放进去直接编译不过。
        val visibleRows = remember(rows, collapsedDays) {
            if (collapsedDays.isEmpty()) {
                rows
            } else {
                val out = ArrayList<HomeRow>(rows.size)
                var hidden = false
                for (row in rows) {
                    when (row) {
                        is HomeRow.Header -> {
                            hidden = row.label in collapsedDays
                            out.add(row)
                        }
                        is HomeRow.Item -> if (!hidden) out.add(row)
                    }
                }
                out
            }
        }

        PullToRefreshBox(
            state = pullState,
            isRefreshing = isRefreshing,
            onRefresh = vm::refresh,
            indicator = {},
            modifier = Modifier.fillMaxSize()
        ) {
                if (rows.isEmpty()) {
                    // 空状态也要让开顶栏，否则文案会被玻璃顶栏压在下面
                    Box(Modifier.fillMaxSize().padding(top = listTop)) {
                        EmptyState(
                            icon = Icons.Filled.RssFeed,
                            title = if (isRefreshing) "正在获取内容…" else "这里还没有内容",
                            subtitle = if (isRefreshing) {
                                "首次刷新要同时抓取多个源，稍等几秒"
                            } else {
                                "点右上角 ↻ 或下拉刷新；\n也可以去「阅源 → 发现推荐」一键订阅精选源"
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        // v2.6：顶部留白 = 状态栏 + 顶栏。内容因此会从顶栏和状态栏下面穿过去。
                        // v2.7：底部留白改由 [bottomInset] 承担 —— 底栏只在四个按钮那一行有实底
                        // （v2.7.2），列表要一直画到屏幕最底边、从底栏后面穿过去；但滚到最后一项时
                        // 得留出一段空白，否则最后一张卡片会被底栏压住看不见。
                        contentPadding = PaddingValues(top = listTop + 2.dp, bottom = bottomInset + 20.dp)
                    ) {
                        // ---------------- 筛选胶囊（分类 / 阅源 / 关键词）----------------
                        // v2.6：整组从「常驻顶栏」搬进列表，变成**第一项**，跟着内容一起滚走。
                        // 它们是筛选用的一次性操作，不该长期占着一屏里最贵的位置；
                        // 滚回顶部（或双击标题回顶）它们就回来。
                        if (showCategoryRow || showSourceRow || showKeywords) {
                            item(key = "filters", contentType = "filters") {
                                Column {
                                    // v2.0.2：顶栏胶囊支持两种维度，具体显示哪些由「设置 → 外观 → 首页筛选」决定。
                                    // 两个都显示时是**两级筛选**：上面选分类，下面选该分类里的阅源。
                                    // v2.5：显隐判断直接落到两个独立开关上，VM 那边负责「关掉某行就清掉那一级的筛选」。
                                    if (showCategoryRow) {
                                        HomeChipRow(
                                            // 「推荐」在数据库里就是「不限分类」，胶囊上写成「全部」更好懂
                                            items = categories.map { if (it == "推荐") "全部" to it else it to it },
                                            selected = category,
                                            onSelect = vm::selectCategory
                                        )
                                    }
                                    if (showSourceRow) {
                                        // v2.0.3：当前就是「全部阅源」（根本没筛源）时，不再摆一个高亮的「全部阅源」胶囊 ——
                                        // 没筛的时候它只是白占一格；一旦选了具体源，它才出现，作为「取消筛选、回到全部」的入口。
                                        val srcNames = vm.sourceNamesFor(category, sources)
                                        HomeChipRow(
                                            items = if (sourceFilter.isBlank()) srcNames.map { it to it }
                                                    else listOf("全部阅源" to "") + srcNames.map { it to it },
                                            selected = sourceFilter,
                                            onSelect = vm::selectSource
                                        )
                                    }

                                    // ---------------- v2.4：关键词胶囊 ----------------
                                    // 和上面两行是**并列的第三种维度**（分类 / 阅源 / 关键词），三者可以叠加。
                                    // 画法刻意跟前两行不一样（左边挂个标签图标 + 胶囊用三级色）：
                                    // 三行都是「一排一模一样的胶囊」的话，用户根本分不清哪行是哪行。
                                    if (showKeywords) {
                                        HomeKeywordRow(
                                            keywords = keywords,
                                            selected = keyword,
                                            onSelect = vm::selectKeyword
                                        )
                                    }
                                }
                            }
                        }
                        // 注：按折叠状态过滤后的 [visibleRows] 是在 LazyColumn **外面**算好的
                        //（见 `val pullState = ...` 那一段下面的定义）。
                        // 这里已经是 `LazyListScope`，**不是 @Composable 作用域** —— 放进来会编译不过。
                        items(
                            items = visibleRows,
                            key = { it.key },
                            contentType = { if (it is HomeRow.Header) "header" else "article" }
                        ) { row ->
                            when (row) {
                                is HomeRow.Header -> DayHeader(
                                    label = row.label,
                                    count = row.count,
                                    collapsed = row.label in collapsedDays,
                                    onToggle = {
                                        collapsedDays = if (row.label in collapsedDays) {
                                            collapsedDays - row.label
                                        } else {
                                            collapsedDays + row.label
                                        }
                                    }
                                )
                                is HomeRow.Item -> {
                                    val article = row.article
                                    val link = article.link
                                    val checked = link in selectedLinks

                                    // ⚠️ v2.5：回调一律 `remember` 住。
                                    // 写在参数里的 lambda 每次重组都是**新引用**，ArticleCard 会因此
                                    // 判定「参数变了」而无法跳过重组 —— 首页一屏十几张卡片一起重排，
                                    // 滑动就发涩，进多选勾一下整屏还会闪。
                                    // key 只放真正影响这段行为的量，别的都不放。
                                    val onClick = remember(link, checked, selectionMode) {
                                        {
                                            if (selectionMode) {
                                                selectedLinks =
                                                    if (checked) selectedLinks - link else selectedLinks + link
                                            } else {
                                                onOpenArticle(link)
                                            }
                                        }
                                    }
                                    val onBookmarkClick = remember(link, article.isBookmarked) {
                                        { vm.toggleBookmark(link, !article.isBookmarked) }
                                    }
                                    val onLongPress = remember(link, selectionMode) {
                                        {
                                            if (selectionMode) selectedLinks = selectedLinks + link
                                            else actionTarget = article
                                        }
                                    }
                                    val itemPadding = remember(mode) {
                                        Modifier.padding(bottom = if (mode == ArticleListMode.Compact) 6.dp else 11.dp)
                                    }

                                    ArticleCard(
                                        article = article,
                                        mode = mode,
                                        selectionMode = selectionMode,
                                        selected = checked,
                                        onClick = onClick,
                                        onBookmark = onBookmarkClick,
                                        onLongClick = onLongPress,
                                        modifier = itemPadding
                                    )
                                }
                            }
                        }
                    }
                }
            }

        // ---------------- v2.0：多选时的底部操作条（悬浮在底栏之上） ----------------
        if (selectionMode) {
            BatchActionBar(
                count = selectedLinks.size,
                onRead = {
                    vm.setReadBatch(selectedLinks.toList(), true)
                    Toast.makeText(context, "已标为已读（${selectedLinks.size} 篇）", Toast.LENGTH_SHORT).show()
                    exitSelection()
                },
                onUnread = {
                    vm.setReadBatch(selectedLinks.toList(), false)
                    Toast.makeText(context, "已标为未读（${selectedLinks.size} 篇）", Toast.LENGTH_SHORT).show()
                    exitSelection()
                },
                onBookmark = {
                    vm.setBookmarkedBatch(selectedLinks.toList(), true)
                    Toast.makeText(context, "已收藏 ${selectedLinks.size} 篇", Toast.LENGTH_SHORT).show()
                    exitSelection()
                },
                onMove = { if (selectedLinks.isNotEmpty()) batchFolderTarget = true },
                onRemove = {
                    val n = selectedLinks.size
                    vm.removeBatch(selectedLinks.toList())
                    Toast.makeText(context, "已从本地移除 $n 篇", Toast.LENGTH_SHORT).show()
                    exitSelection()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // ---------------- 长按操作面板 ----------------
    val target = actionTarget
    if (target != null) {
        ArticleActionsSheet(
            article = target,
            onDismiss = { actionTarget = null },
            onToggleRead = {
                vm.toggleRead(target.link, !target.isRead)
                actionTarget = null
            },
            onToggleBookmark = {
                vm.toggleBookmark(target.link, !target.isBookmarked)
                actionTarget = null
            },
            onMoveFolder = {
                actionTarget = null
                folderTarget = target
            },
            onShare = {
                actionTarget = null
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, target.title)
                    putExtra(Intent.EXTRA_TEXT, "${target.title}\n${target.link}")
                }
                context.startActivity(Intent.createChooser(intent, "分享到"))
            },
            onCopyLink = {
                clipboard.setText(AnnotatedString(target.link))
                actionTarget = null
                Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
            },
            onOpenInBrowser = {
                actionTarget = null
                // v2.2：走设置里选的浏览器，和详情页的「原文」保持一致
                if (!BrowserLauncher.open(context, target.link, browserPkg)) {
                    Toast.makeText(context, "找不到可以打开网页的应用", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    val moveTarget = folderTarget
    if (moveTarget != null) {
        FolderPickerDialog(
            folders = folders,
            onDismiss = { folderTarget = null },
            onPick = { folder ->
                vm.setFolder(moveTarget.link, folder)
                folderTarget = null
                Toast.makeText(context, "已移到「$folder」", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 批量移动收藏夹
    if (batchFolderTarget) {
        FolderPickerDialog(
            folders = folders,
            onDismiss = { batchFolderTarget = false },
            onPick = { folder ->
                val n = selectedLinks.size
                vm.setFolderBatch(selectedLinks.toList(), folder)
                batchFolderTarget = false
                exitSelection()
                Toast.makeText(context, "已把 $n 篇移到「$folder」", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ---------------- 顶栏图标位（v2.7 收窄） ----------------

/** 顶栏单个图标的**槽位**宽度（图标 + 两侧留白）。 */
private val TOP_ICON_SLOT = 36.dp

/** 顶栏图标本身的尺寸。槽位 − 图标 = 相邻两个图标的空隙。 */
private val TOP_ICON_SIZE = 22.dp

/**
 * 顶栏图标位。
 *
 * 原先用的是 Material 的 [IconButton]。它内部挂了 `minimumInteractiveComponentSize`，
 * 会把**布局尺寸**强制撑到 48dp —— 哪怕外面写了 `Modifier.size(40.dp)` 也不管用。
 * 一排 6 个图标就是这么吃掉 288dp 的，比标题区还宽，用户反馈「左右距离太开」。
 *
 * 换成裸 [Box] + `clickable` 后槽位宽度完全由 [TOP_ICON_SLOT] 决定：
 * 每格 36dp、图标 22dp，相邻图标的空隙从 24dp 收到 14dp，整排省下约 60dp 让给标题。
 *
 * 两个刻意的选择：
 * - `indication = null`：不用 `Surface(onClick)`，它会叠一层 ripple 灰块，
 *   压在这条浅色顶栏上很脏（底栏 / 标签栏也是同样的理由）。
 * - [loading] 为真时把图标换成转圈 —— 只有刷新按钮用得上。
 *
 * ⚠️ 槽位小于 48dp 意味着触摸目标小于 Material 推荐值。这是为了紧凑做的取舍，
 * 36dp 在手机上仍然点得中；真要有人反馈点不中，调大 [TOP_ICON_SLOT] 就行。
 */
@Composable
private fun TopBarIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(TOP_ICON_SLOT)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = tint)
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(TOP_ICON_SIZE)
            )
        }
    }
}

// ---------------- 多选相关零件 ----------------

@Composable
private fun BatchTopBar(
    count: Int,
    total: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "退出批量管理", tint = cs.onBackground)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (count == 0) "选择要处理的文章" else "已选 $count 篇",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground
            )
            Text(
                "共 $total 篇 · 长按也能加选",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
        TextButton(onClick = onToggleAll, enabled = total > 0) {
            Text(if (allSelected) "取消全选" else "全选", color = cs.primary)
        }
    }
}

@Composable
private fun BatchActionBar(
    count: Int,
    onRead: () -> Unit,
    onUnread: () -> Unit,
    onBookmark: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val enabled = count > 0
    val tint = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f)

    Surface(
        color = cs.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatchAction(Icons.Filled.DoneAll, "已读", enabled, tint, onRead)
            BatchAction(Icons.Filled.MarkEmailUnread, "未读", enabled, tint, onUnread)
            BatchAction(Icons.Filled.Bookmark, "收藏", enabled, tint, onBookmark)
            BatchAction(Icons.Filled.Folder, "移动", enabled, tint, onMove)
            BatchAction(Icons.Filled.DeleteOutline, "移除", enabled, cs.error.copy(alpha = if (enabled) 1f else 0.38f), onRemove)
        }
    }
}

@Composable
private fun BatchAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/**
 * 顶栏的一行筛选胶囊（v2.0.2，v2.0.3 起允许多种条目组合）。
 *
 * 用 `(显示文字, 实际取值)` 的配对而不是纯字符串：分类行要把「推荐」显示成「全部」，
 * 阅源行要用「全部阅源」代表空值，直接拿显示文字当 key 会到处写映射。
 *
 * 条目为空就整行不渲染 —— 阅源行在「没筛源」时是不带「全部阅源」那一格的，
 * 如果恰好一个源都没订阅，它就会变成空列表。
 */
@Composable
private fun HomeChipRow(
    items: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    // 一个胶囊都没有（例如还没订阅任何阅源）就整行不渲染，免得在顶栏留下一条空白
    if (items.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            // v2.6：左右留白 14 → 2dp。这一行现在住在 LazyColumn 里，
            // 而 LazyColumn 自己已经有 12dp 水平内边距 —— 2 + 12 = 原来的 14，视觉不变。
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, value) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}

/**
 * 首页的关键词胶囊行（v2.4）。
 *
 * 和上面两行的区别都在「长得不一样」上：左边挂一个标签小图标、胶囊用三级色。
 * 三排一模一样的胶囊叠在一起，用户是分不清哪行是分类、哪行是阅源、哪行是关键词的。
 *
 * [keywords] 为空就整行不渲染 —— 用户在设置里把词删光了，这一行就该彻底消失，
 * 而不是留一条空白或者一个孤零零的标签图标。
 */
@Composable
private fun HomeKeywordRow(
    keywords: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    if (keywords.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = cs.surfaceContainerHigh,
        labelColor = cs.onSurfaceVariant,
        selectedContainerColor = cs.tertiaryContainer,
        selectedLabelColor = cs.onTertiaryContainer
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            // ⚠️ 必须用 AutoMirrored 版：普通的 Icons.Filled.Label 已标废弃，
            // 编译会吐一条 w:（本项目要求零警告）。RTL 语言下镜像才对。
            Icons.AutoMirrored.Filled.Label,
            contentDescription = "关键词",
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 和阅源行同一个思路：没筛的时候不摆「全部」，一旦选中了才出现，
            // 充当「取消筛选」的入口。另外再点一次已选中的胶囊也能取消（见 selectKeyword）。
            if (selected.isNotBlank()) {
                FilterChip(
                    selected = false,
                    onClick = { onSelect("") },
                    label = { Text("全部关键词") },
                    colors = chipColors
                )
            }
            keywords.forEach { kw ->
                FilterChip(
                    selected = kw.equals(selected, true),
                    onClick = { onSelect(kw) },
                    label = { Text(kw) },
                    colors = chipColors
                )
            }
        }
    }
}

/**
 * 日期分组标题：「今天 / 昨天 / 本周 / 更早」+ 条数。
 *
 * ⚠️ v2.6.0 去掉了原先摆在最前面的 6dp 主色小圆点（用户反馈：列表里到处都是紫色小点，去干净点）。
 * 分组感现在只靠**排版**承担：SemiBold 的 labelLarge + 一行更小的条数 + 上下留白。
 * 别再往回加装饰性圆点。
 *
 * v2.7：整行可点，折叠 / 展开该分组。右侧那个箭头跟着转，收起时指向右（▸），
 * 展开时朝下（▾）—— 这是折叠控件的通用语汇，不算「装饰」。
 */
@Composable
private fun DayHeader(
    label: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    // 箭头跟着折叠状态转 90°，用补间而不是直接换图标 —— 换图标是「啪」地跳，动效不连贯
    val angle by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "dayHeaderArrow"
    )

    // 和底栏 / 标签栏同一套做法：不用 Surface(onClick)（会叠 ripple 灰块），
    // 用裸 Row + clickable(indication = null)。
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onToggle
            )
            .padding(start = 4.dp, end = 6.dp, top = 14.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count 篇",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = if (collapsed) "展开「$label」" else "收起「$label」",
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(18.dp).rotate(angle)
        )
    }
}

private fun ArticleListMode.icon(): ImageVector = when (this) {
    ArticleListMode.Compact -> Icons.AutoMirrored.Filled.ViewList
    ArticleListMode.Card -> Icons.Filled.ViewAgenda
    ArticleListMode.Magazine -> Icons.Filled.ViewStream
}
