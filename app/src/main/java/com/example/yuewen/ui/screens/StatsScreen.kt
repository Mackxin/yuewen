package com.example.yuewen.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.model.ReadStats
import com.example.yuewen.ui.viewmodel.StatsViewModel

/**
 * 阅读统计。
 *
 * 全部基于本地数据算，不联网：
 * - 已读篇数与时间来自 `readAt`（打开文章时写的时间戳）
 * - 阅读时长按正文字符数 ÷ 300 字/分钟估算（中文粗略 300 字/分钟）
 * - 来源排行按 `sourceName` 分组计数
 */
@Composable
fun StatsScreen(app: YuewenApplication, onBack: () -> Unit) {
    val vm: StatsViewModel = viewModel(factory = StatsViewModel.provide(app))
    val stats by vm.stats.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        // 顶栏
        Surface(color = cs.background, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = cs.onBackground)
                }
                Text(
                    "阅读统计",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onBackground
                )
                Spacer(Modifier.weight(1f))
            }
        }

        if (vm.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = cs.primary)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 24.dp)
        ) {
            HeroCard(streak = streak, today = stats.todayRead)

            Spacer(Modifier.height(12.dp))
            TripleRow(
                a = Triple("累计已读", "${stats.totalRead}", "篇"),
                b = Triple("本周已读", "${stats.weekRead}", "篇"),
                c = Triple("估算时长", "${stats.minutes}", "分钟")
            )

            Spacer(Modifier.height(12.dp))
            ChartCard(stats)

            if (stats.topSources.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SourceRankCard(stats)
            }

            Spacer(Modifier.height(12.dp))
            FooterCard(stats)

            Spacer(Modifier.height(14.dp))
            Text(
                "统计只保存在本机，不会上传。阅读时长按正文字数 ÷ 300 字/分钟估算，仅供参考。",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

/** 顶部大卡：连续阅读天数 + 今日进度。 */
@Composable
private fun HeroCard(streak: Int, today: Int) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (streak > 0) "连续阅读 $streak 天" else "今天还没开始读",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                    fontWeight = FontWeight.Bold,
                    color = cs.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (streak >= 7) "已经坚持一周了，继续保持 ✦" else "每天读一点，节奏比数量重要",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$today",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp),
                    fontWeight = FontWeight.Bold,
                    color = cs.onPrimaryContainer
                )
                Text(
                    "今日已读",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun TripleRow(a: Triple<String, String, String>, b: Triple<String, String, String>, c: Triple<String, String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatBox(a, Modifier.weight(1f))
        StatBox(b, Modifier.weight(1f))
        StatBox(c, Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(data: Triple<String, String, String>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surface, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 12.dp)) {
            Text(
                data.second,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                fontWeight = FontWeight.Bold,
                color = cs.primary
            )
            Text(
                data.third,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                data.first,
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurface
            )
        }
    }
}

/**
 * 近 7 天柱状图。
 *
 * 用 Row + Box 堆高度实现，而不是 Canvas ——
 * 柱子上下的数字/星期标签用真正的 Text，能跟着字体缩放和无障碍服务走，
 * Canvas 里画文字还得额外引 TextMeasurer，得不偿失。
 */
@Composable
private fun ChartCard(stats: ReadStats) {
    val cs = MaterialTheme.colorScheme
    val peak = stats.peak
    val maxBar = 72.dp

    Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Insights, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "近 7 天",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "共 ${stats.last7.sumOf { it.count }} 篇",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                stats.last7.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (day.count > 0) "${day.count}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        val h = if (day.count <= 0) 3.dp
                        else maxBar * (day.count.toFloat() / peak).coerceIn(0.12f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(h)
                                .clip(MaterialTheme.shapes.extraSmall)
                                // 今天那根用点缀色，一眼能看出「就是今天」
                                .background(if (day.isToday) cs.tertiary else cs.primary.copy(alpha = 0.55f))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            day.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (day.isToday) cs.tertiary else cs.onSurfaceVariant,
                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRankCard(stats: ReadStats) {
    val cs = MaterialTheme.colorScheme
    val top = stats.topSources
    val max = maxOf(1, top.maxOfOrNull { it.c } ?: 1)

    Surface(color = cs.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.RssFeed, contentDescription = null, tint = cs.secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "读得最多的来源",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
            }
            Spacer(Modifier.height(14.dp))

            top.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        row.sourceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface,
                        maxLines = 1,
                        modifier = Modifier.width(96.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(cs.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(row.c.toFloat() / max)
                                .height(8.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(cs.secondary)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${row.c}",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.width(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FooterCard(stats: ReadStats) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            MiniRow(Icons.Filled.Bookmark, cs.tertiary, "收藏文章", "${stats.bookmarked} 篇")
            Spacer(Modifier.height(10.dp))
            MiniRow(Icons.Filled.RssFeed, cs.secondary, "启用订阅源", "${stats.sourceCount} 个")
            Spacer(Modifier.height(10.dp))
            MiniRow(Icons.Filled.Storage, cs.primary, "本地缓存文章", "${stats.cached} 篇")
        }
    }
}

@Composable
private fun MiniRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = cs.onSurface)
    }
}
