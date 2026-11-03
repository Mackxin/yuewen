package com.example.yuewen.ui.util

/**
 * 「文内查找」的纯计算部分（v2.8）。
 *
 * 为什么单独抽出来：搜索命中的**段号**必须和
 * 朗读跟随 / 大纲跳转 / 高亮共用同一套编号（0 = 标题，1.. = 正文文本段，图片不占号），
 * 一旦这里算错，表现是「搜索结果点下去跳到别的地方」——
 * 而这属于纯字符串 / 纯下标运算，正好是可以脱离设备离线断言的那类代码。
 *
 * 界面部分（输入框、上下箭头、给命中的段落上底色）不在这里，见 DetailScreen。
 */
object FindInArticle {

    /**
     * 找出所有命中的段号，按出现顺序（不是按段号大小 —— 顺序就是文档顺序）。
     *
     * @param paras 段落序列，**必须**是 `speakParas` 那一份（含标题、不含图片）
     * @param query 关键词。空白串返回空结果（不是匹配所有段）
     */
    fun hits(paras: List<String>, query: String): List<Int> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return paras.indices.filter { paras[it].contains(q, ignoreCase = true) }
    }

    /**
     * 在一段文字里找出所有命中区间 `[start, endInclusive]`。
     *
     * 只在**当前命中的那一段**里划线，所以这里不做跨段处理。
     * 命中区间不重叠：找到一处就从它后面接着找，不会在原地反复命中同一个字符。
     */
    fun ranges(text: String, query: String): List<IntRange> {
        val q = query.trim()
        if (q.isEmpty() || text.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var from = 0
        while (from <= text.length - q.length) {
            val at = text.indexOf(q, from, ignoreCase = true)
            if (at < 0) break
            out += at until at + q.length
            from = at + q.length
        }
        return out
    }

    /**
     * 环形步进：`pos + delta` 越界时绕回另一端。
     *
     * 用环绕而不是夹住 —— 从头一条按「上一个」应该到末尾，
     * 而不是原地不动（原地不动看起来就像按钮坏了）。
     * 空结果返回 0，调用方靠 [hits] 是否为空决定按钮可不可点。
     */
    fun step(pos: Int, delta: Int, size: Int): Int {
        if (size <= 0) return 0
        return ((pos + delta) % size + size) % size
    }
}
