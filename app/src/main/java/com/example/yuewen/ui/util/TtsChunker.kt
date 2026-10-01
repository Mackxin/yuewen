package com.example.yuewen.ui.util

/**
 * 把长文切成语义完整的朗读片段。
 *
 * 为什么必须切：`TextToSpeech.speak()` 单次输入有长度上限
 * （`getMaxSpeechInputLength()`，绝大多数引擎是 4000 字符），
 * 超长文本会被静默截断——表现就是「读到一半突然没了」。
 * 另外整篇一次性丢给引擎，中途想停也要等很久才响应，切成小段后停止是即时的。
 *
 * 这里不依赖任何 Android API，纯字符串处理，所以能在 JVM 上直接跑断言
 * （见 NewsApp/tools/jvmtest）。
 */
object TtsChunker {

    /** 每个片段的字符上限。取 800 是「够长不至于碎、够短停得干脆」的折中。 */
    const val MAX = 800

    /** 句末标点：优先在这些字符处断开，读起来才自然。 */
    private const val BREAKS = "。！？；…!?;\n"

    fun split(text: String, max: Int = MAX): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        if (max <= 0) return listOf(t)
        if (t.length <= max) return listOf(t)

        val out = ArrayList<String>()
        var start = 0
        while (start < t.length) {
            var end = minOf(start + max, t.length)
            if (end < t.length) {
                // 在本片段范围内找最后一个句末标点；找不到（长段无标点）就硬切
                val window = t.substring(start, end)
                val cut = window.indexOfLast { it in BREAKS }
                // 标点太靠前就不采用，否则会切出一堆一两句的碎渣
                if (cut >= max / 3) end = start + cut + 1
            }
            val piece = t.substring(start, end).trim()
            if (piece.isNotEmpty()) out.add(piece)
            start = end
        }
        return out
    }

    /**
     * 组装朗读用的正文：标题 + 正文。
     * 标题后面补一个句号，否则引擎会把标题和正文第一句连读成一个怪句子。
     */
    fun compose(title: String, body: String): String {
        val t = title.trim()
        val b = body.trim()
        return when {
            t.isEmpty() -> b
            b.isEmpty() -> t
            else -> if (t.last() in BREAKS) "$t$b" else "$t。$b"
        }
    }
}
