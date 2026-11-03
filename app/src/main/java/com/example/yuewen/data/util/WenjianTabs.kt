package com.example.yuewen.data.util

/**
 * 「闻件」页子栏的下标换算（v2.8）。
 *
 * 为什么要单独抽一个对象、而不是把逻辑写在 SettingsRepository 的 `map {}` 里：
 * 这一段是**升级路径**，只有在「老用户带着旧数据装上新版本」时才会走到。
 * 开发时反复重装、库和偏好都是空的，本地怎么点都复现不出来 ——
 * 于是它天然属于「必须靠离线断言钉住」的那类代码（和 [HomeRows] 同理）。
 *
 * 现状：
 * ```
 * v2.7 及以前（4 栏）   0 搜索  1 收藏  2 历史  3 笔记
 * v2.8 起   （5 栏）   0 搜索  1 稍后读  2 收藏  3 历史  4 笔记
 * ```
 * 「稍后读」插在「搜索」后面，是因为它是一栏**要去做的事**（待办性质），
 * 比「收藏」这种归档性质的更常被点开，放前面更顺手。
 *
 * 代价是后面三栏的下标整体 +1，所以必须做一次换算 —— 否则一个上次停在
 * 「收藏」（旧值 1）的用户升级后会直接落在「稍后读」上，内容完全变了。
 */
object WenjianTabs {

    /** 子栏总数。改 [com.example.yuewen.ui.screens.WenjianScreen] 的标签表时必须同步改这里。 */
    const val COUNT = 5

    // ---- 下标常量：调用方（比如设置页的跳转）别再写裸数字 ----
    const val SEARCH = 0
    const val QUEUE = 1
    const val BOOKMARKS = 2
    const val HISTORY = 3
    const val NOTES = 4

    /**
     * 算出当前该停在哪一栏。
     *
     * @param newKey v2.8 之后写下的值（`wenjian_tab_v2`）。**非 null 就说明已经迁移过了**，
     *               直接用，不再看旧键 —— 这是「换键即迁移标记」的用法。
     * @param oldKey v2.8 之前留下的值（`wenjian_tab`）。只在新键不存在时读。
     *
     * 换算规则：旧值 ≥ 1 整体后移一格（给「稍后读」腾位置），旧值 0（搜索）原地不动。
     * 任何越界值都会被夹回合法范围 —— 备份被手改、或以后栏目再增删时，
     * 宁可停在第 0 栏也不能因为一个越界下标让 `when` 落到别的分支上去。
     */
    fun resolve(newKey: Int?, oldKey: Int?): Int {
        if (newKey != null) return newKey.coerceIn(0, COUNT - 1)
        val old = oldKey ?: SEARCH
        val shifted = if (old >= 1) old + 1 else old
        return shifted.coerceIn(0, COUNT - 1)
    }
}
