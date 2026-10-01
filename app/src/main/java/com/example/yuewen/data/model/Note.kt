package com.example.yuewen.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条摘录 / 笔记（v2.0）。
 *
 * 设计取舍：**只有这一张表，不做「划线」和「笔记」两张**。
 * 因为「划线」本身就是一条 quote 非空、note 为空的记录，
 * 用户之后想给它补一句想法，直接更新 note 字段即可，不用在两处搬家。
 *
 * [quote] 是原文摘出来的那段话（可能为空 —— 允许「只想写个想法」的场景）；
 * [note]  是用户自己的批注。
 */
@Entity(tableName = "notes")
data class Note(
    /** 主键：link + 时间戳，同一篇文章可以有多条。 */
    @PrimaryKey val id: String,
    /** 所属文章链接（用来在详情页里高亮已摘录的文章）。 */
    val link: String,
    /** 文章标题快照：文章被清缓存删掉后，笔记仍然知道自己来自哪篇文章。 */
    val articleTitle: String = "",
    val sourceName: String = "",
    /** 摘录的原文。 */
    val quote: String = "",
    /** 用户批注。 */
    val note: String = "",
    val createdAt: Long = 0L
) {
    /** 列表里显示的那一行主文案。 */
    val display: String get() = note.ifBlank { quote }
}
