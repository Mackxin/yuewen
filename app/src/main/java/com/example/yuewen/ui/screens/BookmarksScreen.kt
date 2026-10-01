package com.example.yuewen.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.YuewenApplication
import com.example.yuewen.ui.components.ArticleCard
import com.example.yuewen.ui.components.ArticleListMode
import com.example.yuewen.ui.components.EmptyState
import com.example.yuewen.ui.viewmodel.BookmarksViewModel

/** 收藏夹的默认名字。代码里多处依赖这个常量做兜底，所以它不能被改名或删除。 */
private const val DEFAULT_FOLDER = "默认"

/**
 * 「闻件 → 收藏」这一栏（v2.0 从原 BookmarksScreen 里拆出来）。
 *
 * 原来它是一个带 TabRow 的独立页面（收藏 / 历史）；现在外层 [WenjianScreen]
 * 统一管 Tab，这里只负责「收藏」这一件事，顶部那行只留「管理收藏夹」入口。
 */
@Composable
fun BookmarksPane(app: YuewenApplication, onOpenArticle: (String) -> Unit) {
    val vm: BookmarksViewModel = viewModel(factory = BookmarksViewModel.provide(app))
    val list by vm.bookmarks.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val selectedFolder by vm.selectedFolder.collectAsStateWithLifecycle()
    val sizes by vm.folderSizes.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    // 收藏页沿用首页选定的布局，保持一致
    val modeKey by app.settingsRepository.listModeFlow.collectAsStateWithLifecycle("card")
    val mode = ArticleListMode.of(modeKey)
    val context = LocalContext.current

    var moveTarget by remember { mutableStateOf<String?>(null) }
    var showManage by remember { mutableStateOf(false) }

    val allFolders = listOf("全部") + folders

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        // 收藏夹筛选 + 管理入口
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allFolders.forEach { f ->
                    FilterChip(
                        selected = selectedFolder == f,
                        onClick = { vm.selectFolder(f) },
                        label = { Text(f) }
                    )
                }
            }
            if (folders.isNotEmpty()) {
                IconButton(onClick = {
                    vm.loadFolderSizes()
                    showManage = true
                }) {
                    Icon(Icons.Filled.Tune, contentDescription = "管理收藏夹", tint = cs.primary)
                }
            }
        }

        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Bookmark,
                title = if (selectedFolder == "全部") "还没有收藏" else "这个收藏夹还是空的",
                subtitle = if (selectedFolder == "全部") {
                    "在文章卡片右下角点一下书签图标，\n或者在首页长按文章选择「收藏到收藏夹」"
                } else {
                    "长按文章 →「移动到收藏夹」，就能把它放进来"
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)
            ) {
                items(list, key = { it.link }) { a ->
                    ArticleCard(
                        article = a,
                        mode = mode,
                        onClick = { onOpenArticle(a.link) },
                        onBookmark = { vm.toggleBookmark(a.link, false) },
                        onLongClick = { moveTarget = a.link },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
        }
    }

    // 长按 → 移动到收藏夹
    if (moveTarget != null) {
        MoveFolderDialog(
            folders = folders,
            onDismiss = { moveTarget = null },
            onPick = { folder ->
                vm.moveToFolder(moveTarget!!, folder)
                moveTarget = null
                Toast.makeText(context, "已移到「$folder」", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 管理收藏夹
    if (showManage) {
        FolderManageSheet(
            folders = folders,
            sizes = sizes,
            onDismiss = { showManage = false },
            onRename = { from, to -> vm.renameFolder(from, to); Toast.makeText(context, "已改名为「$to」", Toast.LENGTH_SHORT).show() },
            onMove = { index, delta -> vm.moveFolder(index, delta) },
            onDissolve = { vm.dissolveFolder(it); Toast.makeText(context, "「$it」里的文章已移回「$DEFAULT_FOLDER」", Toast.LENGTH_SHORT).show() },
            onDelete = { vm.deleteFolder(it); Toast.makeText(context, "已删除收藏夹「$it」", Toast.LENGTH_SHORT).show() }
        )
    }
}

/** 「闻件 → 历史」这一栏。 */
@Composable
fun HistoryPane(app: YuewenApplication, onOpenArticle: (String) -> Unit) {
    val vm: BookmarksViewModel = viewModel(factory = BookmarksViewModel.provide(app))
    val history by vm.history.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val modeKey by app.settingsRepository.listModeFlow.collectAsStateWithLifecycle("card")
    val mode = ArticleListMode.of(modeKey)
    val context = LocalContext.current
    var showClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "最近读过 ${history.size} 篇",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showClear = true }) { Text("清除历史", color = cs.primary) }
            }
        }

        if (history.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Schedule,
                title = "还没有阅读记录",
                subtitle = "打开过的文章会自动记到这里，方便随时回看"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
            ) {
                items(history, key = { it.link }) { a ->
                    ArticleCard(
                        article = a,
                        mode = mode,
                        onClick = { onOpenArticle(a.link) },
                        onBookmark = { vm.toggleBookmark(a.link, !a.isBookmarked) },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory { n -> Toast.makeText(context, "已清除 $n 条记录", Toast.LENGTH_SHORT).show() }
                    showClear = false
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } },
            title = { Text("清除阅读历史") },
            text = { Text("会清空「历史」列表，但不会影响收藏，文章仍保留在首页。") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveFolderDialog(folders: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var newName by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = { onPick(newName.trim()) }
            ) { Text("新建并移动") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("移动到收藏夹") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(folders, key = { it }) { f ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(f) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Bookmark, contentDescription = null, tint = cs.primary, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(f, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("或新建收藏夹（如 科技 / 周刊）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

// ---------------- 收藏夹管理面板 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderManageSheet(
    folders: List<String>,
    sizes: Map<String, Int>,
    onDismiss: () -> Unit,
    onRename: (String, String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDissolve: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = cs.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "管理收藏夹",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "↑↓ 调顺序，点铅笔改名，点垃圾桶删除。",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            folders.forEachIndexed { index, folder ->
                val isDefault = folder == DEFAULT_FOLDER
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            folder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${sizes[folder] ?: 0} 篇" + if (isDefault) " · 默认收藏夹，不可改名或删除" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { onMove(index, -1) }, enabled = index > 0) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "上移",
                            tint = if (index > 0) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(onClick = { onMove(index, 1) }, enabled = index < folders.lastIndex) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = "下移",
                            tint = if (index < folders.lastIndex) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(onClick = { if (!isDefault) renaming = folder }, enabled = !isDefault) {
                        Icon(
                            Icons.Filled.EditNote,
                            contentDescription = "重命名",
                            tint = if (isDefault) cs.onSurfaceVariant.copy(alpha = 0.3f) else cs.secondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(onClick = { if (!isDefault) deleting = folder }, enabled = !isDefault) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "删除",
                            tint = if (isDefault) cs.onSurfaceVariant.copy(alpha = 0.3f) else cs.error,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                if (index != folders.lastIndex) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }

    // 改名
    renaming?.let { old ->
        var text by remember(old) { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() && text.trim() != old,
                    onClick = { onRename(old, text); renaming = null }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
            title = { Text("重命名收藏夹") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("收藏夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // 删除：给两种口径，避免用户不知道「收藏还在不在」
    deleting?.let { folder ->
        val n = sizes[folder] ?: 0
        AlertDialog(
            onDismissRequest = { deleting = null },
            confirmButton = {
                TextButton(onClick = { onDelete(folder); deleting = null }) {
                    Text("取消收藏", color = cs.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDissolve(folder); deleting = null }) {
                    Text("移回「$DEFAULT_FOLDER」")
                }
            },
            title = { Text("删除「$folder」") },
            text = {
                Text(
                    "这个收藏夹里有 $n 篇文章，你想怎么处理？\n\n" +
                            "· 移回「$DEFAULT_FOLDER」：保留收藏，只是不再分类\n" +
                            "· 取消收藏：把这些文章从收藏里移除（文章仍在首页）"
                )
            }
        )
    }
}
