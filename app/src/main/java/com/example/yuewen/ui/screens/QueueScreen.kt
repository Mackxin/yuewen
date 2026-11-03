package com.example.yuewen.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.YuewenApplication
import com.example.yuewen.ui.components.ArticleActionsSheet
import com.example.yuewen.ui.components.ArticleCard
import com.example.yuewen.ui.components.ArticleListMode
import com.example.yuewen.ui.components.CardTrailing
import com.example.yuewen.ui.components.EmptyState
import com.example.yuewen.ui.util.BrowserLauncher
import com.example.yuewen.ui.viewmodel.QueueViewModel

/**
 * 「闻件 → 稍后读」这一栏（v2.8）。
 *
 * 它和「收藏」的区别，也是这一页值得单独存在的原因：
 * - 收藏是**归档** —— 「这篇我要留着」，会越堆越多，没有终点；
 * - 稍后读是**待办** —— 「这篇我待会儿读」，读一篇少一篇。
 *
 * 所以：默认「先加入的排在最前」（先排的先读，队列语义），
 * 每张卡片右侧那颗按钮是「移出队列」而不是「收藏」，
 * 整页的读法就是**从上往下一条条消灭掉**。
 *
 * 队列项**不会被清缓存 / 删阅源带走**（见 ArticleDao 里那三处 `queuedAt = 0`），
 * 这是它可以当待办用的前提 —— 不然排了两周的队列某次刷新就没了。
 */
@Composable
fun QueuePane(app: YuewenApplication, onOpenArticle: (String) -> Unit, bottomInset: Dp = 0.dp) {
    val vm: QueueViewModel = viewModel(factory = QueueViewModel.provide(app))
    val list by vm.items.collectAsStateWithLifecycle()
    val newestFirst by vm.newestFirst.collectAsStateWithLifecycle()
    val autoDequeue by vm.autoDequeue.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val modeKey by app.settingsRepository.listModeFlow.collectAsStateWithLifecycle("card")
    val mode = ArticleListMode.of(modeKey)
    val browserPkg by app.settingsRepository.browserPkgFlow.collectAsStateWithLifecycle("")

    var actionTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }

    val readCount = list.count { it.isRead }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {

        if (list.isNotEmpty()) {
            // ---- 顶部：条数 + 排序方向 + 折叠说明入口 ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildString {
                        append("${list.size} 篇待读")
                        if (readCount > 0) append(" · 其中 $readCount 篇已读")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // 「先加的在前」= 队列语义（默认）；「最新在前」= 收藏夹语义。
                // 用一个可点的胶囊而不是开关：两种说法各自都读得懂，
                // 比「排序方向：开 / 关」清楚得多。
                FilterChip(
                    selected = newestFirst,
                    onClick = { vm.setNewestFirst(!newestFirst) },
                    label = { Text(if (newestFirst) "最新在前" else "先加的在前") },
                    leadingIcon = {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                )
                IconButton(onClick = { helpOpen = !helpOpen }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (helpOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (helpOpen) "收起说明" else "展开说明",
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (helpOpen) {
                Text(
                    "「稍后读」是待办，「收藏」是归档 —— 读一篇少一篇。" +
                            "点开文章读完，它会自动出队" +
                            (if (autoDequeue) "（可在设置里关掉）" else "（目前关着，读完不会自动移出）") +
                            "；右边那颗「书签带减号」的按钮是立刻移出。" +
                            "队列里的文章不会被清缓存或删阅源带走。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 4.dp)
                )
            }

            // ---- 批量操作 ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        vm.clearRead { n ->
                            Toast.makeText(
                                context,
                                if (n > 0) "已移出 $n 篇读完的" else "队列里还没有读完的",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = readCount > 0
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("清理已读")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmClear = true }) {
                    Text("清空队列", color = cs.error)
                }
            }
        }

        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Bookmarks,
                title = "稍后读是空的",
                subtitle = "在首页长按文章 →「加入稍后读」，\n" +
                        "或者进文章详情页点顶栏的「稍后读」按钮。\n\n" +
                        "排进来的文章不会被清缓存或删阅源带走，\n" +
                        "可以放心当待办清单用。"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = bottomInset + 16.dp)
            ) {
                items(list, key = { it.link }) { a ->
                    ArticleCard(
                        article = a,
                        mode = mode,
                        onClick = { onOpenArticle(a.link) },
                        // 这一页的右下角按钮 = 移出队列（不是收藏）
                        onBookmark = { vm.remove(a.link) },
                        trailing = CardTrailing.Queue,
                        onLongClick = { actionTarget = a.link },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
        }
    }

    // 长按 → 操作面板。复用首页那一套，语义完全一致，只是多一行「移出稍后读」。
    val target = actionTarget?.let { link -> list.firstOrNull { it.link == link } }
    if (target != null) {
        ArticleActionsSheet(
            article = target,
            onDismiss = { actionTarget = null },
            onToggleRead = {
                vm.setRead(target.link, !target.isRead)
                actionTarget = null
            },
            onToggleBookmark = {
                vm.setBookmarked(target.link, !target.isBookmarked)
                actionTarget = null
            },
            // 队列页特有：长按面板上也给一个「移出稍后读」
            isQueued = true,
            onToggleQueue = {
                vm.remove(target.link)
                actionTarget = null
            },
            onMoveFolder = null,
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
                if (!BrowserLauncher.open(context, target.link, browserPkg)) {
                    Toast.makeText(context, "找不到可以打开网页的应用", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll { n ->
                        Toast.makeText(context, "已移出 $n 篇稍后读", Toast.LENGTH_SHORT).show()
                    }
                    confirmClear = false
                }) { Text("清空", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
            title = { Text("清空稍后读") },
            text = {
                Text(
                    "会把队列里的 ${list.size} 篇全部移出。\n\n" +
                            "这只是取消「待读」标记 —— 文章本身和收藏都不受影响，" +
                            "仍能在首页、收藏或历史里找到。"
                )
            }
        )
    }
}
