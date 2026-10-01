package com.example.yuewen.ui.util

import com.example.yuewen.data.model.Article

/**
 * 首页文章的排序方式（v2.2）。
 *
 * ⚠️ **为什么这个枚举和排序函数不在 `HomeViewModel` 里**：
 * `HomeViewModel` 依赖 `ViewModel`、`Application`、`NewsRepository` 这些安卓运行时，
 * 纯 JVM 编不过 —— 放在那里就等于这块排序逻辑永远没法进离线回归（jvmtest）。
 * 但排序是**最容易写错又最难发现**的一段（比较器搞反、种子不确定、洗牌漏元素），
 * 所以必须能被测到。挪到 ui/util 这个不碰安卓的包里，测试脚本挂得上。
 */
enum class HomeSortMode(val key: String, val label: String) {
    TimeDesc("time_desc", "最新在前"),
    TimeAsc("time_asc", "最早在前"),
    Random("random", "随机"),
    Source("source", "按阅源"),
    Title("title", "按标题");

    /** 这一档是否会打乱日期 → 是否还适合按「今天 / 昨天」分组。 */
    val groupedByDay: Boolean get() = this == TimeDesc || this == TimeAsc

    companion object {
        fun of(key: String?): HomeSortMode = entries.firstOrNull { it.key == key } ?: TimeDesc
    }
}

/**
 * 按用户选的排序方式整理列表。
 *
 * 为什么随机排序要用「种子」而不是每次直接 `shuffled()`：
 * 列表是 Flow 驱动的 —— 下拉刷新、标为已读、切布局、切深浅色，任一上游一变整条链就会重算。
 * 如果每次都拿一个新的随机数去洗，那些**跟排序完全无关的操作**也会把列表顺序打乱，
 * 用户会觉得「怎么一动就乱跳」。把种子存进设置、只在点「换一批」时才换，
 * 顺序就稳定了：刷新完新文章会插进来，但已有的相对次序不动。
 *
 * 抽成顶层纯函数是为了让它能被 jvmtest 直接测（见文件头说明）。
 */
fun sortArticles(list: List<Article>, mode: HomeSortMode, seed: Long): List<Article> {
    if (list.size < 2) return list
    return when (mode) {
        HomeSortMode.TimeDesc -> list.sortedByDescending { it.pubDate }
        HomeSortMode.TimeAsc -> list.sortedBy { it.pubDate }
        // 同源凑一起；组内仍按时间倒序（不然组内顺序会随查询顺序飘）
        HomeSortMode.Source -> list.sortedWith(compareBy({ it.sourceName }, { -it.pubDate }))
        HomeSortMode.Title -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        HomeSortMode.Random -> {
            // 用种子驱动一个确定性随机：同一个 seed 洗出来的顺序永远一样
            val rnd = java.util.Random(seed)
            // 先按 link 排序把「输入顺序」固定下来 —— 上游是个 SQL 查询，
            // 同样的数据两次查询顺序不保证一致；不先归一化的话，
            // 同一个 seed 也可能洗出两种结果，种子就白搭了。
            val stable = list.sortedBy { it.link }
            val idx = stable.indices.toMutableList()
            // 手写一次 Fisher-Yates：JDK 没有「带种子的 shuffled()」
            for (i in idx.lastIndex downTo 1) {
                val j = rnd.nextInt(i + 1)
                val t = idx[i]; idx[i] = idx[j]; idx[j] = t
            }
            idx.map { stable[it] }
        }
    }
}
