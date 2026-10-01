package com.example.yuewen

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.yuewen.service.TtsPlaybackService
import com.example.yuewen.ui.MainScreen
import com.example.yuewen.ui.theme.DEFAULT_CUSTOM_HUE
import com.example.yuewen.ui.theme.DEFAULT_CUSTOM_SAT
import com.example.yuewen.ui.theme.ThemePalette
import com.example.yuewen.ui.theme.YuewenTheme
import java.io.File

class MainActivity : ComponentActivity() {

    /**
     * Android 13（API 33）起，通知属于「运行时权限」，光在清单里声明是没用的 ——
     * 不主动申请的话，`NotificationManager.notify()` 会被系统静默丢弃。
     *
     * 后果是两块功能直接失灵，而且**一点提示都没有**：
     * 1. 刷新完的「有 N 条新内容更新」提醒永远收不到；
     * 2. 朗读的前台服务通知挂不上（服务本身用 `runCatching` 兜住了，
     *    所以用户看到的就是「设置里明明开着『朗读时显示通知栏控制』，却什么都没有」）。
     *
     * 这里在启动时申请一次。用户在设置里关掉了也没关系 —— 系统只会问一次，
     * 之后想开可以去系统设置里开。用 `runCatching` 包住，任何机型异常都不该拖垮启动。
     */
    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 结果不影响启动流程：拒了就是没有通知，其它功能照常用 */ }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /**
         * v2.6：沉浸式（edge-to-edge）。
         *
         * 干的事就一件：**让 App 的画面铺满整块屏幕**，包括状态栏与导航栏那两条区域。
         * 关掉系统自动避让（`decorFitsSystemWindows = false`）之后，那两条区域不再由系统上色，
         * 而是透出 App 自己画的背景 —— 于是「状态栏和内容浑然一体」，
         * 冷启动和切换主题时也不会再闪一条异色带。
         *
         * 代价是**所有**系统栏留白都得自己补，不能像以前那样白拿：
         * 见 `MainScreen`（页面 / 浮层）、`HomeScreen`（列表从顶栏下穿过）、
         * `DetailScreen`（阅读器状态栏图标跟着纸感底色翻）。
         * 输入法也归到这里管：`MainScreen` 根节点套了 `imePadding()`，
         * 顶掉以前 `adjustResize` 的效果，输入框不会被键盘盖住。
         */
        enableEdgeToEdge()

        handleOpenRequest(intent)
        askNotificationPermission()

        // 上次若发生过未捕获崩溃，本次启动显示错误页而不是再次闪退
        val crashFile = File(filesDir, "yuewen_crash.log")
        val crashText = if (crashFile.exists()) {
            // 日志里可能有多条记录（==== 时间戳 ==== 分隔），取最后一条
            // 重要：显示"开头"而不是"结尾"——开头的异常类型 + 消息才是病根
            val lastEntry = crashFile.readText().split("==== ").lastOrNull { it.isNotBlank() }
            lastEntry?.take(6000)
        } else null

        setContent {
            YuewenTheme {
                if (crashText != null) {
                    CrashScreen(crashText) {
                        crashFile.delete()
                        recreate()
                    }
                } else {
                    val app = application as YuewenApplication
                    val theme by app.settingsRepository.themeFlow.collectAsState(initial = "")
                    val font by app.settingsRepository.fontFlow.collectAsState(initial = "")
                    val darkTheme = when (theme) {
                        "light" -> false
                        "dark" -> true
                        else -> isSystemInDarkTheme()
                    }
                    val scale = when (font) {
                        "small" -> 0.9f
                        "large" -> 1.15f
                        else -> 1f
                    }
                    // v2.3：配色方案。读取时就能切档（collectAsState 会给最新值），
                    // 所以在设置页点一下选中，整个界面立刻换色，不用重启。
                    val paletteKey by app.settingsRepository.themePaletteFlow.collectAsState(initial = "emerald")
                    val customHue by app.settingsRepository.customHueFlow.collectAsState(initial = DEFAULT_CUSTOM_HUE)
                    val customSat by app.settingsRepository.customSatFlow.collectAsState(initial = DEFAULT_CUSTOM_SAT)
                    YuewenTheme(
                        darkTheme = darkTheme,
                        fontScale = scale,
                        palette = ThemePalette.of(paletteKey),
                        customHue = customHue,
                        customSat = customSat
                    ) {
                        MainScreen()
                    }
                }
            }
        }
    }

    /**
     * Activity 是 singleTop + 从通知栏用 CLEAR_TOP 拉起的，
     * 所以「回到文章」既可能走 onCreate（进程被杀后重启），也可能走 onNewIntent（还在栈里）。
     * 两条路都要接住，统一丢给 Application，MainScreen 订阅后打开那一篇。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenRequest(intent)
    }

    private fun handleOpenRequest(intent: Intent?) {
        val link = intent?.getStringExtra(TtsPlaybackService.EXTRA_OPEN_LINK)
        if (!link.isNullOrBlank()) {
            (application as? YuewenApplication)?.requestOpenArticle(link)
        }
    }
}

@Composable
private fun CrashScreen(text: String, onClear: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("抱歉，应用上次启动时崩溃了", style = MaterialTheme.typography.titleLarge, color = cs.error)
        // 异常类型 + 消息在日志最开头，单独放大显示，截图截这一段就够了
        val header = text.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        Text(header, style = MaterialTheme.typography.titleSmall, color = cs.error)
        Text("请把上面红色那行错误截图发给我们；点「复制全部」可粘贴完整日志：", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
        Text(text, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("复制全部") }
            OutlinedButton(onClick = onClear) { Text("清除记录并重新打开") }
        }
    }
}
