package com.example.yuewen.data.util

import java.util.Locale
import kotlin.math.round

/**
 * 桌面小组件要显示的文字（v2.9）。
 *
 * 和 [DailyDigest] 同性质：**纯函数，一个 android.* 都不碰** ——
 * 这样 `tools/jvmtest` 能在没有任何设备的情况下把「数字怎么写、空态说什么」
 * 全部断言一遍。小组件最容易出的问题就是「数字变成 10000 这种挤爆卡片的写法」
 * 或者「一条都没有时显示空字符串」，靠肉眼看桌面很难覆盖到每种数量级。
 *
 * 小组件有两种尺寸，文案策略不同：
 * - **2×2（[small]）**：只看一个数（未读），其余压成一行摘要；
 * - **4×2（[wide]）**：左边仍是未读数，右边一行摘要，下面补两条标题。
 */
object WidgetSnapshot {

    /** 标题最多显示几条。4×2 高度只够两行，多出来的不显示也不占位。 */
    const val TITLE_LIMIT = 2

    /**
     * 一条待展示的标题。
     *
     * 带上 `link` 是因为 4×2 上点标题要**直接进那一篇**，
     * 而不是只进首页（见 WidgetUpdater 里 PendingIntent 的注释）。
     */
    data class Item(val title: String, val link: String)

    /** 小组件上看到的东西。字段全部给默认值，取数失败时直接 [Facts]() 就是「一片空」。 */
    data class Facts(
        val unread: Int = 0,
        val today: Int = 0,
        val queued: Int = 0,
        val items: List<Item> = emptyList()
    )

    /** 2×2 的文案。 */
    data class Small(
        val unreadText: String,
        val sub: String
    )

    /** 4×2 的文案。 */
    data class Wide(
        val unreadText: String,
        val meta: String,
        val title1: String,
        val showTitle1: Boolean,
        val title2: String,
        val showTitle2: Boolean,
        /** 两行标题各自对应的文章链接；没有标题时为 null（那两行只负责打开 App）。 */
        val link1: String?,
        val link2: String?
    )

    // ------------------------------------------------------------------ 数字

    /**
     * 大数字的写法。
     *
     * 10000 以上收成「1.2万」：卡片上那点宽度，五位数会被挤成两行或者直接截断，
     * 而「1.2万」反而**信息量更大** —— 用户要的是量级，不是个位。
     * 100 万以上连小数点都省掉（「123万」），再保留一位小数就太长了。
     */
    fun count(n: Int): String {
        val v = n.coerceAtLeast(0)
        if (v < 10_000) return v.toString()
        // ⚠️ 先四舍五入到一位小数再决定用哪种写法。否则 999999 会算出
        // wan = 99.9999 → 走「保留一位小数」那支 → 显示成「100.0万」，
        // 既长又自相矛盾（都到 100 万了还留着小数）。
        val wan = round(v / 10_000.0 * 10) / 10.0
        // ⚠️ 必须显式指定 Locale.US：`%.1f` 在部分语言环境下小数点是逗号，
        // 会写出「1,2万」这种在中文里读不通的写法。
        return if (wan >= 100) "${wan.toInt()}万"
        else String.format(Locale.US, "%.1f万", wan)
    }

    // ------------------------------------------------------------------ 摘要

    /**
     * 一行摘要：「今天几条新内容 + 队列里几篇待读」。
     *
     * 优先级有意排成「今天的新内容 → 队列 → 什么都没有」：
     * 用户扫一眼卡片，最想知道的是「有没有新东西可以看」。
     */
    fun summary(f: Facts): String {
        val today = f.today.coerceAtLeast(0)
        val queued = f.queued.coerceAtLeast(0)
        return when {
            today > 0 && queued > 0 -> "今日 $today 条 · 队列 $queued 篇"
            today > 0 -> "今日 $today 条新内容"
            queued > 0 -> "队列 $queued 篇待读"
            f.unread > 0 -> "今日暂无新内容"
            else -> "都读完了"
        }
    }

    /** 库里一条文章都没有时，标题位用来引导订阅，而不是留白。 */
    const val EMPTY_HINT = "去订阅几个源，这里就有内容了"

    // ------------------------------------------------------------------ 组装

    fun small(f: Facts): Small = Small(
        unreadText = count(f.unread),
        sub = summary(f)
    )

    fun wide(f: Facts): Wide {
        val items = f.items
            .map { it.copy(title = it.title.trim()) }
            .filter { it.title.isNotEmpty() }
        return Wide(
            unreadText = count(f.unread),
            meta = summary(f),
            // 一条标题都没有 ≠ 出错了：可能是「全部已读」。这时给一句引导，
            // 而不是让卡片下半截空着（空着看起来像加载失败）。
            title1 = items.getOrNull(0)?.title ?: EMPTY_HINT,
            showTitle1 = true,
            title2 = items.getOrNull(1)?.title.orEmpty(),
            showTitle2 = items.size >= 2,
            // 引导文案不对应任何文章 → 这一行的点击退回「打开 App」
            link1 = items.getOrNull(0)?.link,
            link2 = items.getOrNull(1)?.link
        )
    }
}
