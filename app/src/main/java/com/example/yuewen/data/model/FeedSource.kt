package com.example.yuewen.data.model

/**
 * 一个 RSS 新闻源。category 决定它出现在首页哪个分类下。
 * 默认带一批免费源，用户也能在「设置-新闻源管理」里增删。
 */
data class FeedSource(
    val id: String,
    val name: String,
    val url: String,
    val category: String = "推荐",
    val enabled: Boolean = true
)

/**
 * 由一个订阅地址派生出的**稳定且唯一**的 id（v2.0.2）。
 *
 * 为什么要有它：
 * 早期版本的 id 是「按位置起的序号」（`s0` / `s1` …）。给老用户「增量补种」新推荐源时，
 * 补进来的源沿用了同一套序号，于是**两个不同的源可能拿到同一个 id**。
 *
 * id 重复的后果不是「显示不对」那么简单：
 * - 「阅源」列表用 `id` 当 LazyColumn 的 key，重复 key 会让 Compose 复用错位 ——
 *   表现就是**点某一个源的操作（测试 / 编辑）落到了另一个源上**；
 * - 行内测试结果也用 id 当索引，重复 id 会让结果渲染在别的源那一行下面。
 *
 * 改成从地址派生：同一地址永远得到同一个 id，不同地址（去掉结尾斜杠、忽略大小写后）
 * 必然不同 —— 顺带让「导入备份/OPML 后 id 还能对上」，不再随插入顺序变化。
 *
 * 长度也编进去，是为了让不同长度的地址即使在 hashCode 上撞了，也能被区分开。
 */
fun sourceIdOf(url: String): String {
    val key = feedUrlKey(url)
    return "s${key.length}_${Integer.toHexString(key.hashCode())}"
}

/**
 * 订阅地址的**归一化形式**，用来判断两个地址是不是「同一个源」（v2.3）。
 *
 * 规则：去掉首尾空白 → 去掉结尾的斜杠 → 统一小写。
 *
 * 为什么必须抽成一个函数：以前「OPML 导入」只做了 `trim().trimEnd('/')`，
 * 而「JSON 备份恢复」多做了一步 `lowercase()`，两条导入路径的去重规则不一致 ——
 * 同一个源 `https://x.com/Feed` 与 `https://x.com/feed`，
 * 从 OPML 导会变成两条重复源，从备份导却会被正确去重。用户会看见莫名其妙的重复。
 * 现在两边都调它，行为必然一致；[sanitizeSources] 也用它，读设置时顺手修历史脏数据。
 */
fun feedUrlKey(url: String): String = url.trim().trimEnd('/').lowercase()

/**
 * 读设置时的一道**兜底修复**（纯函数，便于离线测试）。
 *
 * 1. **id 去重**：为空或和前面某条重复的，按地址重新派生一个唯一 id；
 * 2. **地址去重**：同一个地址（忽略大小写与结尾斜杠）只保留第一条 ——
 *    OPML 导入 + 手动添加凑在一起时，很容易出现两条一模一样的源，
 *    它们会各占一行、各有一条测试结果，用户根本分不清点的是哪个。
 *
 * 存量脏数据在「下一次写回设置」时被永久修好，用户不需要做任何操作。
 */
fun sanitizeSources(list: List<FeedSource>): List<FeedSource> {
    if (list.isEmpty()) return list
    val usedIds = HashSet<String>(list.size * 2)
    val usedUrls = HashSet<String>(list.size * 2)
    val out = ArrayList<FeedSource>(list.size)
    for (src in list) {
        val urlKey = feedUrlKey(src.url)
        if (urlKey.isNotEmpty() && !usedUrls.add(urlKey)) continue
        var id = src.id
        if (id.isBlank() || !usedIds.add(id)) {
            var fixed = sourceIdOf(src.url)
            while (!usedIds.add(fixed)) fixed += "_"
            id = fixed
        }
        out.add(if (id == src.id) src else src.copy(id = id))
    }
    return out
}
