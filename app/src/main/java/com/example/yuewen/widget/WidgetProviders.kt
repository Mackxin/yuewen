package com.example.yuewen.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * 桌面小组件的「接收器」（v2.9）。
 *
 * 系统会在几种时机叫我们：用户刚把卡片拖到桌面、卡片被重新布局、系统重启完成、
 * 以及桌面把卡片划掉重加。**周期刷新我们没订阅** —— provider-info 里
 * `updatePeriodMillis = 0`，因为系统最短只给 30 分钟一次、还会顺带唤醒设备，
 * 而我们的数字变化全部来自 App 内部（刷新完、点已读、改收藏），
 * 由 App 自己 push 更新就够及时（见 [WidgetUpdater]）。
 *
 * 为什么是两个子类：一个 `<receiver>` 只能挂一份 provider-info，
 * 而「用哪个布局」是写死在 provider-info 里的 —— 想让 2×2 和 4×2 用不同布局，
 * 就得有两个 receiver，也就得有两个类。取数、配色、点击逻辑全在
 * [WidgetUpdater] 里共用，这里只负责「我是谁」。
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // ⚠️ 必须 `goAsync()`。下面要开协程去查 Room 和 DataStore，
        // 而 onUpdate 是主线程上的广播回调 —— 直接 return 的话系统会认为
        // 「这次广播处理完了」，随时可能回收进程。表现是桌面上刚拖上去的卡片
        // **一直是空白的**，而且日志里什么都没有（因为进程被杀了，不是崩了）。
        val pending = goAsync()
        WidgetUpdater.refreshAll(context) { pending.finish() }
    }
}

/** 2×2「未读」卡片。 */
class YuewenSmallWidgetProvider : BaseWidgetProvider()

/** 4×2「今日速览」卡片。 */
class YuewenWideWidgetProvider : BaseWidgetProvider()
