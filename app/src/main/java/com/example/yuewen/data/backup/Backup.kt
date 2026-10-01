package com.example.yuewen.data.backup

import com.example.yuewen.data.model.Article
import com.example.yuewen.data.model.FeedSource
import com.example.yuewen.data.model.Note
import com.example.yuewen.data.model.sanitizeSources
import com.example.yuewen.data.model.sourceIdOf
import com.example.yuewen.data.util.Json
import com.example.yuewen.data.util.asArray
import com.example.yuewen.data.util.asBooleanOr
import com.example.yuewen.data.util.asLongOr
import com.example.yuewen.data.util.asObject
import com.example.yuewen.data.util.asString

/**
 * 备份与恢复（v2.0）。
 *
 * 备份成一个 **JSON 文件**（用户自己选保存位置，走系统的文件选择器）。
 * 里面装四样东西：订阅源、收藏、摘录笔记、以及个性化设置。
 *
 * 刻意**不备份正文缓存（fullText）**：
 * - 它体积最大（几百篇文章能到几十 MB），而正文随时可以联网重新抓；
 * - 备份文件小才方便丢进网盘、微信传给自己。
 *
 * 格式：
 * ```json
 * { "app":"yuewen", "version":1, "exportedAt":1690000000000,
 *   "sources":[{"id":"…","name":"…","url":"…","category":"…","enabled":true}],
 *   "bookmarks":[{"link":"…","title":"…",…}],
 *   "notes":[{"id":"…","link":"…","quote":"…","note":"…","createdAt":0}],
 *   "settings":{"theme":"dark","list_mode":"card"} }
 * ```
 */
object Backup {

    /** 备份格式版本。以后改字段时递增，[parse] 里按版本兼容旧文件。 */
    const val VERSION = 1

    const val APP_TAG = "yuewen"

    /** 解析出来的备份内容。 */
    data class Bundle(
        val sources: List<FeedSource> = emptyList(),
        val bookmarks: List<Article> = emptyList(),
        val notes: List<Note> = emptyList(),
        val settings: Map<String, String> = emptyMap(),
        val exportedAt: Long = 0L,
        val app: String = "",
        val version: Int = 0
    ) {
        val total: Int get() = sources.size + bookmarks.size + notes.size
    }

    // ------------------------------------------------------------------ 导出

    fun export(
        sources: List<FeedSource>,
        bookmarks: List<Article>,
        notes: List<Note>,
        settings: Map<String, String>,
        now: Long = System.currentTimeMillis()
    ): String {
        val sourcesJson = Json.array(sources.map { s ->
            Json.Raw(
                Json.obj(
                    "id" to s.id,
                    "name" to s.name,
                    "url" to s.url,
                    "category" to s.category,
                    "enabled" to s.enabled
                )
            )
        })
        val bookmarksJson = Json.array(bookmarks.map { a ->
            Json.Raw(
                Json.obj(
                    "link" to a.link,
                    "title" to a.title,
                    "summary" to a.summary,
                    "imageUrl" to a.imageUrl,
                    "sourceName" to a.sourceName,
                    "category" to a.category,
                    "pubDate" to a.pubDate,
                    "isRead" to a.isRead,
                    "readAt" to a.readAt,
                    "folder" to a.folder,
                    "readProgress" to a.readProgress
                )
            )
        })
        val notesJson = Json.array(notes.map { n ->
            Json.Raw(
                Json.obj(
                    "id" to n.id,
                    "link" to n.link,
                    "articleTitle" to n.articleTitle,
                    "sourceName" to n.sourceName,
                    "quote" to n.quote,
                    "note" to n.note,
                    "createdAt" to n.createdAt
                )
            )
        })
        val settingsJson = Json.obj(*settings.map { (k, v) -> k to v }.toTypedArray())

        return Json.obj(
            "app" to APP_TAG,
            "version" to VERSION,
            "exportedAt" to now,
            "sources" to Json.Raw(sourcesJson),
            "bookmarks" to Json.Raw(bookmarksJson),
            "notes" to Json.Raw(notesJson),
            "settings" to Json.Raw(settingsJson)
        )
    }

    // ------------------------------------------------------------------ 导入

    /** 解析备份文件。不是本 App 的文件 / 结构不对时返回 null。 */
    fun parse(text: String): Bundle? {
        // 直接落到 Map 上取值：Json.J.O 只是个包了一层的 data class，
        // 这里需要的全是「按下标取 / 判断有没有这个键」，用 Map 更自然。
        val root = (Json.parse(text) as? Json.J.O)?.v ?: return null
        val app = root["app"]?.asString().orEmpty()
        // 容错：早期只要求有 sources 或 notes 字段就当成本 App 的备份
        val looksOurs = app == APP_TAG || root.containsKey("sources") || root.containsKey("notes")
        if (!looksOurs) return null

        // v2.0.2：解析出来之后统一走一遍去重/补 id。
        // 备份文件可能来自旧版本（里面还是 s0/s1 这种会撞车的 id），
        // 甚至可能是手改过的 —— 这一步保证「恢复进本机的阅源」id 一定唯一，
        // 否则恢复之后会出现「点某个源，结果作用到另一个源」的串行问题。
        val sources = sanitizeSources(
            root["sources"]?.asArray().orEmpty().mapNotNull { node ->
                val o = node.asObject()
                val url = o["url"]?.asString().orEmpty()
                if (url.isBlank()) return@mapNotNull null
                FeedSource(
                    id = o["id"]?.asString().orEmpty().ifBlank { sourceIdOf(url) },
                    name = o["name"]?.asString().orEmpty().ifBlank { url.substringAfter("//").substringBefore('/') },
                    url = url,
                    category = o["category"]?.asString().orEmpty().ifBlank { "未分类" },
                    enabled = o["enabled"]?.asBooleanOr(true) ?: true
                )
            }
        )

        val bookmarks = root["bookmarks"]?.asArray().orEmpty().mapNotNull { node ->
            val o = node.asObject()
            val link = o["link"]?.asString().orEmpty()
            if (link.isBlank()) return@mapNotNull null
            Article(
                link = link,
                title = o["title"]?.asString().orEmpty(),
                summary = o["summary"]?.asString().orEmpty(),
                imageUrl = o["imageUrl"]?.asString()?.takeIf { it.isNotBlank() },
                sourceName = o["sourceName"]?.asString().orEmpty(),
                category = o["category"]?.asString().orEmpty().ifBlank { "推荐" },
                pubDate = o["pubDate"]?.asLongOr(0L) ?: 0L,
                isBookmarked = true,
                isRead = o["isRead"]?.asBooleanOr(false) ?: false,
                readAt = o["readAt"]?.asLongOr(0L) ?: 0L,
                folder = o["folder"]?.asString().orEmpty().ifBlank { "默认" },
                readProgress = (o["readProgress"]?.asLongOr(0L) ?: 0L).toInt()
            )
        }

        val notes = root["notes"]?.asArray().orEmpty().mapNotNull { node ->
            val o = node.asObject()
            val quote = o["quote"]?.asString().orEmpty()
            val note = o["note"]?.asString().orEmpty()
            if (quote.isBlank() && note.isBlank()) return@mapNotNull null
            val created = o["createdAt"]?.asLongOr(0L) ?: 0L
            Note(
                id = o["id"]?.asString().orEmpty().ifBlank { "n$created${quote.hashCode()}" },
                link = o["link"]?.asString().orEmpty(),
                articleTitle = o["articleTitle"]?.asString().orEmpty(),
                sourceName = o["sourceName"]?.asString().orEmpty(),
                quote = quote,
                note = note,
                createdAt = created
            )
        }

        val settings = root["settings"]?.asObject().orEmpty()
            .mapValues { (_, v) -> v.asString() }
            .filterValues { it.isNotBlank() }

        return Bundle(
            sources = sources,
            bookmarks = bookmarks,
            notes = notes,
            settings = settings,
            exportedAt = root["exportedAt"]?.asLongOr(0L) ?: 0L,
            app = app,
            version = (root["version"]?.asLongOr(0L) ?: 0L).toInt()
        )
    }

    /** 备份文件的默认名（带日期，方便用户存多份）。 */
    fun fileName(now: Long = System.currentTimeMillis()): String {
        val d = java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return "阅闻备份-$d.json"
    }
}
