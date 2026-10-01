package com.example.yuewen.ui.theme

/**
 * 阅闻 · 配色方案生成器（v2.3）
 *
 * ## 这个文件为什么全是 Long / Int，而不是 Compose 的 Color
 *
 * 因为要能被**离线回归测试**编译。`tools/jvmtest/run.sh` 只有纯 JDK + 几个注解 jar，
 * 没有 Compose。把颜色算成 ARGB 的 `Long`（`0xFFRRGGBB`），
 * 到了 `Theme.kt` 再转成 `Color(scheme.primary)` —— 这样「配色是否合法 / 对比度够不够」
 * 这类问题能在几秒内跑出来，而不是等 9 分钟的 Gradle 构建。
 *
 * ## 生成思路
 *
 * 和 Material 3 的动态配色同源：从一个**种子色**里只取「色相」，
 * 剩下所有槽位都按一套固定的**亮度阶梯 + 饱和度衰减**推出来。
 * 这样用户随便给一个颜色，得到的都是完整、自洽、可读的一整套配色，
 * 而不是「只有主色变了、背景还是原来那个」的半成品。
 *
 * ## 为什么要「对比度兜底」
 *
 * 直接按固定 HSL 亮度生成，遇到黄色系会翻车：HSL 的 lightness 和感知亮度差别很大，
 * 明黄色在 HSL `l=0.5` 时的相对亮度已经接近 0.9 —— 白字压上去根本看不清。
 * 所以主色/容器色生成完都要过一遍 [fitContrast]：不达标就沿着亮度轴挪，
 * 直到和它的前景色对比度 ≥ 4.5（WCAG AA 正文标准）。
 */

// ==================== 槽位定义 ====================

/**
 * 一整套配色。字段与 Material 3 的 `ColorScheme` 一一对应。
 *
 * 用 `Long` 而不是 `Int`：ARGB 的高位在 Kotlin 里 `0xFF0E9F76` 这种字面量
 * 超过 `Int` 的表示范围（会被当成负数），用 `Long` 少踩这类坑。
 */
data class Scheme(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val inversePrimary: Long,

    val secondary: Long,
    val onSecondary: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,

    val tertiary: Long,
    val onTertiary: Long,
    val tertiaryContainer: Long,
    val onTertiaryContainer: Long,

    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val surfaceVariant: Long,
    val onSurfaceVariant: Long,

    val surfaceBright: Long,
    val surfaceDim: Long,
    val surfaceContainerLowest: Long,
    val surfaceContainerLow: Long,
    val surfaceContainer: Long,
    val surfaceContainerHigh: Long,
    val surfaceContainerHighest: Long,

    val outline: Long,
    val outlineVariant: Long,

    val error: Long,
    val onError: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,

    val inverseSurface: Long,
    val inverseOnSurface: Long
) {
    /** 全部槽位，方便测试逐个校验（新增槽位时这里会自动带上）。 */
    fun all(): List<Pair<String, Long>> = listOf(
        "primary" to primary, "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer, "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary, "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer, "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary, "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer, "onTertiaryContainer" to onTertiaryContainer,
        "background" to background, "onBackground" to onBackground,
        "surface" to surface, "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant,
        "surfaceBright" to surfaceBright, "surfaceDim" to surfaceDim,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "outline" to outline, "outlineVariant" to outlineVariant,
        "error" to error, "onError" to onError,
        "errorContainer" to errorContainer, "onErrorContainer" to onErrorContainer,
        "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface
    )
}

// ==================== 预设配色 ====================

/**
 * 内置配色。`seed` 就是「浅色主题下的主色」，其余槽位由 [generateScheme] 推出来。
 *
 * ⚠️ `Emerald`（青绿）是**默认档**，它的实际取值不走生成器 ——
 * 而是继续用 `Color.kt` 里那套手工调过的色值（见 `Theme.kt`）。
 * 理由很实在：这是用户看惯了的默认外观，生成器再准也会差几个色阶，
 * 没必要为了「架构统一」去动人家的默认体验。别的档位才走生成。
 */
enum class ThemePalette(val key: String, val label: String, val seed: Long) {
    Emerald("emerald", "青绿", 0xFF0E9F76),
    Indigo("indigo", "靛蓝", 0xFF3B4FD8),
    Ocean("ocean", "海蓝", 0xFF0A76BE),
    Violet("violet", "紫罗兰", 0xFF8A43E0),
    Rose("rose", "胭脂", 0xFFD2316B),
    Amber("amber", "琥珀", 0xFFC8791A),
    Forest("forest", "森野", 0xFF2E7D46),
    Graphite("graphite", "石墨", 0xFF4E5A62),
    Custom("custom", "自定义", 0L);

    /** 除「自定义」以外的内置档位，界面上按这个顺序排。 */
    companion object {
        val presets: List<ThemePalette> = entries.filter { it != Custom }

        fun of(key: String): ThemePalette = entries.firstOrNull { it.key == key } ?: Emerald
    }
}

/** 自定义配色的默认色相（取值 0..360）。160° 就是默认那套青绿，用户一进来不突兀。 */
const val DEFAULT_CUSTOM_HUE: Int = 160

/** 自定义配色的默认鲜艳度（0..100，100 = 最艳）。 */
const val DEFAULT_CUSTOM_SAT: Int = 78

/**
 * 算出某个档位实际要喂给生成器的种子色。
 *
 * - 内置档位：用枚举里写好的 [ThemePalette.seed]；
 * - 自定义：由用户选的色相 + 鲜艳度现拼一个种子（亮度固定 0.34，
 *   也就是「看起来像品牌主色」的那个位置）。
 */
fun seedFor(palette: ThemePalette, customHue: Int, customSat: Int): Long {
    if (palette != ThemePalette.Custom) return palette.seed
    val h = (((customHue % 360) + 360) % 360).toFloat()
    val s = (customSat.coerceIn(0, 100) / 100f * 0.92f)
    return hslToArgb(h, s, 0.34f)
}

// ==================== 色彩工具（纯函数） ====================

/** ARGB 三通道组装成一个不透明的颜色。 */
internal fun argb(r: Int, g: Int, b: Int): Long =
    (0xFFL shl 24) or
            ((r.coerceIn(0, 255).toLong()) shl 16) or
            ((g.coerceIn(0, 255).toLong()) shl 8) or
            (b.coerceIn(0, 255).toLong())

/** 白色。生成器里到处要用，抽一个常量省得漏写成 0xFFFFFFFFL 或 0xFFFFFF。 */
internal const val WHITE: Long = 0xFFFFFFFFL

/**
 * RGB → HSL。返回 `[h, s, l]`：h ∈ [0,360)，s / l ∈ [0,1]。
 * 输入是 ARGB 的 Long（忽略 alpha）。
 *
 * 公开（而不是 internal）是为了让离线测试能直接校验「生成的色相是否保住了种子的色相」。
 */
fun rgbToHsl(color: Long): FloatArray {
    val r = ((color shr 16) and 0xFF).toFloat() / 255f
    val g = ((color shr 8) and 0xFF).toFloat() / 255f
    val b = (color and 0xFF).toFloat() / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    if (d == 0f) return floatArrayOf(0f, 0f, l) // 灰色没有色相可言
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d) + (if (g < b) 6f else 0f)
        g -> ((b - r) / d) + 2f
        else -> ((r - g) / d) + 4f
    } * 60f
    return floatArrayOf(h, s, l)
}

/**
 * HSL → ARGB（不透明）。
 *
 * 色相会自动取模，所以传 `h + 400` 这种离谱值也不会炸 ——
 * 生成二级/三级色时要给色相加偏移，取模这一步省不掉。
 */
internal fun hslToArgb(h: Float, s: Float, l: Float): Long {
    val hh = (((h % 360f) + 360f) % 360f) / 360f
    val ss = s.coerceIn(0f, 1f)
    val ll = l.coerceIn(0f, 1f)
    if (ss == 0f) {
        val v = Math.round(ll * 255f)
        return argb(v, v, v)
    }
    val q = if (ll < 0.5f) ll * (1f + ss) else ll + ss - ll * ss
    val p = 2f * ll - q
    fun channel(t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    return argb(
        Math.round(channel(hh + 1f / 3f) * 255f),
        Math.round(channel(hh) * 255f),
        Math.round(channel(hh - 1f / 3f) * 255f)
    )
}

/**
 * 相对亮度（WCAG 定义）。这是算对比度用的，**不是** HSL 的 lightness ——
 * 两者在饱和色上能差出一倍，这也是「黄色主色配白字看不清」的根源。
 */
internal fun relativeLuminance(color: Long): Double {
    fun lin(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    val r = lin(((color shr 16) and 0xFF).toInt())
    val g = lin(((color shr 8) and 0xFF).toInt())
    val b = lin((color and 0xFF).toInt())
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/**
 * 两个颜色的对比度（1.0 ~ 21.0）。
 *
 * WCAG 的阈值参考：正文 ≥ 4.5，大号字 ≥ 3.0，最好 ≥ 7.0。
 * 离线测试会拿它逐对校验生成的配色 —— 光看 hex 是看不出「白字压黄底」的。
 */
fun contrastRatio(a: Long, b: Long): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * 对比度兜底：从 [startL] 出发，沿亮度轴逐步挪动，直到 [color] 与 [against] 的对比度达标。
 *
 * @param darker true = 往暗处挪（浅色主题用，前景是白字）；
 *               false = 往亮处挪（深色主题用，前景是深字）
 */
private fun fitContrast(
    h: Float,
    s: Float,
    startL: Float,
    against: Long,
    darker: Boolean,
    minRatio: Double
): Long {
    var l = startL
    repeat(70) {
        val c = hslToArgb(h, s, l)
        if (contrastRatio(c, against) >= minRatio) return c
        l += if (darker) -0.01f else 0.01f
        // 走到头还没达标就认了：继续挪会变成纯黑/纯白，反而更难看。
        // 正常色相在 70 步内都会达标，这里只是防呆。
        if (l <= 0.04f || l >= 0.96f) return hslToArgb(h, s, l.coerceIn(0.04f, 0.96f))
    }
    return hslToArgb(h, s, l)
}

/** 「正文级」对比度门槛（WCAG AA）。 */
const val MIN_TEXT_CONTRAST: Double = 4.5

// ==================== 生成器 ====================

/**
 * 由种子色生成一整套配色。
 *
 * @param seed 种子色（ARGB Long）。只取它的色相和大致饱和/亮度倾向。
 * @param dark true 生成深色方案，false 生成浅色方案
 */
fun generateScheme(seed: Long, dark: Boolean): Scheme {
    val hsl = rgbToHsl(seed)
    val h = hsl[0]
    // 主色饱和度。下限压到 0.14（而不是「至少 0.44」）：
    // 「石墨」这类本来就该偏灰的配色、以及用户把「鲜艳度」滑块拖到低位的场景，
    // 都得真的变灰 —— 否则滑块下半段会整段失灵（全被夹到同一个值）。
    val pS = hsl[1].coerceIn(0.14f, 0.96f)
    // 主色亮度：收进「像品牌色」的区间。种子本身很亮/很暗时以这个区间为准，
    // 否则白色 App 名字这种地方会直接翻车（尤其明黄、荧光绿）。
    val pL = hsl[2].coerceIn(0.28f, 0.44f)

    // 二级色 = 色相 +30°，三级色 = 色相 -42°。
    // 不留成同色相不同饱和度（M3 默认做法）：那个在「只有主色一个色相」的
    // 阅读类 App 里看起来太单调，稍微错开一点层次感更好，也不至于不像一套。
    val h2 = h + 30f
    val h3 = h - 42f

    return if (dark) darkScheme(h, h2, h3, pS) else lightScheme(h, h2, h3, pS, pL)
}

private fun lightScheme(h: Float, h2: Float, h3: Float, pS: Float, pL: Float): Scheme {
    // 主色要压得住白字：明黄/浅绿这类色相会被这里自动压暗
    val primary = fitContrast(h, pS, pL, WHITE, darker = true, minRatio = MIN_TEXT_CONTRAST)
    val secondary = fitContrast(h2, (pS * 0.82f).coerceIn(0.3f, 0.9f), 0.38f, WHITE, true, MIN_TEXT_CONTRAST)
    val tertiary = fitContrast(h3, (pS * 0.9f).coerceIn(0.3f, 0.92f), 0.40f, WHITE, true, MIN_TEXT_CONTRAST)

    val inverseSurface = neutral(h, 0.40f, 0.19f)

    // 容器色同样要过对比度兜底：容器上是要写字的（标签、徽标、选中态），
    // 只是「浅底 + 深字」通常本来就够，所以大多数时候这一步什么都不做。
    val onPrimaryContainer = hslToArgb(h, (pS * 0.88f).coerceIn(0.30f, 0.95f), 0.125f)
    val onSecondaryContainer = hslToArgb(h2, (pS * 0.92f).coerceIn(0.30f, 0.95f), 0.135f)
    val onTertiaryContainer = hslToArgb(h3, (pS * 0.92f).coerceIn(0.30f, 0.95f), 0.135f)

    return Scheme(
        primary = primary,
        onPrimary = WHITE,
        primaryContainer = fitContrast(h, (pS * 0.58f).coerceIn(0.20f, 0.60f), 0.885f, onPrimaryContainer, false, MIN_TEXT_CONTRAST),
        onPrimaryContainer = onPrimaryContainer,
        // 「反色主色」画在深色的 inverseSurface 上，所以它要够亮才看得见
        inversePrimary = fitContrast(h, (pS * 0.72f).coerceIn(0.28f, 0.8f), 0.60f, inverseSurface, false, MIN_TEXT_CONTRAST),

        secondary = secondary,
        onSecondary = WHITE,
        secondaryContainer = fitContrast(h2, (pS * 0.44f).coerceIn(0.16f, 0.46f), 0.895f, onSecondaryContainer, false, MIN_TEXT_CONTRAST),
        onSecondaryContainer = onSecondaryContainer,

        tertiary = tertiary,
        onTertiary = WHITE,
        tertiaryContainer = fitContrast(h3, (pS * 0.68f).coerceIn(0.22f, 0.62f), 0.875f, onTertiaryContainer, false, MIN_TEXT_CONTRAST),
        onTertiaryContainer = onTertiaryContainer,

        background = neutral(h, 1.10f, 0.966f),
        onBackground = neutral(h, 0.60f, 0.105f),
        surface = WHITE,
        onSurface = neutral(h, 0.60f, 0.105f),
        surfaceVariant = neutral(h, 1.05f, 0.929f),
        onSurfaceVariant = neutral(h, 0.44f, 0.355f),

        surfaceBright = WHITE,
        surfaceDim = neutral(h, 0.88f, 0.847f),
        surfaceContainerLowest = WHITE,
        surfaceContainerLow = neutral(h, 0.95f, 0.951f),
        surfaceContainer = neutral(h, 0.86f, 0.929f),
        surfaceContainerHigh = neutral(h, 0.66f, 0.904f),
        surfaceContainerHighest = neutral(h, 0.62f, 0.879f),

        outline = neutral(h, 0.50f, 0.780f),
        outlineVariant = neutral(h, 0.70f, 0.894f),

        // 错误色**不跟着种子走**：红色是「出错了」的通用语义，
        // 换成紫色主题也必须是红的，否则用户认不出来。
        error = 0xFFBA1A1AL,
        onError = WHITE,
        errorContainer = 0xFFFFDAD6L,
        onErrorContainer = 0xFF410002L,

        inverseSurface = inverseSurface,
        inverseOnSurface = neutral(h, 0.68f, 0.930f)
    )
}

private fun darkScheme(h: Float, h2: Float, h3: Float, pS: Float): Scheme {
    // 先把「压在主色上的深色文字」算出来，再拿它去校准主色 ——
    // 顺序反了的话，校准用的颜色和最终用的颜色差一点，对比度就可能压在及格线上。
    val onPrimary = hslToArgb(h, pS, 0.155f)
    val onSecondary = hslToArgb(h2, pS, 0.185f)
    val onTertiary = hslToArgb(h3, pS, 0.155f)
    val inverseSurface = neutral(h, 0.78f, 0.882f)
    // 容器色上的文字是浅色的，所以容器本身要够暗 —— 这里同样要兜底。
    // （离线测试就是在这里抓到过的：海蓝那套的深色三级容器只有 3.76，
    //   低于 AA 的 4.5，肉眼看着就是「浅绿字压在深绿上有点糊」。）
    val onPrimaryContainer = hslToArgb(h, (pS * 0.58f).coerceIn(0.16f, 0.60f), 0.885f)
    val onSecondaryContainer = hslToArgb(h2, (pS * 0.44f).coerceIn(0.16f, 0.46f), 0.895f)
    val onTertiaryContainer = hslToArgb(h3, (pS * 0.56f).coerceIn(0.16f, 0.60f), 0.885f)

    // 深色下前景是「深色文字」，所以主色要**调亮**到压得住它
    val primary = fitContrast(h, (pS * 0.84f).coerceIn(0.30f, 0.86f), 0.680f, onPrimary, false, MIN_TEXT_CONTRAST)
    val secondary = fitContrast(h2, (pS * 0.66f).coerceIn(0.22f, 0.72f), 0.775f, onSecondary, false, MIN_TEXT_CONTRAST)
    val tertiary = fitContrast(h3, (pS * 0.82f).coerceIn(0.26f, 0.86f), 0.750f, onTertiary, false, MIN_TEXT_CONTRAST)

    return Scheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = fitContrast(h, (pS * 0.84f).coerceIn(0.30f, 0.88f), 0.305f, onPrimaryContainer, true, MIN_TEXT_CONTRAST),
        onPrimaryContainer = onPrimaryContainer,
        // 深色主题的 inverseSurface 是浅色的，所以「反色主色」要压暗
        inversePrimary = fitContrast(h, pS, 0.360f, inverseSurface, true, MIN_TEXT_CONTRAST),

        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = fitContrast(h2, (pS * 0.78f).coerceIn(0.26f, 0.82f), 0.290f, onSecondaryContainer, true, MIN_TEXT_CONTRAST),
        onSecondaryContainer = onSecondaryContainer,

        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = fitContrast(h3, (pS * 0.84f).coerceIn(0.30f, 0.88f), 0.300f, onTertiaryContainer, true, MIN_TEXT_CONTRAST),
        onTertiaryContainer = onTertiaryContainer,

        background = neutral(h, 1.28f, 0.070f),
        onBackground = neutral(h, 0.78f, 0.882f),
        surface = neutral(h, 0.92f, 0.098f),
        onSurface = neutral(h, 0.78f, 0.882f),
        surfaceVariant = neutral(h, 0.58f, 0.267f),
        onSurfaceVariant = neutral(h, 0.22f, 0.769f),

        surfaceBright = neutral(h, 0.72f, 0.245f),
        surfaceDim = neutral(h, 1.28f, 0.070f),
        surfaceContainerLowest = neutral(h, 1.10f, 0.047f),
        surfaceContainerLow = neutral(h, 0.95f, 0.102f),
        surfaceContainer = neutral(h, 0.82f, 0.118f),
        surfaceContainerHigh = neutral(h, 0.70f, 0.157f),
        surfaceContainerHighest = neutral(h, 0.56f, 0.208f),

        outline = neutral(h, 0.30f, 0.556f),
        outlineVariant = neutral(h, 0.58f, 0.267f),

        error = 0xFFFFB4ABL,
        onError = 0xFF690005L,
        errorContainer = 0xFF93000AL,
        onErrorContainer = 0xFFFFDAD6L,

        inverseSurface = inverseSurface,
        inverseOnSurface = neutral(h, 0.50f, 0.190f)
    )
}

/**
 * 中性色（背景 / 表面 / 描边）。
 *
 * 刻意**不完全脱色**：保留一点点种子色的色相 + 低饱和，
 * 让「彩色主色 + 纯灰背景」那种割裂感消失。
 * 但这层色相必须压得很轻（[mul] 乘以基础饱和度约 0.16），
 * 否则粉色主题会变成一整个粉红界面，看久了很累。
 */
private fun neutral(hue: Float, mul: Float, lightness: Float): Long =
    hslToArgb(hue, (0.16f * mul).coerceIn(0f, 0.34f), lightness)
