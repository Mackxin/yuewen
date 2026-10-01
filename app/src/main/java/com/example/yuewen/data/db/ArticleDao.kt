package com.example.yuewen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.model.SourceCount
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    /**
     * 只插入「本地还没有的」文章（IGNORE 语义）。
     * 刷新时不再 REPLACE 覆盖整行，因此不会把已读/收藏/收藏夹/正文全文冲掉，
     * 同时也避免了「先查再写」的 N+1 查询——刷新明显更快。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(articles: List<Article>)

    /** 一次取回本地已有文章的全部链接，用于批量去重（替代逐条 getByLink）。 */
    @Query("SELECT link FROM articles")
    suspend fun allLinks(): List<String>

    /** 控制库大小：只保留最新的 N 条非收藏文章，保证列表查询始终很快。 */
    @Query(
        "DELETE FROM articles WHERE isBookmarked = 0 AND link NOT IN " +
                "(SELECT link FROM articles WHERE isBookmarked = 0 ORDER BY pubDate DESC LIMIT :keep)"
    )
    suspend fun pruneTo(keep: Int)

    /**
     * 首页信息流：**两级筛选**（分类 + 阅源）。
     *
     * - `category = '推荐'` 表示不限分类（推荐 = 全部）；
     * - `source = ''` 表示不限阅源。
     *
     * v2.0.2 起首页顶栏可以按「阅源」筛，所以查询从只认分类变成了两级条件。
     * 两个条件都是「等于则过滤、特殊值则放行」，一条 SQL 覆盖四种组合。
     */
    @Query(
        "SELECT * FROM articles WHERE (:category = '推荐' OR category = :category) " +
                "AND (:source = '' OR sourceName = :source) ORDER BY pubDate DESC LIMIT 400"
    )
    fun observeHomeFeed(category: String, source: String): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY pubDate DESC LIMIT 300")
    fun observeBookmarks(): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE isBookmarked = 1 AND (:folder = '全部' OR folder = :folder) ORDER BY pubDate DESC LIMIT 300")
    fun observeBookmarksByFolder(folder: String): Flow<List<Article>>

    @Query("SELECT DISTINCT folder FROM articles WHERE isBookmarked = 1 ORDER BY folder ASC")
    fun observeBookmarkFolders(): Flow<List<String>>

    @Query("SELECT * FROM articles WHERE readAt > 0 ORDER BY readAt DESC LIMIT 300")
    fun observeHistory(): Flow<List<Article>>

    // ⚠️ v2.3：两条搜索 SQL 都加了 `ESCAPE '\'`。
    // 配合 NewsRepository 里的 `likePattern()`（会把 `\` `%` `_` 转义一遍），
    // 用户输入的 `%` 或 `_` 才会被当成普通字符 —— 否则 `LIKE '%%%'` 会命中全库，
    // 表现成「搜索失灵」。改这里记得同步改 `data/util/SqlLike.kt`。

    @Query(
        "SELECT * FROM articles WHERE (:category = '推荐' OR category = :category) " +
                "AND (title LIKE :q ESCAPE '\\' OR summary LIKE :q ESCAPE '\\') " +
                "ORDER BY pubDate DESC LIMIT 200"
    )
    fun search(category: String, q: String): Flow<List<Article>>

    /**
     * 本地全文搜索：标题 / 摘要 / 已抽取的正文 / 来源名 全都搜，
     * 并且**不限分类**——用户找东西时不会记得它在哪个分类里。
     */
    @Query(
        "SELECT * FROM articles WHERE title LIKE :q ESCAPE '\\' OR summary LIKE :q ESCAPE '\\' " +
                "OR fullText LIKE :q ESCAPE '\\' OR sourceName LIKE :q ESCAPE '\\' " +
                "ORDER BY pubDate DESC LIMIT 300"
    )
    fun searchAll(q: String): Flow<List<Article>>

    /** 只看某个来源的文章（搜索页点来源标签用）。 */
    @Query("SELECT * FROM articles WHERE sourceName = :name ORDER BY pubDate DESC LIMIT 300")
    fun observeBySource(name: String): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE link = :link")
    fun observeByLink(link: String): Flow<Article?>

    @Query("SELECT * FROM articles WHERE link = :link")
    suspend fun getByLink(link: String): Article?

    @Query("UPDATE articles SET isBookmarked = :value WHERE link = :link")
    suspend fun setBookmarked(link: String, value: Boolean)

    @Query("UPDATE articles SET isRead = 1, readAt = :ts WHERE link = :link")
    suspend fun markRead(link: String, ts: Long)

    @Query("UPDATE articles SET isRead = :value, readAt = CASE WHEN :value = 1 THEN :ts ELSE 0 END WHERE link = :link")
    suspend fun setRead(link: String, value: Boolean, ts: Long)

    @Query("UPDATE articles SET folder = :folder WHERE link = :link")
    suspend fun setFolder(link: String, folder: String)

    /** 保存本地抽取的正文全文，并标记为已尝试抽取（成功/失败都算尝试过）。 */
    @Query("UPDATE articles SET fullText = :text, fullFetched = 1 WHERE link = :link")
    suspend fun setFullText(link: String, text: String)

    /**
     * 给「当初没解析出图片」的旧文章补上图片地址。
     * 老版本因为解析 bug 存了一批无图文章，靠刷新回填，用户不用手动清缓存。
     * 只补空的，不会覆盖已有图片。
     */
    @Query("UPDATE articles SET imageUrl = :url WHERE link = :link AND (imageUrl IS NULL OR imageUrl = '')")
    suspend fun backfillImage(link: String, url: String)

    /** 本地缺图的文章链接（刷新时用来回填）。 */
    @Query("SELECT link FROM articles WHERE imageUrl IS NULL OR imageUrl = ''")
    suspend fun linksWithoutImage(): List<String>

    /** 保存阅读进度（千分比）。 */
    @Query("UPDATE articles SET readProgress = :value WHERE link = :link")
    suspend fun setReadProgress(link: String, value: Int)

    /** 重置抽取标记，便于用户手动「重新获取全文」。 */
    @Query("UPDATE articles SET fullFetched = 0 WHERE link = :link")
    suspend fun resetFullFetched(link: String)

    /** 「全部标为已读」：只动当前筛选范围内的文章（分类 + 阅源两级，和首页列表严格一致）。 */
    @Query(
        "UPDATE articles SET isRead = 1, readAt = :ts WHERE (:category = '推荐' OR category = :category) " +
                "AND (:source = '' OR sourceName = :source)"
    )
    suspend fun markAllReadFiltered(category: String, source: String, ts: Long)

    @Query("UPDATE articles SET readAt = 0 WHERE readAt > 0")
    suspend fun clearHistory()

    @Query("DELETE FROM articles WHERE isBookmarked = 0")
    suspend fun clearCache()

    /** 某分类下的全部链接（详情页「左右滑切换上下篇」用）。 */
    @Query("SELECT link FROM articles WHERE (:category = '推荐' OR category = :category) ORDER BY pubDate DESC LIMIT 400")
    suspend fun linksByCategory(category: String): List<String>

    /** 未读总数（底部导航徽标用）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    /** 某个源已缓存的文章数（源管理里显示 / 删除前提示用）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE sourceId = :id")
    suspend fun countBySource(id: String): Int

    /**
     * 删除某个源的缓存文章，但**保留收藏**（弹窗里承诺过「已收藏会保留」）。
     *
     * v2.0.2 起同时按 `sourceName` 兜底：修 id 重复问题时，少数源会拿到新的 id，
     * 而它早先抓下来的文章里存的还是旧 id —— 只按 id 删会留一地删不掉的孤儿缓存。
     * `:name <> ''` 这个条件不能省：名字为空时必须退化成「只按 id 删」，
     * 否则会把 sourceName 为空的历史文章一并清掉。
     */
    @Query(
        "DELETE FROM articles WHERE isBookmarked = 0 " +
                "AND (sourceId = :id OR (:name <> '' AND sourceName = :name))"
    )
    suspend fun deleteBySource(id: String, name: String)

    @Query("SELECT COUNT(*) FROM articles WHERE isBookmarked = 0")
    suspend fun cachedCount(): Int

    @Query("SELECT COUNT(*) FROM articles WHERE readAt > 0")
    suspend fun observeHistoryCount(): Int

    // ==================== v1.6：阅读统计 ====================

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM articles WHERE isBookmarked = 1")
    suspend fun bookmarkCount(): Int

    @Query("SELECT COUNT(*) FROM articles WHERE readAt > 0")
    suspend fun readCount(): Int

    /** 某个时间点之后读过的篇数（今日 / 本周统计用）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE readAt >= :from")
    suspend fun readCountSince(from: Long): Int

    /**
     * 累计读过的正文字符数（用来估算阅读时长）。
     * 优先用抽取到的全文，没有就退回 feed 里的 content；两者都空则记 0。
     */
    @Query(
        "SELECT COALESCE(SUM(LENGTH(CASE WHEN fullText <> '' THEN fullText ELSE content END)), 0) " +
                "FROM articles WHERE readAt > 0"
    )
    suspend fun readChars(): Int

    /** 全部阅读时间戳：近 7 天的柱状图在 Kotlin 侧分桶算，比写 7 条 SQL 清爽。 */
    @Query("SELECT readAt FROM articles WHERE readAt > 0 ORDER BY readAt DESC")
    suspend fun readTimestamps(): List<Long>

    /** 按来源统计读过的篇数，取前几名（来源排行榜）。 */
    @Query(
        "SELECT sourceName, COUNT(*) AS c FROM articles WHERE readAt > 0 " +
                "GROUP BY sourceName ORDER BY c DESC LIMIT :limit"
    )
    suspend fun topSources(limit: Int): List<SourceCount>

    // ==================== v1.6：缓存管理 ====================

    /** 已缓存的正文全文总字符数（缓存管理页展示占用用）。 */
    @Query("SELECT COALESCE(SUM(LENGTH(fullText)), 0) FROM articles WHERE fullText <> ''")
    suspend fun fullTextChars(): Int

    /** 已抽取过全文的文章数。 */
    @Query("SELECT COUNT(*) FROM articles WHERE fullText <> ''")
    suspend fun fullTextCount(): Int

    /**
     * 清空正文缓存：把 fullText 抹掉并把 fullFetched 复位，
     * 这样下次打开文章会重新联网抽取，而不是以为「抽过了」直接显示摘要。
     */
    @Query("UPDATE articles SET fullText = '', fullFetched = 0")
    suspend fun clearFullText()

    // ==================== v1.6：收藏夹管理 ====================

    @Query("SELECT folder FROM articles WHERE isBookmarked = 1 GROUP BY folder")
    suspend fun folderNames(): List<String>

    @Query("SELECT COUNT(*) FROM articles WHERE isBookmarked = 1 AND folder = :folder")
    suspend fun countInFolder(folder: String): Int

    /** 收藏夹改名（只动收藏文章，不碰普通文章）。 */
    @Query("UPDATE articles SET folder = :to WHERE folder = :from AND isBookmarked = 1")
    suspend fun renameFolder(from: String, to: String)

    /** 解散收藏夹：文章退回「默认」，仍然保持收藏状态。 */
    @Query("UPDATE articles SET folder = '默认' WHERE folder = :folder AND isBookmarked = 1")
    suspend fun dissolveFolder(folder: String)

    /** 删除收藏夹内容：连收藏一起取消（文章本体保留在首页）。 */
    @Query("UPDATE articles SET isBookmarked = 0 WHERE folder = :folder")
    suspend fun unbookmarkFolder(folder: String)

    // ==================== v1.9：离线预加载 ====================

    /**
     * 「还值得抓正文」的文章链接，按时间从新到旧。
     *
     * 三条过滤条件缺一不可：
     * - `fullText = ''`   —— 本地还没有全文；
     * - `fullFetched = 0` —— 没尝试过。**这条很关键**：抓失败的文章也会被标记，
     *   否则每次预加载都会把同一批死链接再撞一遍；
     * - `LENGTH(content) < 1200` —— feed 本身只给了短摘要。源里已经是长正文的没必要再抓。
     */
    @Query(
        "SELECT link FROM articles WHERE fullText = '' AND fullFetched = 0 " +
                "AND LENGTH(content) < 1200 ORDER BY pubDate DESC LIMIT :limit"
    )
    suspend fun linksNeedingFullText(limit: Int): List<String>

    // ==================== v2.0：备份 / 恢复 ====================

    /** 备份用：一次性取出全部收藏（按时间倒序）。 */
    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY pubDate DESC LIMIT :limit")
    suspend fun allBookmarks(limit: Int = 1000): List<Article>

    /**
     * 恢复备份：**REPLACE 语义**（与刷新用的 INSERT OR IGNORE 相反）。
     *
     * 恢复的语义是「以备份为准」，所以已存在的那条要被覆盖；
     * 但注意 [Article] 里 fullText/fullFetched 的默认值是空，
     * 所以恢复不会带上正文缓存 —— 正文需要联网重新抓，这是刻意的（备份文件才小）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(articles: List<Article>)

    /** 批量移除本地文章（多选管理用）。只删本地缓存记录，不影响源站。 */
    @Query("DELETE FROM articles WHERE link IN (:links)")
    suspend fun deleteByLinks(links: List<String>)
}
