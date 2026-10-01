package com.example.yuewen.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 胶囊子页标签栏（v2.2）。
 *
 * 页面里「搜索 / 收藏 / 历史 / 笔记」这种**同一页内的分栏**，以前用的是 Material 的
 * [androidx.compose.material3.TabRow] —— 底下一条指示线 + 文字变色。它跟底栏那套
 * 「悬浮胶囊 + 主色高亮块」的视觉语言完全是两种东西，一上一下摆在同一屏里很割裂。
 *
 * 这里把它换成**和底栏同一套语言**：
 * - 外层是一个圆角胶囊（和底栏同色、同圆角、同左右外边距）；
 * - 选中项 = 一整块主色高亮块，贴着胶囊内壁；
 * - 文字与图标颜色从「次要色」连续插值到「主色反色」。
 *
 * 跟底栏的区别只有一个：底栏的高亮块**跟手指连续滑行**（它绑着 PagerState），
 * 而这里是点击切换，所以用 `animateFloatAsState` 做一个短补间动画 ——
 * 观感同样是「滑过去」而不是「啪地跳过去」。
 *
 * 为什么不直接复用 `YuewenBottomBar`？
 * 底栏的签名要求传 `PagerState`（它的滑动进度全靠那个），而子页标签是普通状态切换，
 * 硬套过去得先造一个假的 PagerState —— 那比写这个小组件更绕。
 * 所以这里只复用**视觉**，不强行复用**实现**。
 */
@Composable
fun PillTabRow(
    items: List<PillTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val n = items.size

    // 目标位置（整数下标）→ 动画进度。tween 让高亮块滑过去而不是闪过去。
    // 用 animateFloatAsState 而不是 Animatable：这里只需要「值跟着目标变」，
    // 不需要手工控制协程作用域。
    val target = selectedIndex.coerceIn(0, n - 1).toFloat()
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 260),
        label = "pillTabProgress"
    )

    // 深色判断：与底栏保持同一套规则（surface 亮度低于一半即深色）
    val isDark = cs.surface.luminance() < 0.5f

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = CircleShape,
        color = if (isDark) cs.surfaceContainerLow else cs.surfaceContainer,
        shadowElevation = if (isDark) 0.dp else 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 高亮块直接用 drawBehind 画：能拿到实测宽度，
                // 每个槽位宽度 = 总宽 / 项数，不需要额外的测量布局。
                .drawBehind {
                    val slot = size.width / n
                    drawRoundRect(
                        color = cs.primary,
                        topLeft = Offset(slot * progress, 0f),
                        size = Size(slot, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                // 1 = 高亮块正落在这一项上，0 = 离得最远
                val fraction = (1f - kotlin.math.abs(progress - index)).coerceIn(0f, 1f)
                PillTabItem(
                    item = item,
                    fraction = fraction,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 一个子页标签的内容：文字，可选一个小图标与角标。 */
data class PillTab(
    val label: String,
    val icon: ImageVector? = null,
    val badge: Int = 0
)

/**
 * 单个槽位。
 *
 * ⚠️ 和底栏一样**故意不用** `Surface(onClick = ...)`：Material 的 Surface 只要挂了 onClick
 * 就会自动叠一层 ripple，按下去是一块灰色圆角方块，压在主色高亮块上很脏。
 * 换成纯 [Box] + `clickable(indication = null)`，点击照样切换但不画反馈层。
 */
@Composable
private fun PillTabItem(
    item: PillTab,
    fraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tint = lerp(cs.onSurfaceVariant, cs.onPrimary, fraction)
    val badgeColor = lerp(cs.error, cs.onPrimary, fraction)
    val badgeContent = lerp(cs.onError, cs.primary, fraction)

    // 常驻一个 InteractionSource：没有 indication 时它不会被消费，
    // 但传 null 会走默认 ripple —— 那就白改了。
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (item.icon != null) {
                BadgedBox(
                    badge = {
                        if (item.badge > 0) {
                            Badge(containerColor = badgeColor, contentColor = badgeContent) {
                                Text(if (item.badge > 99) "99+" else "${item.badge}", fontSize = 10.sp)
                            }
                        }
                    }
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(5.dp))
            }
            Text(
                item.label,
                fontSize = 13.5.sp,
                // 选中项加粗一点点：高亮块之外再给一个更弱的次级信号，
                // 色盲用户 / 高对比模式下也能分清选中的是哪个
                fontWeight = if (fraction > 0.5f) FontWeight.Medium else FontWeight.Normal,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 备用：纯色胶囊底（不需要高亮块时用，例如只有一项）。 */
@Composable
fun PillTabContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = CircleShape,
        color = if (isDark) cs.surfaceContainerLow else cs.surfaceContainer,
        shadowElevation = if (isDark) 0.dp else 3.dp
    ) { content() }
}
