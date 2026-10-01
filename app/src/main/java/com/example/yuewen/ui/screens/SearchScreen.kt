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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.YuewenApplication
import com.example.yuewen.ui.components.ArticleCard
import com.example.yuewen.ui.components.ArticleListMode
import com.example.yuewen.ui.components.EmptyState
import com.example.yuewen.ui.viewmodel.SearchViewModel

/**
 * 「闻件 → 搜索」这一栏（v2.0）。
 *
 * 原本是一个独立的底部 Tab（自带「搜索」大标题）；合并进闻件后
 * 标题由外层的 TabRow 提供，这里直接从搜索框开始，把纵向空间留给结果。
 *
 * 关键词会同时匹配 标题 / 摘要 / 已抽取的正文 / 来源名，并且不限分类；
 * 命中的关键词在标题里高亮显示。下面一行来源标签是「只看某个源」的快捷筛选，再点一次取消。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SearchPane(app: YuewenApplication, onOpenArticle: (String) -> Unit) {
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.provide(app))
    val query by vm.query.collectAsStateWithLifecycle()
    val sourceFilter by vm.sourceFilter.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val modeKey by app.settingsRepository.listModeFlow.collectAsStateWithLifecycle("card")
    val mode = ArticleListMode.of(modeKey)
    val cs = MaterialTheme.colorScheme

    val active = query.isNotBlank() || sourceFilter != null

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("搜标题、摘要、正文或来源", color = cs.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = cs.onSurfaceVariant) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        TextButton(onClick = { vm.setQuery("") }) {
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
                keyboardActions = KeyboardActions(onSearch = { vm.submit(query) })
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
                                onClick = { vm.setQuery(r); vm.submit(r) },
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
                contentPadding = PaddingValues(bottom = 20.dp)
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
                        onClick = { onOpenArticle(a.link) },
                        onBookmark = { vm.toggleBookmark(a.link, !a.isBookmarked) },
                        highlight = query.takeIf { it.isNotBlank() },
                        modifier = Modifier.padding(bottom = if (mode == ArticleListMode.Compact) 6.dp else 11.dp)
                    )
                }
            }
        }
    }
}
