package com.example.yuewen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.yuewen.data.model.Article
import com.example.yuewen.ui.util.formatRelativeTime

/**
 * 文章列表的三种「密度」——参照 NetNewsWire / Reeder / Capy Reader 的做法：
 * 同一份数据，用户自己挑喜欢的阅读节奏。
 *
 * - [Compact]  紧凑：只有标题和一行小字，一屏能看很多条，适合快速扫标题
 * - [Card]     卡片：左侧文字 + 右侧缩略图，信息与美观的平衡（默认）
 * - [Magazine] 杂志：顶部通栏大图 + 渐变遮罩，视觉冲击力最强
 */
enum class ArticleListMode(val key: String, val label: String) {
    Compact("compact", "紧凑"),
    Card("card", "卡片"),
    Magazine("magazine", "杂志");

    fun next(): ArticleListMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Card
    }
}

/**
 * 一条文章卡片。已读/未读通过「标题透明度 + 未读圆点」双重区分，一眼能看出哪些没看过。
 *
 * v2.0 新增 [selectionMode] / [selected]：批量管理时左侧多一个勾选框。
 * 勾选框放在**卡片外面**（外层 Row），卡片本体完全不动 ——
 * 三种布局共用同一套勾选逻辑，不用各改一遍。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCard(
    article: Article,
    mode: ArticleListMode,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlight: String? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false
) {
    if (!selectionMode) {
        ArticleCardBody(article, mode, onClick, onBookmark, modifier, onLongClick, highlight)
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        ArticleCardBody(
            article = article,
            mode = mode,
            onClick = onClick,
            onBookmark = onBookmark,
            modifier = Modifier.weight(1f),
            onLongClick = onLongClick,
            highlight = highlight
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArticleCardBody(
    article: Article,
    mode: ArticleListMode,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlight: String? = null
) {
    val cs = MaterialTheme.colorScheme
    // 未读标题用全力度，已读降到 60% —— 比单纯加粗更符合「扫过一遍」的视觉习惯
    val titleColor = if (article.isRead) cs.onSurface.copy(alpha = 0.55f) else cs.onSurface
    val metaColor = cs.onSurfaceVariant.copy(alpha = if (article.isRead) 0.7f else 1f)

    when (mode) {
        ArticleListMode.Compact -> CompactCard(article, titleColor, metaColor, onClick, onBookmark, onLongClick, highlight, modifier)
        ArticleListMode.Card -> StandardCard(article, titleColor, metaColor, onClick, onBookmark, onLongClick, highlight, modifier)
        ArticleListMode.Magazine -> MagazineCard(article, titleColor, metaColor, onClick, onBookmark, onLongClick, highlight, modifier)
    }
}

/**
 * 搜索命中高亮：把标题里匹配到的关键词染成主色 + 加粗。
 * 用户一眼就能看出「为什么这条会被搜出来」。
 */
@Composable
private fun highlightedTitle(text: String, query: String?): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)
    val idx = text.indexOf(query, ignoreCase = true)
    if (idx < 0) return AnnotatedString(text)
    val cs = MaterialTheme.colorScheme
    return buildAnnotatedString {
        append(text.substring(0, idx))
        withStyle(SpanStyle(color = cs.primary, fontWeight = FontWeight.Bold)) {
            append(text.substring(idx, idx + query.length))
        }
        append(text.substring(idx + query.length))
    }
}

// ---------------- 紧凑 ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactCard(
    article: Article,
    titleColor: Color,
    metaColor: Color,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    onLongClick: (() -> Unit)?,
    highlight: String?,
    modifier: Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    // 未读的文章给一层极淡的主色底，扫列表时未读区块自然浮现出来
    val bg = if (article.isRead) cs.surface else cs.primary.copy(alpha = 0.07f)

    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).combinedClickable(
            onLongClickLabel = "更多操作",
            onLongClick = onLongClick,
            onClick = onClick
        ),
        shape = shape,
        color = bg
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 13.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnreadDot(visible = !article.isRead)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    highlightedTitle(article.title, highlight),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.Medium,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                MetaLine(article, metaColor)
            }
            BookmarkButton(article.isBookmarked, onBookmark)
        }
    }
}

// ---------------- 卡片（默认） ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StandardCard(
    article: Article,
    titleColor: Color,
    metaColor: Color,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    onLongClick: (() -> Unit)?,
    highlight: String?,
    modifier: Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium

    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).combinedClickable(
            onLongClickLabel = "更多操作",
            onLongClick = onLongClick,
            onClick = onClick
        ),
        shape = shape,
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 1.dp
    ) {
        // v2.0.3 卡片里三处位置固定下来：
        //   左 —— 标题在上，**源名 · 时间落在卡片左下角**；
        //   中 —— 缩略图靠上；
        //   右 —— **收藏图标贴最右侧、垂直居中**。
        // 先用 IntrinsicSize.Min 把 Row 的高度定成「最高那个子项」（通常就是 84dp 的缩略图），
        // 再让文字列 fillMaxHeight 拉满 + 内部 SpaceBetween —— 这样源名·时间才会真的贴到卡片
        // 底部。少了 IntrinsicSize 这一步，一行标题时文字列比缩略图矮，会被 Row 垂直居中，
        // 看着就不在左下角了。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnreadDot(visible = !article.isRead)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        highlightedTitle(article.title, highlight),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MetaLine(article, metaColor)
            }
            if (!article.imageUrl.isNullOrBlank()) {
                Spacer(Modifier.width(10.dp))
                ArticleImage(
                    article.imageUrl,
                    Modifier.size(84.dp).align(Alignment.Top),
                    MaterialTheme.shapes.small,
                    // v2.2：小图不垫灰底（见 ArticleImage 注释）
                    showPlaceholder = false
                )
            }
            BookmarkButton(article.isBookmarked, onBookmark)
        }
    }
}

// ---------------- 杂志 ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MagazineCard(
    article: Article,
    titleColor: Color,
    metaColor: Color,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    onLongClick: (() -> Unit)?,
    highlight: String?,
    modifier: Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val hasImage = !article.imageUrl.isNullOrBlank()

    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).combinedClickable(
            onLongClickLabel = "更多操作",
            onLongClick = onLongClick,
            onClick = onClick
        ),
        shape = shape,
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (hasImage) {
                Box(modifier = Modifier.fillMaxWidth().height(172.dp)) {
                    ArticleImage(article.imageUrl, Modifier.fillMaxSize(), androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                    // 底部压一层暗渐变：即使图片很亮，压在图上的来源文字也看得清
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                                )
                            )
                    )
                    Text(
                        article.sourceName,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                    if (!article.isRead) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text("未读", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    if (!hasImage) {
                        UnreadDot(visible = !article.isRead)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        highlightedTitle(article.title, highlight),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                        fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.Bold,
                        color = titleColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    MetaLine(article, metaColor, modifier = Modifier.weight(1f, fill = false))
                    BookmarkButton(article.isBookmarked, onBookmark)
                }
            }
        }
    }
}

// ---------------- 复用零件 ----------------

@Composable
private fun UnreadDot(visible: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (visible) cs.primary else Color.Transparent)
    )
}

@Composable
private fun MetaLine(article: Article, color: Color, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${article.sourceName} · ${formatRelativeTime(article.pubDate)}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (article.folder != "默认") {
            Spacer(Modifier.width(6.dp))
            Surface(color = cs.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                Text(
                    article.folder,
                    color = cs.onTertiaryContainer,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun BookmarkButton(checked: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = if (checked) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
            contentDescription = if (checked) "取消收藏" else "收藏",
            tint = if (checked) cs.tertiary else cs.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * 缩略图。
 *
 * [showPlaceholder] 控制「图还没到位时要不要垫一块底色」：
 * - `true`（杂志模式的大图）：垫浅灰底 —— Coil 加载期间不会出现「白洞」，淡入更稳；
 * - `false`（卡片模式的 84dp 小方图）：**什么都不垫**。这里垫灰底是为了「稳定」，
 *   但卡片上那块灰方块在浅色主题下跟正文背景几乎一个色，看着就像是「一张加载失败的图」，
 *   非常廉价 —— 用户明确反馈过「没有图片就不要显示灰色占位」。
 *   小图加载很快，不值得为那零点几秒的稳定感换一个丑方块。
 */
@Composable
private fun ArticleImage(
    url: String?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    showPlaceholder: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(250)
            .build()
    }
    val base = if (showPlaceholder) modifier.clip(shape).background(cs.surfaceVariant)
               else modifier.clip(shape)
    Box(modifier = base) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
