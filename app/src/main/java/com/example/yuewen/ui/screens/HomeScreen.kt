package com.example.yuewen.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.yuewen.ui.viewmodel.HomeChipMode
import com.example.yuewen.ui.viewmodel.HomeRow
import com.example.yuewen.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

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
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    app: YuewenApplication,
    onOpenArticle: (String) -> Unit,
    onOpenSearch: () -> Unit = {}
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.provide(app))
    val categories by vm.categories.collectAsStateWithLifecycle()
    // v2.0.2：必须在这里订阅一次 —— sources 是 WhileSubscribed 的 StateFlow，
    // 没人订阅时它的 .value 一直是空列表，阅源筛选行就会一个源名都列不出来。
    val sources by vm.sources.collectAsStateWithLifecycle()
    val chipMode by vm.chipMode.collectAsStateWithLifecycle()
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

    val exitSelection = {
        selectionMode = false
        selectedLinks = emptySet()
    }

    // 退出多选时顺手清掉选择，避免下次进来还残留
    LaunchedEffect(selectionMode) { if (!selectionMode) selectedLinks = emptySet() }

    Box(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ---------------- 顶部标题栏 ----------------
            if (selectionMode) {
                BatchTopBar(
                    count = selectedLinks.size,
                    total = allLinks.size,
                    allSelected = selectedLinks.size >= allLinks.size && allLinks.isNotEmpty(),
                    onClose = { exitSelection() },
                    onToggleAll = {
                        selectedLinks = if (selectedLinks.size >= allLinks.size) emptySet() else allLinks.toSet()
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 14.dp, bottom = 4.dp),
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
                                    "更新于 ${formatRelativeTime(lastRefresh)}" + if (unread > 0) " · $unread 篇未读" else ""
                                } else {
                                    "下拉或点 ↻ 刷新"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                // 顶栏最多挂 5 个图标，标题区被压窄；宁可省略号也不要折行把顶栏顶高
                                maxLines = 1,
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
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "搜索文章",
                            tint = cs.primary
                        )
                    }

                    // 批量管理入口
                    IconButton(
                        onClick = { selectionMode = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Checklist, contentDescription = "批量管理", tint = cs.onSurfaceVariant)
                    }

                    // ---- v1.8：把原来那两排「全部 / 仅看未读」胶囊 + 「全部标为已读」按钮，
                    //      收成顶栏里的两个图标（放在布局按钮左边）。整组可在设置里隐藏。 ----
                    if (showFilterIcons) {
                        IconButton(
                            onClick = {
                                val next = !unreadOnly
                                vm.setUnreadOnly(next)
                                Toast.makeText(
                                    context,
                                    if (next) "仅看未读" else "显示全部",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (unreadOnly) Icons.Filled.FilterAlt else Icons.Outlined.FilterAlt,
                                contentDescription = if (unreadOnly) "当前仅看未读，点击查看全部" else "当前显示全部，点击仅看未读",
                                tint = if (unreadOnly) cs.primary else cs.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { vm.markAllRead() },
                            enabled = unread > 0,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.DoneAll,
                                contentDescription = "全部标为已读",
                                // 自己指定 tint 就不会走 IconButton 的 disabled 着色，得手动压暗
                                tint = if (unread > 0) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }
                    }

                    // 布局切换：紧凑 → 卡片 → 杂志 循环
                    if (showLayoutBtn) {
                        IconButton(
                            onClick = {
                                vm.cycleListMode()
                                Toast.makeText(context, "布局：${mode.next().label}", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                mode.icon(),
                                contentDescription = "切换列表布局",
                                tint = cs.onSurfaceVariant
                            )
                        }
                    }
                    if (showRefreshBtn) {
                        IconButton(onClick = vm::refresh, enabled = !isRefreshing, modifier = Modifier.size(40.dp)) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.primary)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = cs.primary)
                            }
                        }
                    }
                }
            }

            // ---------------- 筛选胶囊（分类 / 阅源） ----------------
            // v2.0.2：顶栏胶囊支持两种维度，具体显示哪些由「设置 → 外观 → 首页筛选」决定。
            // 两个都显示时是**两级筛选**：上面选分类，下面选该分类里的阅源。
            val showCatRow = chipMode != HomeChipMode.Source
            val showSrcRow = chipMode != HomeChipMode.Category
            if (showCatRow) {
                HomeChipRow(
                    // 「推荐」在数据库里就是「不限分类」，胶囊上写成「全部」更好懂
                    items = categories.map { if (it == "推荐") "全部" to it else it to it },
                    selected = category,
                    onSelect = vm::selectCategory
                )
            }
            if (showSrcRow) {
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

            // ---------------- 列表 ----------------
            // v1.8：下拉刷新只保留「右上角刷新按钮」那一处动效 ——
            // PullToRefreshBox 自带的那个圆形指示器会浮在列表上方（看起来很碍眼），
            // 这里把 indicator 传成空实现。手势本身照旧可用，
            // 转圈提示交给顶栏那个按钮（它和 isRefreshing 是同一份状态）。
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                state = pullState,
                isRefreshing = isRefreshing,
                onRefresh = vm::refresh,
                indicator = {},
                modifier = Modifier.fillMaxSize()
            ) {
                if (rows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.RssFeed,
                        title = if (isRefreshing) "正在获取内容…" else "这里还没有内容",
                        subtitle = if (isRefreshing) {
                            "首次刷新要同时抓取多个源，稍等几秒"
                        } else {
                            "点右上角 ↻ 或下拉刷新；\n也可以去「阅源 → 发现推荐」一键订阅精选源"
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp)
                    ) {
                        items(
                            items = rows,
                            key = { it.key },
                            contentType = { if (it is HomeRow.Header) "header" else "article" }
                        ) { row ->
                            when (row) {
                                is HomeRow.Header -> DayHeader(row.label, row.count)
                                is HomeRow.Item -> {
                                    val link = row.article.link
                                    val checked = link in selectedLinks
                                    ArticleCard(
                                        article = row.article,
                                        mode = mode,
                                        selectionMode = selectionMode,
                                        selected = checked,
                                        onClick = {
                                            if (selectionMode) {
                                                selectedLinks = if (checked) selectedLinks - link else selectedLinks + link
                                            } else {
                                                onOpenArticle(link)
                                            }
                                        },
                                        onBookmark = { vm.toggleBookmark(link, !row.article.isBookmarked) },
                                        onLongClick = {
                                            if (selectionMode) {
                                                selectedLinks = selectedLinks + link
                                            } else {
                                                actionTarget = row.article
                                            }
                                        },
                                        modifier = Modifier.padding(bottom = if (mode == ArticleListMode.Compact) 6.dp else 11.dp)
                                    )
                                }
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

// ---------------- 多选相关零件 ----------------

@Composable
private fun BatchTopBar(
    count: Int,
    total: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
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
            .padding(horizontal = 14.dp, vertical = 3.dp),
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
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

/** 日期分组标题：一个小圆点 + 「今天 / 昨天 / 本周 / 更早」+ 条数。 */
@Composable
private fun DayHeader(label: String, count: Int) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(cs.primary))
        Spacer(Modifier.width(8.dp))
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
    }
}

private fun ArticleListMode.icon(): ImageVector = when (this) {
    ArticleListMode.Compact -> Icons.AutoMirrored.Filled.ViewList
    ArticleListMode.Card -> Icons.Filled.ViewAgenda
    ArticleListMode.Magazine -> Icons.Filled.ViewStream
}
