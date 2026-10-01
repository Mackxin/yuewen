package com.example.yuewen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.YuewenApplication
import com.example.yuewen.ui.components.ArticleCard
import com.example.yuewen.ui.components.ArticleListMode
import com.example.yuewen.ui.components.EmptyState
import com.example.yuewen.ui.util.rememberImeDismiss
import com.example.yuewen.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.delay

/**
 * 「闻件 → 搜索」这一栏（v2.0）。
 *
 * 原本是一个独立的底部 Tab（自带「搜索」大标题）；合并进闻件后
 * 标题由外层的 TabRow 提供，这里直接从搜索框开始，把纵向空间留给结果。
 *
 * 关键词会同时匹配 标题 / 摘要 / 已抽取的正文 / 来源名，并且不限分类；
 * 命中的关键词在标题里高亮显示。下面一行来源标签是「只看某个源」的快捷筛选，再点一次取消。
 *
 * ---- v2.4 的两处变化 ----
 *
 * 1. **输入法终于会自己退下去了。** 以前只有 `KeyboardActions(onSearch = { vm.submit(...) })`，
 *    既没有 `keyboard.hide()` 也没有 `clearFocus()` —— 于是打完字点键盘上的「搜索」、
 *    或者直接点一条结果，键盘都赖在屏幕上不动，把结果列表挡住半屏，
 *    只能靠按系统返回键才能收起来。现在：键盘搜索键 / 点结果 / 点来源标签 /
 *    切走这一栏（`DisposableEffect`）/ 被浮层盖住，都会主动收键盘。
 *
 * 2. **可以当浮层用。** 传了 [onBack] 就在最左边画一个返回箭头（首页顶栏的放大镜走这条路），
 *    并配合 [autoFocus] 自动弹键盘 —— 用户点放大镜就是来打字的，再让他点一下输入框是多余的一步。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SearchPane(
    app: YuewenApplication,
    onOpenArticle: (String) -> Unit,
    /** 非空 = 作为**浮层**在用，会在搜索框左边画一个返回箭头。 */
    onBack: (() -> Unit)? = null,
    /** 进来就自动聚焦并弹出键盘（只有浮层模式才该开，标签页里一进来就跳键盘很吓人）。 */
    autoFocus: Boolean = false,
    /** v2.7：底栏高度。作为「闻件」标签页时由 [WenjianScreen] 传进来（底栏透明，内容要让它）。 */
    bottomInset: Dp = 0.dp
) {
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.provide(app))
    val query by vm.query.collectAsStateWithLifecycle()
    val sourceFilter by vm.sourceFilter.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val modeKey by app.settingsRepository.listModeFlow.collectAsStateWithLifecycle("card")
    val mode = ArticleListMode.of(modeKey)
    val cs = MaterialTheme.colorScheme

    // 收键盘的统一入口（取消焦点 + 明确 hide，两件事都要做，见 Ime.kt）
    val dismissIme = rememberImeDismiss()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val isOverlay = onBack != null

    val active = query.isNotBlank() || sourceFilter != null

    // 浮层模式：进来就把焦点给搜索框，并把键盘弹出来。
    // 那个 delay 不是凑数 —— 组合刚结束、TextField 还没真正挂到窗口上时
    // requestFocus() 会直接抛 IllegalStateException，等一帧布局最稳。
    LaunchedEffect(autoFocus) {
        if (!autoFocus) return@LaunchedEffect
        delay(150)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    // 离开这一栏（切到收藏 / 历史 / 底部别的 Tab / 打开文章盖上来）时收掉键盘。
    // 只靠 TextField 自己被回收是不够的 —— 输入法是系统窗口，Compose 不会替你关。
    DisposableEffect(Unit) {
        onDispose { dismissIme() }
    }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 有返回箭头时左边距收窄（箭头自己带 12dp 内边距），免得整行被顶得偏右
                .padding(start = if (isOverlay) 4.dp else 14.dp, end = 14.dp)
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = {
                        dismissIme()
                        onBack()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = cs.onSurfaceVariant
                    )
                }
            }
            TextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("搜标题、摘要、正文或来源", color = cs.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = cs.onSurfaceVariant) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        // 清空**不收键盘**：清空的下一步多半是重新输一个词
                        TextButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                    disabledContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // v2.4：搜完顺手收键盘。以前这里是空有 submit、键盘纹丝不动。
                keyboardActions = KeyboardActions(onSearch = {
                    vm.submit(query)
                    dismissIme()
                })
            )
        }

        // 来源快捷筛选（横排可横向滚动，不挤占列表）
        if (sources.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    item {
                        val selected = sourceFilter == null
                        FilterChip(
                            selected = selected,
                            onClick = { vm.clearSourceFilter() },
                            label = { Text("全部") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cs.primaryContainer,
                                selectedLabelColor = cs.onPrimaryContainer
                            )
                        )
                    }
                    items(sources, key = { it.id }) { s ->
                        val selected = sourceFilter == s.name
                        FilterChip(
                            selected = selected,
                            onClick = { vm.toggleSourceFilter(s.name) },
                            label = { Text(s.name) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.RssFeed,
                                    contentDescription = null,
                                    tint = if (selected) cs.onPrimaryContainer else cs.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cs.primaryContainer,
                                selectedLabelColor = cs.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        if (!active) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).verticalScroll(rememberScrollState())
            ) {
                if (recent.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("最近搜索", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recent.forEach { r ->
                            FilterChip(
                                selected = false,
                                // 点历史词 = 词填好、结果出来，人接下来要看的是列表，不是键盘
                                onClick = {
                                    vm.setQuery(r)
                                    vm.submit(r)
                                    dismissIme()
                                },
                                label = { Text(r) }
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                Text(
                    "搜索范围是已经缓存到本地的文章：标题、摘要、已抓取的正文、来源名都会匹配，不区分分类。\n" +
                            "在首页多刷新几次，能搜到的内容就越多。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        } else if (results.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = "没有找到相关内容",
                subtitle = "换个关键词试试；搜索只覆盖已经缓存到本地的文章，\n在首页刷新一次能拿到更多内容。"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = bottomInset + 20.dp)
            ) {
                item(key = "result_count") {
                    Text(
                        "${results.size} 条结果",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp)
                    )
                }
                items(results, key = { it.link }) { a ->
                    ArticleCard(
                        article = a,
                        mode = mode,
                        // 点进文章前先收键盘：文章页不想要键盘，等退回来时它也不该还在
                        onClick = {
                            dismissIme()
                            onOpenArticle(a.link)
                        },
                        onBookmark = { vm.toggleBookmark(a.link, !a.isBookmarked) },
                        highlight = query.takeIf { it.isNotBlank() },
                        modifier = Modifier.padding(bottom = if (mode == ArticleListMode.Compact) 6.dp else 11.dp)
                    )
                }
            }
        }
    }
}
