package com.example.yuewen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yuewen.BuildConfig
import com.example.yuewen.YuewenApplication
import com.example.yuewen.ui.util.titleOrDefault

/**
 * 新手指南 / 产品定位（v2.0）。
 *
 * 为什么单独做一页而不是塞进「关于」：
 * 「关于」是给已经会用的人看的（版本号、技术参数、隐私声明）；
 * 新手指南是给**第一次打开、还不知道从哪下手**的人看的。
 * 两者受众和语气都不一样，混在一起谁都读不完。
 */
@Composable
fun GuideScreen(app: YuewenApplication, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val customTitle by app.settingsRepository.appTitleFlow.collectAsStateWithLifecycle("")
    val name = titleOrDefault(customTitle)

    // 打开过就算看过了（设置页据此取消小红点）
    LaunchedEffect(Unit) { app.settingsRepository.setGuideSeen(true) }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = cs.onSurface)
            }
            Column {
                Text("使用手册", style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
                Text(
                    "从装好到用顺手，大概三分钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = cs.outlineVariant)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ---------------- 产品定位 ----------------
            GuideCard(title = "这是什么", icon = Icons.Filled.Info) {
                Text(
                    "$name 是一个本地优先的 RSS 阅读器。\n\n" +
                            "说得再直白一点：把你在各个网站、博客、公众号之外还想追的内容，" +
                            "统一收进一个干净的列表里，按你的节奏读。\n\n" +
                            "它和「新闻 App」最大的不同有两点：\n" +
                            "1. 内容是你自己选的。没有推荐算法，没有信息流投喂 —— 你订阅了谁，就只看谁。\n" +
                            "2. 数据在你手机上。订阅、收藏、笔记、阅读进度全部存在本机，" +
                            "不上传任何服务器，断网也能读（配合离线缓存）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface
                )
            }

            GuideCard(title = "它适合谁", icon = Icons.Filled.Lightbulb) {
                GuideBullet("想安静读点东西，不想被「猜你喜欢」牵着走的人")
                GuideBullet("同时追十几个博客 / 站点，开一堆网页标签嫌烦的人")
                GuideBullet("通勤路上想听文章，又不想盯着屏幕的人（朗读可以后台播）")
                GuideBullet("看到好句子想存下来、写两句想法的人（摘录 + 笔记）")
                GuideBullet("担心隐私、不想把阅读记录交给平台的人（全部本地存储）")
            }

            // ---------------- 三分钟上手 ----------------
            GuideCard(title = "三分钟上手", icon = Icons.Filled.Home) {
                StepItem(1, "先选几个源", "打开底部的「阅源」→「发现推荐」，按兴趣挑一组（比如「中文科技」），点「整组订阅」。三十秒就有内容了。")
                StepItem(2, "回首页看内容", "切回「首页」，下拉刷新。文章按「今天 / 昨天 / 本周 / 更早」自动分好组，未读的会高亮。")
                StepItem(3, "点开读 / 让它念", "点任意一条进正文。顶部喇叭图标＝朗读全文，读到哪一段就自动滚到哪一段。")
                StepItem(4, "存下来、划下来", "正文里长按选一段话，可以「摘录」或写笔记；标题下的书签图标＝收藏。")
                StepItem(5, "按自己的习惯调", "「设置」→「阅读与朗读」调字号行距和朗读语速；「外观」换配色方案、列表布局、首页按钮；App 的名字在「个性」里改。")
            }

            // ---------------- 换配色（v2.3） ----------------
            GuideCard(title = "换个颜色", icon = Icons.Filled.Palette) {
                Text(
                    "设置 →「外观 → 配色方案」，八个色块点一下就换，整个界面立刻生效，不用重启。\n\n" +
                            "每套配色都配了浅色和深色两版 —— 跟着系统的深浅模式自动切。" +
                            "文字压在上面的清晰度是按 WCAG 标准算过的，不会出现「白字压黄底看不清」那种情况。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "都不满意的话，最右边那个「自定义」可以自己调：\n\n" +
                            "· 色相 —— 拖彩虹条，拖到红就是红、拖到紫就是紫；\n" +
                            "· 鲜艳度 —— 往左是素净（偏灰），往右是浓郁。\n\n" +
                            "松手才保存，拖动过程中就能看到效果。整套配色是从你选的这个颜色推出来的，" +
                            "不是只换一个主色 —— 背景、卡片、描边都会跟着带一点点同色系的调子，看起来才是一整套。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }

            // ---------------- 搜索 / 关键词（v2.4） ----------------
            GuideCard(title = "找东西：搜索与关键词", icon = Icons.Filled.Search) {
                Text(
                    "首页顶栏最左边那个放大镜就是搜索，点开直接打字 —— 不用先切到「闻件」。\n\n" +
                            "搜的范围是已经存到手机里的文章：标题、摘要、抓取过的正文、来源名都会匹配，" +
                            "不分类别。所以在首页多刷新几次，能搜到的就越多。\n\n" +
                            "搜完敲键盘上的「搜索」、或者点任意一条结果，键盘都会自己收起来。" +
                            "（v2.4 之前它赖着不走，得按系统返回键才行。）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "「关键词」是一条更省事的近路：\n\n" +
                            "在「设置 → 外观 → 首页筛选 → 首页关键词」里填几个你关心的词（比如「手机」「汽车」），" +
                            "首页顶部就会多出一排胶囊。点一下，首页就只剩含这个词的文章，再点一下取消。\n\n" +
                            "它和「分类」「阅源」是叠加关系，三个条件可以一起用。想删掉某个词就在设置里点它一下；" +
                            "把词全删光，那一行会自己消失。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }

            // ---------------- 列表怎么排、原文用谁打开（v2.2） ----------------
            GuideCard(title = "列表排序 / 用哪个浏览器看原文", icon = Icons.Filled.SwapVert) {
                Text(
                    "设置 →「外观 → 首页筛选 → 文章排序」，五档可选：\n\n" +
                            "· 最新在前（默认）—— 最新的文章在最上面，按「今天 / 昨天 / 本周 / 更早」分组；\n" +
                            "· 最早在前 —— 反过来，补着看历史用；\n" +
                            "· 随机 —— 顺序打乱，像刷信息流；点「换一批」重新洗一次；\n" +
                            "· 按阅源 —— 同一个源的文章凑在一起，适合「先把这个源的看完」；\n" +
                            "· 按标题 —— 按标题字典序，记得标题忘了来源时好找。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface
                )
                Spacer(Modifier.height(6.dp))
                GuideBullet("「随机」的顺序是**固定的**：只有点「换一批」才会变。下拉刷新、标为已读都不会把列表打乱")
                GuideBullet("除了按时间的两档，其他档位不会再显示「今天 / 昨天」小标题 —— 因为日期已经是乱的了")
                Spacer(Modifier.height(6.dp))
                Text(
                    "打开原文用哪个浏览器，在设置 →「阅读与朗读 → 打开原文的浏览器」里选。\n\n" +
                            "默认是「跟随系统」（交给安卓自己决定，和以前一样）。装了 Chrome、Edge、夸克、" +
                            "神马好几个的时候，在这里固定一个，正文底部的「原文」和长按菜单里的「在浏览器打开原文」" +
                            "就都走它。中途把那个浏览器卸载了会自动退回系统默认，不会打不开。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface
                )
            }

            // ---------------- 四个页面都在干什么 ----------------
            GuideCard(title = "顶部的标签栏 / 底部的四个页面", icon = Icons.Filled.Bookmarks) {
                Text(
                    "「闻件」页顶部的「搜索 / 收藏 / 历史 / 笔记」、「阅源」页顶部的「我的阅源 / 发现推荐」，" +
                        "用的都是**和底部导航同一种胶囊样式**：圆角胶囊底、选中的那一格是一块主色高亮，" +
                        "文字和图标跟着变白。点一下就切，高亮块会滑过去。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface
                )
                Spacer(Modifier.height(6.dp))
                PageItem(Icons.Filled.Home, "首页", "你订阅的全部内容，按时间分组。顶部图标：批量管理、只看未读、全部已读、切换布局、刷新。")
                PageItem(Icons.Filled.Bookmarks, "闻件", "所有已经属于你的东西：搜索（全文检索本地文章）、收藏、历史、笔记 —— 顶部标签栏切换，停留位置会记住。")
                PageItem(Icons.Filled.RssFeed, "阅源", "订阅源的管理与发现。顶部标签栏两格：「我的阅源」管已订的，「发现推荐」一键订阅精选源。")
                PageItem(Icons.Filled.Settings, "设置", "阅读与朗读、外观、个性（改名 / 图标名）、阅源与刷新、离线阅读、备份恢复、新手指南。")
            }

            // ---------------- RSSHub（v2.1） ----------------
            GuideCard(title = "订微博 / 知乎 / B站（RSSHub）", icon = Icons.Filled.Hub) {
                Text(
                    "微博、知乎、B站、小红书这些平台「本身没有 RSS」，平时是加不进来的。\n\n" +
                            "生态里的通用做法是走 RSSHub —— 一个开源的「转接服务」：它把网站内容抓下来，" +
                            "转成标准 RSS 地址；阅闻负责把这个地址拼对、订进来，后面的分类、收藏、朗读、离线缓存" +
                            "全都和别的源一样。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface
                )
                Spacer(Modifier.height(4.dp))
                StepItem(1, "从「阅源」页进 RSSHub", "点阅源页右上角那个网络图标；「我的阅源」列表上方也有一条常驻入口。")
                StepItem(2, "挑模板、填 ID", "按平台分好了 29 条：微博热搜、知乎热榜、B站 UP 主、小红书笔记、GitHub Trending…… 每条都写清「这个 ID 去哪复制」。")
                StepItem(3, "先测试，再订阅", "填完 ID 会自动测一遍，把源名、格式、文章数和最新几条标题摆出来。确认是你要的内容，再点订阅。")
                StepItem(4, "找不到就用自定义路由", "模板只有二十几条，RSSHub 有一千多条。上面找不到就点「自定义路由」，照官方文档填一段路径 —— 地址也能直接粘进来。")
                Spacer(Modifier.height(4.dp))
                GuideBullet("官方公共实例（rsshub.app）是限流的：抓不到就先等一会儿再试")
                GuideBullet("想更稳就自己搭一个实例（Docker / Vercel 一键部署），地址填一次就切过去")
                GuideBullet("微博 / 知乎 / 抖音 / 小红书反爬较强，失败是常态，不是 App 的问题")
            }

            // ---------------- 小技巧 ----------------
            GuideCard(title = "几个能省事的小技巧", icon = Icons.Filled.Compress) {
                GuideBullet("首页顶栏最左边的放大镜 = 直接搜索，不用先切到「闻件」再找「搜索」那一栏")
                GuideBullet("首页关键词：设置 →「外观 → 首页筛选 → 首页关键词」填几个词，首页点一下胶囊就只筛这类文章（v2.4）")
                GuideBullet("首页顶栏那排胶囊能按「分类」筛、也能按「阅源」筛：设置 →「外观 → 首页筛选」里切换，还能设打开 App 时默认停在哪儿")
                GuideBullet("文章可以按「最新 / 最早 / 随机 / 按阅源 / 按标题」排：设置 →「外观 → 文章排序」")
                GuideBullet("点「原文」用哪个浏览器，可以在设置里固定下来：「阅读与朗读 → 打开原文的浏览器」")
                GuideBullet("看腻了主色就去「外观 → 配色方案」换一套，八套预设 + 自定义色相，点一下整个 App 立刻换色")
                GuideBullet("微博 / 知乎 / B站 这些没有 RSS 的站点，走「阅源 → RSSHub」照样能订进来")
                GuideBullet("双击首页标题 / 文章顶栏空白处 → 立刻回到顶部")
                GuideBullet("正文里双指捏合 → 直接缩放字号，不用进设置")
                GuideBullet("朗读切到别的页面不会停；顶部会出现「回到文章」的浮条，点一下就回去")
                GuideBullet("朗读时通知栏 / 锁屏会有控制条，可以暂停、停止、回到文章")
                GuideBullet("长按任意文章 → 已读、收藏、移动收藏夹、分享、复制链接、浏览器打开")
                GuideBullet("首页顶栏勾选图标 → 批量模式，一次处理几十篇")
                GuideBullet("出门前：设置 →「离线阅读」→「立即缓存全部正文」，路上没信号也能读全文")
                GuideBullet("换手机：设置 →「备份与恢复」导出，新机器导入即可（订阅 + 收藏 + 笔记 + 设置一起走）")
            }

            // ---------------- FAQ ----------------
            GuideCard(title = "常见问题", icon = Icons.Filled.Info) {
                Faq("首页是空的，怎么办？", "说明还没有拉到内容。两个办法：① 去「阅源 → 发现推荐」一键订阅；② 在首页下拉刷新。首次刷新要同时抓多个源，等几秒。")
                Faq("某个源一直不出内容？", "去「阅源 → 我的阅源」点它右边那个 ↻：它会检查这个源还能不能连上，同时顺手拉一遍新文章，结果直接显示在那一行下面。很多站点改版后会悄悄停掉 RSS，确认挂了就删掉或换一个。")
                Faq("微博 / 知乎订阅不了？", "这些平台本身不提供 RSS，得走 RSSHub：「阅源」页 → RSSHub → 挑模板填 ID → 先测试再订阅。测不出来多半是公共实例限流或平台反爬，等一会儿再试，或者自己搭一个实例把地址换掉。")
                Faq("RSSHub 那些 ID 去哪找？", "每条模板下面都写了去哪复制。常说的几个：微博 UID 在主页地址 weibo.com/u/ 后面；B站 UP 主 UID 在 space.bilibili.com/ 后面；知乎用户是 zhihu.com/people/ 后面那一段；GitHub 仓库直接填「作者/仓库名」。")
                Faq("怎么一次找出失效的源？", "「阅源 → 我的阅源」右上角有「测试全部」：一次把几十个源都体检一遍，结果直接标在每一行下面（能不能连上、什么格式、多少篇），最后还给你一句「N 个可用、M 个失败」。")
                Faq("朗读点了没声音？", "系统里需要装一个中文语音引擎（多数手机自带）。第一次点可能会提示「引擎还在准备中」，再点一次就好。也检查一下手机是不是静音了。")
                Faq("怎么让它停下来？", "四种方式：文章页的停止按钮、顶部朗读浮条的 ✕、通知栏的「停止」、或者让它自己念完。")
                Faq("文章里的图片不显示？", "多半是源站图片走了 http 或做了防盗链。App 已经尽量兼容；个别图挂了不影响读正文。")
                Faq("离线真的能读吗？", "能，但要先缓存过。开着「自动预加载正文」，或者出门前点一次「立即缓存全部正文」。缓存过的文章断网也能读全文。")
                Faq("我的数据会上传吗？", "不会。订阅源、文章、收藏、笔记、阅读记录全部只存在本机数据库里。清除缓存只会删未收藏的文章与已缓存正文。")
                Faq("能改成别的名字吗？", "应用内显示的名字（首页标题、关于页）可以随便填；桌面图标上的名字受系统限制，只能在设置里从几个预设里挑。")
            }

            Text(
                "$name v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

// ------------------------------------------------------------------ 零件

@Composable
private fun GuideCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun GuideBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text("· ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StepItem(index: Int, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Surface(color = cs.primary, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.size(20.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "$index",
                    color = cs.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun PageItem(icon: ImageVector, name: String, desc: String) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Faq(q: String, a: String) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Q：$q", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Spacer(Modifier.height(3.dp))
        Text("A：$a", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}
