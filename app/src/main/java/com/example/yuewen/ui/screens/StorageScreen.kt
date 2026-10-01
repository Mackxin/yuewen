package com.example.yuewen.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.YuewenApplication
import com.example.yuewen.ui.viewmodel.CacheInfo
import com.example.yuewen.ui.viewmodel.SettingsViewModel

/** 待确认的清理动作。用枚举而不是三个 boolean，避免同时弹出多个对话框。 */
private enum class PendingClear { None, Image, Unbookmarked, FullText }

/**
 * 缓存管理。
 *
 * 之前只有一个笼统的「清除缓存」按钮，用户不知道清的是什么、会清掉多少。
 * 这里把三类缓存拆开显示占用，并各自提供清理入口：
 * 1. **图片缓存** —— Coil 的磁盘/内存缓存（看过的缩略图与正文配图）
 * 2. **未收藏文章** —— 数据库里非收藏的文章（收藏永远不动）
 * 3. **正文全文** —— 本地抽取的正文，清掉后下次打开会重新联网抓
 */
@Composable
fun StorageScreen(app: YuewenApplication, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.provide(app))
    val info by vm.cacheInfo.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var pending by remember { mutableStateOf(PendingClear.None) }

    LaunchedEffect(Unit) { vm.loadCacheInfo() }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Surface(color = cs.background, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = cs.onBackground)
                }
                Text(
                    "缓存管理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onBackground
                )
                Spacer(Modifier.weight(1f))
                if (info.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.primary)
                    Spacer(Modifier.width(12.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 24.dp)
        ) {
            // ---- 图片缓存 ----
            CacheCard(
                icon = Icons.Filled.Image,
                tint = cs.primary,
                title = "图片缓存",
                primary = formatSize(info.imageDisk),
                caption = if (info.imageDiskMax > 0) "磁盘上限 ${formatSize(info.imageDiskMax)}" else "磁盘缓存未启用",
                lines = listOf(
                    "内存占用" to formatSize(info.imageMem.toLong()),
                    "内存上限" to formatSize(info.imageMemMax.toLong())
                ),
                actionLabel = "清理图片缓存",
                actionEnabled = info.imageDisk > 0 || info.imageMem > 0,
                onAction = { pending = PendingClear.Image }
            )

            Spacer(Modifier.height(12.dp))

            // ---- 文章缓存 ----
            CacheCard(
                icon = Icons.Filled.Storage,
                tint = cs.secondary,
                title = "文章缓存",
                primary = "${info.articles} 篇",
                caption = "其中未收藏 ${info.unbookmarked} 篇可清理",
                lines = listOf(
                    "收藏文章" to "${info.articles - info.unbookmarked} 篇（永远不会被清理）"
                ),
                actionLabel = "清理未收藏文章",
                actionEnabled = info.unbookmarked > 0,
                onAction = { pending = PendingClear.Unbookmarked }
            )

            Spacer(Modifier.height(12.dp))

            // ---- 正文缓存 ----
            CacheCard(
                icon = Icons.Filled.AutoStories,
                tint = cs.tertiary,
                title = "正文全文",
                primary = "${info.fullTextCount} 篇",
                caption = if (info.fullTextCount > 0) "约 ${info.fullTextChars / 1000} 千字" else "还没有抽取过正文",
                lines = listOf(
                    "作用" to "阅读模式直接读全文，不用跳浏览器"
                ),
                actionLabel = "清理正文缓存",
                actionEnabled = info.fullTextCount > 0,
                onAction = { pending = PendingClear.FullText }
            )

            Spacer(Modifier.height(16.dp))
            Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "清理只会删除缓存，不会动你的订阅源、收藏与阅读设置。" +
                                "清理后首页重新下拉刷新即可把文章拉回来（收藏与已读状态保留）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ---- 二次确认 ----
    when (pending) {
        PendingClear.None -> Unit

        PendingClear.Image -> ConfirmDialog(
            title = "清理图片缓存",
            text = "将清掉已下载的缩略图与配图（约 ${formatSize(info.imageDisk)}）。" +
                    "之后浏览文章会重新联网加载图片，不会影响文章内容与收藏。",
            confirmText = "清理",
            onDismiss = { pending = PendingClear.None },
            onConfirm = {
                pending = PendingClear.None
                vm.clearImageCache { freed ->
                    Toast.makeText(context, "已释放 ${formatSize(freed)}", Toast.LENGTH_SHORT).show()
                }
            }
        )

        PendingClear.Unbookmarked -> ConfirmDialog(
            title = "清理未收藏文章",
            text = "将删除本地缓存中未收藏的 ${info.unbookmarked} 篇文章。\n" +
                    "收藏的文章不会被动；已读记录会被一并清掉，重新刷新后文章会回到「未读」。",
            confirmText = "清理",
            onDismiss = { pending = PendingClear.None },
            onConfirm = {
                pending = PendingClear.None
                vm.clearUnbookmarked { n ->
                    Toast.makeText(context, "已清理 $n 篇文章", Toast.LENGTH_SHORT).show()
                }
            }
        )

        PendingClear.FullText -> ConfirmDialog(
            title = "清理正文缓存",
            text = "将清掉 ${info.fullTextCount} 篇已抽取的正文全文。\n" +
                    "下次打开这些文章时会重新联网抓取，文章列表与阅读进度不受影响。",
            confirmText = "清理",
            onDismiss = { pending = PendingClear.None },
            onConfirm = {
                pending = PendingClear.None
                vm.clearFullTextCache { n ->
                    Toast.makeText(context, "已清理 $n 篇正文", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun CacheCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    primary: String,
    caption: String,
    lines: List<Pair<String, String>>,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Spacer(Modifier.weight(1f))
                Text(
                    primary,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)

            lines.forEach { (k, v) ->
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.labelMedium, color = cs.onSurface)
                }
            }

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onAction, enabled = actionEnabled) {
                Icon(
                    Icons.Filled.CleaningServices,
                    contentDescription = null,
                    tint = if (actionEnabled) tint else cs.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    actionLabel,
                    color = if (actionEnabled) tint else cs.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(title) },
        text = { Text(text) }
    )
}

/** 字节数转人话：1.2 MB / 340 KB / 0 B。 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
