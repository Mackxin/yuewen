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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yuewen.YuewenApplication
import com.example.yuewen.ui.components.PillTab
import com.example.yuewen.ui.components.PillTabRow
import kotlinx.coroutines.launch

/** 闻件页的四个子页。顺序 = 标签栏顺序 = 偏好里存的序号。 */
private val WENJIAN_TABS = listOf("搜索", "收藏", "历史", "笔记")

/**
 * 「闻件」＝ 一切**已经属于自己的内容**（v2.0）。
 *
 * 命名来自用户：原来「搜索」「收藏」是两个底部 Tab，占了两个位置，
 * 但它们本质是同一件事 —— 我要找回我看过 / 存下来的东西。
 * 合并成一页，四个子页：搜索 / 收藏 / 历史 / 笔记。
 * 少占一个底栏位置，也多出一个位置给「阅源」。
 *
 * 上次停留在哪一栏会记进 DataStore：切去别处再回来，不至于每次都跳回第一栏。
 */
@Composable
fun WenjianScreen(
    app: YuewenApplication,
    onOpenArticle: (String) -> Unit,
    glass: Boolean = false
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
        // v2.6：① 加 `statusBarsPadding()` —— 沉浸式之后窗口不再自动避让状态栏，
        //         不补这一层标签会被状态栏压住；
        //       ② 顺带把外壳换成玻璃，和底栏、首页顶栏统一。
        PillTabRow(
            items = tabs,
            selectedIndex = tabIndex,
            onSelect = { select(it) },
            glass = glass,
            modifier = Modifier.statusBarsPadding()
        )

        when (tabIndex) {
            0 -> SearchPane(app = app, onOpenArticle = onOpenArticle)
            1 -> BookmarksPane(app = app, onOpenArticle = onOpenArticle)
            2 -> HistoryPane(app = app, onOpenArticle = onOpenArticle)
            else -> NotesPane(app = app, onOpenArticle = onOpenArticle)
        }
    }
}

/** 供设置页 / 外部跳转用：直接打开闻件的某一栏。 */
object WenjianTabIndex {
    const val SEARCH = 0
    const val BOOKMARKS = 1
    const val HISTORY = 2
    const val NOTES = 3
}
