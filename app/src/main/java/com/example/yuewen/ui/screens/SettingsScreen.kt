package com.example.yuewen.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.BuildConfig
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.backup.BackupException
import com.example.yuewen.data.backup.BackupTarget
import com.example.yuewen.data.util.DailyDigest
import com.example.yuewen.data.util.HOME_KEYWORD_LIMIT
import com.example.yuewen.ui.components.ArticleListMode
import com.example.yuewen.ui.theme.ReaderFont
import com.example.yuewen.ui.theme.ReaderSizeLabels
import com.example.yuewen.ui.theme.ReaderSpacing
import com.example.yuewen.ui.theme.ReaderTheme
import com.example.yuewen.ui.theme.ThemePalette
import com.example.yuewen.ui.theme.seedFor
import com.example.yuewen.ui.util.BrowserLauncher
import com.example.yuewen.ui.util.HomeSortMode
import com.example.yuewen.ui.util.IconNames
import com.example.yuewen.ui.util.TtsRateLabels
import com.example.yuewen.ui.util.iconNameOf
import com.example.yuewen.ui.util.rememberImeDismiss
import com.example.yuewen.ui.util.titleOrDefault
import com.example.yuewen.ui.viewmodel.SettingsViewModel
import com.example.yuewen.ui.viewmodel.UpdateUiState
import com.example.yuewen.ui.viewmodel.UpdateViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ------------------------------------------------------------------ 小组件
//
// 设置页的行只有三种形态：分组标题、一行（标签 + 尾巴）、一排可选胶囊。
// 统一成这三个（外加分隔线与子标题），全页看起来才是同一个节奏。
//
// v2.2 分两轮收紧了纵向间距：
//   第一轮 14dp → 10dp（行高 44 → 36），用户还嫌宽；
//   第二轮再压到 8dp（行高约 32dp），说明文字底边距也一起收。
// 下面这些数值是**全页共用**的 —— 改一处就全页跟着变，
// 别再在某个具体行上单独写 padding，否则节奏又乱了。

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // v2.11 对齐修正：原来这里是 22dp —— 比卡片左边缘（12dp）往右一点、
        // 又比行内容（30dp）往左一点，**三条左边界谁也对不齐**，整页看起来就是歪的。
        // 现在改成和卡片左边缘同一条线（12dp）：页面上只剩两列 —— 卡片/组标题一列、
        // 行内容一列。这是列表里最常见的做法，也是最省事的判据：
        // 任何新加的东西，要么齐 12、要么齐 30，没有第三种。
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp)
    )
}

/**
 * 卡片内部的小标题：用来把一张长卡片里的「顶栏」「筛选」「排序」这类子块分开。
 *
 * v2.11：**必须和 [Caption] 明显不同**，否则整页就散了。
 *
 * 原来的写法是 `labelMedium` + `onSurfaceVariant`，跟说明文字（`labelSmall` +
 * `onSurfaceVariant`）几乎一个样 —— 屏幕上看到的就是「顶栏」两个字孤零零地浮在卡片顶部，
 * 分不清它到底是「下面这一段的标题」还是「上面那条的补充说明」。这正是用户说的「乱」。
 *
 * 现在四个层级各走各的：组标题（卡外，灰小字）→ **小标题（卡内，近黑、中等字重、
 * 比行标签小一号）** → 行标签（正文字号、近黑）→ 说明（灰小字）。小标题靠「字号小 +
 * 字重中 + 颜色近黑 + 上下留白」跟行标签区分，靠「颜色深」跟说明文字区分。
 */
@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        // 0.85 而不是纯 onSurface：比行标签轻一点，不会抢走「真正要点的东西」的分量
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 2.dp)
    )
}

/** 说明文字：统一左边距，不再到处手写 padding。 */
@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // 底边距 9 → 6：说明文字和它下面那条分隔线本来就不该隔太远
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 6.dp)
    )
}

/**
 * 折叠说明（v2.7.6）：常显一行摘要，点右侧小箭头展开完整解释。
 *
 * **为什么设置页不能照搬阅源页那种「整块说明收进折叠」**：
 * 阅源页顶部那三行是「怎么用这个 App」的通用引导，看过一次就永远不需要了；
 * 而设置里每条说明都是**这一项配置到底会干什么** —— 是用户当下正要读的东西。
 * 整块收掉等于把帮助藏起来，得不偿失。
 *
 * 所以这里用折中方案：摘要在外面（一眼看懂这条设置是干嘛的），
 * 补充细节（边界情况、连带影响、历史沿革）挪进展开区。
 * 原本 2~4 行的灰字一律压到 1 行，整页读下来不再是「说明书」。
 *
 * ⚠️ 状态用 `rememberSaveable` 而不是 `remember`：这一页是 `Column + verticalScroll`
 * 而不是 `LazyColumn`，节点不会被回收，但**转屏 / 分屏会重建**，
 * `remember` 会让用户刚展开的说明又自己合上。
 */
@Composable
private fun CaptionMore(summary: String, detail: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 6.dp)) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { open = !open }
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                summary,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "收起说明" else "展开说明",
                // v2.11：箭头从主色改成次要文字色。
                // 主色 + 贴在行尾，看上去和「点这一条能跳转」的导航行是同一个东西 ——
                // 而它其实只是「这段灰字还能展开」。降成灰的、尺寸也压小一点，
                // 它才回到「注脚的一部分」这个位置。
                tint = cs.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.size(13.dp)
            )
        }
        if (open) {
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

/**
 * 卡片内的分隔线。
 *
 * v2.11：**全页只有一种分隔线** —— 左侧让出 18dp、和行文字对齐，右侧一直画到卡片边缘。
 *
 * 以前这里有个 `inset` 参数，第一张卡的「字号 / 行距 / 字体」用内缩版、
 * 其余全用通栏版，于是同一张卡里分隔线一会儿从左边界开始、一会儿从卡片边缘开始，
 * 看着像两套模板拼起来的。现在把参数删掉，只保留内缩那一种：
 * 通栏版会顶到卡片的圆角上，视觉上像把卡片切断了。
 */
@Composable
private fun Div() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 18.dp)
    )
}

/**
 * 一行「标签 + 行尾控件」。
 *
 * v2.11 修：**行尾太长会把标签压到 0 宽，两块文字直接叠在一起。**
 *
 * 起因是「备份与恢复」那一行 —— 行尾要显示本机统计（`12 个阅源 · 0 篇收藏 ·
 * 0 篇稍后读 · 0 条笔记`），二十几个字。原来的写法是
 * `Row(SpaceBetween) { Text(label); trailing() }`：两段都是「要多少给多少」，
 * 行尾先吃饱，标签就只剩 0 宽 —— 文字不会消失，它会从 0 宽的格子里**画出来**，
 * 于是「备份与恢复」四个字直接压在统计数字上面。这个 bug 平时看不见，
 * 因为默认文案「订阅 / 收藏 / 笔记」很短，只有数据一多才露出来。
 *
 * ⚠️ 修法必须是「**标签自然宽度、行尾吃剩余空间**」，不能反过来给两边各一半：
 * 试过 `weight(1f)` 对半分，结果「读完的文章自动移出稍后读」这种十字标签
 * 半行放不下，直接折成两行 —— 比原来的叠字还难看。
 *
 * Compose 的 Row 本来就先量「不带 weight 的」子项、再按剩余空间分配给带 weight 的，
 * 所以这里只给行尾那个 Box 加 weight：
 * · 标签按自己的内容取宽度，永远不折行（标签都很短）；
 * · 行尾最多拿到「整行减去标签」的宽度，超了就换行/省略，但绝不会覆盖标签。
 */
@Composable
private fun SettingsRow(label: String, trailing: @Composable () -> Unit, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            // v2.2 第二轮：10dp → 8dp（行高约 32dp）
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f, fill = false),
            contentAlignment = Alignment.CenterEnd
        ) { trailing() }
    }
}

/** 一行「标签 + 若干可选胶囊」，设置里的排版选项都用它。 */
@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        // v2.2 第二轮：7dp → 5dp，配合胶囊自身的 6dp 内边距，一行约 31dp
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurface,
            modifier = Modifier.width(66.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEachIndexed { index, text ->
                val sel = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = MaterialTheme.shapes.extraSmall,
                    // v2.7：去掉选中/未选中的描边（用户要求「全部按钮的边框去掉，
                    // 有高亮的也不要边框」）。选中态改由**底色差**承担：
                    // 主色 16% 的底 + 主色文字，对比原来的浅灰底是很明显的。
                    color = if (sel) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerHigh
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) cs.primary else cs.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/**
 * 「推送时间」那一排时刻胶囊（v2.8 每日摘要）。
 *
 * 和 [ChoiceRow] 长得像但不复用它，差在两处：
 * ① 选项是「时:分」对，不是字符串，需要现拼文案 + 按对比较；
 * ② 用 `FlowRow` 允许换行 —— 6 个时刻在某些字号下（系统字体放大到 1.15 倍，
 *    见 MainActivity 的 `fontScale`）横着排不下，硬塞会被挤扁或者裁掉。
 *    换行之后最多多占一行，不会破版。
 *
 * ⚠️ 标签宽度沿用 [ChoiceRow] 的 66dp，这样左边的「推送时间」和上面
 * 「阅读器字体」那些标签是**同一条竖线**，设置页看起来才是一整块。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimePresetRow(
    label: String,
    presets: List<Pair<Int, Int>>,
    hour: Int,
    minute: Int,
    onSelect: (Int, Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurface,
            modifier = Modifier.width(66.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            presets.forEach { (h, m) ->
                val sel = h == hour && m == minute
                Surface(
                    onClick = { onSelect(h, m) },
                    shape = MaterialTheme.shapes.extraSmall,
                    // 和 ChoiceRow 完全同一套选中态：主色 16% 底 + 主色文字，无描边
                    color = if (sel) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerHigh
                ) {
                    Text(
                        DailyDigest.format(h, m),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) cs.primary else cs.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/** 行尾的「当前值 ▾」样式，三处下拉菜单共用。 */
@Composable
private fun ValueTrailing(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // v2.11：值可能很长（「打开原文的浏览器」存的是包名，二十几个字符），
            // 不设上限就会把左边的标签挤成两行、或者顶出卡片。截断成省略号，
            // 完整值反正进菜单就能看到。
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 一行「标签 + 当前值 ▾」，点开右侧弹出下拉菜单（v2.2 新增）。
 *
 * ⚠️ **为什么要有这个组件**：以前每处都是
 * ```
 * Box { SettingsRow(...); DropdownMenu(...) }
 * ```
 * `DropdownMenu` 会**锚在它的父 Box 的位置**上 —— 而那个 Box 是整行宽的，
 * 于是菜单永远从**屏幕左边**弹出来，跟右侧那个「当前值 ▾」完全不在一列，
 * 看着像「下拉选项跑到左边去了」。用户反馈的就是这个。
 *
 * 正确做法：把菜单锚在**行尾那个值**上 —— 让 `DropdownMenu` 待在
 * `Row(Modifier.align(Alignment.CenterEnd))` 里，它就会贴着右侧展开。
 * 顺便统一了菜单项文案、勾选图标和宽度，全页的菜单长得一模一样。
 */
@Composable
private fun DropdownRow(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onPick: (String) -> Unit,
    /** 菜单最大宽度：太长的选项（比如浏览器包名）会换行，别撑破屏幕。 */
    menuWidth: Dp = 240.dp
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(true) }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 标签走自然宽度（不折行），权重只加在右边的值上 —— 理由见 [SettingsRow]：
        // 两边各一半那种做法会让「打开原文的浏览器」这种七字标签被挤成两行。
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurface,
            maxLines = 1
        )
        // 菜单挂在这个 Box 上（它只占右侧值那么宽）→ 从右边弹出来
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .weight(1f, fill = false)
                .padding(start = 10.dp)
        ) {
            ValueTrailing(value)
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.widthIn(max = menuWidth)
            ) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = { onPick(key); onExpandedChange(false) },
                        leadingIcon = if (key == selectedKey) ({
                            Icon(Icons.Filled.Check, contentDescription = null, tint = cs.primary)
                        }) else null
                    )
                }
            }
        }
    }
}

/**
 * 下拉行（带附加菜单项）—— 给「默认浏览器」这种需要在菜单末尾插一条特殊项的场合用。
 *
 * [extraItems] 排在正常选项之后，用一条分隔线隔开；点击后同样会收起菜单。
 */
@Composable
private fun DropdownRowWithExtra(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onPick: (String) -> Unit,
    extraItems: List<Pair<String, String>>,
    onPickExtra: (String) -> Unit,
    menuWidth: Dp = 260.dp
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(true) }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 同 [DropdownRow]：标签自然宽度、值吃剩余空间。
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurface,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .weight(1f, fill = false)
                .padding(start = 10.dp)
        ) {
            ValueTrailing(value)
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.widthIn(max = menuWidth)
            ) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = { onPick(key); onExpandedChange(false) },
                        leadingIcon = if (key == selectedKey) ({
                            Icon(Icons.Filled.Check, contentDescription = null, tint = cs.primary)
                        }) else null
                    )
                }
                extraItems.forEach { (key, text) ->
                    HorizontalDivider(color = cs.outlineVariant)
                    DropdownMenuItem(
                        text = { Text(text, color = cs.error) },
                        onClick = { onPickExtra(key); onExpandedChange(false) }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 配色方案（v2.3）

/**
 * 配色方案选择器。
 *
 * 交互设计上的两个考虑：
 *
 * 1. **点一下整个 App 立刻换色** —— 不用「预览」也不用「应用」按钮。
 *    因为配色是 `MainActivity` 直接订阅的 DataStore 值，写进去就生效。
 *    直观、零学习成本，比让用户在设置页里想象效果强得多。
 *
 * 2. **拖滑块时只改本地状态，松手才落盘**。
 *    色相滑块每帧都在变，如果每帧都 `dataStore.edit{}` 就是每帧重写一次配置文件 ——
 *    卡顿还是小事，闪存写入寿命才是真的浪费。所以拖动中只更新本地预览，
 *    `onFinish` 时才提交一次。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PalettePicker(
    selected: ThemePalette,
    customHue: Int,
    customSat: Int,
    onPick: (ThemePalette) -> Unit,
    onHue: (Int) -> Unit,
    onSat: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // 用当前生效的底色判断深浅，而不是 isSystemInDarkTheme()：
    // 用户手动选了「深色」而系统是浅色时，后者会给出错误的预览色。
    val dark = cs.background.luminance() < 0.5f

    // 拖动中的本地值。松手前不落盘（见上面的说明）。
    var hueLocal by remember { mutableFloatStateOf(customHue.toFloat()) }
    var satLocal by remember { mutableFloatStateOf(customSat.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    // 外部值变化（恢复备份 / 换档位）时同步回本地；正在拖动时不要抢用户的输入
    LaunchedEffect(customHue, dragging) { if (!dragging) hueLocal = customHue.toFloat() }
    LaunchedEffect(customSat, dragging) { if (!dragging) satLocal = customSat.toFloat() }

    val hueInt = hueLocal.roundToInt().coerceIn(0, 360)
    val satInt = satLocal.roundToInt().coerceIn(0, 100)
    val customPreview = remember(hueInt, satInt, dark) {
        Color(seedFor(ThemePalette.Custom, hueInt, satInt))
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 5
    ) {
        ThemePalette.presets.forEach { p ->
            PaletteSwatch(
                color = Color(p.seed),
                label = p.label,
                selected = selected == p,
                onClick = { onPick(p) }
            )
        }
        PaletteSwatch(
            color = customPreview,
            label = "自定义",
            selected = selected == ThemePalette.Custom,
            onClick = { onPick(ThemePalette.Custom) }
        )
    }

    if (selected == ThemePalette.Custom) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("色相", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.width(48.dp))
                HueSlider(
                    hue = hueLocal,
                    modifier = Modifier.weight(1f),
                    onHue = { hueLocal = it; dragging = true },
                    onFinish = { dragging = false; onHue(hueLocal.roundToInt().coerceIn(0, 360)) }
                )
                Text("$hueInt°", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("鲜艳度", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.width(48.dp))
                Slider(
                    value = satLocal,
                    onValueChange = { satLocal = it; dragging = true },
                    onValueChangeFinished = { dragging = false; onSat(satLocal.roundToInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f)
                )
                Text("$satInt", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
        }
    }
}

/** 配色方案里的一个圆形色块。选中时加一圈主色描边 + 对勾。 */
@Composable
private fun PaletteSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(60.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                // 对勾用「和色块对比度更高」的那个颜色：
                // 琥珀这类浅底如果固定用白勾，几乎看不见。
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.55f) Color(0xFF1A1A1A) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) cs.primary else cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 彩虹色相滑块。
 *
 * 没用 Material3 的 `Slider`：它只支持纯色轨道，而色相条必须是彩虹渐变，
 * 否则用户根本不知道拖到哪儿是什么颜色。轨道用 `drawBehind` 手绘，
 * 拖拽用 `pointerInput` + `awaitEachGesture`（按下即响应，然后跟随拖动）。
 */
@Composable
private fun HueSlider(
    hue: Float,
    onHue: (Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbow = remember { List(37) { i -> Color.hsv((i * 10f) % 360f, 0.85f, 0.95f) } }
    val fraction = (hue.coerceIn(0f, 360f) / 360f)
    val thumb = Color.hsv(hue.coerceIn(0f, 360f) % 360f, 0.85f, 0.92f)

    Box(
        modifier = modifier
            .height(34.dp)
            .drawBehind {
                val trackH = 10.dp.toPx()
                val r = 11.dp.toPx()
                drawRoundRect(
                    brush = Brush.horizontalGradient(rainbow),
                    topLeft = Offset(0f, (size.height - trackH) / 2f),
                    size = Size(size.width, trackH),
                    cornerRadius = CornerRadius(trackH / 2f)
                )
                // 滑块位置：圆心在 (r, width-r) 之间移动，让圆整体不越界
                val cx = r + fraction * (size.width - 2 * r)
                drawCircle(Color.White, radius = r, center = Offset(cx, size.height / 2f))
                drawCircle(thumb, radius = r - 3.5f.dp.toPx(), center = Offset(cx, size.height / 2f))
            }
            .pointerInput(Unit) {
                val r = 11.dp.toPx()
                val usable = (size.width - 2 * r).coerceAtLeast(1f)
                fun emit(x: Float) {
                    onHue(((x - r) / usable).coerceIn(0f, 1f) * 360f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    emit(down.position.x)
                    down.consume()
                    drag(down.id) { change ->
                        emit(change.position.x)
                        change.consume()
                    }
                    onFinish()
                }
            }
    )
}

/**
 * 设置页（v2.0 重排）。
 *
 * 原来十个分组各管一摊，找一样东西得先猜它在哪个组里。现在按「用得最多 → 最少」排成八组：
 * 阅读与朗读 → 外观 → 个性 → 阅源与刷新 → 离线阅读 → 提醒与屏蔽 → 数据 → 帮助与关于；
 * 「首页显示」并进了「外观」（它本来就只是外观的一部分），「通知」并进了「提醒与屏蔽」。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(
    app: YuewenApplication,
    onOpenAddSource: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenSources: () -> Unit = {},
    /**
     * v2.7.3：底栏高度（含导航栏 inset）。
     *
     * 底栏改成浮动胶囊之后，四个页面**统一穿透** —— 内容一路画到屏幕最底边、
     * 从胶囊下面穿过去。所以这一页不能再靠父层 `padding(bottom)` 让位，
     * 底部留白由滚动内容末尾那个 `Spacer` 承担。
     */
    bottomInset: Dp = 0.dp
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.provide(app))
    val theme by vm.theme.collectAsStateWithLifecycle()
    val font by vm.font.collectAsStateWithLifecycle()
    val notify by vm.notify.collectAsStateWithLifecycle()
    val autoread by vm.autoread.collectAsStateWithLifecycle()
    val queueAutoDequeue by vm.queueAutoDequeue.collectAsStateWithLifecycle()
    // v2.8：每日摘要
    val digestEnabled by vm.digestEnabled.collectAsStateWithLifecycle()
    val digestHour by vm.digestHour.collectAsStateWithLifecycle()
    val digestMinute by vm.digestMinute.collectAsStateWithLifecycle()
    val refresh by vm.refreshMinutes.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val blockedSources by vm.blockedSources.collectAsStateWithLifecycle()
    val blockedKeywords by vm.blockedKeywords.collectAsStateWithLifecycle()
    val listMode by vm.listMode.collectAsStateWithLifecycle()
    val readerTheme by vm.readerTheme.collectAsStateWithLifecycle()
    val readerFont by vm.readerFont.collectAsStateWithLifecycle()
    val readerSpacing by vm.readerSpacing.collectAsStateWithLifecycle()
    val readerSize by vm.readerSize.collectAsStateWithLifecycle()
    val showBarInReader by vm.showBarInReader.collectAsStateWithLifecycle()
    val refreshOnLaunch by vm.refreshOnLaunch.collectAsStateWithLifecycle()
    val unreadBadge by vm.unreadBadge.collectAsStateWithLifecycle()
    val barIcons by vm.barIcons.collectAsStateWithLifecycle()
    val homeFilterIcons by vm.homeFilterIcons.collectAsStateWithLifecycle()
    // v1.9：离线阅读 + 朗读语速
    val preloadAuto by vm.preloadAuto.collectAsStateWithLifecycle()
    val preloadWifiOnly by vm.preloadWifiOnly.collectAsStateWithLifecycle()
    val ttsRate by vm.ttsRate.collectAsStateWithLifecycle()
    val preload by vm.preloadProgress.collectAsStateWithLifecycle()
    val cache by vm.cacheInfo.collectAsStateWithLifecycle()
    // v2.0：个性化
    val appTitle by vm.appTitle.collectAsStateWithLifecycle()
    val iconIdx by vm.iconNameIndex.collectAsStateWithLifecycle()
    val homeShowLayout by vm.homeShowLayout.collectAsStateWithLifecycle()
    val homeShowRefresh by vm.homeShowRefresh.collectAsStateWithLifecycle()
    val homeShowSubtitle by vm.homeShowSubtitle.collectAsStateWithLifecycle()
    // v2.7：液态玻璃开关已随整套设计语言一起删除（见 SettingsRepository 注释）
    val ttsNotify by vm.ttsNotify.collectAsStateWithLifecycle()
    val guideSeen by vm.guideSeen.collectAsStateWithLifecycle()
    // v2.0.2 / v2.5：首页那两行筛选项各自的开关 + 打开时默认停在哪儿
    val showCategoryRow by vm.homeShowCategoryRow.collectAsStateWithLifecycle()
    val showSourceRow by vm.homeShowSourceRow.collectAsStateWithLifecycle()
    val homeDefaultCategory by vm.homeDefaultCategory.collectAsStateWithLifecycle()
    val homeDefaultSource by vm.homeDefaultSource.collectAsStateWithLifecycle()
    // v2.2：首页排序 + 默认浏览器
    val homeSort by vm.homeSort.collectAsStateWithLifecycle()
    val browserPkg by vm.browserPkg.collectAsStateWithLifecycle()
    // v2.3：配色方案
    val themePalette by vm.themePalette.collectAsStateWithLifecycle()
    val customHue by vm.customHue.collectAsStateWithLifecycle()
    val customSat by vm.customSat.collectAsStateWithLifecycle()
    // v2.4：首页关键词
    val homeKeywords by vm.homeKeywords.collectAsStateWithLifecycle()
    val homeShowKeywords by vm.homeShowKeywords.collectAsStateWithLifecycle()

    // v2.9：云备份
    val backupKeep by vm.backupKeep.collectAsStateWithLifecycle()
    val gistToken by vm.backupGistToken.collectAsStateWithLifecycle()
    val gistId by vm.backupGistId.collectAsStateWithLifecycle()
    val davUrl by vm.backupDavUrl.collectAsStateWithLifecycle()
    val davUser by vm.backupDavUser.collectAsStateWithLifecycle()
    val davPass by vm.backupDavPass.collectAsStateWithLifecycle()

    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /**
     * v2.14：检查更新。
     *
     * 单独一个 ViewModel，不塞进 SettingsViewModel —— 它管的是「下载 / 调起系统安装器」
     * 这条完全独立的链路（网络 + 文件 + 跨 App 跳转），和「读写设置」没有交集。
     * 混在一起会让 SettingsViewModel 的职责变成「设置页上碰巧用到的所有东西」。
     */
    val updateVm: UpdateViewModel = viewModel(factory = UpdateViewModel.provide(app))
    val updateVisible by updateVm.visible.collectAsStateWithLifecycle()
    val updateState by updateVm.state.collectAsStateWithLifecycle()

    // 「添加」按钮敲完要收键盘，否则输入法会一直挡着下面那排关键词
    val dismissIme = rememberImeDismiss()

    var showBlock by remember { mutableStateOf(false) }
    var kwInput by remember { mutableStateOf("") }
    var showKeywords by remember { mutableStateOf(false) }
    var homeKwInput by remember { mutableStateOf("") }
    var fontExpanded by remember { mutableStateOf(false) }
    var refreshExpanded by remember { mutableStateOf(false) }
    var titleEditing by remember { mutableStateOf(false) }
    var iconExpanded by remember { mutableStateOf(false) }
    var defaultExpanded by remember { mutableStateOf(false) }
    var browserExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("") }
    var showBackup by remember { mutableStateOf(false) }
    var showMigrate by remember { mutableStateOf(false) }

    // v2.2：本机能打开网页的应用列表。只在进设置页时查一次 ——
    // 用户装/卸浏览器后需要重进设置页才会刷新，但「进设置页查一次系统」已经是够廉价的代价。
    val browsers = remember { BrowserLauncher.browsers(context) }

    // 备份面板要显示「本机现有多少东西」，进页面时读一次。
    LaunchedEffect(Unit) { summary = vm.backupSummary() }

    // v1.9：离线阅读那块要知道当前网络是不是 Wi-Fi（用于提示「已跳过：不在 Wi-Fi」）。
    // 只在进页面时问一次：这是一个廉价的系统查询，没必要每帧都问。
    val onWifi = remember { vm.isOnWifi() }

    // 缓存用量：进页面量一次；预加载跑完（running 变 false）再量一次，
    // 这样「已缓存 N 篇」的数字在抓完之后立刻跟上。
    LaunchedEffect(preload.running) {
        if (!preload.running) vm.loadCacheInfo()
    }

    val fontLabel = when (font) { "small" -> "小"; "large" -> "大"; else -> "标准" }
    val refreshLabel = when (refresh) {
        0 -> "关闭"; 15 -> "每 15 分钟"; 30 -> "每 30 分钟"; 60 -> "每 1 小时"; 120 -> "每 2 小时"
        else -> "$refresh 分钟"
    }

    // ---- v2.0.2 / v2.5：首页筛选相关的派生值 ----
    // 分类直接从已订阅的源里抽（不再单独存一份，源删了分类自然消失）
    val categories = sources.map { it.category }.distinct().filter { it.isNotBlank() }
    val defaultFilterLabel = when {
        // 选了具体阅源就显示它（阅源是更「具体」的那一级）
        showSourceRow && homeDefaultSource.isNotBlank() -> homeDefaultSource
        showCategoryRow && homeDefaultCategory != "推荐" -> homeDefaultCategory
        showSourceRow -> "全部阅源"
        else -> "全部"
    }

    // v2.2：当前默认浏览器的显示名（存的是包名，要翻译成人话）
    val browserLabel = BrowserLauncher.displayName(context, browserPkg, browsers)

    // ---------------- OPML 导入 / 导出 ----------------
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = runCatching { vm.writeOpml(uri) }.getOrDefault(0)
            Toast.makeText(
                context,
                if (n > 0) "已导出 $n 个订阅源（可用其他阅读器导入）" else "没有可导出的订阅源",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (added, skipped) = runCatching { vm.readOpml(uri) }.getOrDefault(0 to 0)
            val msg = when {
                added == 0 && skipped == 0 -> "没读到订阅源，请确认选的是 .opml / .xml 文件"
                added == 0 -> "没有新增（$skipped 个都已经在列表里了）"
                else -> "已导入 $added 个源" + if (skipped > 0) "，跳过 $skipped 个重复的" else ""
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- v2.0：完整备份 / 恢复（JSON） ----------------
    // 和上面 OPML 是两个不同的东西：OPML 只搬「订阅源」，备份还要带上收藏、笔记和个性化设置。
    // 所以各自有独立的文件选择器，不能混用（混用会出现「导出成 .json 却按 OPML 解析」）。
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (s, b, n) = runCatching { vm.writeBackup(uri) }.getOrDefault(Triple(0, 0, 0))
            Toast.makeText(
                context,
                if (s + b + n > 0) "已导出：$s 个阅源、$b 篇收藏、$n 条笔记"
                else "本机还没有可备份的内容",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = runCatching { vm.readBackup(uri) }.getOrNull()
            val msg = when {
                r == null -> "这不是阅闻的备份文件"
                r.first + r.second + r.third == 0 -> "备份里没有可恢复的内容"
                else -> "已恢复：新增 ${r.first} 个阅源、${r.second} 篇收藏、${r.third} 条笔记"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            summary = vm.backupSummary()
        }
    }

    // ---------------- v2.9：云备份（三条通道） ----------------
    var showCloud by remember { mutableStateOf(false) }
    /** 正在忙的那条通道标签；null = 空闲。用它禁用按钮，避免连点发出两份备份。 */
    var cloudBusy by remember { mutableStateOf<String?>(null) }
    var cloudStatus by remember { mutableStateOf(emptyMap<BackupTarget, String>()) }
    var showGistConfig by remember { mutableStateOf(false) }
    var showDavConfig by remember { mutableStateOf(false) }

    /**
     * 本地文件夹走系统的目录选择器。
     *
     * ⚠️ 选完之后必须 `takePersistableUriPermission`（在 vm.setLocalBackupTree 里做）——
     * 不长期化的话，App 一重启授权就没了，用户下次点「备份」只会收到一句
     * 「访问不了这个文件夹」，而他会以为是自己把文件夹删了。
     */
    val localTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.setLocalBackupTree(uri)
            cloudStatus = vm.cloudStatuses()
        }
    }

    /** 备份 / 恢复共用一个入口：都要置忙、都要弹结果、都要把状态重新读一遍。 */
    val runCloud: (BackupTarget, Boolean) -> Unit = { target, isRestore ->
        if (cloudBusy == null) {
            cloudBusy = target.label
            scope.launch {
                val msg = try {
                    if (isRestore) vm.cloudRestore(target) else vm.cloudBackup(target)
                } catch (e: Exception) {
                    // BackupException 带的是一句能直接给用户看的中文；
                    // 其它异常理论上不该出现，但也必须兜成一句人话，
                    // 不能让用户对着一句类名（或者空白）猜发生了什么。
                    (e as? BackupException)?.message
                        ?: ((if (isRestore) "恢复失败：" else "备份失败：") + (e.message ?: "未知错误"))
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                cloudStatus = vm.cloudStatuses()
                cloudBusy = null
            }
        }
    }

    // 展开云备份面板时读一次三条通道的状态（会走网络，所以只在打开的那一刻读）。
    LaunchedEffect(showCloud) {
        if (showCloud) cloudStatus = vm.cloudStatuses()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(cs.background)
    ) {
        item {
            Row(
                // v2.11：18 → 12，跟卡片左边缘、跟每个组标题同一条竖线。
                // 页面上原本「设置」「阅读与朗读」「字号」三行字分别齐 18 / 22 / 30，
                // 三条左边界 = 没有网格。现在只剩 12（卡片、标题）和 30（卡内内容）两列。
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("设置", style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
            }
        }

        item {
            // ==================== 1. 阅读与朗读 ====================
            GroupTitle("阅读与朗读")
            Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    ChoiceRow(
                        label = "字号",
                        options = ReaderSizeLabels,
                        selectedIndex = readerSize.coerceIn(0, ReaderSizeLabels.lastIndex),
                        onSelect = { vm.setReaderSize(it) }
                    )
                    Div()
                    ChoiceRow(
                        label = "行距",
                        options = ReaderSpacing.entries.map { it.label },
                        selectedIndex = ReaderSpacing.of(readerSpacing).ordinal,
                        onSelect = { vm.setReaderSpacing(ReaderSpacing.entries[it].key) }
                    )
                    Div()
                    ChoiceRow(
                        label = "字体",
                        options = ReaderFont.entries.map { it.label },
                        selectedIndex = ReaderFont.of(readerFont).ordinal,
                        onSelect = { vm.setReaderFont(ReaderFont.entries[it].key) }
                    )
                    Div()
                    ChoiceRow(
                        label = "底色",
                        options = ReaderTheme.entries.map { it.label },
                        selectedIndex = ReaderTheme.of(readerTheme).ordinal,
                        onSelect = { vm.setReaderTheme(ReaderTheme.entries[it].key) }
                    )
                    CaptionMore(
                        summary = "详情页右上角的 Aa 按钮也能改这几项。",
                        detail = "「米黄纸感」和「墨夜」就是原来的正念阅读模式，只是换了名字。"
                    )
                    Div()
                    ChoiceRow(
                        label = "朗读语速",
                        options = TtsRateLabels,
                        selectedIndex = ttsRate.coerceIn(0, TtsRateLabels.lastIndex),
                        onSelect = { vm.setTtsRate(it) }
                    )
                    Caption("朗读过程中改的话，从下一句开始生效。")
                    Div()
                    SettingsRow(
                        "朗读时显示通知栏控制",
                        trailing = { Switch(checked = ttsNotify, onCheckedChange = { vm.setTtsNotify(it) }) }
                    )
                    CaptionMore(
                        summary = "朗读时挂一条通知，锁屏也能暂停 / 继续 / 停止。",
                        detail = "详情页顶部还会浮出一条「回到文章」，点一下直接跳回正文。"
                    )
                    // v2.11：「阅读文章时显示底部导航栏」搬去了「底部导航栏」那一组。
                    // 它说的是**底栏**的事，和「朗读」没有半点关系，原先挤在这里纯粹是历史原因；
                    // 而底栏的三项（图标 / 未读数字 / 阅读时是否显示）本来就该放在一起看。
                    Div()
                    // v2.2：打开原文用哪个浏览器。
                    // 「跟随系统」永远排第一 —— 它是默认值，也是出问题时的退路。
                    // 存的浏览器被卸载了 → 菜单末尾补一条「已卸载」让用户知道为什么名字对不上。
                    DropdownRowWithExtra(
                        label = "打开原文的浏览器",
                        value = browserLabel,
                        expanded = browserExpanded,
                        onExpandedChange = { browserExpanded = it },
                        options = listOf(BrowserLauncher.SYSTEM to "跟随系统") +
                                browsers.map { it.pkg to it.label },
                        selectedKey = browserPkg,
                        onPick = { vm.setBrowserPkg(it) },
                        extraItems = if (browserPkg.isNotBlank() && browsers.none { it.pkg == browserPkg })
                            listOf(browserPkg to "$browserPkg（已卸载，点击恢复默认）")
                        else emptyList(),
                        onPickExtra = { vm.setBrowserPkg(BrowserLauncher.SYSTEM) }
                    )
                    CaptionMore(
                        summary = "选「跟随系统」就交给安卓自己决定用哪个应用打开。",
                        detail = "选了具体浏览器之后，中途把它卸载了会自动退回系统默认，不会出现点「原文」打不开的情况。"
                    )
                }
            }
        }

        item {
            // ==================== 2. 外观 ====================
            GroupTitle("外观")
            Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("外观主题", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("light" to "浅色", "system" to "自动", "dark" to "深色").forEach { (v, l) ->
                                val sel = theme == v
                                Surface(
                                    onClick = { vm.setTheme(v) },
                                    color = if (sel) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerHigh,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        l,
                                        color = if (sel) cs.primary else cs.onSurface,
                                        style = MaterialTheme.typography.labelMedium,
                                        // v2.11：12/7 → 10/5，和 [ChoiceRow] 的胶囊完全同一套尺寸。
                                        // 原来这排（浅色/自动/深色）比下面「字号」「行距」的胶囊
                                        // 各高一头、宽一头，同一张卡里出现两种大小的胶囊，很显乱。
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                    Div()
                    // ---- v2.3：配色方案 ----
                    // 点一下立刻换色，不需要「应用」按钮（MainActivity 直接订阅这个值）。
                    SubTitle("配色方案")
                    PalettePicker(
                        selected = themePalette,
                        customHue = customHue,
                        customSat = customSat,
                        onPick = { vm.setThemePalette(it) },
                        onHue = { vm.setCustomHue(it) },
                        onSat = { vm.setCustomSat(it) }
                    )
                    CaptionMore(
                        summary = "点一下就换，整个界面立刻生效，不需要再点「应用」。",
                        detail = "上面这排是内置配色；选「自定义」可以自己拖色相和鲜艳度，拖到哪就是哪。"
                    )
                    Div()
                    DropdownRow(
                        label = "界面字体大小",
                        value = fontLabel,
                        expanded = fontExpanded,
                        onExpandedChange = { fontExpanded = it },
                        options = listOf("standard" to "标准", "small" to "小", "large" to "大"),
                        selectedKey = font,
                        onPick = { vm.setFont(it) }
                    )
                    Div()
                    ChoiceRow(
                        label = "列表布局",
                        options = ArticleListMode.entries.map { it.label },
                        selectedIndex = listMode.ordinal,
                        onSelect = { vm.setListMode(ArticleListMode.entries[it]) }
                    )
                    CaptionMore(
                        summary = "紧凑省地方、卡片有缩略图、杂志图最大。",
                        detail = "首页右上角的布局按钮也能随时切换（可以在下面把那个按钮关掉）。"
                    )

                    // ---- v2.11：原来的「个性」组并进这里 ----
                    // 「个性」单独一张卡只剩「应用内名称 / 桌面图标名称」两行，为两行开一个
                    // 组标题、单独占一段留白，反而是全页最碎的地方。而这两项改的本来就是
                    // 「长什么样」，和外观是同一类东西 —— 合进来之后少一个组标题、少一次视线跳。
                    Div()
                    SubTitle("应用名称与图标")
                    // 应用内名称：首页大标题、「关于」页都用它。留空回落到默认「阅闻」。
                    SettingsRow(
                        "应用内名称",
                        trailing = { ValueTrailing(titleOrDefault(appTitle)) },
                        onClick = { titleEditing = true }
                    )
                    Caption("首页大标题和「关于」页都用它；留空用默认的「阅闻」。")
                    Div()
                    // 桌面图标名：系统不允许运行时改 android:label，
                    // 只能预置若干个 activity-alias，在这里挑一个（本质是切换 alias）。
                    DropdownRow(
                        label = "桌面图标名称",
                        value = iconNameOf(iconIdx),
                        expanded = iconExpanded,
                        onExpandedChange = { iconExpanded = it },
                        options = IconNames.mapIndexed { i, name -> "$i" to name },
                        selectedKey = "$iconIdx",
                        onPick = { key ->
                            val i = key.toIntOrNull() ?: return@DropdownRow
                            val ok = vm.setIconNameIndex(i)
                            Toast.makeText(
                                context,
                                if (ok) "已切换，桌面图标名可能要一两秒才刷新"
                                else "切换失败：部分系统不允许改桌面图标名",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    CaptionMore(
                        summary = "桌面图标底下那行字，系统不允许随便改。",
                        detail = "只能从这几个里挑。想改 App 里的标题用上面那个「应用内名称」，那个可以随便写。"
                    )
                }
            }
        }

        item {
            // ==================== 3. 首页 ====================
            //
            // v2.11：从「外观」里拆出来的。原来「外观」一张卡装着主题、配色、字体、列表布局、
            // 首页顶栏、首页筛选、关键词、默认筛选、排序、底栏未读数字**十项**,跨了五个小节，
            // 想改「首页筛选」得先滚过一整屏的配色和字体。拆开之后每个组只回答一个问题：
            // 外观 = 长什么样；首页 = 首页上有什么；底部导航栏 = 底栏怎么显示。
            GroupTitle("首页")
            Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    // 首页顶栏：三个按钮加副标题，都能单独关掉
                    SubTitle("顶栏")
                    SettingsRow(
                        "显示「布局」按钮",
                        trailing = { Switch(checked = homeShowLayout, onCheckedChange = { vm.setHomeShowLayout(it) }) }
                    )
                    Div()
                    SettingsRow(
                        "显示「刷新」按钮",
                        trailing = { Switch(checked = homeShowRefresh, onCheckedChange = { vm.setHomeShowRefresh(it) }) }
                    )
                    Div()
                    SettingsRow(
                        "显示副标题",
                        trailing = { Switch(checked = homeShowSubtitle, onCheckedChange = { vm.setHomeShowSubtitle(it) }) }
                    )
                    Caption("三个按钮都能单独关；关掉刷新按钮后，下拉仍然可以刷新。")
                    Div()
                    SettingsRow(
                        "首页顶部筛选图标",
                        trailing = { Switch(checked = homeFilterIcons, onCheckedChange = { vm.setHomeFilterIcons(it) }) }
                    )
                    Caption("两个小图标分别是「仅看未读 / 显示全部」和「全部标为已读」。")

                    // ---- v2.0.2 / v2.5：首页那两行筛选胶囊各自的开关 ----
                    // v2.5：原来是一把三档开关（分类 / 阅源 / 都显示），但它描述的其实就是
                    // 「两行各自要不要显示」，读起来绕。拆成两个独立开关，和上面「显示副标题」
                    // 是同一套心智模型，四种组合也都能表达。
                    Div()
                    SubTitle("筛选")
                    SettingsRow(
                        "显示「分类」行",
                        trailing = { Switch(checked = showCategoryRow, onCheckedChange = { vm.setHomeShowCategoryRow(it) }) }
                    )
                    Div()
                    SettingsRow(
                        "显示「阅源」行",
                        trailing = { Switch(checked = showSourceRow, onCheckedChange = { vm.setHomeShowSourceRow(it) }) }
                    )
                    CaptionMore(
                        summary = "「分类」选大类，「阅源」选具体来源，两个条件叠加生效。",
                        detail = "关掉某一行会同时清空那一级的筛选，不会留下看不见的条件。"
                    )

                    // ---- v2.4：首页关键词胶囊 ----
                    // 和「屏蔽关键词」放在一起容易混，所以刻意隔开：那个是「不想看什么」，
                    // 这个是「想只看什么」，一正一反，各自在自己那一组里。
                    Div()
                    SettingsRow(
                        "首页关键词",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (homeKeywords.isEmpty()) "未设置" else "${homeKeywords.size} 个",
                                    color = cs.onSurfaceVariant
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = cs.onSurfaceVariant)
                            }
                        },
                        onClick = { showKeywords = !showKeywords }
                    )
                    if (showKeywords) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = homeKwInput,
                                onValueChange = { homeKwInput = it },
                                label = { Text("添加关键词") },
                                placeholder = { Text("如：手机") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                if (homeKwInput.isNotBlank()) {
                                    vm.addHomeKeyword(homeKwInput.trim())
                                    homeKwInput = ""
                                    // 加完就收键盘：下面那排词才是用户接下来要看的东西
                                    dismissIme()
                                }
                            }) { Text("添加") }
                        }
                        if (homeKeywords.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                homeKeywords.forEach { kw ->
                                    Surface(
                                        color = cs.primaryContainer,
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.clickable { vm.removeHomeKeyword(kw) }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                            Text(kw, color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelMedium)
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Filled.Close, contentDescription = "移除", tint = cs.onPrimaryContainer, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                            Caption("点一下某个词就能删掉它（最多 $HOME_KEYWORD_LIMIT 个）。")
                        }
                    }
                    CaptionMore(
                        summary = "填的词会变成首页顶部的胶囊，点一下只看含这个词的文章。",
                        detail = "匹配范围是标题 / 摘要 / 正文，再点一次取消。和分类、阅源是叠加关系，三个条件同时生效。"
                    )
                    Div()
                    SettingsRow(
                        "显示关键词行",
                        trailing = { Switch(checked = homeShowKeywords, onCheckedChange = { vm.setHomeShowKeywords(it) }) }
                    )
                    Caption("关掉只是不显示，填过的词会留着，随时能再打开。")
                    Div()
                    // ⚠️⚠️ v2.6.0 修：选项**不再按顶栏那两行的开关过滤**。
                    // 原写法是「分类行关着就不列分类项、阅源行关着就不列阅源项」，本意是「免得选了不生效」。
                    // 但如果两行都关掉（只用关键词筛，用户就是这么用的），整个 `options` 就是**空的** ——
                    // 点开下拉只剩右侧那个「全部」值，看着就是坏的（用户反馈「点了只有全部选项」）。
                    // 现在两级永远都列全：至少保证「全部（不限分类）」和「全部阅源」两项在，
                    // 而且订阅过的每个分类 / 每个源都能直接选。
                    // 代价是「选了不生效」的可能回来了 —— 这条改由下面那行 Caption 明确交代。
                    // 取值用带前缀的 key（"c:" / "s:"）区分两级 —— 分类名和阅源名可能重名，
                    // 光看字符串分不出用户点的是哪一级。
                    DropdownRow(
                        label = "打开时默认停在",
                        value = defaultFilterLabel,
                        expanded = defaultExpanded,
                        onExpandedChange = { defaultExpanded = it },
                        options = buildList {
                            add("c:推荐" to "全部（不限分类）")
                            categories.forEach { add("c:$it" to it) }
                            add("s:" to "全部阅源")
                            sources.filter { it.enabled }.map { it.name }.distinct().forEach { add("s:$it" to it) }
                        },
                        // 显示哪一级就以哪一级为准：阅源更「具体」，所以优先显示它
                        selectedKey = when {
                            showSourceRow && homeDefaultSource.isNotBlank() -> "s:$homeDefaultSource"
                            showCategoryRow -> "c:$homeDefaultCategory"
                            else -> "s:$homeDefaultSource"
                        },
                        onPick = { key ->
                            if (key.startsWith("c:")) vm.setHomeDefaultCategory(key.removePrefix("c:"))
                            else vm.setHomeDefaultSource(key.removePrefix("s:"))
                        }
                    )
                    CaptionMore(
                        summary = "「开机默认」，不是「记住上次」—— 中途点别的胶囊不影响它。",
                        detail = "选项来自你订阅的源和它们的分类。如果上面「显示「分类」行 / 显示「阅源」行」是关着的，" +
                            "对应那一级选了也不会生效，想用就先把它打开。"
                    )

                    // ---- v2.2：文章怎么排 ----
                    //
                    // v2.11：这里原来还有个 SubTitle("排序")，删掉了 —— 它底下只有
                    // 「排序方式」一行，而那一行的标签本身就把意思说完，小标题等于复读一遍。
                    // 判断标准：小标题底下**至少两行**才值得加，否则就是多一层噪声。
                    Div()
                    // 用下拉而不是横排胶囊：五个档位横排会挤出屏幕（「最新在前」这种四字标签尤其占地方）
                    DropdownRow(
                        label = "排序方式",
                        value = homeSort.label,
                        expanded = sortExpanded,
                        onExpandedChange = { sortExpanded = it },
                        options = HomeSortMode.entries.map { it.key to it.label },
                        selectedKey = homeSort.key,
                        onPick = { vm.setHomeSort(HomeSortMode.of(it)) }
                    )
                    // 「换一批」只在随机排序下才出现 —— 别的排序下它没有任何意义，
                    // 摆在那里只会让人以为点了会重排
                    if (homeSort == HomeSortMode.Random) {
                        Div()
                        SettingsRow(
                            "重新洗牌",
                            trailing = { Text("换一批", color = cs.primary) },
                            onClick = { vm.reshuffle() }
                        )
                    }
                    Caption(
                        when (homeSort) {
                            HomeSortMode.TimeDesc -> "默认：最新的文章排在最前面，按「今天 / 昨天 / 本周 / 更早」分组。"
                            HomeSortMode.TimeAsc -> "最早的文章排在最前面，适合补着看历史。同样会按日期分组。"
                            HomeSortMode.Random -> "每次打开顺序都一样（种子固定），点「换一批」才会重洗。这样下拉刷新不会把列表打乱。"
                            HomeSortMode.Source -> "同一个阅源的文章凑在一起，组内按时间倒序。想「先把这个源的看完」就用它。"
                            HomeSortMode.Title -> "按标题字典序排。记得标题里几个字、忘了是哪个源的，用它翻更快。"
                        }
                    )
                }
            }
        }

        item {
            // ==================== 4. 底部导航栏 ====================
            //
            // v2.11：新开的一组。这三项原来散在两处 ——「阅读文章时显示底部导航栏」在
            // 「阅读与朗读」里（它跟朗读毫无关系，纯粹是早先随手放的），「未读数字」在
            // 「外观」的最末尾（得滚过整屏配色、字体、排序才找得到）。
            // 而用户想调底栏的时候，脑子里就一个问题：「底栏长什么样」。三项并排给他看。
            GroupTitle("底部导航栏")
            Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    SettingsRow(
                        "显示图标",
                        trailing = { Switch(checked = barIcons, onCheckedChange = { vm.setBarIcons(it) }) }
                    )
                    CaptionMore(
                        summary = "默认只留文字：四个短词并排，比四个图标更好认。",
                        detail = "打开之后每个词前面加一个图标（首页 / 闻件 / 阅源 / 设置）。" +
                            "底栏高度不会因为这一项变化。"
                    )
                    Div()
                    SettingsRow(
                        "显示未读数字",
                        trailing = { Switch(checked = unreadBadge, onCheckedChange = { vm.setUnreadBadge(it) }) }
                    )
                    // v2.11：文案里原来写「底栏「首页」**图标上**显示未读条数」——
                    // 图标现在默认是关的，数字其实是跟在「首页」两个字后面，改成「后面」。
                    Caption("打开后底栏「首页」后面显示未读条数，最多 99+。")
                    Div()
                    SettingsRow(
                        "阅读文章时也显示",
                        trailing = { Switch(checked = showBarInReader, onCheckedChange = { vm.setShowBarInReader(it) }) }
                    )
                    Caption("关掉就是全屏沉浸阅读，只能靠返回键退出。")
                }
            }
        }

        item {
            // ==================== 5. 阅源与刷新 ====================
            GroupTitle("阅源与刷新")
            Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    // v2.0：订阅源的完整管理搬去了「阅源」页（底部第三个 Tab）。
                    // 设置里再挂一份完整列表，等于同一个东西两种说法，用户反而不知道哪边是真的。
                    SettingsRow(
                        "我的阅源",
                        trailing = { Text("${sources.size} 个 · 去管理", color = cs.primary) },
                        onClick = onOpenSources
                    )
                    Caption("添加、改名、改分类、单独刷新、测试全部，都在「阅源」页里。")
                    Div()
                    SettingsRow(
                        "添加阅源",
                        trailing = { Icon(Icons.Filled.Add, contentDescription = null, tint = cs.primary) },
                        onClick = onOpenAddSource
                    )
                    Div()
                    DropdownRow(
                        label = "刷新频率",
                        value = refreshLabel,
                        expanded = refreshExpanded,
                        onExpandedChange = { refreshExpanded = it },
                        options = listOf(0, 15, 30, 60, 120).map { m ->
                            "$m" to when (m) {
                                0 -> "关闭"; 15 -> "每 15 分钟"; 30 -> "每 30 分钟"
                                60 -> "每 1 小时"; 120 -> "每 2 小时"; else -> "$m 分钟"
                            }
                        },
                        selectedKey = "$refresh",
                        onPick = { vm.setRefreshMinutes(it.toIntOrNull() ?: 30) }
                    )
                    Caption("关掉之后就只在你手动下拉时刷新。")
                    Div()
                    SettingsRow(
                        "打开 App 自动刷新",
                        trailing = { Switch(checked = refreshOnLaunch, onCheckedChange = { vm.setRefreshOnLaunch(it) }) }
                    )
                    Caption("距上次刷新不到 5 分钟会自动跳过。")
                    Div()
                    SettingsRow(
                        "打开文章自动标为已读",
                        trailing = { Switch(checked = autoread, onCheckedChange = { vm.setAutoRead(it) }) }
                    )
                    // v2.8：和上面那条是**一对** —— 它认的就是「打开文章」这个动作，
                    // 上面关掉之后这条永远没机会触发，所以跟着一起藏起来，
                    // 而不是留一个永远不生效的开关让人猜。（同款做法见「离线阅读」里的 Wi-Fi 那条。）
                    if (autoread) {
                        Div()
                        SettingsRow(
                            "读完的文章自动移出稍后读",
                            trailing = { Switch(checked = queueAutoDequeue, onCheckedChange = { vm.setQueueAutoDequeue(it) }) }
                        )
                        CaptionMore(
                            summary = "打开一篇在稍后读里的文章，它会自动从队列里消失，并弹一条可「撤销」的提示。",
                            detail = "关掉就一直是「读过但还没清」的状态（队列页会显示有几篇已读），什么时候清由你自己决定。"
                        )
                    }
                }
            }
        }

        item {
            // ==================== 6. 离线阅读 ====================
            GroupTitle("离线阅读")
            Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    SettingsRow(
                        "自动预加载正文",
                        trailing = { Switch(checked = preloadAuto, onCheckedChange = { vm.setPreloadAuto(it) }) }
                    )
                    CaptionMore(
                        summary = "打开后会把每篇的完整正文抓下来，没网也能读全文。",
                        detail = "默认关闭。抓的是正文而不是 feed 里那句摘要，所以每次刷新之后还要多跑一会儿。"
                    )
                    if (preloadAuto) {
                        Div()
                        SettingsRow(
                            "仅在 Wi-Fi 下预加载",
                            trailing = { Switch(checked = preloadWifiOnly, onCheckedChange = { vm.setPreloadWifiOnly(it) }) }
                        )
                        Caption("抓正文要逐篇访问网页，比抓 RSS 重得多，不该在流量上偷跑。")
                    }
                    Div()
                    // 一次性把还没正文的文章全抓下来（手动不受上面两个开关限制：点了就是要跑）
                    SettingsRow(
                        "立即缓存全部正文",
                        trailing = {
                            Text(
                                when {
                                    preload.running -> "${preload.done} / ${preload.total}"
                                    cache.pendingPreload > 0 -> "待缓存 ${cache.pendingPreload} 篇"
                                    cache.fullTextCount > 0 -> "已全部缓存"
                                    else -> "点此开始"
                                },
                                color = if (preload.running || cache.pendingPreload > 0) cs.primary else cs.onSurfaceVariant
                            )
                        },
                        onClick = { if (preload.running) vm.cancelPreload() else vm.startPreload() }
                    )
                    if (preload.running) {
                        LinearProgressIndicator(
                            progress = {
                                if (preload.total > 0) preload.done.toFloat() / preload.total else 0f
                            },
                            color = cs.primary,
                            trackColor = cs.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .height(4.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "正在后台抓正文，可以随时中断（已抓到的会保留）…",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.cancelPreload() }) { Text("取消") }
                        }
                    }
                    Caption(
                        buildString {
                            append("已缓存 ${cache.fullTextCount} 篇正文")
                            if (cache.fullTextChars > 0) append("（约 ${cache.fullTextChars / 1000} 千字）")
                            append("。\n出门前点一次「立即缓存全部正文」，路上没信号也能读。")
                            if (preloadAuto && preloadWifiOnly && !onWifi) {
                                append("\n当前不在 Wi-Fi：自动预加载会等连上 Wi-Fi 再跑。")
                            }
                            if (!preload.running && preload.saved > 0) {
                                append("\n上一轮新缓存了 ${preload.saved} 篇。")
                            }
                        }
                    )
                }
            }
        }

        item {
            // ==================== 7. 提醒与屏蔽 ====================
            GroupTitle("提醒与屏蔽")
            Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    SettingsRow(
                        "新内容提醒",
                        trailing = { Switch(checked = notify, onCheckedChange = { vm.setNotify(it) }) }
                    )
                    Div()
                    // v2.8：每日摘要。
                    //
                    // 刻意**不**跟着上面那个「新内容提醒」走：那条是后台刷新顺手报个数，
                    // 这条是 App 主动在固定时刻找上门，两者的心理预期不一样 ——
                    // 用户完全可能想「刷新完告诉我一声，但别每天早上打扰我」，反之亦然。
                    SettingsRow(
                        "每日摘要",
                        trailing = { Switch(checked = digestEnabled, onCheckedChange = { vm.setDigestEnabled(it) }) }
                    )
                    if (digestEnabled) {
                        TimePresetRow(
                            label = "推送时间",
                            presets = DailyDigest.PRESETS,
                            hour = digestHour,
                            minute = digestMinute,
                            onSelect = { h, m -> vm.setDigestTime(h, m) }
                        )
                        CaptionMore(
                            summary = "每天到点汇总一条：今天有多少新内容、队列里还压着几篇。",
                            detail = "没有新内容、队列也空的时候不会推送 —— 与其准时响一声让你点开发现什么都没有，" +
                                    "不如安静一天。\n\n" +
                                    "推送时刻是在 App 冷启动时补排的，所以改完设置不用等；" +
                                    "但如果系统把 App 强行停掉（比如手动「强制停止」），" +
                                    "需要重新打开一次 App 才会继续推送。\n\n" +
                                    "这条通知走的是独立频道，可以在系统设置里单独关掉，" +
                                    "不影响上面的「新内容提醒」。"
                        )
                    }
                    Div()
                    SettingsRow(
                        "不想看的内容",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val n = blockedSources.size + blockedKeywords.size
                                if (n > 0) Text("$n 项", color = cs.onSurfaceVariant)
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = cs.onSurfaceVariant)
                            }
                        },
                        onClick = { showBlock = !showBlock }
                    )
                    if (showBlock) {
                        SubTitle("屏蔽来源")
                        if (sources.isEmpty()) {
                            Text(
                                "暂无可屏蔽的来源",
                                color = cs.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 18.dp)
                            )
                        }
                        sources.forEach { src ->
                            val blocked = blockedSources.contains(src.name)
                            SettingsRow(
                                src.name,
                                trailing = {
                                    Switch(checked = blocked, onCheckedChange = {
                                        if (it) vm.addBlockedSource(src.name) else vm.removeBlockedSource(src.name)
                                    })
                                }
                            )
                        }
                        Div()
                        SubTitle("屏蔽关键词")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = kwInput,
                                onValueChange = { kwInput = it },
                                label = { Text("添加关键词") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                if (kwInput.isNotBlank()) {
                                    vm.addBlockedKeyword(kwInput.trim())
                                    kwInput = ""
                                    // v2.4：加完收键盘，别让它一直挡着下面那排已屏蔽的词
                                    dismissIme()
                                }
                            }) { Text("添加") }
                        }
                        if (blockedKeywords.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                blockedKeywords.forEach { kw ->
                                    Surface(
                                        color = cs.errorContainer,
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.clickable { vm.removeBlockedKeyword(kw) }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                            Text(kw, color = cs.onErrorContainer, style = MaterialTheme.typography.labelMedium)
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Filled.Close, contentDescription = "移除", tint = cs.onErrorContainer, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        item {
            // ==================== 8. 数据 ====================
            GroupTitle("数据")
            Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    // v2.11：分两小段 —— 「备份与迁移」是「把数据搬去别处」，
                    // 「存储与统计」是「这台机器上的东西」。原来五条平铺在一起，
                    // 「阅读统计」「缓存管理」夹在两条备份之间，找备份入口要先扫一遍。
                    //
                    // 行尾颜色也统一了：原来「云端备份」「订阅迁移」是主色、
                    // 另外三条是灰的，同一种「点进去」的行却两种色，看着像有的能点有的不能。
                    // 现在的规则是：主色只留给「动作文字」（去管理 / 换一批 / 点此开始），
                    // 这里五条都是「当前状态」的说明，一律用次要色。
                    SubTitle("备份与迁移")
                    SettingsRow(
                        "备份与恢复",
                        trailing = { Text("导出 / 恢复", color = cs.onSurfaceVariant) },
                        onClick = { showBackup = true }
                    )
                    // v2.11：「本机现有 12 个阅源 · …」从行尾挪到这里。
                    // 行尾那块位置只装得下四五个字，这句有二十几个字，
                    // 挤在右边会折成两行、或者把标签压没（见 [SettingsRow] 的注释）。
                    // 说明本来就是放这种细节的地方，而且它是多行会长高的，不会破坏行高节奏。
                    Caption(
                        buildString {
                            if (summary.isNotBlank()) append("本机现有：$summary。\n")
                            append("换手机、重装时导入这一个文件就够了。")
                        }
                    )
                    Div()
                    SettingsRow(
                        "云端备份",
                        trailing = { Text("Gist / 网盘 / 本地文件夹", color = cs.onSurfaceVariant) },
                        onClick = { showCloud = true }
                    )
                    Caption("传到远端或别的文件夹，换机时不用先找到那个 JSON 文件。")
                    Div()
                    SettingsRow(
                        "订阅迁移（OPML）",
                        trailing = { Text("导出 / 导入订阅源", color = cs.onSurfaceVariant) },
                        onClick = { showMigrate = true }
                    )
                    Div()
                    SubTitle("存储与统计")
                    SettingsRow(
                        "阅读统计",
                        trailing = { Text("已读 / 来源排行", color = cs.onSurfaceVariant) },
                        onClick = onOpenStats
                    )
                    Div()
                    SettingsRow(
                        "缓存管理",
                        trailing = { Text("图片 / 文章 / 正文", color = cs.onSurfaceVariant) },
                        onClick = onOpenStorage
                    )
                }
            }
        }

        item {
            // ==================== 9. 帮助与关于 ====================
            GroupTitle("帮助与关于")
            Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    SettingsRow(
                        "使用手册 / 新手指南",
                        // 右侧原来还有一行小字「产品定位 · 上手 · 常见问题」，
                        // 属于「把目录塞进目录」，去掉更干净；只在没读过时留一个「新」提示。
                        trailing = {
                            if (!guideSeen) {
                                Surface(color = cs.primary, shape = MaterialTheme.shapes.extraSmall) {
                                    Text(
                                        "新",
                                        color = cs.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        },
                        onClick = onOpenGuide
                    )
                    Div()
                    // v2.14：从 GitHub Releases 拉新版并调起系统安装器。
                    // 放在「关于」上面：这一行是**动作**，下面是**信息**。
                    SettingsRow(
                        "检查更新",
                        trailing = { UpdateTrailing(updateState) },
                        onClick = { updateVm.open() }
                    )
                    Div()
                    SettingsRow(
                        "关于${titleOrDefault(appTitle)}",
                        trailing = { Text("v${BuildConfig.VERSION_NAME}", color = cs.onSurfaceVariant) },
                        onClick = onOpenAbout
                    )
                }
            }
            // v2.7.3：底栏是浮动胶囊、内容从它下面穿过 —— 底部留白由这一页自己承担
            //（父层不再压 padding，压了就穿不过去）。24dp 是原本的收尾留白，另加底栏高度。
            Spacer(Modifier.height(bottomInset + 24.dp))
        }
    }

    // ---------------- v2.14：检查更新 ----------------
    if (updateVisible) {
        UpdateDialog(
            state = updateState,
            currentVersion = updateVm.currentVersion,
            onCheck = { updateVm.check() },
            onDownload = { info -> updateVm.download(info) },
            onInstallAgain = { updateVm.installAgain() },
            onDismiss = { updateVm.dismiss() }
        )
    }

    // ---------------- v2.0：备份 / 恢复 ----------------
    if (showBackup) {
        BackupDialog(
            summary = summary,
            onDismiss = { showBackup = false },
            onExport = { showBackup = false; backupExportLauncher.launch(vm.backupFileName()) },
            onImport = { showBackup = false; backupImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )
    }

    // ---------------- v2.9：云备份 ----------------
    if (showCloud) {
        CloudBackupDialog(
            status = cloudStatus,
            keep = backupKeep,
            busy = cloudBusy,
            onKeep = { vm.setBackupKeep(it) },
            onConfig = { target ->
                when (target) {
                    BackupTarget.GIST -> showGistConfig = true
                    BackupTarget.WEBDAV -> showDavConfig = true
                    // 本地文件夹没有「配置」对话框 —— 系统自己的目录选择器就是那个对话框
                    BackupTarget.LOCAL -> localTreeLauncher.launch(null)
                }
            },
            onBackup = { runCloud(it, false) },
            onRestore = { runCloud(it, true) },
            onDismiss = { showCloud = false }
        )
    }

    if (showGistConfig) {
        GistConfigDialog(
            token = gistToken,
            gistId = gistId,
            onSave = { t, g ->
                showGistConfig = false
                // 先等写库落地、再拉状态。反过来写（先拉后写）会读到旧值，
                // 那一行小字就一直停在「未配置」，看着像没保存上。
                scope.launch {
                    vm.saveGistConfig(t, g)
                    cloudStatus = vm.cloudStatuses()
                }
            },
            onDismiss = { showGistConfig = false }
        )
    }

    if (showDavConfig) {
        DavConfigDialog(
            url = davUrl,
            user = davUser,
            pass = davPass,
            onSave = { u, n, p ->
                showDavConfig = false
                scope.launch {
                    vm.saveDavConfig(u, n, p)
                    cloudStatus = vm.cloudStatuses()
                }
            },
            onDismiss = { showDavConfig = false }
        )
    }

    // ---------------- v2.0：应用内名称 ----------------
    if (titleEditing) {
        var draft by remember { mutableStateOf(appTitle) }
        AlertDialog(
            onDismissRequest = { titleEditing = false },
            title = { Text("应用内名称") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        // 限 12 字：首页标题是双色字标，太长会把顶栏撑变形
                        onValueChange = { if (it.length <= 12) draft = it },
                        label = { Text("显示名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "最多 12 个字。留空 = 用默认名「阅闻」。这里改的是 App 里的标题，" +
                            "桌面图标名在「个性」里单独设置。",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setAppTitle(draft.trim()); titleEditing = false }) {
                    Text("保存", color = cs.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { titleEditing = false }) { Text("取消") }
            }
        )
    }

    // ---------------- 订阅迁移（OPML） ----------------
    if (showMigrate) {
        AlertDialog(
            onDismissRequest = { showMigrate = false },
            title = { Text("订阅迁移（OPML）") },
            text = {
                Text(
                    "OPML 是 RSS 阅读器之间通用的订阅列表格式。\n\n" +
                            "· 导出：把当前 ${sources.size} 个订阅源写成一个 .opml 文件，别的阅读器可以直接导入；\n" +
                            "· 导入：从别的阅读器导出 OPML，再选进来，重复的会自动跳过。\n\n" +
                            "（想连收藏和笔记一起搬？用上面的「备份与恢复」。）"
                )
            },
            confirmButton = {
                TextButton(onClick = { showMigrate = false; exportLauncher.launch("yuewen-订阅源.opml") }) {
                    Text("导出", color = cs.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMigrate = false; importLauncher.launch(arrayOf("*/*")) }) {
                    Text("导入")
                }
            }
        )
    }
}

/**
 * 备份 / 恢复面板（v2.0）。
 *
 * 故意做成一个「说人话」的小弹窗，而不是直接甩两个文件选择器：
 * 用户需要先知道备份里包含什么、恢复会不会覆盖本机数据，才敢点。
 */
@Composable
private fun BackupDialog(
    summary: String,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备份与恢复") },
        text = {
            Column {
                Text(
                    "这份备份里包含：\n" +
                            "· 全部订阅源（阅源）\n" +
                            "· 全部收藏文章（含收藏夹、已读状态、阅读进度）\n" +
                            "· 全部摘录与笔记\n" +
                            "· 个性化设置（主题、排版、名字等）\n\n" +
                            "不包含正文缓存（那部分换台机器重新联网抓就行），所以文件很小，方便丢进网盘。\n\n" +
                            "本机现有：$summary",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onExport) { Text("导出备份文件", color = cs.primary) }
        },
        dismissButton = {
            TextButton(onClick = onImport) { Text("从文件恢复") }
        }
    )
}

// ------------------------------------------------------------------ v2.9：云备份

/**
 * 云备份面板（v2.9）。
 *
 * 三条通道摆在同一个弹窗里，而不是各自藏进子菜单：
 * 用户要判断的是「我现在到底有没有备份」，三条通道分三处看等于每次都要点三下；
 * 摆在一条竖线上，「哪条配了、哪条还没」一眼就看完。
 *
 * 每一格都配「配置 / 备份 / 恢复」三个动作，**不给「测试连接」** ——
 * 状态行本身就是在读远端的目录列表，能列出「已有 N 份，最近 …」就说明连接是通的，
 * 再多一个测试按钮只是多一次网络请求和一个要解释的失败态。
 */
@Composable
private fun CloudBackupDialog(
    status: Map<BackupTarget, String>,
    keep: Int,
    busy: String?,
    onKeep: (Int) -> Unit,
    onConfig: (BackupTarget) -> Unit,
    onBackup: (BackupTarget) -> Unit,
    onRestore: (BackupTarget) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("云端备份") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "内容和「备份与恢复」里的完全一样（订阅源 / 收藏与稍后读 / 笔记 / 个性化设置），" +
                            "只是直接传到远端，换机时不用先找到那个文件。\n\n" +
                            "三条通道可以同时用。令牌和密码只存在这台设备上，不会写进备份文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                BackupTarget.values().forEach { target ->
                    CloudTargetBlock(
                        target = target,
                        status = status[target] ?: "读取中…",
                        // 任意一条通道在忙就把全部按钮锁上：同时往两条通道发备份没有好处，
                        // 而并发写同一份 DataStore 设置是我们要避开的。
                        enabled = busy == null,
                        onConfig = { onConfig(target) },
                        onBackup = { onBackup(target) },
                        onRestore = { onRestore(target) }
                    )
                }

                HorizontalDivider(color = cs.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Text("每条通道各保留", style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(1, 3, 5, 10, 30).forEach { n ->
                        val sel = n == keep
                        Surface(
                            onClick = { onKeep(n) },
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (sel) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerHigh
                        ) {
                            Text(
                                "$n 份",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel) cs.primary else cs.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "超出份数的旧备份会被自动删掉。文件很小（不含正文缓存），一般留 5 份就够。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = cs.primary) }
        }
    )
}

@Composable
private fun CloudTargetBlock(
    target: BackupTarget,
    status: String,
    enabled: Boolean,
    onConfig: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(target.label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CloudActionChip("配置", enabled, onConfig)
            CloudActionChip("备份", enabled, onBackup)
            CloudActionChip("恢复", enabled, onRestore)
        }
    }
}

/** 弹窗里的小按钮。和设置页的胶囊同一个形状，只是窄一点 —— 一行要放三个。 */
@Composable
private fun CloudActionChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
        color = if (enabled) cs.primary.copy(alpha = 0.12f) else cs.surfaceContainerHigh
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

/**
 * Gist 配置。
 *
 * ⚠️ 输入框里显示的是**明文令牌**，这是刻意的取舍：
 * 这是用户自己的设备、自己的设置页，而他最需要做的事就是「确认我粘对了没有」——
 * 打上一串圆点只会让他反复粘贴。真正要防的是**备份文件被转发出去**，
 * 而令牌根本不进备份（见 SettingsRepository 的 BACKUP_KEYS 注释）。
 */
@Composable
private fun GistConfigDialog(
    token: String,
    gistId: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var tk by remember { mutableStateOf(token) }
    var id by remember { mutableStateOf(gistId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub Gist") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "1. 打开 github.com/settings/tokens 新建一个 Classic token；\n" +
                            "2. 权限只勾 gist 这一项，别的都不用勾；\n" +
                            "3. 把生成的那串粘到下面。\n\n" +
                            "令牌只保存在这台设备上，不会写进备份文件 —— 换机后需要重新填一次。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = tk,
                    onValueChange = { tk = it },
                    label = { Text("个人访问令牌") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Gist 标识（留空 = 首次备份时自动新建）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Gist 标识会跟着备份走，所以恢复之后只要再填一次令牌，" +
                            "后面的备份会接着写进原来那个 Gist，不会又新建一个。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tk, id) }) { Text("保存", color = cs.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * WebDAV 配置。
 *
 * 地址填到**目录**（不是文件），例如坚果云是 `https://dav.jianguoyun.com/dav/阅闻`；
 * App 会在那里放 `yuewen-<时间戳>.json`，并顺手把多出来的旧文件删掉。
 */
@Composable
private fun DavConfigDialog(
    url: String,
    user: String,
    pass: String,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var u by remember { mutableStateOf(url) }
    var n by remember { mutableStateOf(user) }
    var p by remember { mutableStateOf(pass) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV 网盘") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "坚果云 / Nextcloud / 群晖这些都能用。地址填到想放备份的那个目录，例如：\n" +
                            "https://dav.jianguoyun.com/dav/阅闻\n\n" +
                            "很多网盘要求用「应用密码 / 授权码」而不是登录密码 —— " +
                            "坚果云在「账户信息 → 安全选项 → 第三方应用管理」里生成。\n\n" +
                            "密码只保存在这台设备上，不会写进备份文件。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = u,
                    onValueChange = { u = it },
                    label = { Text("地址（到目录）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = n,
                    onValueChange = { n = it },
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = p,
                    onValueChange = { p = it },
                    label = { Text("密码 / 应用授权码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(u, n, p) }) { Text("保存", color = cs.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 「检查更新」那一行右侧的状态文字（v2.14）。
 *
 * 为什么不显示成一行「版本号」：这一行是**动作**，右侧应当回答
 * 「现在点下去会发生什么的预告」—— 有没有新版、下没下完。
 * 当前版本号在「关于」那一行和更新对话框底部都有，不需要在这儿再说一遍。
 *
 * 颜色只分三档：**主色 = 有事可做**（有新版本 / 已下载待装）、
 * 次级灰 = 无事可做、error = 出错了。别用更多档，否则整列颜色会花。
 */
@Composable
private fun UpdateTrailing(state: UpdateUiState) {
    val cs = MaterialTheme.colorScheme
    val (text, color) = when (state) {
        UpdateUiState.Idle -> "点一下查" to cs.onSurfaceVariant
        UpdateUiState.Checking -> "检查中…" to cs.onSurfaceVariant
        is UpdateUiState.Latest -> "已是最新" to cs.onSurfaceVariant
        is UpdateUiState.Available -> "有新版本 v${state.info.versionName}" to cs.primary
        is UpdateUiState.Downloading -> {
            // 服务端没给总大小时（percent == null）不编一个百分比出来
            val p = state.percent
            (if (p == null) "下载中…" else "下载中 $p%") to cs.primary
        }
        is UpdateUiState.Ready -> "待安装 v${state.info.versionName}" to cs.primary
        is UpdateUiState.Failed -> "检查失败" to cs.error
    }
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
}
