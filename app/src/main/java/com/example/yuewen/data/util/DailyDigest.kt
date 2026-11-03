package com.example.yuewen.data.util

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * 每日摘要（v2.8）的**纯逻辑**。
 *
 * 单独抽出来的理由和 [WenjianTabs] / [HomeRows] 一样：
 * 「什么时候该推」和「推什么话」这两件事全是纯计算，
 * 但它们一旦算错，症状是**在手机上悄悄不推 / 推错时间 / 数字对不上**——
 * 这种 bug 靠手动点两下根本发现不了（要等到第二天早上）。
 * 抽成纯函数以后，几条断言就能把边界钉死，见 tools/jvmtest 第 30 组。
 *
 * 这个文件里**不许出现任何安卓类型**，否则离线测试编译不过。
 */
object DailyDigest {

    /** 默认推送时间：早上 8 点。多数人这个点在看手机，而且当天的早新闻已经出来了。 */
    const val DEFAULT_HOUR = 8
    const val DEFAULT_MINUTE = 0

    /**
     * 设置页给的时间选项。
     *
     * **为什么不给一个完整的时间选择器**：
     * ① 摘要这种东西不需要精确到分钟，「几点档」就够；
     * ② 项目里所有多选一都是胶囊（列表布局 / 阅读器主题 / 配色），
     *    突然冒一个表盘出来是另一套交互，反而突兀；
     * ③ 6 个整点覆盖了「上班前 / 通勤 / 午休 / 下班 / 睡前」几个典型场景。
     */
    val PRESETS: List<Pair<Int, Int>> = listOf(
        7 to 0,
        8 to 0,
        9 to 0,
        12 to 0,
        18 to 0,
        21 to 0
    )

    fun sanitizeHour(v: Int): Int = v.coerceIn(0, 23)

    fun sanitizeMinute(v: Int): Int = v.coerceIn(0, 59)

    /** "08:00" 这种两位补零的写法。设置页的胶囊文案和通知里都用它。 */
    fun format(hour: Int, minute: Int): String =
        "%02d:%02d".format(sanitizeHour(hour), sanitizeMinute(minute))

    /**
     * 本地时区里「今天 00:00」的时间戳。
     *
     * 用 `LocalDate.atStartOfDay(zone)` 而不是「now - now % 86400000」：
     * 后者在中国没问题，但用户一旦出国（或者其他调过 DST 的时区），
     * 那天的实际长度不是 24 小时，取整会偏一小时，
     * 表现是「摘要里的『今天』从凌晨 1 点才开始算」。
     */
    fun todayStart(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /**
     * 距离「下一次 hh:mm」还有多少毫秒。**永远大于 0**。
     *
     * ⚠️ 严格大于：如果此刻正好等于目标时刻（比如 08:00:00.000 整，
     * 或者 WorkManager 刚好在目标时刻那一毫秒问了一次），返回 0 的话
     * Worker 会「立刻跑 → 排下一次 → 又是 0 → 立刻跑」，一秒之内把电量烧完。
     * 所以判定写成 `> now`，跨过之后才轮到明天。
     *
     * 明天那一班用 `plusDays(1)` 重算而不是「今天 + 24h」：
     * 夏令时切换那天，+24h 会落在 09:00 或 07:00 上。
     */
    fun nextDelayMillis(
        nowMs: Long,
        hour: Int,
        minute: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val h = sanitizeHour(hour)
        val m = sanitizeMinute(minute)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

        val todayAt = today.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
        val target = if (todayAt > nowMs) {
            todayAt
        } else {
            // ⚠️ 这里**不能**用 todayAt + 86_400_000（见上面的 DST 注释）
            today.plusDays(1).atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
        }
        return target - nowMs
    }

    /**
     * 摘要要用的几个数字（纯数据，方便离线断言）。
     *
     * - [todayCount] 今天（本地 00:00 起）入库的条数，按文章发布时间算；
     * - [unreadCount] 全库未读数；
     * - [queuedCount] 稍后读队列长度；
     * - [topTitles] 今天最新的几条标题，用来撑 notification 的展开态。
     */
    data class DigestFacts(
        val todayCount: Int,
        val unreadCount: Int,
        val queuedCount: Int,
        val topTitles: List<String>
    )

    /** 一条摘要通知的最终文案。 */
    data class DigestContent(
        val title: String,
        val text: String,
        val bigText: String
    )

    const val TITLE = "今日摘要"

    /**
     * 把数字拼成通知文案；**没什么可说的就返回 null**（调用方据此不推送）。
     *
     * 「什么都不说就静默」这条是刻意的：一个每天准时响、点开却发现无事发生的通知，
     * 是所有通知里最招人烦的一种，用户三天之内一定会去把它关掉 ——
     * 那这个功能就白做了。所以宁可今天不响。
     *
     * 三档文案的分支依据是「今天有没有新内容」和「队列里压着没有」，
     * 这两个维度正交（今天没新内容 ≠ 队列是空的），四种组合里只有全空才不推。
     */
    fun buildContent(facts: DigestFacts): DigestContent? {
        val today = facts.todayCount.coerceAtLeast(0)
        val unread = facts.unreadCount.coerceAtLeast(0)
        val queued = facts.queuedCount.coerceAtLeast(0)

        val text = when {
            today > 0 && queued > 0 -> "今天 $today 条新内容 · 队列里 $queued 篇待读"
            today > 0 -> "今天 $today 条新内容 · 未读 $unread 条"
            queued > 0 -> "今天暂无新内容 · 队列里 $queued 篇待读"
            // 今天没新内容、队列也空 —— 唯一一条不打扰用户的分支
            else -> return null
        }

        val titles = facts.topTitles.map { it.trim() }.filter { it.isNotEmpty() }
        val bigText = if (titles.isEmpty()) {
            text
        } else {
            text + "\n\n" + titles.joinToString("\n") { "· $it" }
        }

        return DigestContent(TITLE, text, bigText)
    }
}
