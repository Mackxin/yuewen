package com.example.yuewen.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 阅闻·品牌配色
 *
 * 主色：青绿（像「新鲜资讯」的颜色）；辅助：靛蓝（用于链接/次要强调）；点缀：琥珀（用于收藏/收藏夹）。
 * 比 Material 默认的紫更贴合「阅读类工具」的气质，也更清爽。
 * 色槽按 Material 3 规范配齐（含 surfaceContainer 系列），否则未指定的槽会退回 M3 默认的紫色调。
 */

// ---------------- 浅色 ----------------
val L_Primary = Color(0xFF0E9F76)
val L_OnPrimary = Color(0xFFFFFFFF)
val L_PrimaryContainer = Color(0xFFBFF3E2)
val L_OnPrimaryContainer = Color(0xFF00281D)
val L_InversePrimary = Color(0xFF5ADBB1)

val L_Secondary = Color(0xFF4356E0)
val L_OnSecondary = Color(0xFFFFFFFF)
val L_SecondaryContainer = Color(0xFFDDE1FF)
val L_OnSecondaryContainer = Color(0xFF001158)

val L_Tertiary = Color(0xFFD98420)
val L_OnTertiary = Color(0xFFFFFFFF)
val L_TertiaryContainer = Color(0xFFFFE0BC)
val L_OnTertiaryContainer = Color(0xFF2B1700)

val L_Background = Color(0xFFF5F8F6)
val L_OnBackground = Color(0xFF171D1A)

val L_Surface = Color(0xFFFFFFFF)
val L_OnSurface = Color(0xFF171D1A)
val L_SurfaceVariant = Color(0xFFE9EFEB)
val L_OnSurfaceVariant = Color(0xFF55605A)

val L_SurfaceBright = Color(0xFFFFFFFF)
val L_SurfaceDim = Color(0xFFD5DAD7)
val L_SurfaceContainerLowest = Color(0xFFFFFFFF)
val L_SurfaceContainerLow = Color(0xFFF0F4F1)
val L_SurfaceContainer = Color(0xFFEAEFEB)
val L_SurfaceContainerHigh = Color(0xFFE4E9E6)
val L_SurfaceContainerHighest = Color(0xFFDEE4E0)

val L_Outline = Color(0xFFC3CBC6)
val L_OutlineVariant = Color(0xFFE1E7E3)

val L_Error = Color(0xFFBA1A1A)
val L_OnError = Color(0xFFFFFFFF)
val L_ErrorContainer = Color(0xFFFFDAD6)
val L_OnErrorContainer = Color(0xFF410002)

val L_Scrim = Color(0xFF000000)
val L_InverseSurface = Color(0xFF2C322F)
val L_InverseOnSurface = Color(0xFFEDF1EE)

// ---------------- 深色 ----------------
val D_Primary = Color(0xFF55D9B0)
val D_OnPrimary = Color(0xFF003828)
val D_PrimaryContainer = Color(0xFF00513C)
val D_OnPrimaryContainer = Color(0xFFBFF3E2)
val D_InversePrimary = Color(0xFF0E9F76)

val D_Secondary = Color(0xFFB9C3FF)
val D_OnSecondary = Color(0xFF08218C)
val D_SecondaryContainer = Color(0xFF26359E)
val D_OnSecondaryContainer = Color(0xFFDDE1FF)

val D_Tertiary = Color(0xFFFFB868)
val D_OnTertiary = Color(0xFF482900)
val D_TertiaryContainer = Color(0xFF663D00)
val D_OnTertiaryContainer = Color(0xFFFFE0BC)

val D_Background = Color(0xFF0F1512)
val D_OnBackground = Color(0xFFDEE4E0)

val D_Surface = Color(0xFF161C19)
val D_OnSurface = Color(0xFFDEE4E0)
val D_SurfaceVariant = Color(0xFF3F4945)
val D_OnSurfaceVariant = Color(0xFFBFC9C3)

val D_SurfaceBright = Color(0xFF3B423E)
val D_SurfaceDim = Color(0xFF0F1512)
val D_SurfaceContainerLowest = Color(0xFF0A100D)
val D_SurfaceContainerLow = Color(0xFF171D1A)
val D_SurfaceContainer = Color(0xFF1B211E)
val D_SurfaceContainerHigh = Color(0xFF252C28)
val D_SurfaceContainerHighest = Color(0xFF303733)

val D_Outline = Color(0xFF89938D)
val D_OutlineVariant = Color(0xFF3F4945)

val D_Error = Color(0xFFFFB4AB)
val D_OnError = Color(0xFF690005)
val D_ErrorContainer = Color(0xFF93000A)
val D_OnErrorContainer = Color(0xFFFFDAD6)

val D_Scrim = Color(0xFF000000)
val D_InverseSurface = Color(0xFFDEE4E0)
val D_InverseOnSurface = Color(0xFF2C322F)

// ---------------- 阅读器专用底色 ----------------
// 「米黄纸感」和「墨夜」两套阅读配色，独立于全局主题，用于正念阅读模式。
val ReaderPaperBg = Color(0xFFFBF6EC)
val ReaderPaperInk = Color(0xFF33302A)
val ReaderPaperInkVariant = Color(0xFF6E6A61)

val ReaderNightBg = Color(0xFF0E1210)
val ReaderNightInk = Color(0xFFC6CCC8)
val ReaderNightInkVariant = Color(0xFF7C837E)
