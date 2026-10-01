package com.example.yuewen.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 桌面图标名称的预设（v2.0）。
 *
 * ⚠️ **顺序必须和 AndroidManifest.xml 里 Alias0..Alias7、以及 strings.xml 的
 * icon_name_0..7 完全一致**。这三处任意一处调换顺序，用户切出来的名字就对不上了。
 *
 * 为什么只有预设、不能随便填：Android 的桌面图标名来自 manifest 的 `android:label`，
 * 运行时没有任何 API 能改它；想换名字只能预先声明若干个 `activity-alias`，
 * 再用 [applyIconName] 启用其中一个。所以这里给的是 8 个够用的名字，
 * 而真正「随便写」的是**应用内名称**（首页标题 / 关于页），见 [titleOrDefault]。
 */
val IconNames: List<String> = listOf(
    "阅闻", "阅闻新闻", "阅闻阅读", "每日阅闻", "新闻速览", "极简新闻", "头条聚合", "阅闻 RSS"
)

/** 应用默认名称（用户没自定义时用它）。 */
const val DEFAULT_APP_TITLE = "阅闻"

/** 应用内显示名：用户填过就用他的，否则用默认名。 */
fun titleOrDefault(custom: String?): String =
    custom?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_APP_TITLE

/** 桌面图标名称（按预设下标取；越界兜底为第一个）。 */
fun iconNameOf(index: Int): String = IconNames.getOrElse(index) { IconNames.first() }

/** alias 的完整组件名（包名固定是本 App）。 */
private fun aliasOf(context: Context, index: Int) =
    ComponentName(context.packageName, "${context.packageName}.Alias$index")

/**
 * 读取当前生效的图标名下标。
 *
 * 第一次装好后从没调用过 `setComponentEnabledSetting` 时，所有 alias 都返回
 * `COMPONENT_ENABLED_STATE_DEFAULT`，此时**以 manifest 的静态声明为准** ——
 * 我们固定让 Alias0 是 enabled、其余 disabled，所以兜底返回 0 就对了。
 */
fun currentIconIndex(context: Context): Int {
    val pm = context.packageManager
    for (i in IconNames.indices) {
        val state = runCatching { pm.getComponentEnabledSetting(aliasOf(context, i)) }.getOrNull()
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return i
    }
    return 0
}

/**
 * 切换桌面图标名称。
 *
 * 关键点：用 `DONT_KILL_APP` —— 否则切一下名字，正在用的 App 会被系统杀掉重启。
 * 切换后个别启动器要过一两秒才刷新桌面，属于正常现象。
 *
 * @return 是否切换成功（失败一般是系统/启动器限制，调用方提示用户即可）
 */
fun applyIconName(context: Context, index: Int): Boolean {
    val i = index.coerceIn(0, IconNames.lastIndex)
    val pm = context.packageManager
    var ok = true
    for (n in IconNames.indices) {
        val state = if (n == i) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val r = runCatching {
            pm.setComponentEnabledSetting(aliasOf(context, n), state, PackageManager.DONT_KILL_APP)
        }
        if (r.isFailure) ok = false
    }
    return ok
}
