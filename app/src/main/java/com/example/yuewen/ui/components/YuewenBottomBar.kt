package com.example.yuewen.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuewen.ui.navigation.Screen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 高亮块滑行一次的时长（毫秒）。和 [PillTabRow] 的 260ms 同一档，观感统一。 */
private const val PILL_ANIM_MS = 280

/**
 * 悬浮胶囊底栏。
 *
 * - v1.5：高亮不再「滑停后才切过去」，改为跟着 [PagerState] 的滑动进度连续驱动。
 * - v1.6：点击不再有灰色反馈块（原因见 [BarItem]）。
 * - v2.5：滑动进度改为**按需读取**（`pageOffsetOf`) 函数），避免每帧重组整条底栏。
 * - v2.6：**动效重做**，见下。
 *
 * ## v2.6 为什么要改
 *
 * v2.5 为了治「点底栏切页时途经页被现场组合」的卡顿，把跨页跳转改成了**瞬切**
 * （`scrollToPage`）。卡顿是没了，但高亮块是**直接绑 Pager 的即时位置**的 ——
 * Pager 瞬移，高亮块也就跟着瞬移，用户看到的就是「啪」地跳过去，很生硬，
 * 和「闻件」顶部那排胶囊标签栏（`PillTabRow`，用补间动画滑过去）完全两种手感。
 *
 * 现在把高亮块的位置**从 Pager 里解耦**，交给一个自己的 [Animatable]：
 *
 * - **手指拖动 / 滑动动画进行中**（`currentPageOffsetFraction != 0`）→ `snapTo` 直接跟手，
 *   一帧都不能落后；
 * - **其余情况**（点击切换、翻页动画结束落位）→ `animateTo` 补一段 280ms 的滑行。
 *
 * 于是：拖动时和以前一样跟手，点击时和「闻件」标签栏一样顺滑。
 *
 * ⚠️ 判据为什么是「`isScrollInProgress` **且** `offsetFraction != 0`」而不是只用前者：
 * `scrollToPage()`（跨页瞬切）内部也会把 `isScrollInProgress` 置真，但整个过程
 * `offsetFraction` 恒为 0。只看前者的话，会把「瞬切」误判成拖拽 → 又退回跳变。
 *
 * ## v2.6 其它改动
 * - 加 `navigationBarsPadding()`：沉浸式之后底栏会压在手势条上，得让开；
 * - 底部外边距从 7dp 加到 13dp —— 用户反馈「整体再往上走一点」；
 * - 玻璃质感走 [GlassSurface]（和顶栏、标签栏共用同一份实现）。
 */
@Composable
fun YuewenBottomBar(
    items: List<Screen>,
    pagerState: PagerState,
    onSelect: (Screen) -> Unit,
    unreadCount: Int = 0,
    glass: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val n = items.size.coerceAtLeast(1)

    // 深色判断：surface 亮度低于一半就是深色主题（比传参简单，也跟手势组件解耦）
    val isDark = cs.surface.luminance() < 0.5f

    /**
     * 高亮块自己的位置（单位＝「第几格」，可以是 2.4 这种小数）。
     *
     * 初值取 Pager 的初始页，避免第一帧从 0 滑过去。
     * 它**不直接读 Pager 的即时位置** —— 只在下面的循环里被喂值，
     * 这样「瞬切页面」和「高亮块滑行」就是两件互不干扰的事。
     */
    val pill = remember { Animatable(pagerState.currentPage.toFloat()) }

    LaunchedEffect(pagerState, n) {
        // 正在跑的那段滑行动画。拖拽开始时必须把它掐掉，否则会跟手指打架。
        var anim: Job? = null
        snapshotFlow {
            Triple(
                pagerState.currentPage,
                pagerState.currentPageOffsetFraction,
                pagerState.isScrollInProgress
            )
        }.collect { (page, frac, scrolling) ->
            val last = (n - 1).toFloat()
            if (scrolling && frac != 0f) {
                // 拖拽 / 滑动动画中：直接吸附到当前进度，跟手
                anim?.cancel()
                pill.snapTo((page + frac).coerceIn(0f, last))
            } else {
                // 点击切换：页面已经瞬切过去了，这里补一段滑行把高亮块送过去
                val target = page.coerceIn(0, n - 1).toFloat()
                if (abs(pill.value - target) > 0.001f) {
                    anim?.cancel()
                    anim = launch {
                        pill.animateTo(
                            targetValue = target,
                            animationSpec = tween(PILL_ANIM_MS, easing = FastOutSlowInEasing)
                        )
                    }
                }
            }
        }
    }

    // 计算一次即可，drawBehind 每帧会用
    val pillColor = cs.primary

    GlassSurface(
        glass = glass,
        shape = CircleShape,
        shadowElevation = if (isDark) 0.dp else 8.dp,
        // ① 左右外边距：v1.8 曾误改成归零，胶囊通到屏幕两边反而不好看，保持 16dp；
        // ② 内边距归零：高亮块直接贴着胶囊两端，不再空出一条窄带；
        // ③ v2.6 底部 7dp → 13dp：整体上移一点；
        // ④ v2.6 让开系统导航栏（沉浸式之后底栏会压在手势条上）。
        modifier = modifier
            .fillMaxWidth()
            // ⚠️ 不是 `navigationBarsPadding()`：键盘弹起时导航栏躲在键盘后面，
            // 系统却仍报出它的高度 —— 直接用它会让底栏在键盘上方多浮一截。
            // `exclude(ime)` 表示「键盘在的时候这一段算 0」，
            // 上移交给 MainScreen 根节点的 `imePadding()` 统一管。
            .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
            .padding(start = 16.dp, end = 16.dp, top = 7.dp, bottom = 13.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 胶囊直接用 drawBehind 画：能拿到 Row 的实测宽度，
                // 每个槽位宽度 = 总宽 / 项数，不需要额外的测量布局。
                .drawBehind {
                    // 绘制阶段读：滑行时只重绘这一层，不重组
                    val slot = size.width / n
                    // 高亮块 = 整个槽位宽，两端与胶囊边缘严丝合缝
                    drawRoundRect(
                        color = pillColor,
                        topLeft = Offset(slot * pill.value, 0f),
                        size = Size(slot, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                BarItem(
                    item = item,
                    // 传函数不传值：让读取发生在 BarItem 自己的组合作用域里，
                    // 这样滑行时只失效那一个小格子，外层 Row / GlassSurface 照常跳过
                    pillPosition = { pill.value },
                    index = index,
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
    pillPosition: () -> Float,
    index: Int,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    // ⚠️ 读取放在**这里**（而不是调用方算好传进来）是有意的：
    // 这样每帧失效的只有这一个格子，外层不会被一起拖下去重组。
    // 1 = 高亮块正落在这一项上，0 = 离得最远（相邻项之间的中间位置）
    val fraction = (1f - abs(pillPosition() - index)).coerceIn(0f, 1f)
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
