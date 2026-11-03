package com.example.yuewen.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条新闻文章。用 link 作为主键（RSS 里通常唯一）。
 * pubDate 存 epoch millis，避免 Room 类型转换麻烦。
 */
@Entity(tableName = "articles")
data class Article(
    @PrimaryKey val link: String,
    val title: String,
    val summary: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val sourceName: String = "",
    val category: String = "推荐",
    val pubDate: Long = 0L,
    val isBookmarked: Boolean = false,
    val isRead: Boolean = false,
    val readAt: Long = 0L,
    val folder: String = "默认",
    val sourceId: String = "",
    /** 本地抽取的正文全文（阅读模式）。空字符串表示尚未抽取。 */
    val fullText: String = "",
    /** 是否已尝试抽取过正文（避免每次进详情都重复联网）。 */
    val fullFetched: Boolean = false,
    /**
     * 阅读进度（千分比 0~1000）。长文读到一半退出，下次进来能接着读。
     * 存比例而不是像素：换字号 / 换行距 / 换屏幕后总高度会变，存比例才跳得准。
     */
    val readProgress: Int = 0,
    /**
     * 加入「稍后读」队列的时间戳（0 = 不在队列里）。**v2.8 新增。**
     *
     * 为什么存时间戳而不是 `isQueued: Boolean`：
     * 队列的核心语义是**有顺序**（先加的排前面），布尔值排不出顺序来。
     * 一个字段同时承担「在不在队列」和「什么时候加的」，也不会有
     * 「isQueued=true 但 queuedAt=0」这种自相矛盾的状态。
     *
     * ⚠️ **它和 [isBookmarked] 是正交的两件事**，可以同时为真：
     * 收藏 = 「这篇我要留着」，稍后读 = 「这篇我待会儿读」。
     * 所以**所有「删掉非收藏文章」的清理逻辑都必须同时放过 queuedAt > 0 的行**
     * （见 [com.example.yuewen.data.db.ArticleDao.pruneTo] / clearCache / deleteBySource）——
     * 漏掉任何一处，用户排好的队列都会在某次刷新后被静默清空。
     */
    val queuedAt: Long = 0L
)
