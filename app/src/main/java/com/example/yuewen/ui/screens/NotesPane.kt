package com.example.yuewen.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.model.Note
import com.example.yuewen.ui.components.EmptyState
import com.example.yuewen.ui.util.formatRelativeTime
import com.example.yuewen.ui.viewmodel.NotesViewModel

/**
 * 「闻件 → 笔记」这一栏（v2.0）。
 *
 * 用户在正文里划一段话、写一句想法，都会落到这里。
 * 每条笔记都保留 [Note.articleTitle] 与 [Note.link] 的**快照**，
 * 所以就算那篇文章后来被清缓存删掉了，笔记本身依然是完整、可追溯的。
 */
@Composable
fun NotesPane(app: YuewenApplication, onOpenArticle: (String) -> Unit) {
    val vm: NotesViewModel = viewModel(factory = NotesViewModel.provide(app))
    val all by vm.notes.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    val notes = vm.filtered(all, query)

    var editing by remember { mutableStateOf<Note?>(null) }
    var deleting by remember { mutableStateOf<Note?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("搜摘录、批注或文章标题", color = cs.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = cs.onSurfaceVariant) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        TextButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                    disabledContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        if (notes.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.Notes,
                title = if (all.isEmpty()) "还没有摘录或笔记" else "没有匹配的笔记",
                subtitle = if (all.isEmpty()) {
                    "在文章正文里长按选中一段话，\n就能「摘录」下来或写一句自己的想法"
                } else {
                    "换个关键词试试"
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp)
            ) {
                item(key = "count") {
                    Text(
                        "${notes.size} 条${if (query.isBlank()) "" else "（共 ${all.size} 条）"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }
                items(notes, key = { it.id }) { n ->
                    NoteCard(
                        note = n,
                        onOpen = { if (n.link.isNotBlank()) onOpenArticle(n.link) },
                        onEdit = { editing = n },
                        onDelete = { deleting = n },
                        onShare = {
                            val text = buildString {
                                if (n.quote.isNotBlank()) append("「").append(n.quote).append("」\n\n")
                                if (n.note.isNotBlank()) append(n.note).append("\n\n")
                                if (n.articleTitle.isNotBlank()) append("—— ").append(n.articleTitle)
                                if (n.link.isNotBlank()) append("\n").append(n.link)
                            }
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            runCatching { context.startActivity(Intent.createChooser(i, "分享笔记")) }
                        }
                    )
                }
            }
        }
    }

    editing?.let { n ->
        var text by remember(n.id) { mutableStateOf(n.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑批注") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (n.quote.isNotBlank()) {
                        Surface(color = cs.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                            Text(
                                n.quote,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("我的批注") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateNoteText(n, text)
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }
        )
    }

    deleting?.let { n ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条笔记？") },
            text = { Text("删除后无法撤销。文章本身不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(n.id)
                    deleting = null
                }) { Text("删除", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(cs.surface)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 8.dp)
    ) {
        // 摘录（划线原文）：左侧一条主色竖线，视觉上就是「引文」
        if (note.quote.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(48.dp)
                        .background(cs.primary, MaterialTheme.shapes.extraSmall)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    note.quote,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = cs.onSurface,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (note.note.isNotBlank()) {
            Text(
                note.note,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
                modifier = Modifier.padding(start = if (note.quote.isNotBlank()) 13.dp else 0.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    note.articleTitle.ifBlank { "（原文已不在本地）" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (note.sourceName.isNotBlank()) append(note.sourceName).append(" · ")
                        append(formatRelativeTime(note.createdAt))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Share, contentDescription = "分享笔记", tint = cs.onSurfaceVariant, modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.EditNote, contentDescription = "编辑批注", tint = cs.secondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "删除", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}
