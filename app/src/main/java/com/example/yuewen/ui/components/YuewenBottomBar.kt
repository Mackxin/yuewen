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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
 * - 曾短暂用过玻璃质感（v2.6.0），已按用户要求**整套撤掉**：
 *   底栏现在没有任何自己的底（无填充 / 无描边 / 无投影），只留图标文字 + 高亮块。
 *   因此本组件**不再接收 `glass` 参数** —— 玻璃开关只管首页顶栏和闻件标签栏。
 * - 未读数从「图标右上角的悬浮徽标」改成**跟在文字后面的一小段数字**：
 *   悬浮徽标会压在图标和文字上（`BadgedBox` 的徽标画在图标边界之外、且不参与布局，
 *   所以下面的文字不知道要给它让位）。详见 [BarItem]。
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

    // ---------------- v2.6.0：底栏不再有自己的底 ----------------
    // 用户反馈「tab 栏那层灰色背景不要了，换成透明的」。查过截图确认：
    // 那层底就是玻璃表面的半透明填充（0.76 alpha 叠在页面底色上，比四周深一档，
    // 实测 (237,239,241) vs 页面 (245,247,249)）。
    //
    // 现在整条底栏**没有填充、没有描边、没有投影**，直接露出页面背景 ——
    // 视觉上只剩「四个图标文字 + 蓝色高亮块」，高亮块自己就能说明当前在哪一页。
    //
    // 因此这里不套任何外壳，也不需要 CircleShape（没有底就无所谓外形）。
    // 副作用：「液态玻璃」开关从此只作用于**首页顶栏**和**闻件标签栏**（设置页文案已同步）。
    Box(
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
                // 高亮块直接用 drawBehind 画：能拿到 Row 的实测宽度，
                // 每个槽位宽度 = 总宽 / 项数，不需要额外的测量布局。
                .drawBehind {
                    // 绘制阶段读：滑行时只重绘这一层，不重组
                    val slot = size.width / n
                    // 高亮块 = 整个槽位宽，两端就是 Row 的左右边界
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
                    // 这样滑行时只失效那一个小格子，外层 Row 照常跳过
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
    // 未读数字的颜色跟高亮块一起插值：未选中时用 error 色（醒目），
    // 选中时变成 onPrimary（白字压在主色胶囊上）。不再需要单独的「徽标文字色」，
    // 因为数字现在是普通文字、直接坐在底栏底色上，不是坐在徽标圆点上。
    val badgeColor = lerp(cs.error, cs.onPrimary, fraction)

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
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                item.label,
                fontSize = 12.5.sp,
                color = tint,
                maxLines = 1
            )
            // ---------------- v2.6.0：数字跟在文字后面，不再用悬浮徽标 ----------------
            // 以前用 `BadgedBox` + `Badge`：徽标画在**图标边界的右上角之外**，
            // 而且**完全不参与布局** —— 所以它自己不知道、旁边的文字也不知道要给它让位，
            // 数字一长（比如 36）就直接压在图标和「首页」两个字上。用户反馈的就是这个。
            //
            // 改成普通文字排在标签后面：宽度由布局系统如实算进去，**从结构上不可能重叠**。
            // 颜色用 badgeColor（未选中时 error 色，选中时变白），照旧醒目。
            // 字号压到 10sp、前面只留 3dp —— 一个槽位约 80dp 宽，装得下「首页 99+」。
            if (badge > 0) {
                Spacer(Modifier.width(3.dp))
                Text(
                    if (badge > 99) "99+" else "$badge",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                    maxLines = 1
                )
            }
        }
    }
}
