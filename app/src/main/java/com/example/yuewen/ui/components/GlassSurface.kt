package com.example.yuewen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yuewen.ui.theme.Glass

/**
 * 一块「液态玻璃」表面：半透明填充 + 顶部受光渐变 + 一圈高光描边。
 *
 * 这是整个 App 里唯一的玻璃实现 —— 底栏、首页顶栏、闻件胶囊标签栏都走它，
 * 保证三处的透明度、描边、受光面完全一致（各写一份迟早会走形）。
 *
 * [glass] 为 false 时退化成一个**不透明**的普通 Surface：
 * 和 v2.5 及以前的观感一模一样，这样「液态玻璃」开关关掉时外观不变。
 *
 * ⚠️ 数值全部来自 [Glass]（纯 ARGB 运算、可离线测试），这里只负责转成 Compose 颜色。
 * 关于「为什么不是真模糊」，见 [Glass] 的类注释。
 *
 * @param glass 是否启用玻璃质感（设置 → 外观 → 液态玻璃）
 * @param shape 外形。底栏 / 标签栏传 `CircleShape`，顶栏传矩形。
 * @param shadowElevation 投影高度。深色下建议传 0（深底上的阴影看不出来，还会发灰）。
 * @param solidColor 关掉玻璃时用的实色。**默认色**是 `surfaceContainer` 系（底栏 / 标签栏
 *   一直用的就是它）；首页顶栏传 `colorScheme.background`，
 *   因为它在 v2.5 及以前本来就是「和页面同色的一条」。
 */
@Composable
fun GlassSurface(
    glass: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 0.dp,
    solidColor: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // 深浅判断沿用底栏 / 标签栏一直用的那套规则：surface 亮度低于一半即深色。
    // 这样三处的判断口径一致，不会出现「底栏是玻璃、顶栏却当成浅色」的错位。
    val dark = cs.surface.luminance() < 0.5f

    val baseArgb = cs.surfaceContainer.toArgb().toLong() and 0xFFFFFFFFL

    val fill = when {
        glass -> Color(Glass.withAlpha(baseArgb, Glass.fillAlpha(dark)))
        // 不透明档：默认取 surfaceContainer 系，和 v2.5 完全一致
        solidColor != Color.Unspecified -> solidColor
        dark -> cs.surfaceContainerLow
        else -> cs.surfaceContainer
    }

    val border = if (glass) {
        BorderStroke(Glass.STROKE_DP.dp, Color(Glass.white(Glass.strokeAlpha(dark))))
    } else null

    // 顶部受光面：从白色渐变到全透明，只盖顶部约一半。
    // 写成 Brush 而不是纯色 —— 纯色会在中间留下一条硬边。
    val sheen = if (glass) {
        Brush.verticalGradient(
            0f to Color(Glass.white(Glass.sheenAlpha(dark))),
            1f to Color(Glass.white(0f))
        )
    } else null

    Surface(
        modifier = modifier,
        shape = shape,
        color = fill,
        border = border,
        shadowElevation = shadowElevation
    ) {
        Box(
            modifier = Modifier.drawBehind {
                // 在内容之下、Surface 之内画受光面（会被 shape 裁掉边角）
                sheen?.let {
                    drawRect(
                        brush = it,
                        size = Size(size.width, size.height * Glass.SHEEN_HEIGHT_RATIO)
                    )
                }
            }
        ) {
            content()
        }
    }
}
