package com.example.yuewen.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yuewen.BuildConfig
import com.example.yuewen.YuewenApplication
import com.example.yuewen.ui.util.AppLinks
import com.example.yuewen.ui.util.titleOrDefault

/** 小圆点前缀的要点行，替代纯 "· " 文本，视觉上更整齐。 */
@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text("· ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun TechRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 统一的信息卡片：形状跟随主题 YuewenShapes.large。 */
@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Surface(
        color = container,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/**
 * 「官网 / 开源地址」这一类外链行（v2.0.2）。
 *
 * 地址还没填（[url] 为空）时不做一个「点了没反应的死链」，
 * 而是显示占位文案 + 一句说明 —— 用户知道是还没上线，而不是 App 坏了。
 */
@Composable
private fun LinkRow(label: String, url: String, emptyText: String) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val ready = url.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!ready) {
                    Toast.makeText(context, "这个地址还没上线，之后再回来看看", Toast.LENGTH_SHORT).show()
                    return@clickable
                }
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(context, "没有可以打开链接的应用", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            // 显示时把 http(s):// 和结尾斜杠去掉，看着干净；点的时候仍然用完整地址
            if (ready) url.substringAfter("://").trimEnd('/') else emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ready) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun AboutScreen(app: YuewenApplication, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // 用户可以在「设置 → 个性」里改名，这一页也要跟着变（否则「关于阅闻」和首页标题会对不上）
    val customTitle by app.settingsRepository.appTitleFlow.collectAsStateWithLifecycle("")
    val name = titleOrDefault(customTitle)

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = cs.onSurface)
            }
            Text(
                "关于$name",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            // 名称 + 版本
            Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = cs.onBackground)
            Text(
                "$name · 版本 ${BuildConfig.VERSION_NAME}（Build ${BuildConfig.VERSION_CODE}）",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )

            // 一句话介绍
            InfoCard {
                SectionTitle("应用介绍")
                Text(
                    "$name 是一个本地优先（Local First）的 RSS 阅读器。说得直白一点：把你在各个网站、博客上还想追的内容，" +
                        "统一收进一个干净的列表里，按你自己的节奏读——内容是你选的，数据在你手机上。\n\n" +
                        "底部四个页面：「首页」是你订阅的全部内容，按「今天 / 昨天 / 本周 / 更早」自动分组；" +
                        "「闻件」装一切已经属于你的东西（搜索 / 收藏 / 历史 / 笔记）；" +
                        "「阅源」管订阅源——内置了 36 个分好类的推荐源，挑一组点「整组订阅」，三十秒就有内容，也可以搜索网上的免费 RSS 或粘贴任意地址；" +
                        "「设置」里能调外观、阅读排版、离线阅读、朗读、备份恢复，以及改这个 App 的名字。\n\n" +
                        "读：详情页自动抽取正文全文，不必跳浏览器；左右滑动切换上下篇；字号 / 行距 / 字体 / 底色四项可调，" +
                        "正文里双指捏合还能直接缩放。长文会记住读到哪儿，下次打开接着读；顶栏有文章大纲，小标题一点即达。\n\n" +
                        "听：点喇叭就用系统语音从标题往下念，读到哪段正文自动滚到哪段并高亮，语速四档可调。" +
                        "切到别的页面声音也不断——顶部会浮出一条「正在朗读」的胶囊，点一下直接回到那篇文章；" +
                        "锁屏和通知栏也有控制条，可以暂停、继续、停止。\n\n" +
                        "记：长按正文里任意一段，就能把那句话摘下来存成摘录，顺手再写两句自己的想法。" +
                        "所有摘录和笔记会在「闻件 → 笔记」里汇总，可以搜索、编辑、删除。\n\n" +
                        "整理：首页顶栏的勾选图标进入批量模式，一次可以把几十篇标已读 / 未读、收藏、移动收藏夹或从本地移除。" +
                        "「离线阅读」能让 App 在后台把正文提前抓好（可限定仅 Wi-Fi），出门前点一次「立即缓存全部正文」，路上没信号也能读完。" +
                        "换手机时，「备份与恢复」把订阅源、收藏、摘录笔记和个性化设置打包成一个 JSON 文件，新机器导入即可。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface
                )
            }

            // 功能亮点
            InfoCard(modifier = Modifier.padding(top = 14.dp)) {
                SectionTitle("功能亮点")
                val features = listOf(
                    "闻件：搜索 / 收藏 / 历史 / 笔记四合一，少占一个底栏位置，找回自己的东西只在一处",
                    "阅源：内置 36 个分好类的精选源（可整组订阅）+ 在线搜索免费 RSS + 按关键词生成专属信息流",
                    "文章大纲：自动抽取正文小标题成目录，点一下跳到那一节，长文不再靠盲滚",
                    "摘录与笔记：长按任意一段正文即可摘下来，还能写批注；统一在「闻件 → 笔记」里管理",
                    "批量管理：首页勾选进入多选，一次标已读 / 未读、收藏、移动收藏夹、移除",
                    "锁屏 / 通知栏朗读控制：暂停、继续、停止、一键回到文章；朗读时切走 App 也不会被回收",
                    "回到文章：后台朗读时顶部常驻一条胶囊，显示在念什么，点一下直接跳回那篇",
                    "备份与恢复：订阅源 + 收藏 + 摘录笔记 + 个性化设置打包成 JSON，换手机一键搬（刻意不含正文缓存，所以文件很小）",
                    "自定义名字：应用内名称随便填（首页标题、关于页同步改）；桌面图标名可从 8 个预设里挑",
                    "首页可配置：右上角「布局」「刷新」按钮与副标题都能在设置里单独关掉，顶栏要多干净有多干净",
                    "首页筛选可选：顶栏胶囊能按「分类」筛、按「阅源」筛，或者两行都显示（两级叠加）；打开 App 时默认停在哪儿也能设",
                    "首页排序可选：最新在前 / 最早在前 / 随机 / 按阅源 / 按标题五档；随机档位顺序固定，点「换一批」才重洗，下拉刷新不会打乱列表",
                    "首页搜索入口：顶栏最左边的放大镜点开就是全屏搜索，搜完自动收起键盘；不用先切到「闻件」再找「搜索」那一栏",
                    "首页关键词：设置里填几个自己关心的词（手机 / 汽车 / AI…），首页顶部多出一排胶囊，点一下就只看标题 / 摘要 / 正文含这个词的文章，与分类、阅源三级叠加",
                    "自选浏览器：点「原文」用哪个浏览器可以固定下来（Chrome / Edge / 夸克 / 神马…），默认跟随系统；选定的浏览器被卸载会自动退回系统默认",
                    "统一标签栏：闻件页与阅源页顶部的子页切换，和底部导航同一套胶囊样式（主色高亮块 + 反色文字），点一下滑过去",
                    "内置配色方案：青绿 / 靛蓝 / 海蓝 / 紫罗兰 / 胭脂 / 琥珀 / 森野 / 石墨 八套一键切换，点一下整个界面立刻换色（深浅色各有一套，对比度按 WCAG AA 校准）",
                    "自定义配色：不满足于预设的话，可以自己拖色相（彩虹条）和鲜艳度，从种子色实时推出一整套配色，拖到哪就是哪",
                    "RSSHub 订阅：微博 / 知乎 / B站 / 小红书 / 抖音这些本来没有 RSS 的站点，内置 29 条高频路由模板，填个 ID 就能订进来；配「自定义路由」可覆盖 RSSHub 全部路由，实例地址可切换成自建的",
                    "新手指南：设置 →「使用手册」，讲清产品定位、三分钟上手、四个页面各干什么、常见问题",
                    "三档布局：紧凑 / 卡片 / 杂志一键循环切换，杂志模式通栏大图更像杂志排版",
                    "日期分组：信息流按「今天 / 昨天 / 本周 / 更早」自动分节，扫读更高效",
                    "长按操作：任意文章长按呼出操作面板——已读、收藏、移动分组、分享、复制、浏览器打开",
                    "本地全文搜索：标题 / 摘要 / 正文 / 来源名一起搜，不限分类，命中关键词高亮",
                    "阅读进度记忆：长文读到一半退出，下次打开自动回到上次的位置",
                    "连续阅读：详情页左右滑动切换上下篇，正文顶部显示「第 N / 共 M 篇」，附阅读进度条",
                    "排版自定义：字号四档、行距三档、无衬线 / 衬线可换、三种阅读底色",
                    "双指捏合调字号：正文里两指一捏即可放大缩小，不用再进面板",
                    "离线预加载：可在后台把文章正文提前抓到本机缓存（可勾选仅 Wi-Fi），网络差或完全没网也能读全文",
                    "一键缓存全部正文：出门前点一下，把所有还没有正文的文章抓下来，路上离线照读",
                    "双击回顶：首页双击标题、文章里双击顶栏空白处，立刻回到顶部",
                    "阅读统计：累计已读、连续天数、近 7 天柱状图、来源阅读排行、估算阅读时长",
                    "收藏夹管理：改名、调整顺序、移回默认或整体取消收藏",
                    "缓存管理：图片 / 文章 / 正文三类缓存分别显示占用，想清哪类清哪类",
                    "阅读时可保留底栏：点底栏即可离开文章去别的页面，朗读继续在后台播放（也可关掉底栏做全屏沉浸）",
                    "首页顶栏图标化：仅看未读、全部标为已读收成两个小图标，可在设置里整组隐藏",
                    "下拉刷新只有一处动效：转圈提示统一放在右上角刷新按钮上，列表上方不再浮一个圆环",
                    "富预览测试：粘贴订阅地址即时显示「格式 + 文章数 + 最新三条标题」，并提示是否重复添加",
                    "一键测试全部源：并发把订阅源全测一遍（限流 4 个），结果直接标在每一行下面，并给出「N 个可用 / M 个失败」汇总",
                    "源行内管理：点开任意源即可改名 / 改地址 / 改分类；点行尾的 ↻ 会「检查 + 拉文章」，结果落在那一行下面",
                    "打开 App 自动刷新：默认开启，5 分钟内不重复联网（可在设置里关闭）",
                    "未读徽标：底部导航「首页」实时显示未读条数（超过 99 显示 99+）",
                    "订阅迁移：OPML 文件一键导入导出，兼容扁平与嵌套大纲、自动去重",
                    "智能屏蔽：按来源或关键词过滤不感兴趣的内容",
                    "阅读历史：自动记录读过的文章，随时回看",
                    "订阅发现：粘贴网站首页即可自动发现可订阅的 RSS",
                    "分享卡片：一键生成图文卡片分享给好友"
                )
                features.forEach { Bullet(it) }
            }

            // 技术参数
            InfoCard(modifier = Modifier.padding(top = 14.dp, bottom = 14.dp)) {
                SectionTitle("技术参数")
                TechRow("开发语言", "Kotlin 1.9.24")
                TechRow("UI 框架", "Jetpack Compose（Material 3）")
                TechRow("架构模式", "MVVM + 单向数据流（StateFlow）")
                TechRow("本地数据库", "Room（SQLite，articles + notes 两表，含 5 个版本的迁移）")
                TechRow("偏好存储", "DataStore Preferences")
                TechRow("网络请求", "OkHttp（共享连接池）")
                TechRow("全文搜索", "SQLite LIKE 多字段检索（标题/摘要/正文/来源）")
                TechRow("正文抽取", "Readability4J + jsoup（Mozilla 阅读模式算法）")
                TechRow("XML 解析", "XmlPullParser（RSS / Atom / OPML 共用）")
                TechRow("JSON 序列化", "自建极简实现（备份 / 源搜索共用，可离线单测）")
                TechRow("图片加载", "Coil + 自定义 ImageLoader（浏览器 UA / 缓存）")
                TechRow("后台刷新", "WorkManager")
                TechRow("订阅迁移", "OPML 2.0（SAF 文件选择器读写）")
                TechRow("备份格式", "JSON（订阅源 + 收藏 + 笔记 + 设置；不含正文缓存，KB 级）")
                TechRow("桌面图标名", "activity-alias 切换（系统不允许运行时改 android:label）")
                TechRow("列表布局", "紧凑 / 卡片 / 杂志三档（偏好持久化）")
                TechRow("首页筛选", "分类 × 阅源两级筛选（一条 SQL 覆盖四种组合，顶栏按设置显示一行或两行）")
                TechRow("订阅源去重", "id 由地址派生（稳定唯一）+ 读设置时就地修复历史重复数据")
                TechRow("RSSHub 支持", "实例 + 路由 → 订阅地址；路径按 UTF-8 百分号编码并保留斜杠（支持「作者/仓库名」这类两段式参数）")
                TechRow("阅读排版", "字号 / 行距 / 字体 / 底色四项可调")
                TechRow("文章大纲", "从正文小标题生成目录，段号与朗读 / 滚动共用同一套编号")
                TechRow("手势缩放", "多指捏合检测（单指事件放行给滚动，互不抢占）")
                TechRow("语音朗读", "系统 TextToSpeech，按标点分片续读（每片 ≤ 800 字）")
                TechRow("朗读生命周期", "引擎挂在 Application 上，离开文章不中断（可后台续读）")
                TechRow("朗读暂停", "自行实现：记录分片位置 → 停 → 重发剩余（系统 TTS 无 pause API）")
                TechRow("跟读滚动", "按窗口坐标换算内容坐标 + 舒适区判断（已在可视区就不打扰）")
                TechRow("前台服务", "TtsPlaybackService（mediaPlayback 类型 + MediaStyle 通知）")
                TechRow("朗读语速", "0.8 / 1.0 / 1.25 / 1.5 倍四档（档位持久化）")
                TechRow("离线预加载", "后台批量抓正文写 Room，并发 3 + 间隔限流，可中断")
                TechRow("网络策略", "预加载可限定仅 Wi-Fi（ConnectivityManager 判断）")
                TechRow("阅读统计", "基于本地 readAt 时间戳分桶（java.time 按本地时区）")
                TechRow("缓存分类", "Coil 磁盘/内存缓存 + Room 文章 + 正文全文三类独立")
                TechRow("过渡动画", "底栏胶囊由 Pager 滑动进度连续驱动")
                TechRow("回顶手势", "首页双击标题 / 文章双击顶栏空白（LazyListState / ScrollState 动画滚动）")
                TechRow("抓取策略", "多源并行抓取 + upsert 合并写库（保留阅读状态）")
                TechRow("列表性能", "查询分页限额（400/300/200）+ 后台线程过滤")
                TechRow("构建优化", "Release 包启用 R8 代码压缩与资源裁剪")
                TechRow("离线自测", "tools/jvmtest：解析 / 切块 / 统计 / 正文块 / Json / 备份 135 项断言，无需真机")
                TechRow("最低系统", "Android 8.0（API 26）")
                TechRow("目标系统", "Android 14（API 34）")
                TechRow("包名", "com.example.yuewen")
                TechRow("数据策略", "全部存本地，不上传云端（暂无多设备云同步）")
            }

            // 官网 / 开源地址（v2.0.2）
            // 地址先留空，填在 ui/util/AppLinks.kt 里即可 —— 填上之后这两行自动变可点。
            InfoCard(modifier = Modifier.padding(top = 14.dp)) {
                SectionTitle("官网与开源")
                LinkRow("官网", AppLinks.OFFICIAL_SITE, AppLinks.PLACEHOLDER)
                LinkRow("开源地址", AppLinks.GITHUB_REPO, AppLinks.PLACEHOLDER)
                Text(
                    if (AppLinks.noneReady()) {
                        "两个地址都还在准备中，上线之后这里会变成能点的链接，点一下直接跳浏览器。"
                    } else {
                        "点任意一行即可跳到浏览器打开。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 隐私说明
            InfoCard(container = cs.secondaryContainer, modifier = Modifier.padding(top = 14.dp, bottom = 16.dp)) {
                SectionTitle("隐私与数据")
                Text(
                    "$name 不会上传你的任何阅读数据。订阅源、文章、收藏、摘录笔记、阅读进度与屏蔽设置都只保存在本机数据库中；" +
                        "阅读统计与分享卡片均在设备端生成。导出的备份文件也由你自己选择存放位置，App 不会偷偷传出去。\n\n" +
                        "清除缓存只会删掉未收藏的文章与已缓存正文，不会动你的订阅源、藏书记录与笔记。\n\n" +
                        "为了兼容全网订阅源，应用允许明文 HTTP 连接——这是很多中文源（尤其是它们的图片链接）仍在使用的老协议，" +
                        "不是用于上传数据。「阅源 → 发现」里的在线搜索会把关键词发给公开的订阅源目录服务（Feedly / 必应新闻）；" +
                        "不使用搜索功能时不会有任何对外请求。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSecondaryContainer
                )
            }
            Text(
                "© 2026 $name · 用 ✦ 与 ☕ 制作",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
