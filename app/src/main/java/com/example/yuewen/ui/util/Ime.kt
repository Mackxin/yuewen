package com.example.yuewen.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 返回一个「收起输入法」的动作（v2.4）。
 *
 * 为什么**两件事都要做**，少一件都会漏：
 * - `focus.clearFocus()` 只是让输入框失去焦点，**系统并不保证会顺手把键盘收下去**
 *   （尤其是从 Compose 页面跳走、或者被别的浮层盖住的时候）；
 * - `keyboard?.hide()` 才是明确让输入法退场。
 *
 * v2.4 之前这套「两行」在 AddSource / RssHub / Sources 三个页面里各写了一份，
 * 偏偏「闻件 → 搜索」那一栏没有 —— 于是就有了「搜索完键盘赖在屏幕上不走」的问题。
 * 新代码统一用这个入口，别再各写各的。
 */
@Composable
fun rememberImeDismiss(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    return remember(keyboard, focus) {
        {
            // force = true：即使焦点还在往下传递的过程中也立刻清掉
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}
