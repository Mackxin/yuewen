package com.example.yuewen.ui.util

/**
 * 应用对外地址（v2.0.2）。
 *
 * 刻意集中在一个文件里：地址定下来之后，只改这里两行，「关于」页那一栏
 * 会自动从「暂未上线」变成可点的链接 —— 不用再去 UI 里翻字符串。
 *
 * 留空 = 还没准备好，界面上显示占位文案并且点了只给一句提示，不会打开空链接。
 */
object AppLinks {

    /** 官网。填成 `https://xxx.com` 这样的完整地址。 */
    const val OFFICIAL_SITE = "http://yihaozhan.xyz/"

    /** 开源仓库（GitHub）。填成 `https://github.com/xxx/yyy`。 */
    const val GITHUB_REPO = "https://github.com/Mackxin/yuewen"

    /** 地址还没上线时显示在行尾的占位文案。 */
    const val PLACEHOLDER = "暂未上线"

    fun siteReady(): Boolean = OFFICIAL_SITE.isNotBlank()

    fun repoReady(): Boolean = GITHUB_REPO.isNotBlank()

    /** 两个地址是不是都还没填（都空时，关于页会加一句说明）。 */
    fun noneReady(): Boolean = !siteReady() && !repoReady()
}
