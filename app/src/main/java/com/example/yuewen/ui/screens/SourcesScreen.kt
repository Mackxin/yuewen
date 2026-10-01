package com.example.yuewen.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.model.CatalogFeed
import com.example.yuewen.data.model.FeedCatalog
import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.rss.RemoteFeed
import com.example.yuewen.ui.components.PillTab
import com.example.yuewen.ui.components.PillTabRow
import com.example.yuewen.ui.viewmodel.SourceStatus
import com.example.yuewen.ui.viewmodel.SourcesViewModel
import kotlinx.coroutines.delay

/**
 * 「阅源」页（v2.0）。
 *
 * 两栏：
 * - **我的阅源**：已订阅的源，开关 / 改名 / 改分类 / 单独刷新 / 删除；
 * - **发现推荐**：内置精选（离线可用，分好类、描述人话）+ 在线目录搜索 + 按关键词订阅。
 *
 * 这一页存在的意义，是让一个刚装好 App 的人**三十秒内就有内容可读**。
 *
 * v2.1 起入口里多了一条 [onOpenRssHub]：微博、知乎、B站这些**本身没有 RSS** 的站点，
 * 走 RSSHub 也能变成普通阅源 —— 进去之后走的还是同一条链路。
 */
@Composable
fun SourcesScreen(
    app: YuewenApplication,
    onOpenAddSource: () -> Unit,
    onOpenRssHub: () -> Unit
) {
    val vm: SourcesViewModel = viewModel(factory = SourcesViewModel.provide(app))
    val sources by vm.sources.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val statusMap by vm.statusMap.collectAsStateWithLifecycle()
    val testingAll by vm.testingAll.collectAsStateWithLifecycle()
    val testSummary by vm.testSummary.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    var tab by remember { mutableIntStateOf(0) }

    var pendingDelete by remember { mutableStateOf<FeedSource?>(null) }
    var editing by remember { mutableStateOf<FeedSource?>(null) }

    // 提示条自动消失，不让它常驻占地方
    LaunchedEffect(notice) {
        if (notice.isNotBlank()) {
            delay(3200)
            vm.clearNotice()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("阅源", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                Text(
                    "${sources.size} 个订阅源 · 从推荐里挑，或粘贴任意 RSS 地址",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.primary)
                Spacer(Modifier.width(10.dp))
            }
            // v2.1：RSSHub 入口。放顶栏是因为它和「+」一样属于「加源」这个动作，
            // 只是加的是微博 / 知乎 / B站这种本来没有 RSS 的站点。
            IconButton(onClick = onOpenRssHub, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Filled.Hub,
                    contentDescription = "从 RSSHub 添加（微博 / 知乎 / B站）",
                    tint = cs.primary
                )
            }
            IconButton(onClick = onOpenAddSource, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "添加阅源", tint = cs.primary)
            }
        }

        // 操作结果提示（订阅了几个源、拉到几篇）
        if (notice.isNotBlank()) {
            Surface(
                color = cs.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(notice, style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
                }
            }
        }

        // v2.2：和闻件页一样，从 Material 的 TabRow 换成与底栏同款的胶囊标签栏。
        PillTabRow(
            items = listOf(PillTab("我的阅源"), PillTab("发现推荐")),
            selectedIndex = tab,
            onSelect = { tab = it }
        )

        if (tab == 0) {
            MySourcesPane(
                sources = sources,
                statusMap = statusMap,
                testingAll = testingAll,
                testSummary = testSummary,
                onAdd = onOpenAddSource,
                onRssHub = onOpenRssHub,
                onToggle = { id, on -> vm.toggleSource(id, on) },
                onEdit = { editing = it },
                onDelete = { pendingDelete = it },
                onTestAll = { vm.testAll() },
                onRefresh = { vm.refreshSource(it) }
            )
        } else {
            DiscoverPane(vm = vm)
        }
    }

    pendingDelete?.let { src ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这个阅源？") },
            text = {
                Text(
                    "「${src.name}」会从列表里移除，同时清掉它抓下来的缓存文章（已收藏的会保留）。\n\n" +
                            "删除后无法撤销，但可以随时重新添加。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeSource(src.id)
                    pendingDelete = null
                    Toast.makeText(context, "已删除「${src.name}」", Toast.LENGTH_SHORT).show()
                }) { Text("删除", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    editing?.let { src ->
        EditSourceDialog(
            source = src,
            status = statusMap[src.id],
            onDismiss = { editing = null },
            onTest = { url -> vm.testOne(src.id, url) },
            onSave = { name, url, cat ->
                vm.updateSource(src.id, name, url, cat)
                editing = null
                Toast.makeText(context, "已更新", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ------------------------------------------------------------------ 我的阅源

@Composable
private fun MySourcesPane(
    sources: List<FeedSource>,
    statusMap: Map<String, SourceStatus>,
    testingAll: Boolean,
    testSummary: String,
    onAdd: () -> Unit,
    onRssHub: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (FeedSource) -> Unit,
    onDelete: (FeedSource) -> Unit,
    onTestAll: () -> Unit,
    onRefresh: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    if (sources.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Icon(Icons.Filled.RssFeed, contentDescription = null, tint = cs.primary, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(14.dp))
            Text("还没有任何阅源", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "去「发现推荐」一键订阅几个精选源，\n或者粘贴一个 RSS 地址。",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onAdd) { Text("粘贴地址添加", color = cs.primary) }
            TextButton(onClick = onRssHub) {
                Icon(Icons.Filled.Hub, contentDescription = null, tint = cs.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("从 RSSHub 添加（微博 / 知乎 / B站）", color = cs.primary)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp)
    ) {
        item(key = "tools") {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${sources.size} 个阅源",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onTestAll, enabled = !testingAll) {
                        if (testingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = cs.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("测试中…", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                        } else {
                            Icon(
                                Icons.Filled.NetworkCheck,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("测试全部", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                        }
                    }
                }
                Text(
                    "「测试全部」只检查每个源还能不能连上、有多少篇文章，不会拉文章，所以几十个源也能一次跑完。" +
                        "\n想单独看某一个源：点它右边的 ↻，它会检查 + 拉取新文章，结果就显示在那一行下面。" +
                        "\n点整行可以改名称 / 地址 / 分类；关掉开关就暂时不刷新它（已抓到的文章仍然留着）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
                // 测试结论一直留着，方便对照着下面的逐行结果看
                if (testSummary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(color = cs.primaryContainer, shape = MaterialTheme.shapes.small) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                testSummary,
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onPrimaryContainer
                            )
                        }
                    }
                }
                // v2.1：RSSHub 入口常驻在工具区 —— 微博 / 知乎 / B站 / 小红书这些
                // 平台本身没有 RSS，很多人不知道还能订，所以别把它藏进二级菜单。
                Spacer(Modifier.height(9.dp))
                Surface(color = cs.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onRssHub)
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Hub, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "RSSHub：把微博 / 知乎 / B站 也变成阅源",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onPrimaryContainer
                            )
                            Text(
                                "这些平台本身没有 RSS，走 RSSHub 就能和别的源放在同一个列表里读",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Text("去添加", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer)
                    }
                }
            }
        }
        items(sources, key = { it.id }) { src ->
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(src) }
                        .padding(start = 18.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(src.name, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${src.category} · ${src.url}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRefresh(src.id) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "检查并刷新这个阅源",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Switch(checked = src.enabled, onCheckedChange = { onToggle(src.id, it) })
                    IconButton(onClick = { onDelete(src) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // 测试结果就落在这行下面，一眼看出是哪一个源出了问题
                statusMap[src.id]?.let { st ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            st.busy -> CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 2.dp,
                                color = cs.primary
                            )
                            st.ok == true -> Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            else -> Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = cs.error,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            st.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                st.busy -> cs.onSurfaceVariant
                                st.ok == true -> cs.primary
                                else -> cs.error
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
            }
        }
    }
}

// ------------------------------------------------------------------ 发现推荐

@Composable
private fun DiscoverPane(vm: SourcesViewModel) {
    val cs = MaterialTheme.colorScheme
    val query by vm.query.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val searchError by vm.searchError.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    // 订阅状态要靠它触发重组
    val sources by vm.sources.collectAsStateWithLifecycle()

    val subscribed = remember(sources) {
        sources.map { it.url.trim().trimEnd('/').lowercase() }.toHashSet()
    }
    fun isSub(url: String) = url.trim().trimEnd('/').lowercase() in subscribed

    val showingSearch = query.isNotBlank()

    // 搜完就把键盘收起来：结果要占满整屏，键盘留着只会挡住一半
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = vm::setQuery,
                // placeholder 必须自己限一行：它不受 singleLine 管辖，
                // 文字一多就会换行把整个输入框顶成两行高（原来就是这个毛病）。
                placeholder = {
                    Text(
                        "搜索订阅源或关键词",
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = cs.onSurfaceVariant) },
                trailingIcon = {
                    when {
                        searching -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.primary)
                        query.isNotBlank() -> IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                    disabledContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    focus.clearFocus()
                    vm.search()
                })
            )
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = {
                    keyboard?.hide()
                    focus.clearFocus()
                    vm.search()
                },
                enabled = query.isNotBlank() && !searching
            ) { Text("搜索") }
        }

        if (showingSearch) {
            SearchResults(
                results = results,
                searching = searching,
                error = searchError,
                isSub = ::isSub,
                onSubscribe = { vm.subscribeRemote(it) }
            )
            return@Column
        }

        // 分类筛选
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = group == null, onClick = { vm.selectGroup(null) }, label = { Text("全部") })
            FeedCatalog.groups.forEach { g ->
                FilterChip(
                    selected = group == g.name,
                    onClick = { vm.selectGroup(if (group == g.name) null else g.name) },
                    label = { Text("${g.emoji} ${g.name}") }
                )
            }
        }

        // ⚠️ 下面这三处都是「纯计算」，但以前写在组合体里 —— 页面里任何一个状态
        // （输入框每敲一个字、订阅状态变化）都会把 36 个源的列表重算好几遍。
        // catalogFor / featured 只跟常量目录表有关，remember 住即可。
        val list = remember(group) { vm.catalogFor(group) }
        val featured = remember { FeedCatalog.featured() }
        val featuredPending = remember(featured, subscribed) { featured.any { !isSub(it.url) } }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)
        ) {
            // 精选区（只在「全部」时出现）：跨分类挑最稳的几家
            if (group == null) {
                item(key = "featured_header") {
                    FeaturedHeader(
                        onSubscribeAll = { vm.subscribeAllFeatured() },
                        anyPending = featuredPending
                    )
                }
                items(featured, key = { "f_" + it.url }) { f ->
                    CatalogRow(feed = f, subscribed = isSub(f.url), onSubscribe = { vm.subscribe(f) })
                }
                item(key = "all_header") {
                    Text(
                        "全部推荐（${FeedCatalog.all().size} 个）",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp)
                    )
                }
                items(FeedCatalog.all(), key = { "a_" + it.url }) { f ->
                    CatalogRow(feed = f, subscribed = isSub(f.url), onSubscribe = { vm.subscribe(f) })
                }
            } else {
                item(key = "group_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$group · ${list.size} 个源",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.subscribeGroup(group!!) }) {
                            Text("整组订阅", color = cs.primary)
                        }
                    }
                }
                items(list, key = { it.url }) { f ->
                    CatalogRow(feed = f, subscribed = isSub(f.url), onSubscribe = { vm.subscribe(f) })
                }
            }

            item(key = "tail_note") {
                Text(
                    "推荐源都是免费的公开 RSS。源站改版或停止更新时会失效，\n" +
                            "可以在「我的阅源」里关掉或删掉；也可以随时搜索或粘贴地址自己加。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun FeaturedHeader(onSubscribeAll: () -> Unit, anyPending: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = cs.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("编辑精选", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Text(
                "长期稳定、更新勤的一批源，挑不出来就先订这几个",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
        TextButton(onClick = onSubscribeAll, enabled = anyPending) {
            Text(if (anyPending) "全部订阅" else "已订阅", color = if (anyPending) cs.primary else cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun CatalogRow(feed: CatalogFeed, subscribed: Boolean, onSubscribe: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(feed.name, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                Spacer(Modifier.width(6.dp))
                Surface(color = cs.surfaceContainerHigh, shape = MaterialTheme.shapes.extraSmall) {
                    Text(
                        feed.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                if (feed.lang != "中文") {
                    Spacer(Modifier.width(4.dp))
                    Surface(color = cs.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                        Text(
                            feed.lang,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                feed.desc,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (subscribed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text("已订阅", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
        } else {
            TextButton(onClick = onSubscribe) { Text("订阅", color = cs.primary) }
        }
    }
    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun SearchResults(
    results: List<RemoteFeed>,
    searching: Boolean,
    error: String,
    isSub: (String) -> Boolean,
    onSubscribe: (RemoteFeed) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    if (searching && results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = cs.primary)
                Spacer(Modifier.height(10.dp))
                Text("正在搜索订阅源…", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp)
    ) {
        if (error.isNotBlank()) {
            item(key = "err") {
                Text(
                    error,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.error,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp)
                )
            }
        }
        if (results.isEmpty() && !searching) {
            item(key = "empty") {
                Text(
                    "没搜到结果。换一个词试试，或者到「我的阅源」里粘贴一个已知的 RSS 地址。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
        items(results, key = { it.url }) { r ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (r.provider.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = cs.surfaceContainerHigh, shape = MaterialTheme.shapes.extraSmall) {
                                Text(
                                    r.provider,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        buildString {
                            if (r.subscribers > 0) append("${r.subscribers} 人订阅 · ")
                            append(r.url)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (r.description.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            r.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (isSub(r.url)) {
                    Text("已订阅", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                } else {
                    TextButton(onClick = { onSubscribe(r) }) { Text("订阅", color = cs.primary) }
                }
            }
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
        }
    }
}

// ------------------------------------------------------------------ 编辑对话框

@Composable
private fun EditSourceDialog(
    source: FeedSource,
    status: SourceStatus?,
    onDismiss: () -> Unit,
    onTest: (String) -> Unit,
    onSave: (String, String, String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var name by remember(source.id) { mutableStateOf(source.name) }
    var url by remember(source.id) { mutableStateOf(source.url) }
    var cat by remember(source.id) { mutableStateOf(source.category) }

    // 「完成」键要把键盘收起来 —— 否则输完地址键盘赖着不走，得去按返回键
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑阅源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text("RSS / Atom 地址") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        focus.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cat,
                    onValueChange = { cat = it },
                    label = { Text("分类（如 科技 / 财经 / 国际）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        focus.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
                // 改完地址先测一下，别等保存完刷新时才发现地址是坏的
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onTest(url) }, enabled = url.isNotBlank() && status?.busy != true) {
                        if (status?.busy == true) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = cs.primary)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("测试这个源", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    }
                    Spacer(Modifier.width(6.dp))
                    if (status != null && status.message.isNotBlank()) {
                        Text(
                            status.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (status.ok == false) cs.error else cs.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    "改名后首页分类胶囊会跟着更新；分类留空则归入「订阅」。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, url, cat) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
