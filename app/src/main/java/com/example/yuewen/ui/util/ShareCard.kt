package com.example.yuewen.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.yuewen.data.model.Article
import java.io.File

/**
 * 把一篇文章渲染成一张「纸感分享卡片」PNG，保存到 cache/share 并通过 FileProvider 拿到可分享的 Uri。
 * 用于详情页的「分享卡片」功能（视觉向，符合极简清新的调性）。
 */
object ShareCard {

    /** 卡片右下角署名的默认文案（用户清空输入时回退到它）。 */
    const val DEFAULT_FOOTER = "阅闻 · 极简新闻阅读"

    /**
     * @param footer 右下角署名，v1.7.0 起可自定义（保存在设置里，下次预填）。
     */
    fun generate(context: Context, article: Article, footer: String = DEFAULT_FOOTER): Uri? {
        return try {
            val W = 1080
            val accent = Color.parseColor("#1D9E75")
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 66f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.parseColor("#1A1A1A")
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; color = accent }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; color = Color.parseColor("#333333") }
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 32f; color = Color.parseColor("#9A9A9A"); textAlign = Paint.Align.RIGHT
            }

            val side = 80f
            val titleLines = wrap(titlePaint, article.title, W.toFloat() - side * 2)
            val raw = (if (article.summary.isNotBlank()) article.summary else article.content).let { stripHtml(it) }
            val bodyText = if (raw.length > 240) raw.take(240) + "…" else raw
            val bodyLines = wrap(bodyPaint, bodyText, W.toFloat() - side * 2)

            val lineH1 = 88f
            val lineH2 = 60f
            val gap = 40f
            val titleH = titleLines.size * lineH1
            val bodyH = bodyLines.size * lineH2
            val H = (side + titleH + gap + lineH2 + gap + bodyH + 100f + side).toInt()

            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.parseColor("#FBF8F2")) // 纸感米白

            val bar = Paint().apply { color = accent }
            canvas.drawRect(0f, 0f, W.toFloat(), 16f, bar)

            var y = side + lineH1 * 0.65f
            titleLines.forEach { line ->
                canvas.drawText(line, side, y, titlePaint)
                y += lineH1
            }
            y += gap
            canvas.drawText("${article.sourceName} · 阅闻", side, y, metaPaint)
            y += gap + lineH2 * 0.65f
            bodyLines.forEach { line ->
                canvas.drawText(line, side, y, bodyPaint)
                y += lineH2
            }
            canvas.drawText(footer.ifBlank { DEFAULT_FOOTER }, W - side, H - side * 0.55f, brandPaint)

            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "card_${System.currentTimeMillis()}.png")
            bmp.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
            bmp.recycle()

            FileProvider.getUriForFile(context, "com.example.yuewen.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    /** 按最大宽度把文本折成多行（优先在空格处断行）。 */
    private fun wrap(paint: Paint, text: String, maxW: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxW, null)
            if (count <= 0) break
            var end = start + count
            if (end < text.length && !text[end].isWhitespace()) {
                val sp = text.lastIndexOf(' ', end - 1)
                if (sp > start) end = sp + 1
            }
            lines.add(text.substring(start, end).trimEnd())
            start = end
        }
        return lines
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
