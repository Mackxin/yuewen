package com.example.yuewen.data.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 单日的阅读量（柱状图一根柱子）。 */
data class DayBucket(val label: String, val count: Int, val isToday: Boolean)

/**
 * 来源排行的一行。
 *
 * 放在 model 包而不是 DAO 文件里：DAO 依赖 Room 注解，纯 JVM 编译不了，
 * 而这些模型要跟着 [ReadStatsCalc] 一起在离线测试里跑。
 * 字段名必须和 SQL 里的列名 / 别名对上，Room 才会自动装配。
 */
data class SourceCount(val sourceName: String, val c: Int)

/** 阅读统计快照。一次性从库里读出来，界面只负责画。 */
data class ReadStats(
    val totalRead: Int = 0,
    val todayRead: Int = 0,
    val weekRead: Int = 0,
    val chars: Int = 0,
    val last7: List<DayBucket> = emptyList(),
    val topSources: List<SourceCount> = emptyList(),
    val bookmarked: Int = 0,
    val cached: Int = 0,
    val sourceCount: Int = 0
) {
    /** 估算阅读时长（分钟）。中文按 300 字/分钟粗算，读过就至少算 1 分钟。 */
    val minutes: Int get() = if (chars <= 0) 0 else maxOf(1, chars / 300)

    /** 柱状图的纵轴上限，至少为 1，避免全 0 时除零。 */
    val peak: Int get() = maxOf(1, last7.maxOfOrNull { it.count } ?: 0)
}

/**
 * 统计口径的计算。
 *
 * 单独抽出来是为了能在本机用纯 JVM 跑断言——
 * 这台机器没有设备也没有模拟器，日期分桶这种「跨月/跨年/时区」容易出错的地方
 * 只能靠离线测试兜住（见 NewsApp/tools/jvmtest）。
 */
object ReadStatsCalc {

    /** 取某个时间戳所在「当天 00:00」的时间戳（按传入时区）。 */
    fun startOfDay(ts: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(ts)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /**
     * 把阅读时间戳按「最近 7 天」分桶，返回从早到晚 7 个。
     *
     * 用 `LocalDate` 判断「是不是同一天」而不是拿时间戳做除法——
     * 后者在跨时区、夏令时切换、跨月跨年时会算错一整天。
     */
    fun last7Buckets(
        timestamps: List<Long>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<DayBucket> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

        val counts = HashMap<LocalDate, Int>()
        for (t in timestamps) {
            if (t <= 0) continue
            val d = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            counts[d] = (counts[d] ?: 0) + 1
        }

        return (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            DayBucket(
                label = weekdayLabel(d),
                count = counts[d] ?: 0,
                isToday = back == 0
            )
        }
    }

    /** 本周（含今天，往前推 6 天）的起点时间戳。 */
    fun weekStart(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** 连续阅读天数（从今天往前数，哪天没读就断）。 */
    fun streak(timestamps: List<Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        if (timestamps.isEmpty()) return 0
        val days = timestamps.mapNotNull {
            if (it <= 0) null else Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        }.toHashSet()
        if (days.isEmpty()) return 0

        var cursor = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // 今天还没读不算断——从昨天开始数，避免一早打开就显示 0 天
        if (cursor !in days) cursor = cursor.minusDays(1)

        var n = 0
        while (cursor in days) {
            n++
            cursor = cursor.minus(1, ChronoUnit.DAYS)
        }
        return n
    }

    private fun weekdayLabel(d: LocalDate): String = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        // Java 的 DayOfWeek 对 Kotlin 来说是「可空的枚举」（平台类型），
        // 必须补 else 分支才能编出穷尽判断，否则会报「exhaustive when 缺 null 分支」
        DayOfWeek.SUNDAY, null -> "日"
    }
}
