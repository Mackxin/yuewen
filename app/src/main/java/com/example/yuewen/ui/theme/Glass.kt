package com.example.yuewen.ui.theme

/**
 * 「液态玻璃」（Liquid Glass）的**纯数值层**。
 *
 * 为什么单独拆一个文件：这里只有 ARGB Long 的算术，**完全不依赖 Compose** ——
 * 于是能跟配色生成器（[generateScheme]）一样进离线回归测试（`tools/jvmtest`）。
 * 到 UI 层才用 `Color(Glass.xxx(...))` 转成 Compose 的颜色，转换关口只有一处。
 *
 * ⚠️ 先说清楚一件事：Compose 里**没有真正的「背景模糊」**。
 * `Modifier.blur()` 模糊的是**自己的内容**，不是身后透出来的东西；
 * 想在某个子区域做毛玻璃，需要把身后的内容先渲染成纹理再采样，代价很高。
 * 所以这里的「玻璃」是**高仿**：
 * 半透明填充 + 顶部受光渐变 + 一圈高光描边。
 * 观感已经很像，但**没有**真实毛玻璃的折射与色彩扩散 —— 别对外说成真模糊。
 */
object Glass {

    /**
     * 玻璃填充色的不透明度。
     *
     * 深色下取更小的值（更透）：深底上透出来的层次比浅底明显，
     * 用同样的 alpha 会显得「糊了一层灰」。
     */
    fun fillAlpha(dark: Boolean): Float = if (dark) 0.62f else 0.76f

    /** 一圈高光描边的不透明度。浅色底需要更实的白边才看得见轮廓。 */
    fun strokeAlpha(dark: Boolean): Float = if (dark) 0.16f else 0.60f

    /**
     * 顶部受光面（内高光）的不透明度。
     * 玻璃在光下最亮的是上沿，这一层就是模拟它 —— 没有它，半透明块会显得很平。
     */
    fun sheenAlpha(dark: Boolean): Float = if (dark) 0.10f else 0.32f

    /** 受光面覆盖的高度占整体的比例（只盖顶部这一小段）。 */
    const val SHEEN_HEIGHT_RATIO: Float = 0.52f

    /** 描边宽度（dp）。1dp 是「看得见但不像画框」的临界值。 */
    const val STROKE_DP: Int = 1

    /**
     * 把颜色按 [alpha] 变成半透明，**RGB 保持不变**（只改最高 8 位的 alpha）。
     *
     * alpha 会被夹到 0..1：调用方传负数或大于 1 时，如果直接乘 255 再 shl，
     * 会溢出到相邻通道上，算出一种诡异的颜色（而不是「全透明 / 不透明」）。
     */
    fun withAlpha(color: Long, alpha: Float): Long {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (color and 0x00FFFFFFL) or (a.toLong() shl 24)
    }

    /** 白色按 [alpha] 取一个 ARGB 值（描边 / 受光面用）。 */
    fun white(alpha: Float): Long = withAlpha(0xFFFFFFFFL, alpha)

    /** 取出 ARGB 里的 alpha 通道（0..255）。测试与调试用。 */
    fun alphaOf(color: Long): Int = ((color ushr 24) and 0xFFL).toInt()
}
