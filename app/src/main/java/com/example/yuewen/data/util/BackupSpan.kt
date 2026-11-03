package com.example.yuewen.data.util

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 云备份的**文件命名**与**保留份数**规则（v2.9）。
 *
 * 三条通道（GitHub Gist / WebDAV / 本地文件夹）共用这一份规则，
 * 所以「哪些文件是我们生成的」「该删哪几个」在两处走的是同一套判断 ——
 * 否则会出现「Gist 上删干净了，WebDAV 上越堆越多」这类不一致。
 *
 * ## 为什么文件名长这样
 *
 * ```
 * yuewen-20261005-231045.json
 *         └──────┬──────┘
 *           yyyyMMdd-HHmmss
 * ```
 *
 * - **固定宽度**（15 个字符）→ **字典序 == 时间序**。
 *   排序和挑「最新的一份」都只靠字符串比较，不用解析成时间再比，
 *   少一处能算错的地方（这条在 [newestFirst] 的断言里钉着）。
 * - **全 ASCII**：文件名要进 URL（WebDAV 的 PUT / DELETE）和 JSON 的键（Gist 的 files）。
 *   中文名进去就得处理百分号编码、还得指望对面的服务器正确解码 ——
 *   换成一串纯数字字母，这些问题一个都不存在。用户在界面上看到的是
 *   [displayOf] 出来的「2026-10-05 23:10」，一样看得懂。
 * - **带秒**：不带秒的话「同一分钟内点两次备份」会生成同一个名字，
 *   后一次会**静默覆盖**前一次 —— 用户看到的是「备份了两次，但只有一份」。
 *
 * ## 时区
 *
 * 用**设备本地时区**，不是 UTC。文件名是给用户看的，
 * 「我昨天 23:10 备份的」应该就是他手表上的 23:10。
 * 代价是跨时区换机后文件名的时间戳含义会跟着变 —— 但我们只用它排序和展示，
 * 不拿它做任何逻辑判断，所以没有影响。
 */
object BackupSpan {

    /** 保留份数的下限。**不允许 0**：那等于「备份完立刻删掉」，是一个必然踩的坑。 */
    const val KEEP_MIN = 1

    /** 保留份数的上限：30 份已经够「翻回一个月前」，再多就是拿用户的网盘当垃圾场。 */
    const val KEEP_MAX = 30

    /** 默认保留份数。 */
    const val KEEP_DEFAULT = 5

    /** 备份文件名前缀。它同时也是「这个文件是我们生成的」的判据。 */
    const val PREFIX = "yuewen-"

    /** 备份文件名后缀。 */
    const val SUFFIX = ".json"

    /**
     * 严格匹配我们自己的文件名。
     *
     * ⚠️ 必须**锚定首尾**（`^…$`）：本地文件夹那一条通道是用户自己选的目录，
     * 里面很可能还有别的文件。判据一松（比如只 `startsWith(PREFIX)`），
     * 用户存的 `yuewen-笔记.json` 就会被算进「我们的备份」，
     * 按时间排序时排在最后，然后**被当成过期备份删掉**。
     *
     * 正则由 [PREFIX] / [SUFFIX] 拼出来而不是手写一份字面量：
     * 改前缀时只改一处，不会出现「命名按新前缀、识别按旧前缀」这种错位。
     */
    private val NAME_RE =
        Regex("^${Regex.escape(PREFIX)}(\\d{8}-\\d{6})${Regex.escape(SUFFIX)}$")

    /** 保留份数夹取。越界值收进 [KEEP_MIN]..[KEEP_MAX]。 */
    fun sanitizeKeep(n: Int): Int = n.coerceIn(KEEP_MIN, KEEP_MAX)

    /**
     * 生成时间戳串（`yyyyMMdd-HHmmss`）。
     *
     * ⚠️ `Locale.US` 是必须的：`String.format` 会跟随默认语言环境，
     * 在阿拉伯语 / 波斯语等环境下 `%d` 会输出当地数字（如 `٢٠٢٦`），
     * 那样文件名既不是 ASCII、字典序也不再等于时间序，两条前提同时崩掉。
     */
    fun stamp(now: Long): String {
        val t = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        return String.format(
            Locale.US,
            "%04d%02d%02d-%02d%02d%02d",
            t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second
        )
    }

    /** 一次备份的默认文件名。 */
    fun autoName(now: Long): String = PREFIX + stamp(now) + SUFFIX

    /** 取文件名里的时间戳；**不是我们的文件就返回 null**。 */
    fun stampOf(name: String): String? = NAME_RE.matchEntire(name)?.groupValues?.get(1)

    /** 这个文件名是不是我们生成的备份。 */
    fun isOurs(name: String): Boolean = stampOf(name) != null

    /**
     * 只留我们的备份，按**时间从新到旧**排好。
     *
     * 去重是刻意的：同名的两份（同一个秒内备份两次，或者调用方把列表拼重了）
     * 如果都留着，[toPrune] 里的 `drop(keep)` 会多数掉一个名额，
     * 实际保留的份数比用户设的少一份。
     */
    fun newestFirst(names: List<String>): List<String> =
        names.filter(::isOurs).distinct().sortedByDescending { stampOf(it).orEmpty() }

    /**
     * 该删掉哪些文件：保留最新 [keep] 份，返回其余（**从新到旧**，删的顺序无所谓）。
     *
     * 非我们的文件永远不会出现在返回值里 —— 见 [NAME_RE]。
     */
    fun toPrune(names: List<String>, keep: Int = KEEP_DEFAULT): List<String> =
        newestFirst(names).drop(sanitizeKeep(keep))

    /**
     * 给界面看的时间：`yuewen-20261005-231045.json` → `2026-10-05 23:10`。
     *
     * 秒不进展示：备份列表里那一栏只有一行宽度，精确到秒属于噪声。
     * 不是我们的文件返回 null，调用方据此决定要不要显示那一行。
     */
    fun displayOf(name: String): String? {
        val s = stampOf(name) ?: return null
        return "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)} " +
                "${s.substring(9, 11)}:${s.substring(11, 13)}"
    }
}
