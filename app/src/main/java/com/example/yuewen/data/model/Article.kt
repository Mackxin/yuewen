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
    val readProgress: Int = 0
)
