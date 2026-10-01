package com.example.yuewen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuewen.ui.navigation.Screen
import kotlin.math.abs

/**
 * 悬浮胶囊底栏。
 *
 * 交互升级（v1.5.0）：以前的高亮是「滑动停止后才切过去」——
 * 因为只在 `isScrollInProgress == false` 时才更新选中项，观感上会「啪」地跳一下，很生硬。
 * 现在直接把 [PagerState] 传给底栏，用 **当前页 + 滑动偏移比例** 算出一个连续进度：
 * - 胶囊（主色圆角块）跟着手指在槽位之间平滑滑行
 * - 图标与文字的着色在「未选中色 ↔ 主色反色」之间连续插值
 * 整个动画由手势驱动，任意速度拖动都不会有跳变。
 *
 * v1.6.0：点击不再有灰色反馈块。原因见 [BarItem] 里的注释。
 */
@Composable
fun YuewenBottomBar(
    items: List<Screen>,
    pagerState: PagerState,
    onSelect: (Screen) -> Unit,
    unreadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val n = items.size.coerceAtLeast(1)

    // 连续进度：currentPage 是整数页，currentPageOffsetFraction 是页间偏移（-0.5~0.5）。
    // 两者相加就是「底栏应该停在哪儿」，拖到一半时它就是 1.5 这种小数。
    // coerceIn 兜住两端过度滚动的越界值，让胶囊不会滑出底栏。
    val pageOffset = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (n - 1).toFloat())

    // 计算一次即可，drawBehind 每帧会用
    val pillColor = cs.primary

    // 深色判断：surface 亮度低于一半就是深色主题（比传参简单，也跟手势组件解耦）
    val isDark = cs.surface.luminance() < 0.5f

    Surface(
        // v1.7.0：
        // ① 高度瘦身到「和高亮块一样高」——Row 的上下 padding 归零后，
        //    胶囊背景就是高亮块本身的尺寸，没有多余的空心带；
        // ② 颜色改为**全不透明**。此前 alpha=0.96 的半透明胶囊在部分机型的系统
        //    「深色模式滤镜」下会混出一条发白的高亮带（用户反馈的暗色白底栏）；
        // ③ 深色下降 shadow（阴影在深色底上没意义，还可能被滤镜处理成亮边）。
        // v1.8：
        // ④ 去掉 1dp 描边 —— 胶囊底色本身已经和背景分得开，多一圈线显得拘谨。
        // v1.9：
        // ⑤ 左右外边距**恢复**（v1.8 曾误改成归零，胶囊通到屏幕两边反而不好看）；
        // ⑥ 真正要去掉的是**内边距** —— Row 的 horizontal padding 与胶囊左右留白都归零，
        //    高亮块直接贴着胶囊两端，不再有一条空出来的窄带。
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
        shape = CircleShape,
        color = if (isDark) cs.surfaceContainerLow else cs.surfaceContainer,
        shadowElevation = if (isDark) 0.dp else 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 胶囊直接用 drawBehind 画：能拿到 Row 的实测宽度，
                // 私有每个槽位宽度 = 总宽 / 项数，不需要额外的测量布局。
                .drawBehind {
                    val slot = size.width / n
                    // 高亮块 = 整个槽位宽，两端与胶囊边缘严丝合缝
                    drawRoundRect(
                        color = pillColor,
                        topLeft = Offset(slot * pageOffset, 0f),
                        size = Size(slot, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                // 1 = 胶囊正落在这一项上，0 = 离得最远（相邻项之间的中间位置）
                val fraction = (1f - abs(pageOffset - index)).coerceIn(0f, 1f)
                BarItem(
                    item = item,
                    fraction = fraction,
                    badge = if (item == Screen.Home) unreadCount else 0,
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 单个槽位。
 *
 * ⚠️ 这里**故意不用** `Surface(onClick = ...)`：Material 的 Surface 无论底色多透明，
 * 只要挂了 onClick 就会自动叠一层 ripple indication，按下去是一块灰色圆角方块，
 * 压在滑行的主色胶囊上非常脏（用户反馈的「点击 tab 栏有灰色背景」就是它）。
 * 换成一个纯 [Box] + `clickable(indication = null)`，点击照样翻页，但不画任何反馈层。
 * `Role.Tab` 保留无障碍语义（读屏软件仍会播报「标签页」）。
 */
@Composable
private fun BarItem(
    item: Screen,
    fraction: Float,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tint = lerp(cs.onSurfaceVariant, cs.onPrimary, fraction)
    val badgeColor = lerp(cs.error, cs.onPrimary, fraction)
    val badgeContent = lerp(cs.onError, cs.primary, fraction)

    // 常驻一个 InteractionSource：没有 indication 时它不会被消费，但传 null 会走默认 ripple
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
        // 多给 3dp 内边距，让点击热区略大于图标本身
        .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgedBox(
                badge = {
                    if (badge > 0) {
                        Badge(containerColor = badgeColor, contentColor = badgeContent) {
                            Text(if (badge > 99) "99+" else "$badge", fontSize = 10.sp)
                        }
                    }
                }
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                item.label,
                fontSize = 12.5.sp,
                color = tint,
                maxLines = 1
            )
        }
    }
}
