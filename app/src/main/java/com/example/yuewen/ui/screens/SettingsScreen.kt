package com.example.yuewen.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.BuildConfig
import com.example.yuewen.YuewenApplication
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
import com.example.yuewen.ui.util.titleOrDefault
import com.example.yuewen.ui.viewmodel.HomeChipMode
import com.example.yuewen.ui.viewmodel.SettingsViewModel
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
        // 分组标题：顶部 11dp 把上一组和这一组分开，底部 4dp 贴近它自己的卡片
        modifier = Modifier.padding(start = 22.dp, top = 11.dp, bottom = 4.dp)
    )
}

/** 卡片内部的小标题：用来把一张长卡片里的「首页顶栏」这类子块分开。 */
@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 7.dp, bottom = 1.dp)
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

/** 分隔线。`inset` = 左右留边（夹在两排胶囊之间时用，视觉上更松）。 */
@Composable
private fun Div(inset: Boolean = false) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = if (inset) Modifier.padding(horizontal = 18.dp) else Modifier
    )
}

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
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        trailing()
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
                    color = if (sel) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) cs.primary else cs.outlineVariant)
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

/** 行尾的「当前值 ▾」样式，三处下拉菜单共用。 */
@Composable
private fun ValueTrailing(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
        // 菜单挂在这个 Box 上（它只占右侧值那么宽）→ 从右边弹出来
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
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
        Text(label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
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
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) cs.primary else cs.outlineVariant,
                    shape = CircleShape
                ),
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
    onOpenSources: () -> Unit = {}
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.provide(app))
    val theme by vm.theme.collectAsStateWithLifecycle()
    val font by vm.font.collectAsStateWithLifecycle()
    val notify by vm.notify.collectAsStateWithLifecycle()
    val autoread by vm.autoread.collectAsStateWithLifecycle()
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
    val ttsNotify by vm.ttsNotify.collectAsStateWithLifecycle()
    val guideSeen by vm.guideSeen.collectAsStateWithLifecycle()
    // v2.0.2：首页筛选用哪种维度 + 打开时默认停在哪儿
    val chipMode by vm.homeChipMode.collectAsStateWithLifecycle()
    val homeDefaultCategory by vm.homeDefaultCategory.collectAsStateWithLifecycle()
    val homeDefaultSource by vm.homeDefaultSource.collectAsStateWithLifecycle()
    // v2.2：首页排序 + 默认浏览器
    val homeSort by vm.homeSort.collectAsStateWithLifecycle()
    val browserPkg by vm.browserPkg.collectAsStateWithLifecycle()
    // v2.3：配色方案
    val themePalette by vm.themePalette.collectAsStateWithLifecycle()
    val customHue by vm.customHue.collectAsStateWithLifecycle()
    val customSat by vm.customSat.collectAsStateWithLifecycle()

    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showBlock by remember { mutableStateOf(false) }
    var kwInput by remember { mutableStateOf("") }
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

    // ---- v2.0.2：首页筛选相关的派生值 ----
    // 分类直接从已订阅的源里抽（不再单独存一份，源删了分类自然消失）
    val categories = sources.map { it.category }.distinct().filter { it.isNotBlank() }
    val defaultFilterLabel = when {
        // 选了具体阅源就显示它（阅源是更「具体」的那一级）
        chipMode != HomeChipMode.Category && homeDefaultSource.isNotBlank() -> homeDefaultSource
        homeDefaultCategory != "推荐" -> homeDefaultCategory
        chipMode == HomeChipMode.Source -> "全部阅源"
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

    Column(
        modifier = Modifier.fillMaxSize().background(cs.background).verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设置", style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
        }

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
                Div(inset = true)
                ChoiceRow(
                    label = "行距",
                    options = ReaderSpacing.entries.map { it.label },
                    selectedIndex = ReaderSpacing.of(readerSpacing).ordinal,
                    onSelect = { vm.setReaderSpacing(ReaderSpacing.entries[it].key) }
                )
                Div(inset = true)
                ChoiceRow(
                    label = "字体",
                    options = ReaderFont.entries.map { it.label },
                    selectedIndex = ReaderFont.of(readerFont).ordinal,
                    onSelect = { vm.setReaderFont(ReaderFont.entries[it].key) }
                )
                Div(inset = true)
                ChoiceRow(
                    label = "底色",
                    options = ReaderTheme.entries.map { it.label },
                    selectedIndex = ReaderTheme.of(readerTheme).ordinal,
                    onSelect = { vm.setReaderTheme(ReaderTheme.entries[it].key) }
                )
                Caption("「米黄纸感」和「墨夜」就是原来的正念阅读模式。详情页右上角那个 Aa 按钮也能改这几项。")
                Div()
                ChoiceRow(
                    label = "朗读语速",
                    options = TtsRateLabels,
                    selectedIndex = ttsRate.coerceIn(0, TtsRateLabels.lastIndex),
                    onSelect = { vm.setTtsRate(it) }
                )
                Caption("慢 / 标准 / 快 / 很快四档。朗读过程中改的话，从下一句开始生效。")
                Div()
                SettingsRow(
                    "朗读时显示通知栏控制",
                    trailing = { Switch(checked = ttsNotify, onCheckedChange = { vm.setTtsNotify(it) }) }
                )
                Caption(
                    "开启后，朗读时会挂一条通知：锁屏 / 通知栏就能暂停、继续、停止，详情页顶部也会浮出一条「回到文章」。"
                )
                Div()
                SettingsRow(
                    "阅读文章时显示底部导航栏",
                    trailing = { Switch(checked = showBarInReader, onCheckedChange = { vm.setShowBarInReader(it) }) }
                )
                Caption("开启后读文章时底栏依然在，可以随时切走；关闭则是全屏沉浸阅读。")
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
                Caption(
                    "点「原文」时用哪个应用打开。选「跟随系统」就交给安卓自己决定（和以前一样）；" +
                        "选了具体浏览器之后，中途把它卸载了会自动退回系统默认，不会打不开。"
                )
            }
        }

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
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) cs.primary else cs.outlineVariant),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    l,
                                    color = if (sel) cs.primary else cs.onSurface,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
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
                Caption(
                    "点一下就换，整个界面立刻生效。下面这排是内置配色；" +
                        "选「自定义」可以自己调色相和鲜艳度，拖到哪就是哪。"
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
                Caption("紧凑省地方、卡片有缩略图、杂志图最大。首页右上角的按钮也能随时切换。")

                // 首页顶栏：三个按钮加副标题，都能单独关掉
                Div()
                SubTitle("首页顶栏")
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
                Caption("不喜欢顶栏太挤就关掉几个；下拉着照样能刷新，布局在下面「列表布局」里也能改。")
                Div()
                SettingsRow(
                    "首页顶部筛选图标",
                    trailing = { Switch(checked = homeFilterIcons, onCheckedChange = { vm.setHomeFilterIcons(it) }) }
                )
                Caption("默认显示。那两个小图标分别是「仅看未读 / 显示全部」和「全部标为已读」。")

                // ---- v2.0.2：首页筛选胶囊按什么维度（分类 / 阅源 / 两行都显示） ----
                Div()
                SubTitle("首页筛选")
                ChoiceRow(
                    label = "显示",
                    options = HomeChipMode.entries.map { it.label },
                    selectedIndex = chipMode.ordinal,
                    onSelect = { vm.setHomeChipMode(HomeChipMode.entries[it]) }
                )
                Caption(
                    "顶栏那排胶囊可以按「分类」筛，也可以按「阅源」筛；选「都显示」就是上下两行，" +
                        "上面选分类、下面选这个分类里的阅源，两个条件是叠加的。"
                )
                Div()
                // 选项按当前顶栏模式拼：
                //   分类项只在顶栏显示分类时才有意义，阅源项同理 —— 免得选了不生效。
                // 取值用带前缀的 key（"c:" / "s:"）区分两级 —— 分类名和阅源名可能重名，
                // 光看字符串分不出用户点的是哪一级。
                DropdownRow(
                    label = "打开时默认停在",
                    value = defaultFilterLabel,
                    expanded = defaultExpanded,
                    onExpandedChange = { defaultExpanded = it },
                    options = buildList {
                        if (chipMode != HomeChipMode.Source) {
                            add("c:推荐" to "全部（不限分类）")
                            categories.forEach { add("c:$it" to it) }
                        }
                        if (chipMode != HomeChipMode.Category) {
                            add("s:" to "全部阅源")
                            sources.filter { it.enabled }.map { it.name }.distinct().forEach { add("s:$it" to it) }
                        }
                    },
                    selectedKey = if (chipMode != HomeChipMode.Source && homeDefaultSource.isBlank())
                        "c:$homeDefaultCategory"
                    else "s:$homeDefaultSource",
                    onPick = { key ->
                        if (key.startsWith("c:")) vm.setHomeDefaultCategory(key.removePrefix("c:"))
                        else vm.setHomeDefaultSource(key.removePrefix("s:"))
                    }
                )
                Caption(
                    "下次打开 App 时列表默认按这一项筛选。中途点了别的胶囊不影响这里 —— " +
                        "这一项是「开机默认」，不是「记住上次」。"
                )

                // ---- v2.2：文章怎么排 ----
                Div()
                SubTitle("文章排序")
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
                Div()
                SettingsRow(
                    "底栏显示未读数字",
                    trailing = { Switch(checked = unreadBadge, onCheckedChange = { vm.setUnreadBadge(it) }) }
                )
                Caption("默认关闭：底部导航「首页」图标上不显示未读条数，底栏更干净。打开后最多显示 99+。")
            }
        }

        // ==================== 3. 个性 ====================
        GroupTitle("个性")
        Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column {
                // 应用内名称：首页大标题、「关于」页都用它。留空回落到默认「阅闻」。
                SettingsRow(
                    "应用内名称",
                    trailing = { ValueTrailing(titleOrDefault(appTitle)) },
                    onClick = { titleEditing = true }
                )
                Caption("首页顶部那个大标题、还有「关于」页里都用这个名字。留空就用默认的「阅闻」。")
                Div()
                // 桌面图标名：系统不允许运行时改 android:label，
                // 只能预置若干个 activity-alias，在这里挑一个（本质是切换 alias）。
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
                Caption(
                    "这是手机桌面上图标底下显示的文字。系统不允许 App 随便改，只能从这几个里挑；" +
                        "而上面那个「应用内名称」可以随便写。"
                )
            }
        }

        // ==================== 4. 阅源与刷新 ====================
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
                Caption("后台多久自动拉一次新文章。关掉之后就只在你手动下拉时刷新。")
                Div()
                SettingsRow(
                    "打开 App 自动刷新",
                    trailing = { Switch(checked = refreshOnLaunch, onCheckedChange = { vm.setRefreshOnLaunch(it) }) }
                )
                Caption("默认开启：每次打开 App 自动拉一次最新文章。距上次刷新不到 5 分钟会自动跳过。")
                Div()
                SettingsRow(
                    "打开文章自动标为已读",
                    trailing = { Switch(checked = autoread, onCheckedChange = { vm.setAutoRead(it) }) }
                )
            }
        }

        // ==================== 5. 离线阅读 ====================
        GroupTitle("离线阅读")
        Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column {
                SettingsRow(
                    "自动预加载正文",
                    trailing = { Switch(checked = preloadAuto, onCheckedChange = { vm.setPreloadAuto(it) }) }
                )
                Caption(
                    "默认关闭。打开后每次刷新完会在后台把每篇文章的完整正文抓下来存进本机 —— " +
                        "网络差或完全没网时也能读到全文，而不是只有 feed 里那句摘要。"
                )
                if (preloadAuto) {
                    Div()
                    SettingsRow(
                        "仅在 Wi-Fi 下预加载",
                        trailing = { Switch(checked = preloadWifiOnly, onCheckedChange = { vm.setPreloadWifiOnly(it) }) }
                    )
                    Caption("默认开启。抓正文是逐篇访问网页，比抓 RSS 重得多，不该在流量上偷跑。")
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
                        append("。\n出门前点一次「立即缓存全部正文」，路上没信号也能把当天的文章读完。")
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

        // ==================== 6. 提醒与屏蔽 ====================
        GroupTitle("提醒与屏蔽")
        Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column {
                SettingsRow(
                    "新内容提醒",
                    trailing = { Switch(checked = notify, onCheckedChange = { vm.setNotify(it) }) }
                )
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
                        TextButton(onClick = { if (kwInput.isNotBlank()) { vm.addBlockedKeyword(kwInput.trim()); kwInput = "" } }) { Text("添加") }
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

        // ==================== 7. 数据 ====================
        GroupTitle("数据")
        Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column {
                SettingsRow(
                    "备份与恢复",
                    trailing = { Text(summary.ifBlank { "订阅 / 收藏 / 笔记" }, color = cs.onSurfaceVariant) },
                    onClick = { showBackup = true }
                )
                Caption("把订阅源、收藏、摘录笔记和个性化设置打包成一个文件，换手机或重装时导入即可。")
                Div()
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
                Div()
                SettingsRow(
                    "订阅迁移（OPML）",
                    trailing = { Text("导出 / 导入订阅源", color = cs.primary) },
                    onClick = { showMigrate = true }
                )
            }
        }

        // ==================== 8. 帮助与关于 ====================
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
                SettingsRow(
                    "关于${titleOrDefault(appTitle)}",
                    trailing = { Text("v${BuildConfig.VERSION_NAME}", color = cs.onSurfaceVariant) },
                    onClick = onOpenAbout
                )
            }
        }
        Spacer(Modifier.height(24.dp))
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
