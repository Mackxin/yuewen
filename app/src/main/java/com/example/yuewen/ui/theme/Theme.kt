package com.example.yuewen.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ---------------- 配色方案 ----------------

/**
 * 把生成的 [Scheme]（纯 ARGB Long）转成 Compose 的 `ColorScheme`。
 *
 * 生成器刻意不依赖 Compose（这样能进离线回归测试），这里就是那道转换关口。
 */
private fun Scheme.toColorScheme(): ColorScheme = if (isDark(this)) {
    darkColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceBright = Color(surfaceBright),
        surfaceDim = Color(surfaceDim),
        surfaceContainerLowest = Color(surfaceContainerLowest),
        surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        scrim = Color(0xFF000000),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        surfaceTint = Color(primary)
    )
} else {
    lightColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceBright = Color(surfaceBright),
        surfaceDim = Color(surfaceDim),
        surfaceContainerLowest = Color(surfaceContainerLowest),
        surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        scrim = Color(0xFF000000),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        surfaceTint = Color(primary)
    )
}

/**
 * 判断生成出来的方案是不是深色。
 *
 * 不额外传一个 `dark` 参数进来：`Scheme` 已经有了全部信息，
 * 用「背景比前景暗」这个不变量自己判断，调用方少一个可能传错的参数。
 */
private fun isDark(s: Scheme): Boolean = relativeLuminance(s.background) < relativeLuminance(s.onBackground)

/** 默认档「青绿」的手工配色 —— 保持和 v2.2 及以前完全一致，不动老用户的观感。 */
private val EmeraldLight = lightColorScheme(
    primary = L_Primary,
    onPrimary = L_OnPrimary,
    primaryContainer = L_PrimaryContainer,
    onPrimaryContainer = L_OnPrimaryContainer,
    inversePrimary = L_InversePrimary,

    secondary = L_Secondary,
    onSecondary = L_OnSecondary,
    secondaryContainer = L_SecondaryContainer,
    onSecondaryContainer = L_OnSecondaryContainer,

    tertiary = L_Tertiary,
    onTertiary = L_OnTertiary,
    tertiaryContainer = L_TertiaryContainer,
    onTertiaryContainer = L_OnTertiaryContainer,

    background = L_Background,
    onBackground = L_OnBackground,
    surface = L_Surface,
    onSurface = L_OnSurface,
    surfaceVariant = L_SurfaceVariant,
    onSurfaceVariant = L_OnSurfaceVariant,

    surfaceBright = L_SurfaceBright,
    surfaceDim = L_SurfaceDim,
    surfaceContainerLowest = L_SurfaceContainerLowest,
    surfaceContainerLow = L_SurfaceContainerLow,
    surfaceContainer = L_SurfaceContainer,
    surfaceContainerHigh = L_SurfaceContainerHigh,
    surfaceContainerHighest = L_SurfaceContainerHighest,

    outline = L_Outline,
    outlineVariant = L_OutlineVariant,

    error = L_Error,
    onError = L_OnError,
    errorContainer = L_ErrorContainer,
    onErrorContainer = L_OnErrorContainer,

    scrim = L_Scrim,
    inverseSurface = L_InverseSurface,
    inverseOnSurface = L_InverseOnSurface,
    surfaceTint = L_Primary
)

private val EmeraldDark = darkColorScheme(
    primary = D_Primary,
    onPrimary = D_OnPrimary,
    primaryContainer = D_PrimaryContainer,
    onPrimaryContainer = D_OnPrimaryContainer,
    inversePrimary = D_InversePrimary,

    secondary = D_Secondary,
    onSecondary = D_OnSecondary,
    secondaryContainer = D_SecondaryContainer,
    onSecondaryContainer = D_OnSecondaryContainer,

    tertiary = D_Tertiary,
    onTertiary = D_OnTertiary,
    tertiaryContainer = D_TertiaryContainer,
    onTertiaryContainer = D_OnTertiaryContainer,

    background = D_Background,
    onBackground = D_OnBackground,
    surface = D_Surface,
    onSurface = D_OnSurface,
    surfaceVariant = D_SurfaceVariant,
    onSurfaceVariant = D_OnSurfaceVariant,

    surfaceBright = D_SurfaceBright,
    surfaceDim = D_SurfaceDim,
    surfaceContainerLowest = D_SurfaceContainerLowest,
    surfaceContainerLow = D_SurfaceContainerLow,
    surfaceContainer = D_SurfaceContainer,
    surfaceContainerHigh = D_SurfaceContainerHigh,
    surfaceContainerHighest = D_SurfaceContainerHighest,

    outline = D_Outline,
    outlineVariant = D_OutlineVariant,

    error = D_Error,
    onError = D_OnError,
    errorContainer = D_ErrorContainer,
    onErrorContainer = D_OnErrorContainer,

    scrim = D_Scrim,
    inverseSurface = D_InverseSurface,
    inverseOnSurface = D_InverseOnSurface,
    surfaceTint = D_Primary
)

/**
 * 取当前生效的配色。
 *
 * **默认档（青绿）走手工色值**，其余档位与自定义都由 [generateScheme] 现算 ——
 * 这样既有「想加多少套就加多少套」的扩展性，又保证用户看惯的默认外观一个像素都不变。
 */
fun yuewenColorScheme(
    palette: ThemePalette,
    customHue: Int,
    customSat: Int,
    dark: Boolean
): ColorScheme = when {
    palette == ThemePalette.Emerald && dark -> EmeraldDark
    palette == ThemePalette.Emerald -> EmeraldLight
    else -> generateScheme(seedFor(palette, customHue, customSat), dark).toColorScheme()
}

// ---------------- 形状：统一用「大圆角」，视觉更柔和现代 ----------------

val YuewenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

// ---------------- 字体 ----------------

fun scaledTypography(scale: Float) = Typography(
    headlineMedium = TextStyle(fontSize = (27 * scale).sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = (16.5 * scale).sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = (14.5 * scale).sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = (15.5 * scale).sp, lineHeight = (24 * scale).sp),
    bodyMedium = TextStyle(fontSize = (13.5 * scale).sp, lineHeight = (20 * scale).sp),
    bodySmall = TextStyle(fontSize = (12.5 * scale).sp, lineHeight = (18 * scale).sp),
    labelLarge = TextStyle(fontSize = (14 * scale).sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = (12 * scale).sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = (11 * scale).sp)
)

// ---------------- 阅读器配色 ----------------

/**
 * 阅读底色三选一：跟随主题 / 米黄纸感 / 墨夜。
 *
 * 「跟随主题」跟的是 **App 的主题设置**（浅色 / 深色 / 跟随系统），不是直接问系统 ——
 * 用户在设置里手动选了深色，阅读页就该是深色，哪怕系统还是浅色。
 */
enum class ReaderTheme(val key: String, val label: String) {
    Auto("auto", "跟随主题"),
    Paper("paper", "米黄纸感"),
    Night("night", "墨夜");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Auto
    }
}

/** 阅读正文字体：无衬线（清爽）/ 衬线（纸感）。 */
enum class ReaderFont(val key: String, val label: String, val family: FontFamily) {
    Sans("sans", "无衬线", FontFamily.SansSerif),
    Serif("serif", "衬线", FontFamily.Serif);

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Sans
    }
}

/** 行距三档。 */
enum class ReaderSpacing(val key: String, val label: String, val multiplier: Float) {
    Tight("tight", "紧凑", 1.45f),
    Normal("normal", "标准", 1.7f),
    Loose("loose", "宽松", 2.0f);

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Normal
    }
}

/** 阅读字号四档（正文 sp）。 */
val ReaderSizes = listOf(15, 17, 19, 22)
val ReaderSizeLabels = listOf("小", "标准", "大", "特大")

/**
 * 当前生效的阅读配色。
 * @param onPaper true 表示底色本身是**浅色**（需要把状态栏图标切成深色）。
 *   注意它描述的是「底色亮不亮」，不是「主题深不深」—— 米黄纸感在深色主题下也是 true。
 */
data class ReaderPalette(
    val background: Color,
    val ink: Color,
    val inkVariant: Color,
    val onPaper: Boolean
)

/**
 * 阅读页配色。
 *
 * ⚠️ **深浅一律看「当前生效的 `MaterialTheme.colorScheme`」，不接受外部传 `darkTheme`。**
 *
 * 这是 v2.7.4 修掉的一个真 bug：以前签名是 `readerPalette(theme, darkTheme)`，
 * 而 `DetailScreen` 传的是 `isSystemInDarkTheme()` ——
 * 于是「设置里手动选了深色、系统还是浅色」时，阅读页拿到的是下面那个硬编码的浅色底，
 * 用户反馈「深色模式的时候文章内容界面没有变成深色」。
 *
 * 同一个教训在 [com.example.yuewen.ui.components.YuewenBottomBar] 也踩过：
 * **要判断深浅就读当前生效的底色，别问系统。**
 * `MaterialTheme.colorScheme` 已经是由 App 主题设置（浅色 / 深色 / 跟随系统）算好的结果，
 * 再自己去问一次系统，就是第二份真相，迟早对不上。
 *
 * 顺带把「跟随主题」的浅色分支从硬编码 `#FCFDFB` 换成 `cs.background`
 * —— 阅读页底色从此和 App 主题方案（含青绿 / 靛蓝 / 琥珀 / 自定义色相）严格一致。
 */
@Composable
fun readerPalette(theme: ReaderTheme): ReaderPalette {
    val cs = MaterialTheme.colorScheme
    return when (theme) {
        // 「跟随主题」= 完全交给当前配色方案，深浅与色相都跟着走
        ReaderTheme.Auto -> ReaderPalette(
            background = cs.background,
            ink = cs.onBackground,
            inkVariant = cs.onSurfaceVariant,
            onPaper = cs.background.luminance() > 0.5f
        )
        ReaderTheme.Paper -> ReaderPalette(ReaderPaperBg, ReaderPaperInk, ReaderPaperInkVariant, true)
        ReaderTheme.Night -> ReaderPalette(ReaderNightBg, ReaderNightInk, ReaderNightInkVariant, false)
    }
}

@Composable
fun YuewenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    palette: ThemePalette = ThemePalette.Emerald,
    customHue: Int = DEFAULT_CUSTOM_HUE,
    customSat: Int = DEFAULT_CUSTOM_SAT,
    content: @Composable () -> Unit
) {
    // 配色方案变了要重算（生成器是纯计算，几毫秒的事；用 remember 记一下避免每帧都算）
    val colorScheme = remember(palette, customHue, customSat, darkTheme) {
        yuewenColorScheme(palette, customHue, customSat, darkTheme)
    }

    // ---------------- 状态栏 / 导航栏 / 窗口底色 ----------------
    // v1.6.1：这两条以前是「透明 + 透出 M3 默认窗口底色」，视觉上就是顶部和底部各一条粉色带。
    //   当时的做法：把两条系统栏的底色刷成当前页面背景色 → 色带消失。
    // v2.6：改成真正的沉浸式（`enableEdgeToEdge`，见 MainActivity），系统栏是**透明**的，
    //   透出来的就是 App 自己铺的背景 —— 于是这里不再需要（也不该）去写
    //   `statusBarColor` / `navigationBarColor`：那两个 API 在 Android 15 已废弃，
    //   写了也会被透明覆盖，留着只会多两条弃用告警。
    //
    // 现在这里只管两件事：
    // ① **窗口底色跟着「当前生效的配色」走**，而不是跟着系统深浅走。
    //    窗口底色原本来自 themes.xml 的 `@color/yuewen_window_bg`，走 `values-night` 限定符 ——
    //    那个限定符跟的是**系统**的深浅模式。用户把 App 手动设成深色、系统却还是浅色时，
    //    窗口底色仍然是浅色的 #F5F8F6；只要屏幕上有一小块没人覆盖的区域
    //    （v2.5 及以前就是底部那条 58dp 的底栏占位带），就会白出来一块。
    //    这正是用户截图反馈的「深色模式底部一条白」。
    // ② 关掉导航栏的对比度遮罩，否则系统会在浅色内容上再糊一层半透明黑。
    //
    // 为什么放在 Compose 而不是 themes.xml：App 支持「跟随系统 / 手动浅色 / 手动深色」三档，
    // 手动选择时可能与系统深浅不一致，而只有这里才知道此刻真正生效的是哪一套配色。
    val view = LocalView.current
    val barColor = colorScheme.background.toArgb()
    if (!view.isInEditMode) {
        // 用 LaunchedEffect 而不是 SideEffect：SideEffect 每次成功重组都会跑，
        // 而这几件事只在「底色或深浅变了」的时候才需要做一次。
        LaunchedEffect(barColor, darkTheme) {
            val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
            window.setBackgroundDrawable(ColorDrawable(barColor))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // ⚠️ 这里必须 remember：`scaledTypography()` 内部会 new 出一整套 TextStyle，
    // 直接在参数里调它，等于每次重组都重新分配几十个对象，还会让所有用到
    // `MaterialTheme.typography` 的组件被判定为「参数变了」而跟着重组。
    val typography = remember(fontScale) { scaledTypography(fontScale) }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = YuewenShapes,
        typography = typography,
        content = content
    )
}
