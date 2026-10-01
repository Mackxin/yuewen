package com.example.yuewen.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.rss.FeedCandidate
import com.example.yuewen.data.rss.FeedProbe
import com.example.yuewen.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 添加阅源：**只需要粘贴一个 RSS 地址**。
 *
 * 升级点：检测结果不再是「可用 / 不可用」两个状态，而是一张富预览卡——
 * 源名称、格式（RSS / Atom）、文章总数、最新 3 条标题，
 * 让你「先试读再订阅」，避免加进来才发现是个空源或无关站点。
 * 名称由订阅源自己提供，不用手填；分类之后在「阅源 → 我的阅源」里统一改。
 */
@Composable
fun AddSourceScreen(app: YuewenApplication, onBack: () -> Unit) {
    val vm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = SettingsViewModel.provide(app))
    val sources by vm.sources.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // 「完成」键负责收键盘：以前 onDone 是个空实现，导致输完地址键盘赖着不走，
    // 只能去按系统返回键才行 —— 这就是「输入法无法取消」的原因。
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    var url by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<FeedProbe?>(null) }
    var probeError by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<FeedCandidate>>(emptyList()) }
    var discovering by remember { mutableStateOf(false) }

    val cs = MaterialTheme.colorScheme
    val urlOk = url.startsWith("http://") || url.startsWith("https://")
    val ok = probe != null && probeError == null
    val duplicate = ok && vm.isDuplicateUrl(url)

    /** 收起键盘 + 取消焦点，让用户看清下面的检测结果。 */
    fun dismissIme() {
        keyboard?.hide()
        focus.clearFocus()
    }

    // 地址变化后自动检测（防抖 700ms）：能不能用 + 叫什么名字 + 有哪些内容
    LaunchedEffect(url) {
        candidates = emptyList()
        probe = null
        probeError = null
        if (url.isBlank() || !urlOk) return@LaunchedEffect
        delay(700)
        checking = true
        val p = vm.probeSource(url)
        checking = false
        if (p.error == null) probe = p else probeError = p.error
    }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = cs.onSurface)
            }
            Text("添加阅源", style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
        }
        HorizontalDivider(color = cs.outlineVariant)

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.trim() },
                label = { Text("粘贴 RSS / Atom 地址") },
                placeholder = { Text("https://sspai.com/feed") },
                singleLine = true,
                isError = probeError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                // Done 一定要真的「完成」：收起键盘 + 取消焦点
                keyboardActions = KeyboardActions(onDone = { dismissIme() }),
                trailingIcon = {
                    IconButton(onClick = {
                        val t = clipboard.getText()?.text?.trim().orEmpty()
                        if (t.isNotBlank()) {
                            url = t
                            dismissIme()
                        }
                    }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "粘贴", tint = cs.onSurfaceVariant)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            when {
                checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("正在检测地址…", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }

                ok -> ProbeCard(probe!!, duplicate = duplicate)

                probeError != null -> Surface(
                    color = cs.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = cs.onErrorContainer, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            probeError!!,
                            color = cs.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                !urlOk && url.isNotBlank() -> Text(
                    "地址要以 http:// 或 https:// 开头",
                    color = cs.error,
                    style = MaterialTheme.typography.bodySmall
                )

                else -> Text(
                    "粘贴订阅地址后会自动检测，并显示这个源的名字和最新几篇文章。",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 首页也能自动找出订阅地址
            TextButton(
                onClick = {
                    dismissIme()
                    if (url.isBlank()) { probeError = "先把网站地址填到上面"; return@TextButton }
                    scope.launch {
                        discovering = true
                        candidates = emptyList()
                        val list = vm.discoverFeeds(url)
                        discovering = false
                        if (list.isEmpty()) {
                            probeError = "在这个页面没找到标准订阅链接，请手动找「RSS / 订阅」链接后粘贴"
                        } else {
                            probeError = null
                            candidates = list
                        }
                    }
                },
                enabled = !discovering && !checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (discovering) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("正在自动发现订阅地址…", color = cs.primary)
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = cs.primary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("这是网站首页？自动找订阅地址", color = cs.primary)
                }
            }

            if (candidates.isNotEmpty()) {
                Text("发现以下订阅地址，点一下即可填入并自动检测：", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                candidates.forEach { c ->
                    Surface(
                        color = cs.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                            .clickable {
                                dismissIme()
                                url = c.url
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.RssFeed, contentDescription = null, tint = cs.primary, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                if (c.title.isNotBlank()) {
                                    Text(c.title, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                                }
                                Text(c.url, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Surface(color = cs.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("怎么看一个地址对不对？", style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "订阅地址通常以 .xml、/feed、/rss、/atom.xml 结尾，例如：\n" +
                                "• https://sspai.com/feed\n" +
                                "• https://www.v2ex.com/index.xml\n" +
                                "• https://hnrss.org/frontpage\n\n" +
                                "分类先不用管，添加后在「阅源 → 我的阅源」里点开就能改名称 / 地址 / 分类。\n\n" +
                                "想订微博 / 知乎 / B站 这类「没有 RSS」的站点？回上一页用 RSSHub —— " +
                                "它能把它们转成标准 RSS 地址，填个 ID 就行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            if (sources.isNotEmpty()) {
                Text("已添加 ${sources.size} 个源", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack, enabled = !adding, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = {
                    dismissIme()
                    scope.launch {
                        adding = true
                        val p = probe
                        val name = (p?.title ?: "").ifBlank { url.substringAfter("//").substringBefore("/") }
                        vm.addSourceAndRefresh(name, url)
                        adding = false
                        Toast.makeText(context, "已添加「$name」，正在刷新…", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                enabled = ok && !checking && !adding && !duplicate,
                modifier = Modifier.weight(1f)
            ) {
                if (adding) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                } else {
                    Text(if (duplicate) "已经添加过了" else if (probe?.title?.isNotBlank() == true) "添加「${probe!!.title}」" else "添加")
                }
            }
        }
    }
}

/** 检测通过后的富预览卡：名称 / 格式 / 文章数 / 最新 3 条标题。 */
@Composable
private fun ProbeCard(probe: FeedProbe, duplicate: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("地址可用", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
                Spacer(Modifier.weight(1f))
                // 格式 + 文章数，一眼看出这个源靠不靠谱
                Surface(color = cs.onPrimaryContainer.copy(alpha = 0.10f), shape = CircleShape) {
                    Text(
                        "${probe.kind} · ${probe.count} 篇",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                probe.title.ifBlank { "（未识别到名称，将用域名代替）" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onPrimaryContainer
            )

            if (probe.samples.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = cs.onPrimaryContainer.copy(alpha = 0.18f))
                Spacer(Modifier.height(8.dp))
                Text("最新内容预览", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
                Spacer(Modifier.height(6.dp))
                probe.samples.forEachIndexed { i, t ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onPrimaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.width(16.dp)
                        )
                        Text(
                            t,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (duplicate) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(cs.tertiaryContainer)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = cs.onTertiaryContainer, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "这个地址已经添加过了，不用重复添加",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onTertiaryContainer
                    )
                }
            }
        }
    }
}
