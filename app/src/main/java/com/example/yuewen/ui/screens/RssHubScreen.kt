package com.example.yuewen.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.rss.FeedProbe
import com.example.yuewen.data.rss.RssHubCatalog
import com.example.yuewen.data.rss.RssHubParamKind
import com.example.yuewen.data.rss.RssHubRoute
import com.example.yuewen.data.rss.buildRssHubUrl
import com.example.yuewen.data.rss.isUsableRssHubInstance
import com.example.yuewen.data.rss.normalizeRssHubInstance
import com.example.yuewen.data.rss.rssHubDefaultName
import com.example.yuewen.data.rss.rssHubHost
import com.example.yuewen.ui.viewmodel.SourcesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RSSHub 订阅页（v2.1）。
 *
 * ## 这一页解决的问题
 *
 * 阅闻原来只能订「本来就提供 RSS 的网站」。微博、知乎、B站、小红书、抖音这些平台**不提供 RSS**，
 * 想让它们进列表，生态里的通用做法是走 **RSSHub**：一个把网站转成 RSS 的开源服务。
 *
 * 它的用法本身极简单 —— **实例地址 + 路由 = 订阅地址**，例如
 * `https://rsshub.app` + `/weibo/user/1234567`。门槛在于：得知道路由长什么样、
 * 参数去哪找、地址怎么拼、拼完到底有没有效。这一页就是把这几件事做掉：
 *
 * 1. **模板**：把 29 条高频路由做成可点的条目，用户只需要填一个参数；
 * 2. **参数提示**：每条都写清「这个 ID 去哪复制」—— 这是最容易卡死的地方；
 * 3. **先看再订**：填完自动探测一遍，把源名、格式、文章数、最新几条标题摆出来，
 *    路由失效或被限流会当场看见，不会悄悄订进来一个空源。
 *
 * 另外两条保命设计：
 * - **自定义路由**：模板只有 29 条，RSSHub 却有上千条。用户可以直接填 `/xxx/yyy`
 *   （甚至粘整条地址），于是这个功能天然覆盖全部路由，也不怕内置模板哪天过时；
 * - **实例可切换**：官方公共实例限流，官方自己都说别长期依赖。自建实例填一行就切过去。
 */
@Composable
fun RssHubScreen(app: YuewenApplication, onBack: () -> Unit) {
    val vm: SourcesViewModel = viewModel(factory = SourcesViewModel.provide(app))
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 订阅状态要跟着变更重组（「已订阅」徽标）
    val sources by vm.sources.collectAsStateWithLifecycle()
    val instance by vm.rssHubInstance.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<RssHubDraft?>(null) }
    var editingInstance by remember { mutableStateOf(false) }

    val subscribed = remember(sources) {
        sources.map { it.url.trim().trimEnd('/').lowercase() }.toHashSet()
    }
    fun isSub(url: String) = url.trim().trimEnd('/').lowercase() in subscribed

    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    val all = RssHubCatalog.byPlatform(platform)
    val list = remember(platform, query) {
        val k = query.trim()
        if (k.isEmpty()) all else all.filter {
            it.title.contains(k, true) ||
                    it.platform.contains(k, true) ||
                    it.desc.contains(k, true) ||
                    it.path.contains(k, true)
        }
    }
    // 没筛选时按平台分组显示（有分组标题更好找）；一筛选就平铺（结果少，标题反而碍事）
    val grouped = query.isBlank() && platform == null

    /** 收起键盘 + 取消焦点：输完参数得让用户看清下面的检测结果。 */
    fun dismissIme() {
        keyboard?.hide()
        focus.clearFocus()
    }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        // ---------------------------------------------------------- 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = cs.onSurface)
            }
            Column {
                Text("RSSHub 订阅", style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
                Text(
                    "把微博 / 知乎 / B站这些没有 RSS 的站点，变成你的阅源",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(color = cs.outlineVariant)

        // ---------------------------------------------------------- 搜索
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                // placeholder 不受 singleLine 管辖，必须自己限一行，否则文字一多就把输入框顶成两行高
                placeholder = {
                    Text(
                        "搜平台或用途，如 微博 / 热榜 / bilibili",
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = cs.onSurfaceVariant) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清空",
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                    disabledContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // 回车 = 收键盘。以前写过空实现，结果键盘赖着不走，只能按系统返回键
                keyboardActions = KeyboardActions(onSearch = { dismissIme() })
            )
        }

        // ---------------------------------------------------------- 平台筛选
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = platform == null, onClick = { platform = null }, label = { Text("全部") })
            RssHubCatalog.platforms.forEach { p ->
                FilterChip(
                    selected = platform == p,
                    // 再点一次已选中的 = 取消筛选，省得专门去点「全部」
                    onClick = { platform = if (platform == p) null else p },
                    label = { Text(p) }
                )
            }
        }

        // ---------------------------------------------------------- 列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)
        ) {
            // 实例那一行放进列表（不钉在顶上），空间紧张时让它先滚走
            item(key = "instance") {
                InstanceBar(instance = instance, onChange = { editingInstance = true })
            }

            // 自定义路由：二十几条模板之外的全都从这走
            item(key = "custom") {
                CustomRouteCard(
                    onClick = {
                        dismissIme()
                        draft = RssHubDraft.custom()
                    }
                )
            }

            if (grouped) {
                RssHubCatalog.platforms.forEach { p ->
                    val group = all.filter { it.platform == p }
                    if (group.isEmpty()) return@forEach
                    item(key = "head_$p") {
                        Text(
                            "$p · ${group.size} 条",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }
                    items(group, key = { it.id }) { route ->
                        RouteRow(
                            route = route,
                            // 分组视图和筛选视图的判定必须一致，否则会出现
                            // 「筛一下才显示已订阅」这种自相矛盾的现象
                            subscribed = !route.needsParam && isSub(buildRssHubUrl(instance, route.path))
                        ) {
                            dismissIme()
                            draft = RssHubDraft.of(route)
                        }
                    }
                }
            } else {
                if (list.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "没找到匹配的路由。换个词试试，或者用上面的「自定义路由」自己填一条。",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
                items(list, key = { it.id }) { route ->
                    RouteRow(
                        route = route,
                        // 不要参数的（榜单类）地址是固定的，能直接判断订没订过；
                        // 要填 ID 的没法反推，就不瞎标，等对话框里填完再判
                        subscribed = !route.needsParam && isSub(buildRssHubUrl(instance, route.path))
                    ) {
                        dismissIme()
                        draft = RssHubDraft.of(route)
                    }
                }
            }

            item(key = "tail") {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp)) {
                    Text(
                        "这些路由来自 RSSHub 官方，个别平台改版后可能会失效 —— 但每条在订阅前都能先「测试」，" +
                                "失效会当场看见。想找更多路由（一共上千条）：",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { openUrl(context, RssHubCatalog.DOCS) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("打开路由大全", color = cs.primary)
                        }
                        TextButton(onClick = { openUrl(context, RssHubCatalog.REPO) }) {
                            Text("RSSHub 开源仓库", color = cs.primary)
                        }
                    }
                    Text(
                        "一句话用法：把「路由」接在实例地址后面就是订阅地址，例如\n" +
                                "${rssHubHost(instance)}/weibo/user/1234567",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------- 弹窗
    draft?.let { d ->
        RouteDraftDialog(
            vm = vm,
            draft = d,
            instance = instance,
            isSubscribed = ::isSub,
            onDismiss = { draft = null },
            onDone = { draft = null }
        )
    }

    if (editingInstance) {
        InstanceDialog(
            current = instance,
            onDismiss = { editingInstance = false },
            onSave = { raw -> scope.launch { vm.setRssHubInstance(raw) } },
            onReset = { scope.launch { vm.resetRssHubInstance() } }
        )
    }
}

/** 用系统浏览器打开地址；没有可用的应用时给一句提示，而不是静默失败。 */
private fun openUrl(context: Context, url: String) {
    val ok = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!ok) Toast.makeText(context, "没有可以打开链接的应用", Toast.LENGTH_SHORT).show()
}

/**
 * 复制到剪贴板。
 *
 * 用系统的 [ClipboardManager] 而不是 Compose 的 `LocalClipboardManager`：
 * 后者在更新的 Compose 版本里换了 API，用老写法会留下弃用告警 ——
 * 这个项目一直保持「零警告」，不想为了复制一行字破例。
 */
private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (cm == null) {
        Toast.makeText(context, "复制失败，可以长按地址手动选中", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching { cm.setPrimaryClip(ClipData.newPlainText("阅闻", text)) }
        .onSuccess { Toast.makeText(context, "地址已复制", Toast.LENGTH_SHORT).show() }
        .onFailure { Toast.makeText(context, "复制失败，可以长按地址手动选中", Toast.LENGTH_SHORT).show() }
}

// ============================================================ 实例

@Composable
private fun InstanceBar(instance: String, onChange: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val usable = isUsableRssHubInstance(instance)
    val isDefault = normalizeRssHubInstance(instance) == RssHubCatalog.DEFAULT_INSTANCE

    Surface(
        color = cs.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Dns, contentDescription = null, tint = cs.primary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("实例地址", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Text(
                        rssHubHost(instance),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (usable) cs.onSurface else cs.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onChange) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = cs.primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("切换", color = cs.primary)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    !usable -> "这个地址看起来不对，点「切换」改成正确的实例地址。"
                    isDefault -> "正在用官方公共实例。它是给大家白用的，有请求频率限制（大约每小时 200 次），" +
                            "也不保证随时可用；自己搭一个会更稳。"
                    else -> "正在用你自己的实例。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (usable) cs.onSurfaceVariant else cs.error
            )
        }
    }
}

@Composable
private fun InstanceDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(current.ifBlank { RssHubCatalog.DEFAULT_INSTANCE }) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RSSHub 实例") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("实例地址") },
                    placeholder = { Text("https://rsshub.app") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        focus.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "填域名就行，没写 https:// 会自动补上。\n\n" +
                            "为什么可以换：官方公共实例是给大家白用的，有频率限制，也不保证一直在线。" +
                            "自己有服务器的话可以用 Docker 或 Vercel 一键部署一个（RSSHub 官网有部署文档），" +
                            "把地址填进来，速度和稳定性都会好很多。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
                TextButton(onClick = {
                    keyboard?.hide()
                    focus.clearFocus()
                    onReset()
                    onDismiss()
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = cs.primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("恢复默认（官方公共实例）", color = cs.primary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    keyboard?.hide()
                    focus.clearFocus()
                    onSave(text)
                    onDismiss()
                },
                enabled = isUsableRssHubInstance(text)
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ============================================================ 自定义路由入口

@Composable
private fun CustomRouteCard(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Terminal, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "自定义路由",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onPrimaryContainer
                )
                Text(
                    "上面这些找不到？直接填路由，覆盖 RSSHub 全部上千条",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            Icon(Icons.Filled.Hub, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
    }
}

// ============================================================ 一行路由

@Composable
private fun RouteRow(route: RssHubRoute, subscribed: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(route.title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                if (route.fragile) {
                    Spacer(Modifier.width(6.dp))
                    Pill("反爬强", cs.tertiaryContainer, cs.onTertiaryContainer)
                }
                if (subscribed) {
                    Spacer(Modifier.width(6.dp))
                    Pill("已订阅", cs.primaryContainer, cs.onPrimaryContainer)
                }
            }
            if (route.desc.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    route.desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (route.needsParam) "要填 ID" else "直接订阅",
            style = MaterialTheme.typography.labelSmall,
            color = cs.primary
        )
    }
    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
}

/** 小圆角标签（「反爬强」/「已订阅」这类）。 */
@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

// ============================================================ 填参数 / 测 / 订

/**
 * 一次订阅动作的全部信息。
 *
 * 模板与自定义路由共用同一个对话框：[route] 为 null 就表示「用户自己填路径」。
 * 差别只有「输入框填的是什么」这一点，没必要写两个几乎一样的对话框。
 */
private data class RssHubDraft(val route: RssHubRoute?) {
    val isCustom: Boolean get() = route == null
    val header: String get() = route?.let { "${it.platform} · ${it.title}" } ?: "自定义路由"
    val platform: String get() = route?.platform ?: "RSSHub"
    val paramLabel: String get() = if (isCustom) "路由路径" else route?.paramLabel.orEmpty().ifBlank { "参数" }
    val paramExample: String get() = if (isCustom) CUSTOM_EXAMPLE else route?.paramExample.orEmpty()
    val paramHint: String get() = if (isCustom) CUSTOM_HINT else route?.paramHint.orEmpty()
    val kind: RssHubParamKind get() = route?.kind ?: RssHubParamKind.Text
    val fragile: Boolean get() = route?.fragile == true

    /** 要不要填参数：自定义路由必须填；模板看路径里有没有 `{q}`。 */
    val needsParam: Boolean get() = if (isCustom) false else route?.needsParam == true

    companion object {
        const val CUSTOM_EXAMPLE = "weibo/user/1234567"
        const val CUSTOM_HINT =
            "例如 weibo/user/1234567。也可以直接粘整条地址（https://rsshub.app/... 会被自动去掉前缀）；" +
                    "路由末尾的 ?limit=20 这类参数可以保留。"

        fun of(route: RssHubRoute) = RssHubDraft(route)
        fun custom() = RssHubDraft(null)
    }
}

/**
 * 填参数 → 自动探测 → 确认订阅。
 *
 * 「自动探测」是这一页最有价值的一环：路由会随平台改版失效、公共实例还会限流，
 * 用户没法预判。所以在点「订阅」之前先把源名、格式、文章数、最新几条标题摆出来 ——
 * 地址废了、或者抓回来的是完全不相干的东西，当场就能看见。
 */
@Composable
private fun RouteDraftDialog(
    vm: SourcesViewModel,
    draft: RssHubDraft,
    instance: String,
    isSubscribed: (String) -> Boolean,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    // 自定义路由时这一格填的是路径；否则填的是参数
    var input by remember(draft) { mutableStateOf("") }
    var probing by remember(draft) { mutableStateOf(false) }
    var probe by remember(draft) { mutableStateOf<FeedProbe?>(null) }
    var subscribing by remember(draft) { mutableStateOf(false) }

    val url = remember(instance, draft, input) {
        if (draft.isCustom) buildRssHubUrl(instance, input) else buildRssHubUrl(instance, draft.route!!.path, input)
    }
    val ready = if (draft.isCustom) input.trim().trim('/').contains('/') else !draft.needsParam || input.isNotBlank()
    val duplicate = ready && isSubscribed(url)
    val fallbackName = if (draft.isCustom) {
        "RSSHub · " + input.trim().trim('/').substringBefore('/').ifBlank { "自定义" }
    } else {
        rssHubDefaultName(draft.route!!, input)
    }

    /** 收起键盘 + 取消焦点。 */
    fun dismissIme() {
        keyboard?.hide()
        focus.clearFocus()
    }

    // 输入稳下来 900ms 再去探一次：一边打 UID 一边探测会把（本来就限流的）公共实例打爆
    LaunchedEffect(url, ready) {
        probe = null
        if (!ready) {
            probing = false
            return@LaunchedEffect
        }
        probing = true
        try {
            delay(900)
            probe = vm.probeUrl(url)
        } finally {
            // 在 finally 里复位：否则用户连续输入时，上一次被取消的协程会把「测试中」
            // 这个状态永远留在界面上（转圈转到天荒地老）
            probing = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft.header) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.trim() },
                    label = { Text(draft.paramLabel) },
                    placeholder = { Text(draft.paramExample) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when {
                            draft.isCustom -> KeyboardType.Uri
                            draft.kind == RssHubParamKind.Number -> KeyboardType.Number
                            else -> KeyboardType.Text
                        },
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { dismissIme() }),
                    modifier = Modifier.fillMaxWidth()
                )

                if (draft.paramHint.isNotBlank()) {
                    Text(draft.paramHint, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                if (draft.fragile) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = cs.tertiary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "这个平台反爬较强，公共实例经常取不到。失败的话可以自己搭一个实例再试。",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.tertiary
                        )
                    }
                }

                // 拼出来的地址：让「实例 + 路由」这件事看得见，而不是黑箱
                if (ready) {
                    Surface(color = cs.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("订阅地址", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                Text(
                                    url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { copyToClipboard(context, url) }) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = "复制地址",
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                when {
                    !ready -> Text(
                        if (draft.isCustom) {
                            "填一条带 / 的路由，例如 ${RssHubDraft.CUSTOM_EXAMPLE}"
                        } else {
                            "填上${draft.paramLabel}，下面会立刻测一遍能不能用"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )

                    probing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = cs.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("正在测试这个地址…", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }

                    probe != null && probe!!.error == null -> RouteProbeCard(probe!!)

                    probe != null -> InfoSurface(
                        bg = cs.errorContainer,
                        icon = Icons.Filled.Warning,
                        iconTint = cs.onErrorContainer,
                        text = probe?.error ?: "测试失败：网络异常或地址无法访问",
                        textColor = cs.onErrorContainer
                    )

                    else -> Unit
                }

                if (duplicate) {
                    InfoSurface(
                        bg = cs.tertiaryContainer,
                        icon = Icons.Filled.Info,
                        iconTint = cs.onTertiaryContainer,
                        text = "这个地址已经在你的阅源里了，不用重复添加。",
                        textColor = cs.onTertiaryContainer
                    )
                }

                Text(
                    "订阅后会归到「${draft.platform}」分类，之后可以在「阅源 → 我的阅源」里改名字和分类。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    dismissIme()
                    scope.launch {
                        subscribing = true
                        // 名字优先用源自己给的（更准），探测失败才退回拼出来的名字
                        val name = probe?.title?.takeIf { it.isNotBlank() } ?: fallbackName
                        val n = vm.addRssHubSource(name, url, draft.platform)
                        subscribing = false
                        val msg = when {
                            n < 0 -> "这个地址已经在你的阅源里了"
                            n > 0 -> "已订阅「$name」，拉到 $n 篇文章"
                            else -> "已添加「$name」，但暂时没拉到内容 —— 这条路由可能已经失效"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
                enabled = ready && !duplicate && !subscribing
            ) {
                if (subscribing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                } else {
                    // 测失败也允许订阅：公共实例偶尔抽风（限流），过一会儿可能就好了，
                    // 硬拦着反而更烦；用「仍然订阅」这种文案把风险说在明处
                    Text(
                        when {
                            duplicate -> "已经添加过了"
                            probe?.error != null -> "仍然订阅"
                            else -> "订阅"
                        }
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 测试通过后的预览卡：源名 / 格式 / 文章数 / 最新几条标题。 */
@Composable
private fun RouteProbeCard(probe: FeedProbe) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("地址可用", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
                Spacer(Modifier.weight(1f))
                Text(
                    "${probe.kind} · ${probe.count} 篇",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                probe.title.ifBlank { "（这个源没给出名称，将用参数拼一个）" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onPrimaryContainer
            )
            if (probe.samples.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = cs.onPrimaryContainer.copy(alpha = 0.18f))
                Spacer(Modifier.height(6.dp))
                probe.samples.forEachIndexed { i, t ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onPrimaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.width(14.dp)
                        )
                        Text(
                            t,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** 一行提示条（错误 / 重复提醒都用它，省得各写一遍）。 */
@Composable
private fun InfoSurface(bg: Color, icon: ImageVector, iconTint: Color, text: String, textColor: Color) {
    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = textColor)
        }
    }
}
