package com.example.yuewen.data.util

/**
 * 首页「关键词胶囊」的纯逻辑（v2.4）。
 *
 * 为什么单独放一个文件、而不是写在 ViewModel 里：
 * 这里全是字符串处理，**不碰任何 `android.*` / Compose**，
 * 所以能直接挂进 `tools/jvmtest` 离线跑（本机没有真机也没有模拟器，
 * 这类「光看代码看不出对错」的逻辑只能靠断言兜住）。
 */

/** 最多允许多少个关键词（再多首页那一行就只剩滚动条了）。 */
const val HOME_KEYWORD_LIMIT = 10

/** 单个关键词最长几个字。 */
const val HOME_KEYWORD_MAX_LEN = 8

/**
 * 内置的默认关键词。
 *
 * 刻意挑了四个「一看就懂、删掉也不心疼」的常见词：
 * 新装好 App 就能在首页看到这一行，知道还有这么个东西；
 * 不想要的话在「设置 → 外观 → 首页筛选 → 首页关键词」里删空，那一行会自己消失。
 */
val DEFAULT_HOME_KEYWORDS = listOf("AI", "手机", "汽车", "股票")

/**
 * 清洗用户填的关键词：去首尾空格 → 丢掉空串 → 去重（**忽略大小写**）
 * → 截断过长的 → 限制总条数。
 *
 * 为什么要「忽略大小写去重」：`AI` 和 `ai` 在搜索里筛出来的是同一批文章，
 * 两个胶囊并排摆着，只会让人以为是两个不同的东西。
 *
 * 读取设置时也会先过一遍这个函数（和 `sanitizeSources` 一个思路）——
 * 手改过的备份文件、或者早期版本留下的脏数据，一进来就被修干净。
 */
fun sanitizeKeywords(list: List<String>): List<String> {
    val out = ArrayList<String>(list.size)
    val seen = HashSet<String>()
    for (raw in list) {
        val kw = raw.trim().take(HOME_KEYWORD_MAX_LEN)
        if (kw.isEmpty()) continue
        // add 返回 false = 已经收过（忽略大小写之后）同名的词
        if (!seen.add(kw.lowercase())) continue
        out.add(kw)
        if (out.size >= HOME_KEYWORD_LIMIT) break
    }
    return out
}

/**
 * 判断一篇文章是否命中某个关键词。
 *
 * 匹配范围是 **标题 / 摘要 / 正文**，和「闻件 → 搜索」保持一致：
 * 只匹配标题的话，「汽车」这种经常只出现在正文里的词会一篇都筛不出来，
 * 用户会觉得「这个关键词明明有文章，怎么点了是空的」。
 *
 * 关键词为空 = 不筛（直接 true），调用方就不必再套一层 `if`。
 */
fun matchesKeyword(title: String, summary: String, fullText: String, keyword: String): Boolean {
    val kw = keyword.trim()
    if (kw.isEmpty()) return true
    return title.contains(kw, true) ||
            summary.contains(kw, true) ||
            fullText.contains(kw, true)
}
