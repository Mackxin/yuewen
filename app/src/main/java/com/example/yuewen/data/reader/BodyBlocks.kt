package com.example.yuewen.data.reader

import com.example.yuewen.data.model.BodyBlock
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import java.net.URI

/**
 * 正文块解析器：任意输入（完整网页 HTML / 轻量 HTML / 旧版纯文本）→ 块序列。
 *
 * 这是「文章里图片显示不全」的正解所在：以前全文抽取完是纯文本，
 * 图片在抽取阶段就被剥掉了；现在全链路都保图——
 *   网页 HTML → [parse]（递归遍历，img 单独成块）→ [toLightHtml]（存库）
 *   → 渲染端再 [parse] 回块序列画出来。
 *
 * 图片提取规则对标主流阅读器（FeedMe / Palu 等）：
 *   - 属性按 data-src → data-original → data-lazy-src → data-actualsrc → data-lazyload
 *     → data-original-src → src 的顺序找（中文源大量懒加载，src 常是占位图）
 *   - srcset / data-srcset 取第一张
 *   - 相对地址用文章链接补全成绝对地址
 *   - 过滤 data:URI 和 width≤3 的追踪像素
 *
 * 纯 JVM 实现（jsoup 不依赖 Android），tools/jvmtest 可直接测。
 */
object BodyBlocks {

    /** 图片懒加载属性，按可靠性从高到低排。 */
    private val IMG_ATTRS = listOf(
        "data-src", "data-original", "data-lazy-src", "data-actualsrc",
        "data-lazyload", "data-original-src", "src"
    )

    private val SKIP_TAGS = setOf("script", "style", "nav", "aside", "form", "noscript", "svg", "button", "select", "input")
    private val BLOCK_TAGS = setOf("p", "li", "blockquote", "pre", "figcaption")
    private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    /**
     * 解析入口。
     * @param input 轻量 HTML / 完整网页 HTML / 旧版纯文本
     * @param baseUrl 用于补全图片相对地址（一般传文章链接）
     */
    fun parse(input: String, baseUrl: String? = null): List<BodyBlock> {
        val t = input.trim()
        if (t.isEmpty()) return emptyList()
        return if (looksLikeHtml(t)) {
            parseHtml(t, baseUrl)
        } else {
            // 旧版纯文本（v1.6 及以前存的 fullText / 摘要）：
            // toPlainText 的输出是「段落 \n\n、列表行 \n」，统一展平成段
            t.split(Regex("\n\\s*\n|\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { BodyBlock.Paragraph(it) }
        }
    }

    /** 把块序列压成轻量 HTML（存库格式，体积小、渲染端可无损还原）。 */
    fun toLightHtml(blocks: List<BodyBlock>): String = buildString {
        for (b in blocks) {
            when (b) {
                is BodyBlock.Heading -> append("<h3>").append(escape(b.text)).append("</h3>\n")
                is BodyBlock.Paragraph -> append("<p>").append(escape(b.text)).append("</p>\n")
                is BodyBlock.Image -> append("<img src=\"").append(escape(b.url)).append("\"/>\n")
            }
        }
    }.trim()

    /** 全文有效文本长度（图片不计），用于判断抽取质量。 */
    fun textLength(blocks: List<BodyBlock>): Int =
        blocks.sumOf { BodyBlocks.textOf(it)?.length ?: 0 }

    fun textOf(b: BodyBlock): String? = when (b) {
        is BodyBlock.Heading -> b.text
        is BodyBlock.Paragraph -> b.text
        is BodyBlock.Image -> null
    }

    // ------------------------------------------------------------------ 内部

    private fun looksLikeHtml(t: String): Boolean {
        // 旧纯文本里也可能出现零星 "<"（如公式），必须同时看到标签结构才按 HTML 处理
        return Regex("</?(p|img|h[1-6]|div|br|ul|ol|blockquote|pre|span|a)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(t.take(4000))
    }

    private fun parseHtml(html: String, baseUrl: String?): List<BodyBlock> {
        val doc = Jsoup.parse(html, baseUrl ?: "")
        val out = ArrayList<BodyBlock>()
        walk(doc.body(), out, baseUrl ?: "")
        return out
    }

    /**
     * 按文档顺序递归遍历。关键点：
     *  - img 一律单独成块（正文里图和文字的相对位置基本保得住）
     *  - 块级元素若无嵌套块子元素，则「块内图 → 块文本」一起输出，不再下钻
     *  - div / section 等容器只下钻不输出
     */
    private fun walk(node: Node, out: MutableList<BodyBlock>, baseUri: String) {
        if (node !is Element) return
        val tag = node.tagName().lowercase()
        when {
            tag in SKIP_TAGS -> return
            tag == "img" -> {
                imgUrl(node, baseUri)?.let { out.add(BodyBlock.Image(it)) }
            }
            tag in HEADING_TAGS -> {
                collectImages(node, baseUri, out)
                val t = node.text().trim()
                if (t.isNotEmpty()) out.add(BodyBlock.Heading(t))
            }
            tag in BLOCK_TAGS -> {
                if (hasBlockChild(node)) {
                    // 图文混排容器（如 li 里套 p）：下钻保顺序
                    node.childNodes().forEach { walk(it, out, baseUri) }
                } else {
                    collectImages(node, baseUri, out)
                    val t = node.text().trim()
                    if (t.isNotEmpty()) {
                        out.add(BodyBlock.Paragraph(if (tag == "li") "· $t" else t))
                    }
                }
            }
            tag == "br" -> return
            else -> node.childNodes().forEach { walk(it, out, baseUri) } // div/section/body 等容器
        }
    }

    /** 无嵌套块的块级元素：把里面的图先输出（图常常占整段或配图在开头），再输出文字。 */
    private fun collectImages(el: Element, baseUri: String, out: MutableList<BodyBlock>) {
        el.getElementsByTag("img").forEach { img ->
            imgUrl(img, baseUri)?.let { out.add(BodyBlock.Image(it)) }
        }
    }

    private fun hasBlockChild(el: Element): Boolean =
        el.children().any { c ->
            val tn = c.tagName().lowercase()
            tn in BLOCK_TAGS || tn in HEADING_TAGS
        }

    /** 从一个 <img> 元素里挑出最靠谱的图片地址；不可用返回 null。 */
    fun imgUrl(img: Element, baseUri: String): String? {
        // width ≤ 3 的基本是追踪像素（有些站还是字符串 "3px"）
        val w = img.attr("width").trim().removeSuffix("px").toIntOrNull() ?: -1
        if (w in 1..3) return null

        var raw = IMG_ATTRS.firstNotNullOfOrNull { img.attr(it).trim().takeIf { v -> v.isNotEmpty() } }
        if (raw == null) {
            // srcset 取第一张（"url 640w, url2 1280w"）
            raw = img.attr("srcset").trim().ifEmpty { img.attr("data-srcset").trim() }
                .split(",").firstOrNull()?.trim()?.split(Regex("\\s+"))?.firstOrNull()
        }
        if (raw.isNullOrEmpty() || raw.startsWith("data:")) return null

        if (raw.startsWith("//")) raw = "https:$raw"
        return resolve(raw, baseUri)
    }

    private fun resolve(raw: String, baseUri: String): String = try {
        if (baseUri.isBlank()) raw
        else URI(baseUri).resolve(raw.replace(" ", "%20")).toString()
    } catch (_: Exception) {
        raw
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
