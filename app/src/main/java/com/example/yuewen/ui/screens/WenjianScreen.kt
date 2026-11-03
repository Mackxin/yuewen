package com.example.yuewen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.util.WenjianTabs
import com.example.yuewen.ui.components.PillTab
import com.example.yuewen.ui.components.PillTabRow
import kotlinx.coroutines.launch

/**
 * 闻件页的五个子页。顺序 = 标签栏顺序 = [com.example.yuewen.data.util.WenjianTabs] 里的下标。
 *
 * ⚠️ 改这个列表必须同步改 [com.example.yuewen.data.util.WenjianTabs]：
 * 那边有 `COUNT` 常量和升级用的下标换算，两处对不上会导致
 * 「上次停在哪一栏」记错位置（老用户升级后直接落到别的栏）。
 *
 * v2.8：在「搜索」后面插入了「稍后读」。
 * 放这么靠前是因为它是一栏**要去做的事**（待办性质），
 * 和后面「收藏 / 历史 / 笔记」这种归档性质的不一样，该更顺手够到。
 */
private val WENJIAN_TABS = listOf("搜索", "稍后读", "收藏", "历史", "笔记")

/**
 * 「闻件」＝ 一切**已经属于自己的内容**（v2.0）。
 *
 * 命名来自用户：原来「搜索」「收藏」是两个底部 Tab，占了两个位置，
 * 但它们本质是同一件事 —— 我要找回我看过 / 存下来的东西。
 * 合并成一页，子页：搜索 / 稍后读 / 收藏 / 历史 / 笔记。
 * 少占一个底栏位置，也多出一个位置给「阅源」。
 *
 * 上次停留在哪一栏会记进 DataStore：切去别处再回来，不至于每次都跳回第一栏。
 */
@Composable
fun WenjianScreen(
    app: YuewenApplication,
    onOpenArticle: (String) -> Unit,
    /**
     * v2.7：底栏高度（含导航栏 inset）。
     *
     * 底栏只在**四个按钮那一行**铺了一层与页面同色的实底，上下留白是透的（v2.7.2），
     * 所以这里的内容要一路画到屏幕最底边、从底栏后面穿过去；
     * 但滚到最后一项时得留出这段空白，否则最后一张卡片会被底栏压住看不见。
     */
    bottomInset: Dp = 0.dp
) {
    val saved by app.settingsRepository.wenjianTabFlow.collectAsStateWithLifecycle(0)
    var tabIndex by remember { mutableIntStateOf(saved.coerceIn(0, WENJIAN_TABS.lastIndex)) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    // 首次进来时把持久化值同步一次（collectAsStateWithLifecycle 的首帧给的是初值，
    // 真实偏好到达后要跟上）
    LaunchedEffect(saved) {
        val v = saved.coerceIn(0, WENJIAN_TABS.lastIndex)
        if (v != tabIndex) tabIndex = v
    }

    fun select(i: Int) {
        tabIndex = i
        scope.launch { app.settingsRepository.setWenjianTab(i) }
    }

    // 标签条目只跟常量表有关，别每次重组都新建一遍 List<PillTab>
    // （列表对象换了，PillTabRow 内部所有依赖它的东西都会跟着失效）
    val tabs = remember { WENJIAN_TABS.map { PillTab(it) } }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        // v2.2：从 Material 的 TabRow（底下一条指示线）换成和底栏同款的胶囊标签栏 ——
        // 一上一下两种完全不同的视觉语言摆在同一屏里太割裂了。
        // v2.6：加 `statusBarsPadding()` —— 沉浸式之后窗口不再自动避让状态栏，
        //       不补这一层标签会被状态栏压住。
        // v2.7：撤掉「液态玻璃」，退回实色胶囊（和 PillTabRow 内部一起改的）。
        PillTabRow(
            items = tabs,
            selectedIndex = tabIndex,
            onSelect = { select(it) },
            modifier = Modifier.statusBarsPadding()
        )

        // 用常量而不是裸数字：插入新栏时只要改 WenjianTabs，这里的分支跟着常量走，
        // 不会出现「加了栏但 when 还按旧下标分派」这种错位。
        when (tabIndex) {
            WenjianTabs.SEARCH -> SearchPane(app = app, onOpenArticle = onOpenArticle, bottomInset = bottomInset)
            WenjianTabs.QUEUE -> QueuePane(app = app, onOpenArticle = onOpenArticle, bottomInset = bottomInset)
            WenjianTabs.BOOKMARKS -> BookmarksPane(app = app, onOpenArticle = onOpenArticle, bottomInset = bottomInset)
            WenjianTabs.HISTORY -> HistoryPane(app = app, onOpenArticle = onOpenArticle, bottomInset = bottomInset)
            else -> NotesPane(app = app, onOpenArticle = onOpenArticle, bottomInset = bottomInset)
        }
    }
}

/**
 * 供设置页 / 外部跳转用：直接打开闻件的某一栏。
 *
 * v2.8：改为直接复用 [WenjianTabs] 的常量，不再各维护一份 ——
 * 两套常量最容易出现的问题就是改了一处忘了另一处，而症状是
 * 「点设置里的链接跳到了隔壁那一栏」，查起来毫无线索。
 */
object WenjianTabIndex {
    const val SEARCH = WenjianTabs.SEARCH
    const val QUEUE = WenjianTabs.QUEUE
    const val BOOKMARKS = WenjianTabs.BOOKMARKS
    const val HISTORY = WenjianTabs.HISTORY
    const val NOTES = WenjianTabs.NOTES
}
