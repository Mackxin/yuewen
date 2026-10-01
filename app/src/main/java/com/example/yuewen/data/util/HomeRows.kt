package com.example.yuewen.data.util

/**
 * 首页那两行筛选胶囊（分类行 / 阅源行）的显隐规则（v2.5）。
 *
 * ## 为什么要抽出来
 *
 * v2.0.2 用的是一把三档开关 `home_chip_mode`：
 * - `category` —— 只显示分类行
 * - `source`   —— 只显示阅源行
 * - `both`     —— 两行都显示
 *
 * 但这三档其实就是在描述「两行各自要不要显示」，用户读起来绕
 * （「按分类」到底是在说筛选口径，还是在说这一行？）。v2.5 拆成两个独立开关：
 * 「显示分类行」「显示阅源行」，和旁边的「显示副标题」「显示关键词行」是同一套心智模型。
 *
 * ## 老用户怎么办
 *
 * **不做一次性迁移写盘**。迁移写盘要处理「刚写完一半进程被杀」「备份里只有旧键」之类的边界，
 * 收益却只是省几个字节。这里改成「新键没写过就按旧键**现算**」：
 * 外观一个像素都不变，备份恢复老文件也照样对。
 *
 * 代价是这段映射逻辑必须长期稳定 —— 所以单独抽成纯函数，顺便进离线测试。
 */
object HomeRows {

    const val CHIP_MODE_CATEGORY = "category"
    const val CHIP_MODE_SOURCE = "source"
    const val CHIP_MODE_BOTH = "both"

    /** 旧三档的默认值：和加这个功能之前一样 —— 只显示分类行。 */
    const val DEFAULT_CHIP_MODE = CHIP_MODE_CATEGORY

    /**
     * 首页要不要显示「分类」那一行。
     *
     * @param showCatRow 新键 `home_show_cat_row`；`null` = 用户从没设过
     * @param legacyChipMode 旧键 `home_chip_mode`；`null` = 从没设过
     */
    // ⚠️ 右边那对括号**不能省**。
    // Kotlin 里 `?:` 的结合比 `!=` 更紧，写成 `showCatRow ?: normalize(x) != Y`
    // 会被解析成 `(showCatRow ?: normalize(x)) != Y` —— 于是 `showCatRow` 是 `false` 时，
    // 拿 Boolean 去和 String 比，结果恒为 true，用户关掉的那一行会**照样显示**。
    // 这个坑是离线测试 25.05 / 25.07 抓出来的。
    fun showCategoryRow(showCatRow: Boolean?, legacyChipMode: String?): Boolean =
        showCatRow ?: (normalize(legacyChipMode) != CHIP_MODE_SOURCE)

    /**
     * 首页要不要显示「阅源名称」那一行。
     *
     * @param showSrcRow 新键 `home_show_src_row`；`null` = 用户从没设过
     * @param legacyChipMode 旧键 `home_chip_mode`；`null` = 从没设过
     */
    /** 同上：括号不能省，理由见 [showCategoryRow]。 */
    fun showSourceRow(showSrcRow: Boolean?, legacyChipMode: String?): Boolean =
        showSrcRow ?: (normalize(legacyChipMode) != CHIP_MODE_CATEGORY)

    /**
     * 认不出来的旧值一律当默认值。
     *
     * 不这么做的话：给一个手改过的、或者以后误写的值（比如 `Category` 大写），
     * 两个 `!=` 判断会**同时成立** —— 分类行和阅源行一起冒出来，
     * 而用户的印象里自己从没开过其中一行。宁可回到默认，也不要有意外行为。
     */
    private fun normalize(mode: String?): String =
        if (mode == CHIP_MODE_CATEGORY || mode == CHIP_MODE_SOURCE || mode == CHIP_MODE_BOTH) mode
        else DEFAULT_CHIP_MODE
}
