package com.example.yuewen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.example.yuewen.data.update.ApkInstaller
import com.example.yuewen.data.update.UpdateInfo
import com.example.yuewen.ui.viewmodel.UpdateUiState

/**
 * 「检查更新」对话框（v2.14）。
 *
 * 数据来源是 GitHub Releases —— 这个 App 本来就在 GitHub 上开发，
 * 每次发版打一个 tag 就是天然的发布记录，不用再维护一个服务器。
 *
 * ## 为什么每个状态都要有独立的文案，而不是共用一句「请稍候」
 *
 * 更新这个动作里有好几次「需要用户等」，但等的东西完全不一样：
 * 在问 GitHub、在下 4MB、下好了等系统装。如果都显示一个转圈，
 * 用户分不清「是不是卡住了」，就会反复点按钮。
 * 所以这里每一态都给出**具体在做什么**（含 MB 和百分比）。
 *
 * ## 为什么「去开启权限」要在对话框里，而不是拉个系统弹窗
 *
 * API 26+ 的「安装未知应用」是**每应用一个开关**，系统没有运行时的申请弹窗，
 * 只能跳到设置页。跳走之前如果不告诉用户为什么要跳，回来的概率很低。
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    currentVersion: String,
    onCheck: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onInstallAgain: () -> Boolean,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    /**
     * 「允许安装未知应用」这个开关的当前值。
     *
     * 用户去系统设置里打开开关再回来时，Compose 不会自动重组 ——
     * 所以下面用 [LifecycleEventEffect] 在每次回到前台时重新读一遍。
     * 少了这一步，用户明明已经授权了，界面上还挂着「去开启权限」，很莫名其妙。
     */
    var canInstall by remember { mutableStateOf(ApkInstaller.canInstall(context)) }
    // 只是用来强制重组（读一次 canInstall），值本身没人看
    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canInstall = ApkInstaller.canInstall(context)
        permissionTick++
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("检查更新") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (state) {
                    is UpdateUiState.Idle, is UpdateUiState.Checking -> CheckingBody()

                    is UpdateUiState.Latest -> LatestBody(state.current)

                    is UpdateUiState.Available -> AvailableBody(
                        info = state.info,
                        currentVersion = currentVersion,
                        canInstall = canInstall,
                        onOpenPermission = { ApkInstaller.openInstallPermissionSettings(context) }
                    )

                    is UpdateUiState.Downloading -> DownloadingBody(state)

                    is UpdateUiState.Ready -> ReadyBody(
                        info = state.info,
                        canInstall = canInstall,
                        onOpenPermission = { ApkInstaller.openInstallPermissionSettings(context) }
                    )

                    is UpdateUiState.Failed -> FailedBody(state.message)
                }

                // 无论哪一态，底部都给一行「当前版本」，用户不用退出去看设置页
                Spacer(Modifier.height(14.dp))
                Text(
                    "当前版本 v$currentVersion",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                // permissionTick 只是为了让编译器别把它优化掉（它参与了上面的 remember 刷新）
                if (permissionTick < 0) Text("")
            }
        },
        confirmButton = {
            when (state) {
                is UpdateUiState.Idle, is UpdateUiState.Checking -> {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }

                is UpdateUiState.Latest -> {
                    TextButton(onClick = onCheck) { Text("再查一次") }
                }

                is UpdateUiState.Available -> {
                    TextButton(
                        onClick = {
                            // 没授权也能先下 —— 下载是本地行为，授权随时可以补。
                            // 下完停在「去开启权限」那一步，比让人先跳设置再回来点下载更顺。
                            onDownload(state.info)
                        }
                    ) { Text("下载并安装") }
                }

                is UpdateUiState.Downloading -> {
                    TextButton(onClick = onDismiss) { Text("取消下载") }
                }

                is UpdateUiState.Ready -> {
                    if (canInstall) {
                        TextButton(onClick = { onInstallAgain() }) { Text("安装") }
                    } else {
                        TextButton(
                            onClick = { ApkInstaller.openInstallPermissionSettings(context) }
                        ) { Text("去开启权限") }
                    }
                }

                is UpdateUiState.Failed -> {
                    TextButton(onClick = onCheck) { Text("重试") }
                }
            }
        },
        dismissButton = {
            when (state) {
                // 「已是最新」时主按钮就是「再查一次」，右边再给个「好」收尾
                is UpdateUiState.Latest -> TextButton(onClick = onDismiss) { Text("好") }
                is UpdateUiState.Available,
                is UpdateUiState.Downloading,
                is UpdateUiState.Ready,
                is UpdateUiState.Failed -> TextButton(onClick = onDismiss) { Text("关闭") }
                else -> Unit
            }
        }
    )
}

// ---------------- 各状态的内容 ----------------

@Composable
private fun CheckingBody() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("正在问 GitHub 有没有新版本…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LatestBody(current: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "已经是最新版 v$current",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AvailableBody(
    info: UpdateInfo,
    currentVersion: String,
    canInstall: Boolean,
    onOpenPermission: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.SystemUpdate,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "发现新版本 v${info.versionName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "v$currentVersion → v${info.versionName} · ${info.sizeText()}",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    NotesBlock(info.notes)

    if (!canInstall) {
        Spacer(Modifier.height(12.dp))
        PermissionHint(onOpenPermission)
    }
}

@Composable
private fun DownloadingBody(state: UpdateUiState.Downloading) {
    Text(
        "正在下载 v${state.info.versionName}（${state.info.sizeText()}）",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(10.dp))
    val p = state.percent
    if (p == null) {
        // 服务端没给 Content-Length：不假装知道进度，用不确定态进度条
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            "已经下载了 ${state.info.sizeText()} 里的部分内容…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        LinearProgressIndicator(
            progress = { p / 100f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "$p%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReadyBody(
    info: UpdateInfo,
    canInstall: Boolean,
    onOpenPermission: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.CloudDownload,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "v${info.versionName} 已下载完成",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        if (canInstall) {
            "系统安装界面应该已经弹出来了。没看到的话点下面的「安装」。"
        } else {
            "还差一步：系统要先允许「阅闻」安装应用。"
        },
        style = MaterialTheme.typography.labelMedium,
        color = cs.onSurfaceVariant
    )

    if (!canInstall) {
        Spacer(Modifier.height(12.dp))
        PermissionHint(onOpenPermission)
    }
}

@Composable
private fun FailedBody(message: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = cs.error,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 更新说明。
 *
 * 限高 + 可滚动：release body 是 Markdown，可能很长（列了十几条改动），
 * 不限高会把按钮顶出屏幕。
 */
@Composable
private fun NotesBlock(notes: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                "更新说明",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                notes.ifBlank { "这个版本没有写更新说明。" },
                style = MaterialTheme.typography.bodySmall,
                // Markdown 里的 ** 和 - 原样显示就够了：
                // 为了一个只在更新时才出现的弹窗引入 Markdown 渲染库不值得
                color = cs.onSurface
            )
        }
    }
}

/** 「去开启权限」提示条。两处复用（Available / Ready），避免文案走样。 */
@Composable
private fun PermissionHint(onOpenPermission: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "装之前要先允许安装未知应用",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenPermission) { Text("去开启") }
        }
    }
}
