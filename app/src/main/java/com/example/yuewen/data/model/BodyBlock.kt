package com.example.yuewen.data.model

/**
 * 正文渲染的最小「块」模型。
 *
 * 为什么要有它：以前全文抽取完是**纯文本**，`Text(body)` 一把梭渲染——
 * 图片信息在抽取阶段就被剥掉了，这就是「文章里只有一张头图、正文一张图都没有」的根源。
 * 现在正文一律解析成块序列：文字段 + 图片段交替，渲染端按块画出来。
 *
 * 注意：这是纯数据模型，**不要**挪进带 Room 注解的 DAO 文件里，
 * 否则 tools/jvmtest 的纯 JVM 编译会失败（那条规则踩过）。
 */
sealed interface BodyBlock {

    /** 小标题（h1~h6），渲染时加粗放大。 */
    data class Heading(val text: String) : BodyBlock

    /** 普通文字段落。 */
    data class Paragraph(val text: String) : BodyBlock

    /** 图片，url 已补全为绝对地址。 */
    data class Image(val url: String) : BodyBlock

    companion object {
        /** 朗读 / 阅读跟随用的是文本段（标题 + 段落），图片跳过。 */
        fun textOf(b: BodyBlock): String? = when (b) {
            is Heading -> b.text
            is Paragraph -> b.text
            is Image -> null
        }
    }
}
