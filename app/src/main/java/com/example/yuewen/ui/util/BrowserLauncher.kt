package com.example.yuewen.ui.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.net.toUri

/**
 * 「用哪个浏览器打开原文」（v2.2.0）。
 *
 * 之前正文底部的「原文」、长按菜单的「在浏览器打开原文」，走的都是裸的
 * `Intent.ACTION_VIEW` —— 系统会把「默认浏览器 / 上次用过那个 / 每次问」的决定权
 * 全交给 Android 自己。用户装了 Chrome、Edge、夸克、神马，想固定用其中一个，
 * 只能去系统设置里改「默认应用」，跟我们无关。
 *
 * 这里把它变成一个**App 内的偏好**：设置 → 阅读与朗读 → 默认浏览器，
 * 存的是包名（不是显示名：显示名会被系统翻译、也可能重名，包名才唯一）。
 * 存空串 = 「跟随系统」，也就是老行为。
 *
 * 为什么不用 CustomTabs？
 * CustomTabs 是「借用 Chrome 内核在 App 里开网页」，外观上不是用户熟悉的那只浏览器，
 * 也没法指定夸克 / 神马这类自研内核的浏览器。用户要的是「选自己喜欢的浏览器」，
 * 那就老老实实把 Intent 发给那个浏览器。
 */
object BrowserLauncher {

    /** 一个可选的浏览器。 */
    data class Browser(
        /** 包名 —— 真正存进设置里的东西。 */
        val pkg: String,
        /** 界面名字（如「Chrome」），设置页里显示给用户看。 */
        val label: String
    )

    /** 「跟随系统」这一项的取值：存空串，走系统默认。 */
    const val SYSTEM = ""

    /**
     * 列出本机所有能打开网页的应用。
     *
     * 用 `queryIntentActivities` + `MATCH_DEFAULT_ONLY`：
     * 只问「哪些应用把自己注册成了浏览网页的人」，不去翻所有能看到 http 的 App
     * （否则微信、QQ、各家的分享组件都会混进来，它们不是浏览器）。
     *
     * 去重按包名 —— 同一个浏览器可能注册了好几个 Activity（主界面 + 分享入口），
     * 不去重的话列表里会出现三个「Chrome」。
     */
    fun browsers(context: Context): List<Browser> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val resolved: List<ResolveInfo> = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())

        val seen = HashSet<String>()
        val out = ArrayList<Browser>(resolved.size)
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue          // 自己不算
            if (!seen.add(pkg)) continue                       // 同包名只留第一条
            val label = runCatching {
                ri.loadLabel(pm).toString()
            }.getOrDefault(pkg).trim().ifBlank { pkg }
            out.add(Browser(pkg, label))
        }
        // 按显示名排序，列表看起来才有秩序（系统自带的那个通常排在最前）
        return out.sortedBy { it.label.lowercase() }
    }

    /**
     * 打开一个链接。
     *
     * [pkg] 存空串（跟随系统）→ 交给 Android 自己决定；
     * 指定了包名 → 显式发给它（`setPackage`）。如果那个浏览器已经被卸载 / 被停用，
     * 显式 Intent 会抛 `ActivityNotFoundException`，这时**退回系统默认**而不是弹一个
     * 「找不到应用」——用户的目标是「看这篇文章」，不该因为一个过期偏好就读不到。
     *
     * 返回 true 表示成功发出去了；false 表示这台机器根本没有能开网页的应用
     * （调用方可以给个 Toast）。
     */
    fun open(context: Context, url: String, pkg: String = SYSTEM): Boolean {
        if (url.isBlank()) return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false

        // 先试用户指定的浏览器（更具体的意图先走）
        if (pkg.isNotBlank()) {
            val explicit = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
            // 从非 Activity 上下文启动要带 NEW_TASK；Activity 上下文带这个 flag 也无害
            explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(explicit) }.isSuccess) return true
            // 失败（卸载了 / 被禁用了）就往下走系统默认，不打扰用户
        }

        val fallback = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    /**
     * 设置页里的选项文案：把包名翻译成显示名。
     * 找不到（浏览器已被卸载）就退回包名本身 —— 总比显示一片空白强。
     */
    fun labelOf(context: Context, pkg: String, known: List<Browser> = browsers(context)): String =
        known.firstOrNull { it.pkg == pkg }?.label ?: pkg

    /** 设置页里当前值的显示文案。 */
    fun displayName(context: Context, pkg: String, known: List<Browser> = browsers(context)): String =
        if (pkg.isBlank()) "跟随系统" else labelOf(context, pkg, known)
}
