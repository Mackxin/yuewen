package com.example.yuewen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yuewen.data.model.Article
import com.example.yuewen.ui.util.formatRelativeTime

/**
 * 长按文章弹出的操作面板。
 *
 * 为什么用「长按 + 底部面板」而不是列表项左右滑动？
 * 首页本身是横向可滑动的 Pager（左右滑切 Tab），列表项再吃横向手势会互相打架，
 * 手势冲突会让两边都不跟手。长按是零冲突的方案，也是 Feeder / Read You 的常见做法。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleActionsSheet(
    article: Article,
    onDismiss: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleBookmark: () -> Unit,
    onMoveFolder: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            // 顶部：先让用户确认「我长按的是哪一篇」
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${article.sourceName} · ${formatRelativeTime(article.pubDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = cs.outlineVariant)
            Spacer(Modifier.height(4.dp))

            ActionRow(
                label = if (article.isRead) "标为未读" else "标为已读",
                icon = if (article.isRead) Icons.Filled.MarkEmailRead else Icons.Filled.Done,
                onClick = onToggleRead
            )
            ActionRow(
                label = if (article.isBookmarked) "取消收藏" else "收藏到收藏夹",
                icon = Icons.Filled.Bookmark,
                highlighted = article.isBookmarked,
                onClick = onToggleBookmark
            )
            ActionRow("移动到收藏夹", Icons.Filled.Folder, onClick = onMoveFolder)
            ActionRow("分享", Icons.Filled.Share, onClick = onShare)
            ActionRow("复制链接", Icons.Filled.ContentCopy, onClick = onCopyLink)
            ActionRow("在浏览器打开原文", Icons.Filled.OpenInBrowser, onClick = onOpenInBrowser)
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val tint = if (highlighted) cs.tertiary else cs.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlighted) cs.tertiary else cs.onSurface,
            fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * 收藏夹选择弹窗：可选已有收藏夹，也能直接新建一个。
 * 首页长按菜单和收藏页长按共用这一个。
 */
@Composable
fun FolderPickerDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var newName by remember { mutableStateOf("") }

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
                if (folders.isEmpty()) {
                    Text(
                        "还没有收藏夹，在下面输入一个名字就能新建。",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                } else {
                    folders.forEach { f ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onPick(f) }
                                .padding(horizontal = 8.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = cs.tertiary, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(f, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
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
