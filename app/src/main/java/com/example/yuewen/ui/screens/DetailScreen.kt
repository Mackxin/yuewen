package com.example.yuewen.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.model.BodyBlock
import com.example.yuewen.data.model.Note
import com.example.yuewen.data.reader.BodyBlocks
import com.example.yuewen.ui.components.ImageZoomDialog
import com.example.yuewen.ui.theme.ReaderFont
import com.example.yuewen.ui.theme.ReaderPalette
import com.example.yuewen.ui.theme.ReaderSizeLabels
import com.example.yuewen.ui.theme.ReaderSizes
import com.example.yuewen.ui.theme.ReaderSpacing
import com.example.yuewen.ui.theme.ReaderTheme
import com.example.yuewen.ui.theme.readerPalette
import com.example.yuewen.ui.util.BrowserLauncher
import com.example.yuewen.ui.util.ShareCard
import com.example.yuewen.ui.util.TtsChunker
import com.example.yuewen.ui.util.TtsPiece
import com.example.yuewen.ui.util.TtsRateLabels
import com.example.yuewen.ui.util.TtsRates
import com.example.yuewen.ui.util.formatRelativeTime
import com.example.yuewen.ui.viewmodel.DetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

private const val TOP_BAR_HEIGHT = 56

private fun shareArticle(context: Context, link: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n$link")
    }
    context.startActivity(Intent.createChooser(intent, "分享到"))
}

private fun shareCard(context: Context, uri: Uri, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享卡片"))
}

/**
 * 用浏览器打开原文。
 *
 * v2.2 起 [pkg] 是用户在设置里选定的浏览器包名（空串 = 跟随系统）。
 * 统一交给 `BrowserLauncher.open` 处理显式 Intent 与「浏览器被卸载后退回系统默认」的兜底。
 */
private fun openOriginal(context: Context, link: String, pkg: String = "") {
    if (!BrowserLauncher.open(context, link, pkg)) {
        Toast.makeText(context, "找不到可以打开网页的应用", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 文章详情页。
 *
 * 1. **左右滑动切换上一篇 / 下一篇**（同分类内），顶部显示「3 / 12」位置指示；
 * 2. **可调阅读排版**：字号 4 档、行距 3 档、字体衬线/无衬线、底色 跟随主题/米黄纸感/墨夜，
 *    全部持久化保存；还能**双指捏合直接调字号**；
 * 3. 顶部**阅读进度条**，读到哪儿一目了然；
 * 4. **朗读**（系统 TTS）：v1.7 起按段落朗读，**读到哪一段正文自动滚到哪一段并高亮**；
 * 5. **正文分块渲染**：文字段 + 图片段交替，图片可点开全屏预览（双指缩放）；
 * 6. **分享卡片**：右下角署名可自定义（记忆保存）。
 */
@Composable
fun DetailScreen(
    app: YuewenApplication,
    initialLink: String,
    onBack: () -> Unit,
    bottomInset: Dp = 0.dp
) {
    // 必须按 link 作为 key，否则 ViewModel 会按类名缓存在 Activity 作用域，
    // 第一次打开某篇文章后，再点别的文章都会复用同一份缓存 → 永远显示第一条。
    val vm: DetailViewModel = viewModel(key = "detail:$initialLink", factory = DetailViewModel.provide(initialLink, app))

    val links by vm.links.collectAsStateWithLifecycle()
    val startIndex by vm.startIndex.collectAsStateWithLifecycle()

    val readerThemeKey by app.settingsRepository.readerThemeFlow.collectAsStateWithLifecycle("auto")
    val readerFontKey by app.settingsRepository.readerFontFlow.collectAsStateWithLifecycle("sans")
    val readerSpacingKey by app.settingsRepository.readerSpacingFlow.collectAsStateWithLifecycle("normal")
    val readerSizeIdx by app.settingsRepository.readerSizeFlow.collectAsStateWithLifecycle(1)
    val autoread by app.settingsRepository.autoreadFlow.collectAsStateWithLifecycle(true)
    // v1.9：朗读语速档位（0 慢 / 1 标准 / 2 快 / 3 很快）
    val ttsRateIdx by app.settingsRepository.ttsRateFlow.collectAsStateWithLifecycle(1)

    // v2.2：点「原文」时用哪个浏览器打开（包名，空 = 跟随系统）
    val browserPkg by app.settingsRepository.browserPkgFlow.collectAsStateWithLifecycle("")

    // 分享卡片署名（v1.7）：上次填过的默认预填
    val savedFooter by app.settingsRepository.shareFooterFlow.collectAsStateWithLifecycle("")

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkTheme = isSystemInDarkTheme()

    val font = ReaderFont.of(readerFontKey)
    val spacing = ReaderSpacing.of(readerSpacingKey)
    val palette = readerPalette(ReaderTheme.of(readerThemeKey), darkTheme)
    val bodySp = ReaderSizes.getOrElse(readerSizeIdx) { 17 }

    var showTypePanel by remember { mutableStateOf(false) }
    var makingCard by remember { mutableStateOf(false) }
    // -1 表示「链接列表还没准备好」，此时不构建 Pager
    var currentIndex by remember { mutableIntStateOf(-1) }

    // v1.9：每页的滚动位置按 page 索引持有。
    // 为什么提到这一层：顶栏的「双击空白区回到文章顶部」要拿到**当前页**的 ScrollState，
    // 而 ScrollState 原来 remember 在每个 DetailPage 内部，外面够不着。
    // 用 Map 持有还能顺带保住「翻到别的文章再翻回来」时的滚动位置。
    val pageScrolls = remember { mutableMapOf<Int, ScrollState>() }

    LaunchedEffect(links, startIndex) {
        if (links.isNotEmpty() && currentIndex < 0) {
            currentIndex = startIndex.coerceIn(0, links.lastIndex)
        }
    }

    val currentLink = links.getOrNull(currentIndex) ?: initialLink
    val currentArticle by remember(currentLink) { vm.observe(currentLink) }
        .collectAsStateWithLifecycle(null)

    // 打开文章自动标为已读
    LaunchedEffect(currentArticle?.link, currentArticle?.isRead) {
        val a = currentArticle
        if (autoread && a != null && !a.isRead) vm.markRead(a.link)
    }

    // ---------------- 朗读段落序列 ----------------
    // index 0 = 标题，1..n = 正文文本段（与 DetailPage 的渲染计数严格一致：
    // 都来自 BodyBlocks.parse 同一份输入，图片不占段号）。
    val speakParas: List<String> = remember(
        currentArticle?.link, currentArticle?.fullText, currentArticle?.content, currentArticle?.summary
    ) {
        val a = currentArticle ?: return@remember emptyList()
        buildList {
            if (a.title.isNotBlank()) add(a.title)
            val body = if (a.fullText.isNotBlank()) a.fullText else a.content.ifBlank { a.summary }
            BodyBlocks.parse(body, a.link).forEach { b ->
                BodyBlocks.textOf(b)?.let { add(it) }
            }
        }
    }

    // ---------------- 文章朗读（系统 TTS，App 级单例） ----------------
    // v1.8：引擎搬到了 YuewenApplication。以前它跟着详情页创建 / 销毁，
    // 于是「朗读中点一下底栏想切走」会把引擎一起关掉，白断了用户的听书。
    // 现在离开详情页只是不再画高亮，声音继续念；回到这篇文章再点一次即可停。
    val tts = app.tts
    val ttsSpeaking by tts.speaking.collectAsStateWithLifecycle()
    val ttsPaused by tts.paused.collectAsStateWithLifecycle()
    val ttsPara by tts.para.collectAsStateWithLifecycle()
    val ttsLink by tts.link.collectAsStateWithLifecycle()

    // 进详情页顺手预热引擎，等用户点朗读时通常已经就绪，省掉「再点一次」
    LaunchedEffect(Unit) { tts.warmUp() }

    // 语速偏好同步给引擎（引擎重建后也会被 setRate 重新补上）
    LaunchedEffect(ttsRateIdx) {
        tts.setRate(TtsRates.getOrElse(ttsRateIdx) { 1f })
    }

    // 只有「正在朗读的确实是当前这篇」时，按钮才显示停止、正文才做跟随高亮。
    // 否则会出现「在 A 文章里念 B 文章的段落」这种错位。
    val playingHere = ttsSpeaking && ttsLink == currentLink
    val speakingPara = if (playingHere) ttsPara else -1

    // ---------------- v2.0：文章大纲 / 摘录笔记 ----------------
    var showToc by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }

    /** 点大纲里的某一条 → 把「跳到第 N 段」交给正在显示的那一页去执行。 */
    var jumpPara by remember { mutableIntStateOf(-1) }

    // 摘录 / 笔记编辑对话框。target == null 表示新建（quote 由长按那段预填）。
    var showNoteDialog by remember { mutableStateOf(false) }
    var dialogQuote by remember { mutableStateOf("") }
    var dialogNote by remember { mutableStateOf("") }
    var dialogNoteTarget by remember { mutableStateOf<Note?>(null) }

    /** 本篇已有的摘录与笔记（按时间倒序，来自 Room 的 Flow）。 */
    val notes by remember(currentLink) { vm.observeNotes(currentLink) }
        .collectAsStateWithLifecycle(emptyList())

    /**
     * 文章大纲：把正文里的小标题挑出来，并记下它们在「段号体系」里的位置。
     *
     * 段号必须和朗读 / 滚动跟随用同一套规则（标题 0、正文文本段 1..n、图片不占号），
     * 否则点了目录会跳到别的地方去。所以这里刻意复用 BodyBlocks.parse 的同一份输入。
     */
    val outline: List<Pair<Int, String>> = remember(
        currentArticle?.link, currentArticle?.fullText, currentArticle?.content, currentArticle?.summary
    ) {
        val a = currentArticle ?: return@remember emptyList()
        val body = if (a.fullText.isNotBlank()) a.fullText else a.content.ifBlank { a.summary }
        val out = mutableListOf<Pair<Int, String>>()
        var idx = 0
        BodyBlocks.parse(body, a.link).forEach { b ->
            when (b) {
                is BodyBlock.Heading -> { idx++; out += idx to b.text }
                is BodyBlock.Paragraph -> idx++
                is BodyBlock.Image -> Unit
            }
        }
        out
    }

    // ---------------- 分享卡片署名对话框 ----------------
    var showFooterDialog by remember { mutableStateOf(false) }
    var pendingCardArticle by remember { mutableStateOf<Article?>(null) }
    var footerInput by remember { mutableStateOf(ShareCard.DEFAULT_FOOTER) }

    // ---------------- v2.6：状态栏图标跟着阅读底色翻 ----------------
    // 沉浸式之后状态栏区域透出来的就是阅读器的底色。米黄纸感是**浅色底**，
    // 深色主题下如果不把图标切成深色，就是「浅色图标压在米黄底上」，根本看不清。
    // ReaderPalette 早就带了一个 onPaper 字段，但一直没人消费 —— 这里把它接上。
    // 离开时恢复成跟随 App 主题（Theme 那边的 SideEffect 只有主题变化时才会再跑，
    // 所以必须在这里手动还原）。
    val view = LocalView.current
    val appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    DisposableEffect(palette.onPaper) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = palette.onPaper
        onDispose { controller?.isAppearanceLightStatusBars = !appDark }
    }

    Column(modifier = Modifier.fillMaxSize().background(palette.background).statusBarsPadding()) {

        // ---------------- 固定顶栏（不参与滚动，因此永远不会压住标题） ----------------
        Surface(color = palette.background, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TOP_BAR_HEIGHT.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = palette.ink)
                }
                if (links.size > 1 && currentIndex >= 0) {
                    Text(
                        "${currentIndex + 1} / ${links.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkVariant
                    )
                }
                // v1.9：原来这里只是个 Spacer，现在它是一块「双击回到文章顶部」的空白区。
                // currentIndex 是 Compose 状态（by remember），在回调里读到的永远是最新值，
                // 不会像普通 var 那样被闭包捕获成旧值。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                pageScrolls[currentIndex]?.let { s ->
                                    scope.launch { s.animateScrollTo(0) }
                                }
                            })
                        }
                )

                // 文章大纲
                IconButton(
                    onClick = {
                        showToc = !showToc
                        if (showToc) showNotes = false
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Toc,
                        contentDescription = "文章大纲",
                        tint = if (showToc) MaterialTheme.colorScheme.primary else palette.inkVariant
                    )
                }

                // 本篇的摘录与笔记（有笔记时右上角挂个小数字）
                Box {
                    IconButton(
                        onClick = {
                            showNotes = !showNotes
                            if (showNotes) showToc = false
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = "摘录与笔记",
                            tint = if (showNotes) MaterialTheme.colorScheme.primary else palette.inkVariant
                        )
                    }
                    if (notes.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp)
                        ) {
                            Text(
                                if (notes.size > 99) "99+" else "${notes.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // 暂停 / 继续。只在「正在念的就是本篇」时出现。
                // TTS 引擎没有 pause API，这里是 stop + 记住片段位置 + 重发剩余片段凑出来的。
                if (playingHere) {
                    IconButton(onClick = { tts.toggle() }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            if (ttsPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (ttsPaused) "继续朗读" else "暂停朗读",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 朗读 / 停止
                IconButton(onClick = {
                    when {
                        playingHere -> tts.stop()
                        speakParas.isEmpty() -> Toast.makeText(
                            context,
                            "这篇文章没有可朗读的内容",
                            Toast.LENGTH_SHORT
                        ).show()
                        else -> {
                            // 每个段落独立切块：片段携带段落号，读到哪里正文跟到哪里
                            val pieces: List<TtsPiece> = speakParas.flatMapIndexed { p, text ->
                                TtsChunker.split(text).map { TtsPiece(p, it) }
                            }
                            when {
                                pieces.isEmpty() -> Toast.makeText(
                                    context, "这篇文章没有可朗读的内容", Toast.LENGTH_SHORT
                                ).show()
                                // 引擎没就绪：speak 返回 false，提示再点一次。
                                // title 一起传过去 —— 后台悬浮条 / 通知栏要显示「正在念什么」。
                                !tts.speak(pieces, currentLink, currentArticle?.title.orEmpty()) -> Toast.makeText(
                                    context, "语音引擎还在准备中，请稍后再点一次", Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (playingHere) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (playingHere) "停止朗读" else "朗读文章",
                        tint = if (playingHere) MaterialTheme.colorScheme.primary else palette.inkVariant
                    )
                }

                IconButton(onClick = {
                    val a = currentArticle ?: return@IconButton
                    vm.toggleBookmark(a.link, !a.isBookmarked)
                }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (currentArticle?.isBookmarked == true) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
                        contentDescription = "收藏",
                        tint = if (currentArticle?.isBookmarked == true) MaterialTheme.colorScheme.tertiary else palette.inkVariant
                    )
                }
                IconButton(onClick = { showTypePanel = !showTypePanel }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.FormatSize,
                        contentDescription = "阅读排版",
                        tint = if (showTypePanel) MaterialTheme.colorScheme.primary else palette.inkVariant
                    )
                }
                IconButton(
                    onClick = { currentArticle?.let { shareArticle(context, it.link, it.title) } },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "分享", tint = palette.inkVariant)
                }
            }
        }

        // ---------------- 阅读排版面板 ----------------
        if (showTypePanel) {
            TypePanel(
                sizeIndex = readerSizeIdx,
                spacingKey = readerSpacingKey,
                fontKey = readerFontKey,
                themeKey = readerThemeKey,
                rateIndex = ttsRateIdx,
                onSize = { scope.launch { app.settingsRepository.setReaderSize(it) } },
                onSpacing = { scope.launch { app.settingsRepository.setReaderSpacing(it) } },
                onFont = { scope.launch { app.settingsRepository.setReaderFont(it) } },
                onTheme = { scope.launch { app.settingsRepository.setReaderTheme(it) } },
                onRate = { scope.launch { app.settingsRepository.setTtsRate(it) } }
            )
        }

        // ---------------- v2.0：文章大纲 ----------------
        if (showToc) {
            TocPanel(
                outline = outline,
                onJump = { para ->
                    jumpPara = para
                    showToc = false
                },
                onClose = { showToc = false }
            )
        }

        // ---------------- v2.0：本篇的摘录与笔记 ----------------
        if (showNotes) {
            NotesPanel(
                notes = notes,
                onAdd = {
                    dialogNoteTarget = null
                    dialogQuote = ""
                    dialogNote = ""
                    showNoteDialog = true
                },
                onEdit = { n ->
                    dialogNoteTarget = n
                    dialogQuote = n.quote
                    dialogNote = n.note
                    showNoteDialog = true
                },
                onDelete = { vm.deleteNote(it) },
                onClose = { showNotes = false }
            )
        }

        // ---------------- 正文：左右滑动切换上下篇 ----------------
        if (links.isEmpty() || currentIndex < 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val pagerState = rememberPagerState(initialPage = currentIndex, pageCount = { links.size })

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    if (page != currentIndex) {
                        currentIndex = page
                        // 在阅读器里翻到别的文章就停掉朗读，
                        // 否则会「看着 B 的文章、听 A 的句子」。
                        // 注意：这只是翻页；整个阅读器被关掉（切底栏）时不会走到这里，
                        // 那种情况刻意不停 —— 用户就是要边听边逛。
                        tts.stop()
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                DetailPage(
                    vm = vm,
                    link = links[page],
                    // 这一页的滚动状态（由外层 Map 按 page 索引持有，顶栏双击回顶要读它）
                    scroll = pageScrolls.getOrPut(page) { ScrollState(0) },
                    isCurrent = page == pagerState.currentPage,
                    hasSiblings = links.size > 1,
                    palette = palette,
                    font = font,
                    spacing = spacing,
                    bodySp = bodySp,
                    sizeIndex = readerSizeIdx,
                    bottomInset = bottomInset,
                    makingCard = makingCard,
                    speakingPara = if (page == pagerState.currentPage) speakingPara else -1,
                    // 大纲点击：只把请求交给「正在显示的那一页」，别的页不理会
                    jumpPara = if (page == pagerState.currentPage) jumpPara else -1,
                    onJumpHandled = { jumpPara = -1 },
                    onQuote = { text ->
                        // 长按某一段 → 直接把那段话预填进摘录框（还能再手动删改成想要的那半句）
                        dialogNoteTarget = null
                        dialogQuote = text
                        dialogNote = ""
                        showNoteDialog = true
                    },
                    positionText = if (links.size > 1) "${currentIndex + 1} / ${links.size}" else "",
                    onZoom = { delta ->
                        val next = (readerSizeIdx + delta).coerceIn(0, ReaderSizes.lastIndex)
                        if (next != readerSizeIdx) {
                            scope.launch { app.settingsRepository.setReaderSize(next) }
                        }
                    },
                    onShareCard = { a ->
                        // 先让用户确认右下角署名，再生成（上次填的会预填）
                        pendingCardArticle = a
                        footerInput = savedFooter.ifBlank { ShareCard.DEFAULT_FOOTER }
                        showFooterDialog = true
                    },
                    // v2.2：走用户选的浏览器（空 = 跟随系统）
                    onOpenOriginal = { openOriginal(context, it, browserPkg) }
                )
            }
        }
    }

    // ---------------- 卡片署名编辑对话框 ----------------
    if (showFooterDialog) {
        AlertDialog(
            onDismissRequest = { showFooterDialog = false },
            title = { Text("卡片署名") },
            text = {
                Column {
                    Text(
                        "显示在分享卡片的右下角，会记住这次填写的内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = footerInput,
                        onValueChange = { footerInput = it },
                        singleLine = true,
                        placeholder = { Text(ShareCard.DEFAULT_FOOTER) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFooterDialog = false
                    val a = pendingCardArticle ?: return@TextButton
                    val footer = footerInput.trim()
                    if (!makingCard) {
                        makingCard = true
                        // 署名记住 + 生成 1080 宽 PNG 都不轻，丢到后台协程
                        scope.launch {
                            app.settingsRepository.setShareFooter(footer)
                            val uri = withContext(Dispatchers.Default) {
                                ShareCard.generate(context, a, footer.ifBlank { ShareCard.DEFAULT_FOOTER })
                            }
                            makingCard = false
                            if (uri != null) shareCard(context, uri, a.title)
                            else Toast.makeText(context, "卡片生成失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("生成卡片") }
            },
            dismissButton = {
                TextButton(onClick = { showFooterDialog = false }) { Text("取消") }
            }
        )
    }

    // ---------------- v2.0：摘录 / 笔记编辑对话框 ----------------
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text(if (dialogNoteTarget == null) "摘录 / 写笔记" else "编辑这条笔记") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "原文摘录",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = dialogQuote,
                        onValueChange = { dialogQuote = it },
                        placeholder = { Text("长按正文任意一段就会自动填进来，也可以自己删改") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "我的想法（可留空）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = dialogNote,
                        onValueChange = { dialogNote = it },
                        placeholder = { Text("为什么记下它？") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "存下来之后，在这篇文章的「笔记」里能翻到，也会出现在「闻件 → 笔记」里统一管理。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = dialogQuote.isNotBlank() || dialogNote.isNotBlank(),
                    onClick = {
                        val q = dialogQuote.trim()
                        val n = dialogNote.trim()
                        val target = dialogNoteTarget
                        if (target == null) {
                            vm.addNote(currentArticle, currentLink, q, n)
                        } else {
                            vm.updateNote(target.copy(quote = q, note = n))
                        }
                        showNoteDialog = false
                        Toast.makeText(context, "已保存到「闻件 → 笔记」", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("保存", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("取消") }
            }
        )
    }
}

// ---------------- 单篇文章 ----------------

@OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
private fun DetailPage(
    vm: DetailViewModel,
    link: String,
    scroll: ScrollState,
    isCurrent: Boolean,
    hasSiblings: Boolean,
    palette: ReaderPalette,
    font: ReaderFont,
    spacing: ReaderSpacing,
    bodySp: Int,
    sizeIndex: Int,
    bottomInset: Dp,
    makingCard: Boolean,
    speakingPara: Int,
    /** 大纲里点了「跳到第 N 段」；-1 = 没有待处理的跳转。 */
    jumpPara: Int,
    onJumpHandled: () -> Unit,
    /** 长按某一段正文 → 把这段原文交给外层打开「摘录 / 笔记」对话框。 */
    onQuote: (String) -> Unit,
    /** 「3 / 12」这种位置提示（顶栏放不下了，就显示在正文顶部的信息行里）。 */
    positionText: String,
    onZoom: (Int) -> Unit,
    onShareCard: (Article) -> Unit,
    onOpenOriginal: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // 滚动跟随里的「呼吸空隙 / 舒适区」都用 dp 换算成 px —— 不同屏幕密度才不会跑偏
    val density = LocalDensity.current.density
    val article by remember(link) { vm.observe(link) }.collectAsStateWithLifecycle(null)
    val loadingSet by vm.loading.collectAsStateWithLifecycle()
    val failedSet by vm.failed.collectAsStateWithLifecycle()
    val loading = link in loadingSet
    val failed = link in failedSet

    // 只有「正在看的这一篇」才联网抽取全文，滑走的不浪费流量
    LaunchedEffect(isCurrent, link) {
        if (isCurrent) vm.ensureFullText(link)
    }

    // 滚动状态由外层按 page 索引持有（顶栏双击回顶要用），这里不再自己 remember

    // 手势检测块用 pointerInput(Unit) 只建一次，闭包会被长期保留；
    // 直接在里面读 onZoom 会一直用第一帧捕获到的旧 lambda（字号永远只变一档）。
    // rememberUpdatedState 让手势块总是调用到最新的那个。
    val zoomHandler by rememberUpdatedState(onZoom)

    // ---- 阅读进度记忆 ----
    // 恢复：等正文渲染完（滚动范围 > 0）再跳到上次的位置，只跳一次。
    // 用 snapshotFlow + first 等待，而不是把 scroll.value 塞进 key ——
    // 否则滚动时 key 每帧都在变，协程会被反复重启。
    var restored by remember(link) { mutableStateOf(false) }
    LaunchedEffect(link, article?.link) {
        if (restored) return@LaunchedEffect
        val target = article?.readProgress ?: 0
        withTimeoutOrNull(2000) {
            snapshotFlow { scroll.maxValue }.first { it > 0 }
        }
        if (scroll.maxValue > 0) {
            if (target in 1..999) {
                scroll.scrollTo((target / 1000f * scroll.maxValue).toInt())
            }
            restored = true
        }
    }
    // 保存：滚动停下 700ms 后再写库，避免每帧都写
    LaunchedEffect(scroll, link) {
        snapshotFlow { scroll.value to scroll.maxValue }
            .debounce(700)
            .collect { (value, max) ->
                if (max > 0 && restored) {
                    vm.saveProgress(link, (value * 1000f / max).toInt().coerceIn(0, 1000))
                }
            }
    }

    // 点击正文图片 → 全屏预览
    var previewUrl by remember { mutableStateOf<String?>(null) }

    val a = article
    if (a == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = cs.primary)
        }
        return
    }

    val body = if (a.fullText.isNotBlank()) a.fullText else a.content.ifBlank { a.summary }
    // 正文块序列：文字段 + 图片段。旧版存的是纯文本，parse 会按换行拆段，一样能画。
    val blocks = remember(link, body) { BodyBlocks.parse(body, a.link) }
    val shortFeed = a.content.length < 1200
    val fetching = loading && !hasFullText(a)
    // 注：阅读进度**不在这里取值**。`scroll.value` 是每帧都变的高频状态，
    // 在组合阶段读到变量里，等于让下面那一大片正文每滚一帧就整体重组一次 ——
    // 文章越长越卡。进度条的取值改到它自己的绘制 lambda 里，见下面那条注释。

    // ---- 段落定位（朗读跟随 / 大纲跳转共用） ----
    // ⚠️ 这里只存「坐标对象」，不存算好的 y 值。踩过的坑，记一下：
    //
    // 上一版是 `paraY[idx] = it.positionInParent().y + scroll.value`，结果「会滚，但滚不准」。
    // 两个错叠在一起：
    // ① `positionInParent()` 本来就是**内容坐标**（滚动平移发生在 verticalScroll 那一层，
    //    子节点相对自己父节点的偏移不含滚动量），再 + scroll.value 属于重复累加；
    // ② 更要命的是 `onGloballyPositioned` 会因为「回调里读过 scroll.value」而在**滚动时反复触发**，
    //    于是每滚一帧就把当时的滚动值再写进 map 一次，目标位置越漂越远。
    //
    // 现在的做法：布局时只收坐标，等真要滚的那一刻现算
    //     y = 段落窗口坐标 − 视口窗口坐标 + 当前滚动值
    // 两个坐标在同一帧读，结果恒定；式子也与节点层级 / 密度 / 是否重排都无关。
    val paraCoords = remember(link) { mutableMapOf<Int, LayoutCoordinates>() }

    /** 滚动视口自身的坐标（挂在 verticalScroll **外面**，不受滚动平移影响）。 */
    var viewportCoords by remember(link) { mutableStateOf<LayoutCoordinates?>(null) }

    /** 把「第 n 段」翻译成内容坐标系的 y；实时坐标还没到位时返回 null。 */
    fun paraContentY(idx: Int): Int? {
        val vc = viewportCoords ?: return null
        val pc = paraCoords[idx] ?: return null
        if (!vc.isAttached || !pc.isAttached) return null
        return (pc.positionInRoot().y - vc.positionInRoot().y + scroll.value).roundToInt()
    }

    // 朗读自动跟随：当前段变化 → 平滑滚到该段。
    // 已经在视口里就算了（除非贴着顶边 / 底边）—— 否则每读一段都跳一次，
    // 用户自己往前翻页的那点耐心会被反复打断。
    LaunchedEffect(speakingPara, link) {
        val idx = speakingPara
        if (idx < 0) return@LaunchedEffect
        // 段落布局偶尔晚一帧，最多等 800ms
        var y: Int? = null
        withTimeoutOrNull(800) {
            while (y == null) {
                y = paraContentY(idx)
                if (y == null) delay(30)
            }
        }
        val yy = y ?: return@LaunchedEffect
        if (scroll.maxValue <= 0) return@LaunchedEffect
        val lead = (14 * density).toInt()
        val vh = viewportCoords?.size?.height ?: 0
        val cur = scroll.value
        if (vh > 0) {
            val topEdge = cur + lead
            val bottomEdge = cur + maxOf(lead + 1, vh - (110 * density).toInt())
            if (yy in topEdge..bottomEdge) return@LaunchedEffect
        }
        scroll.animateScrollTo((yy - lead).coerceIn(0, scroll.maxValue))
    }

    // v2.0：大纲跳转。段落坐标由各段的 onGloballyPositioned 收上来 ——
    // 正文用的是普通 Column（不是 Lazy），所有段落都已组合并量过，所以通常立刻就能算。
    // 兜底再等最多 1.5 秒（布局偶尔会晚一帧），等不到就放弃，别让按钮看起来像卡住了。
    LaunchedEffect(jumpPara, link) {
        val target = jumpPara
        if (target < 0) return@LaunchedEffect
        var y: Int? = null
        withTimeoutOrNull(1500) {
            while (y == null) {
                y = paraContentY(target)
                if (y == null) delay(50)
            }
        }
        val yy = y
        if (yy != null && scroll.maxValue > 0) {
            scroll.animateScrollTo((yy - (14 * density).toInt()).coerceIn(0, scroll.maxValue))
        }
        onJumpHandled()
    }

    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = bodySp.sp,
        lineHeight = (bodySp * spacing.multiplier).sp,
        fontFamily = font.family
    )
    val headingStyle = bodyStyle.copy(
        fontSize = (bodySp + 3).sp,
        lineHeight = ((bodySp + 3) * spacing.multiplier).sp,
        fontWeight = FontWeight.Bold
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // 阅读进度条
        //
        // ⚠️ v2.5 修卡顿：进度必须在**绘制阶段**读 `scroll.value`。
        // 以前写成 `Modifier.fillMaxWidth(progress)`，而 `progress` 是在组合期算好的 ——
        // Compose 只有「在 drawBehind / graphicsLayer 的 lambda 内部读」才算绘制阶段读取
        // （只重绘、不重组）；在 lambda 外面读一遍再传进去，等于退回组合期读取，
        // 于是滚动时**整篇正文每帧重组**，长文尤其明显。
        // 顺带一提：同一个函数上面那段注释专门讲「不能在有副作用的地方读 scroll.value」，
        // 而这里恰恰是它的另一半 —— 组合期读同样不行。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(palette.ink.copy(alpha = 0.07f))
                .drawBehind {
                    val p = if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f
                    drawRect(
                        color = cs.primary,
                        size = Size(size.width * p.coerceIn(0f, 1f), size.height)
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                // 记下视口自身的窗口坐标与高度。
                // 必须挂在 verticalScroll **之前**（也就是它的外面）：挂在里面拿到的会是被
                // 滚上去的内容的位置，那又要靠加减滚动值去凑，正是上一版的踩坑点。
                .onGloballyPositioned { viewportCoords = it }
                .verticalScroll(scroll)
                // 双指捏合调字号。
                // 只认「两根以上手指」的手势并把手势事件吃掉，单指拖动完全放行给上面的
                // verticalScroll —— 否则缩放检测会把滚动手势一起吞掉，正文就滑不动了。
                .pointerInput(Unit) {
                    var accumulated = 1f
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }

                            if (pressed >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom.isFinite() && zoom > 0f && zoom != 1f) {
                                    accumulated *= zoom
                                    // 累计放大/缩小超过阈值就切一档字号，然后归零重新累计
                                    if (accumulated >= 1.18f) {
                                        zoomHandler(1)
                                        accumulated = 1f
                                    } else if (accumulated <= 0.85f) {
                                        zoomHandler(-1)
                                        accumulated = 1f
                                    }
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                }
                            } else if (pressed == 0) {
                                break
                            }
                        }
                        accumulated = 1f
                    }
                }
                .padding(horizontal = 18.dp)
                // bottomInset：阅读时如果底栏还显示着，正文要多留出这段高度，别被胶囊压住
                .padding(top = 14.dp, bottom = 40.dp + bottomInset)
        ) {
            // 头图（feed 里带的缩略图），也能点开放大
            val headerImg = a.imageUrl
            if (!headerImg.isNullOrBlank()) {
                BodyImage(
                    url = headerImg,
                    cs = cs,
                    onClick = { previewUrl = headerImg }
                )
                Spacer(Modifier.height(16.dp))
            }

            // ---- 文本段号从 0 开始：0 = 标题，1.. = 正文段（与 speakParas 严格对齐）----
            val highlightBg = Modifier.background(palette.ink.copy(alpha = 0.09f), RoundedCornerShape(8.dp))

            // v2.0：长按任意一段 → 摘录 / 写笔记。
            // 用 combinedClickable + indication = null 有两个理由：
            // ① 不要 Material 的涟漪 —— 一整段正文被点出一块灰底很难看；
            // ② clickable 只吃「点」，拖动照旧交给外层 verticalScroll，正文不会滑不动。
            val quoteInteraction = remember { MutableInteractionSource() }

            Text(
                a.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp, lineHeight = 32.sp),
                fontWeight = FontWeight.Bold,
                fontFamily = font.family,
                color = palette.ink,
                modifier = Modifier
                    .onGloballyPositioned { paraCoords[0] = it }
                    .then(if (isCurrent && speakingPara == 0) highlightBg else Modifier)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                buildString {
                    append("${a.sourceName} · ${formatRelativeTime(a.pubDate)} · 阅读约 ${max(1, body.length / 300)} 分钟")
                    if (positionText.isNotBlank()) append(" · $positionText")
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("字号 ${ReaderSizeLabels.getOrElse(sizeIndex) { "标准" }} · 双指捏合可调 · 图片点按可放大")
                    append("\n长按任意一段可摘录 / 写笔记")
                    if (hasSiblings) append("\n左右滑动切换上一篇 / 下一篇")
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkVariant.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(16.dp))

            // 正在联网抽取正文全文
            if (fetching) {
                Row(
                    modifier = Modifier.padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = cs.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("正在获取全文…", style = MaterialTheme.typography.labelMedium, color = palette.inkVariant)
                }
            }

            // ---- 分块渲染正文：段落 + 小标题 + 图片 ----
            // ⚠️ paraIdx 必须在循环内快照成 val 再给闭包用：
            //    onGloballyPositioned 的回调是延迟执行的，直接捕获 var 会全部拿到最后一个值。
            var paraIdx = 0
            blocks.forEach { block ->
                when (block) {
                    is BodyBlock.Heading -> {
                        val idx = ++paraIdx
                        Text(
                            block.text,
                            style = headingStyle,
                            color = palette.ink,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .onGloballyPositioned { paraCoords[idx] = it }
                                .then(if (isCurrent && speakingPara == idx) highlightBg else Modifier)
                                .combinedClickable(
                                    interactionSource = quoteInteraction,
                                    indication = null,
                                    onClick = {},
                                    onLongClick = { onQuote(block.text) }
                                )
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    is BodyBlock.Paragraph -> {
                        val idx = ++paraIdx
                        Text(
                            block.text,
                            style = bodyStyle,
                            color = palette.ink,
                            modifier = Modifier
                                .onGloballyPositioned { paraCoords[idx] = it }
                                .then(if (isCurrent && speakingPara == idx) highlightBg else Modifier)
                                .combinedClickable(
                                    interactionSource = quoteInteraction,
                                    indication = null,
                                    onClick = {},
                                    onLongClick = { onQuote(block.text) }
                                )
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    is BodyBlock.Image -> {
                        // 图片不占段号（朗读跳过），点按进全屏预览
                        BodyImage(url = block.url, cs = cs, onClick = { previewUrl = block.url })
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            // 兜底：解析完一段文字都没有（极端脏数据）至少把原文本亮出来
            if (blocks.none { it is BodyBlock.Paragraph || it is BodyBlock.Heading } && body.isNotBlank()) {
                Text(body, style = bodyStyle, color = palette.ink)
                Spacer(Modifier.height(12.dp))
            }

            // 抽取失败 / 未取到时，提供重试（不跳浏览器）
            if (!hasFullText(a) && !fetching && shortFeed) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { vm.retryFullText(link) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = cs.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (failed) "正文获取失败，点此重试" else "获取正文全文",
                        color = cs.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // 读完之后的操作区：分享卡片 + 去浏览器看原文
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onShareCard(a) }, enabled = !makingCard) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        tint = if (makingCard) palette.inkVariant.copy(alpha = 0.5f) else palette.inkVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (makingCard) "正在生成卡片…" else "生成分享卡片",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (makingCard) palette.inkVariant.copy(alpha = 0.5f) else palette.inkVariant
                    )
                }
                if (a.link.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { onOpenOriginal(a.link) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = palette.inkVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("原文", style = MaterialTheme.typography.labelMedium, color = palette.inkVariant)
                    }
                }
            }
        }
    }

    // 全屏图片预览（双指缩放）
    previewUrl?.let { url ->
        ImageZoomDialog(url = url, onDismiss = { previewUrl = null })
    }
}

private fun hasFullText(a: Article): Boolean = a.fullText.isNotBlank()

/** 正文里的图片：按加载出来的真实宽高比撑开，点击进全屏预览。 */
@Composable
private fun BodyImage(url: String, cs: androidx.compose.material3.ColorScheme, onClick: () -> Unit) {
    // 宽高比（w/h）。加载成功前不知道，先用 3:2 占位；知道后立刻撑到真实比例。
    var ratio by remember(url) { mutableStateOf(0f) }
    AsyncImage(
        model = url,
        contentDescription = "文章配图（点按可放大）",
        contentScale = ContentScale.FillWidth,
        onSuccess = { state ->
            val d = state.result.drawable
            val w = d.intrinsicWidth
            val h = d.intrinsicHeight
            if (w > 0 && h > 0) ratio = w.toFloat() / h
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (ratio > 0.05f) Modifier.aspectRatio(ratio.coerceIn(0.4f, 3.5f)) else Modifier.height(180.dp))
            .clip(MaterialTheme.shapes.medium)
            .background(cs.surfaceVariant)
            .clickable(onClick = onClick)
    )
}

// ---------------- 阅读排版面板 ----------------

@Composable
private fun TypePanel(
    sizeIndex: Int,
    spacingKey: String,
    fontKey: String,
    themeKey: String,
    rateIndex: Int,
    onSize: (Int) -> Unit,
    onSpacing: (String) -> Unit,
    onFont: (String) -> Unit,
    onTheme: (String) -> Unit,
    onRate: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceContainerHigh,
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            ChoiceRow(
                label = "字号",
                options = ReaderSizeLabels,
                selectedIndex = sizeIndex.coerceIn(0, ReaderSizeLabels.lastIndex),
                onSelect = onSize
            )
            Spacer(Modifier.height(10.dp))
            ChoiceRow(
                label = "行距",
                options = ReaderSpacing.entries.map { it.label },
                selectedIndex = ReaderSpacing.of(spacingKey).ordinal,
                onSelect = { onSpacing(ReaderSpacing.entries[it].key) }
            )
            Spacer(Modifier.height(10.dp))
            ChoiceRow(
                label = "字体",
                options = ReaderFont.entries.map { it.label },
                selectedIndex = ReaderFont.of(fontKey).ordinal,
                onSelect = { onFont(ReaderFont.entries[it].key) }
            )
            Spacer(Modifier.height(10.dp))
            ChoiceRow(
                label = "底色",
                options = ReaderTheme.entries.map { it.label },
                selectedIndex = ReaderTheme.of(themeKey).ordinal,
                onSelect = { onTheme(ReaderTheme.entries[it].key) }
            )
            Spacer(Modifier.height(10.dp))
            // v1.9：朗读语速。朗读中改的话从下一句开始生效（TTS 没法给当前句变速）。
            ChoiceRow(
                label = "朗读语速",
                options = TtsRateLabels,
                selectedIndex = rateIndex.coerceIn(0, TtsRateLabels.lastIndex),
                onSelect = onRate
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "提示：在正文里双指捏合也能直接放大 / 缩小字号。",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(42.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEachIndexed { index, text ->
                val sel = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (sel) cs.primary.copy(alpha = 0.16f) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (sel) cs.primary else cs.outlineVariant
                    )
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) cs.primary else cs.onSurface,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ---------------- v2.0：文章大纲 ----------------

/**
 * 文章大纲面板。
 *
 * 只列正文里的小标题 —— 这正是「想快速跳读」需要的粒度。
 * 逐段列出来会变成第二份正文，反倒不如直接往下滚。
 *
 * 点某一条时把「段号」交给正在显示的那一页去滚。段号和朗读高亮用的是同一套编号
 * （标题 0 / 正文文本段 1..n / 图片不占号），所以点目录能精确落到那一行。
 */
@Composable
private fun TocPanel(
    outline: List<Pair<Int, String>>,
    onJump: (Int) -> Unit,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceContainerHigh,
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.Toc,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "文章大纲",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (outline.isNotEmpty()) {
                    Text(
                        "${outline.size} 节",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "收起大纲",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            if (outline.isEmpty()) {
                Text(
                    "这篇文章没有小标题，所以没有大纲。\n" +
                        "想快速略读可以双指捏合调小字号，或者点顶栏的朗读让它念给你听。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            } else {
                Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    outline.forEachIndexed { i, item ->
                        val para = item.first
                        val text = item.second
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onJump(para) }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.primary,
                                modifier = Modifier.width(22.dp)
                            )
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- v2.0：本篇的摘录与笔记 ----------------

/**
 * 本篇的摘录 / 笔记面板。
 *
 * 和「闻件 → 笔记」的区别：那里是**全 App 汇总**（按时间倒序，可以搜索）；
 * 这里是**当前这一篇**的局部视图 —— 读到一半想起来「刚才那句记过没有」，看一眼就知道。
 */
@Composable
private fun NotesPanel(
    notes: List<Note>,
    onAdd: () -> Unit,
    onEdit: (Note) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var pendingDelete by remember { mutableStateOf<Note?>(null) }

    Surface(
        color = cs.surfaceContainerHigh,
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.Notes,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (notes.isEmpty()) "摘录与笔记" else "摘录与笔记 · ${notes.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "收起",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            if (notes.isEmpty()) {
                Text(
                    "还没有摘录。\n长按正文里任意一段，就能把那句话摘下来，顺手写点自己的想法。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
            } else {
                Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    notes.forEach { n ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                            if (n.quote.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(cs.primary)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        n.quote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurface,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (n.note.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    n.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    formatRelativeTime(n.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onEdit(n) }) {
                                    Text("编辑", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                                }
                                TextButton(onClick = { pendingDelete = n }) {
                                    Text("删除", style = MaterialTheme.typography.labelSmall, color = cs.error)
                                }
                            }
                        }
                        HorizontalDivider(color = cs.outlineVariant)
                    }
                }
            }

            // 不选正文也能记：只写想法、quote 留空
            TextButton(onClick = onAdd, modifier = Modifier.padding(top = 4.dp)) {
                Text("+ 写一条笔记", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
        }
    }

    // 删除二次确认（笔记是用户自己敲的字，误删很心疼）
    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条笔记？") },
            text = {
                Text(
                    target.display.take(80).ifBlank { "（空笔记）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(target.id); pendingDelete = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}
