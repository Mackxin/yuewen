package com.example.yuewen.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.yuewen.MainActivity
import com.example.yuewen.R
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.util.DailyDigest
import com.example.yuewen.data.util.WidgetSnapshot
import com.example.yuewen.service.TtsPlaybackService
import com.example.yuewen.ui.theme.DEFAULT_CUSTOM_HUE
import com.example.yuewen.ui.theme.DEFAULT_CUSTOM_SAT
import com.example.yuewen.ui.theme.ThemePalette
import com.example.yuewen.ui.theme.generateScheme
import com.example.yuewen.ui.theme.seedFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 把数据画到桌面上（v2.9）。
 *
 * 两个尺寸共用这一份取数、配色与点击逻辑，只有布局 id 不同。
 *
 * **配色跟 App 走，不跟系统走**：读 DataStore 里的主题设置，
 * 「手动浅色 / 手动深色 / 跟随系统」三档都按 App 的实际结果来 ——
 * 所以 `res/values/colors.xml` 里深浅两套颜色是并列写的，
 * 而不是用 `values-night` 覆盖（那个只认系统的深色开关，会把
 * 「App 锁浅色 + 系统深色」这种组合弄成黑底黑字）。
 * 主色还会跟着用户在设置里选的配色档位 / 自定义色相一起变。
 */
object WidgetUpdater {

    private const val TAG = "YuewenWidget"

    /**
     * PendingIntent 的 requestCode。
     *
     * ⚠️ 两条标题的 requestCode **必须不同**：PendingIntent 的复用判据是
     * `Intent.filterEquals`，而它**不比较 extras** —— 两个只有 extra 不同的
     * Intent 会被当成同一个，于是点第二条标题会打开第一条那篇。
     * 这和通知栏那个「点摘要跳进上次朗读那篇」是同一类坑。
     * 这里除了 requestCode 不同，还给每个 Intent 单独 `setData`（filterEquals 会看 data），
     * 两重保险。
     */
    private const val REQ_OPEN_APP = 40
    private const val REQ_TITLE_1 = 41
    private const val REQ_TITLE_2 = 42

    private const val PI_FLAGS =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 刷新请求队列。
     *
     * 用 **CONFLATED** 而不是「忙就丢掉」：并发请求会被合并成一次，
     * 而**最后一次一定会被执行**。列表下拉刷新时会连着写几百条进库，
     * 如果不合并，就会变成「每个批次都画一遍桌面」；
     * 如果简单丢弃，又可能把最后那次（也就是数据最终形态）丢掉，
     * 桌面停在中间状态 —— 表现是「刷新完了卡片上的数字还是旧的」。
     */
    private val requests = Channel<YuewenApplication>(Channel.CONFLATED)

    init {
        scope.launch {
            for (app in requests) {
                runCatching { render(app) }
                    .onFailure { Log.w(TAG, "刷新桌面小组件失败", it) }
            }
        }
    }

    /**
     * 重画所有尺寸的所有实例。任何线程都可以调；不阻塞调用方。
     *
     * @param onDone 请求入队后立刻回调。给 `goAsync()` 用 ——
     *   广播不能等真正画完（查库要时间），但也不能不解散，否则系统会记一次 ANR。
     */
    fun refreshAll(context: Context, onDone: (() -> Unit)? = null) {
        (context.applicationContext as? YuewenApplication)?.let { requests.trySend(it) }
        onDone?.invoke()
    }

    // ------------------------------------------------------------------ 渲染

    private data class Target(val component: ComponentName, val layout: Int)

    private fun targets(ctx: Context) = listOf(
        Target(ComponentName(ctx, YuewenSmallWidgetProvider::class.java), R.layout.widget_small),
        Target(ComponentName(ctx, YuewenWideWidgetProvider::class.java), R.layout.widget_wide)
    )

    private suspend fun render(app: YuewenApplication) {
        val mgr = AppWidgetManager.getInstance(app)

        // 今天的起点用和每日摘要完全相同的那套算法（含时区 / 夏令时处理），
        // 不另起一套 —— 两处口径不一致的话，会出现「通知说今天 3 条、卡片说 2 条」。
        val facts = runCatching {
            app.newsRepository.widgetFacts(DailyDigest.todayStart(System.currentTimeMillis()))
        }.getOrElse {
            Log.w(TAG, "取小组件数据失败", it)
            WidgetSnapshot.Facts()
        }

        val dark = resolveDark(app)
        val accent = accentColor(app, dark)

        for (target in targets(app)) {
            val ids = runCatching { mgr.getAppWidgetIds(target.component) }
                .getOrDefault(IntArray(0))
            for (id in ids) {
                val views = build(app, target.layout, facts, dark, accent)
                runCatching { mgr.updateAppWidget(id, views) }
                    .onFailure { Log.w(TAG, "更新小组件 $id 失败", it) }
            }
        }
    }

    private fun build(
        ctx: Context,
        layout: Int,
        facts: WidgetSnapshot.Facts,
        dark: Boolean,
        accent: Int
    ): RemoteViews {
        val views = RemoteViews(ctx.packageName, layout)

        // 底色：两张圆角 drawable 二选一。
        // 为什么不用 `setInt(root, "setBackgroundResource", …)`：那条走的是
        // RemoteViews 的反射白名单，API 31+ 上方法没被 @RemotableViewMethod 标注
        // 就会被拦下（而且是在 launcher 进程里抛，自己 App 的 logcat 里未必看得到）。
        // `setImageViewResource` 是一等公民方法，稳。
        views.setImageViewResource(
            R.id.widget_bg,
            if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light
        )

        val textColor = ctx.getColor(
            if (dark) R.color.widget_text_dark else R.color.widget_text_light
        )
        val dimColor = ctx.getColor(
            if (dark) R.color.widget_dim_dark else R.color.widget_dim_light
        )

        views.setTextColor(R.id.widget_brand, accent)
        views.setTextColor(R.id.widget_count, textColor)
        views.setTextColor(R.id.widget_count_label, dimColor)

        // 「阅闻」「未读」这两个固定文案写在布局里（@string/widget_*），
        // 这里不覆盖 —— 同一条文案两处各写一份，改的时候必然漏一处。
        if (layout == R.layout.widget_small) {
            val s = WidgetSnapshot.small(facts)
            views.setTextViewText(R.id.widget_count, s.unreadText)
            views.setTextViewText(R.id.widget_sub, s.sub)
            views.setTextColor(R.id.widget_sub, dimColor)
        } else {
            val w = WidgetSnapshot.wide(facts)
            views.setTextViewText(R.id.widget_count, w.unreadText)
            views.setTextViewText(R.id.widget_meta, w.meta)
            views.setTextColor(R.id.widget_meta, dimColor)

            views.setTextViewText(R.id.widget_title1, w.title1)
            views.setTextColor(R.id.widget_title1, textColor)
            views.setTextViewText(R.id.widget_title2, w.title2)
            views.setTextColor(R.id.widget_title2, textColor)
            views.setViewVisibility(
                R.id.widget_title2,
                if (w.showTitle2) View.VISIBLE else View.GONE
            )

            // 有对应文章就点进那一篇；没有（「去订阅几个源…」那句引导）就退回打开 App。
            views.setOnClickPendingIntent(
                R.id.widget_title1,
                w.link1?.let { openArticleIntent(ctx, it, REQ_TITLE_1) } ?: openAppIntent(ctx)
            )
            views.setOnClickPendingIntent(
                R.id.widget_title2,
                w.link2?.let { openArticleIntent(ctx, it, REQ_TITLE_2) } ?: openAppIntent(ctx)
            )
        }

        // 整张卡片兜底可点。子 View 上没挂点击时，事件会冒泡到这里。
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(ctx))
        return views
    }

    // ------------------------------------------------------------------ 点击

    /**
     * 启动入口。
     *
     * ⚠️ 不能写死 `MainActivity`：这个 App 的桌面入口是 manifest 里的
     * **activity-alias**（Alias0..Alias7，用来实现「运行时改桌面图标名字」），
     * 直接点名 MainActivity 虽然也能起来，但拿不到用户当前选的那个 alias 语义。
     * 问系统「本包的启动 Intent 是什么」最准。
     */
    private fun launchComponent(ctx: Context): ComponentName? =
        runCatching { ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.component }
            .getOrNull()

    private fun openAppIntent(ctx: Context): PendingIntent {
        val component = launchComponent(ctx)
        val intent = if (component != null) {
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
        } else {
            Intent(ctx, MainActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, REQ_OPEN_APP, intent, PI_FLAGS)
    }

    private fun openArticleIntent(ctx: Context, link: String, reqCode: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            // 见 REQ_* 的注释：data 参与 filterEquals 比较，是防 PendingIntent 复用的第二重保险。
            .setData(
                Uri.parse("yuewen://widget/article")
                    .buildUpon()
                    .appendQueryParameter("u", link)
                    .build()
            )
            .putExtra(TtsPlaybackService.EXTRA_OPEN_LINK, link)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, reqCode, intent, PI_FLAGS)
    }

    // ------------------------------------------------------------------ 配色

    /** 和 MainActivity 里的判定保持同一套语义（"light" / "dark" / 其它=跟随系统）。 */
    private suspend fun resolveDark(app: YuewenApplication): Boolean {
        val mode = runCatching { app.settingsRepository.themeFlow.first() }
            .getOrDefault("system")
        return when (mode) {
            "light" -> false
            "dark" -> true
            else -> (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
    }

    /**
     * 主色。
     *
     * 「青绿」是默认档，它在 App 里走的是 Color.kt 那套**手工调过的色值**
     * （见 Theme.kt 的 yuewenColorScheme），不是生成器算的 —— 这里必须照抄这个分支，
     * 否则默认档的卡片会跟 App 里差一个色阶。其余档位与「自定义」都走生成器，
     * 和 App 里是同一段纯函数，结果必然一致。
     */
    private suspend fun accentColor(app: YuewenApplication, dark: Boolean): Int {
        val key = runCatching { app.settingsRepository.themePaletteFlow.first() }
            .getOrDefault("emerald")
        val palette = ThemePalette.of(key)
        if (palette == ThemePalette.Emerald) {
            return app.getColor(
                if (dark) R.color.widget_accent_dark else R.color.widget_accent_light
            )
        }
        val hue = runCatching { app.settingsRepository.customHueFlow.first() }
            .getOrDefault(DEFAULT_CUSTOM_HUE)
        val sat = runCatching { app.settingsRepository.customSatFlow.first() }
            .getOrDefault(DEFAULT_CUSTOM_SAT)
        return generateScheme(seedFor(palette, hue, sat), dark).primary.toInt()
    }
}
