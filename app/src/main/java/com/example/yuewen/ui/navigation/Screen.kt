package com.example.yuewen.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航的四个页面（v2.0 起）。
 *
 * 变化说明：
 * - 原来的「搜索」和「收藏」是两个独立 Tab，各自占一个位置，
 *   但用户真正的动作是「我要找一篇看过的 / 存下来的东西」——是**一件事**。
 *   现在合并成一个 Tab「闻件」，里面再分 搜索 / 收藏 / 历史 / 笔记 四栏。
 * - 新增「阅源」：订阅源不再藏在设置里的一个折叠项，而是独立一页 ——
 *   发现、搜索、一键订阅推荐源都在那里，解决「装好之后一片空白」的冷启动问题。
 * - 原来的「新闻源」这个说法统一改成「**阅源**」（订阅源），
 *   与「闻件」一起构成一套自洽的中文命名。
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "首页", Icons.Filled.Home)
    object Wenjian : Screen("wenjian", "闻件", Icons.Filled.Bookmarks)
    object Sources : Screen("sources", "阅源", Icons.Filled.RssFeed)
    object Settings : Screen("settings", "设置", Icons.Filled.Settings)
    object Detail : Screen("detail/{link}", "详情", Icons.Filled.Home)

    companion object {
        // 注意：这里必须用"函数"而不是 val 属性。
        // 原因：若用 val，companion 初始化时会引用 Home 等 object，
        // 而 Home 初始化又要加载父类 Screen（Screen 初始化会创建 companion），
        // 形成类初始化死循环，JVM 会静默跳过，导致列表里混入 null → 启动必崩。
        // 改成函数后，列表在真正调用时才构建，此时所有对象早已初始化完毕。
        fun bottomTabs(): List<Screen> = listOf(Home, Wenjian, Sources, Settings)
    }
}
