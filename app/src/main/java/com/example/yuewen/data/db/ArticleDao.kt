package com.example.yuewen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.model.SourceCount
import com.example.yuewen.data.util.WidgetSnapshot
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

    /**
     * 控制库大小：只保留最新的 N 条非收藏文章，保证列表查询始终很快。
     *
     * ⚠️ **v2.8：三条过滤条件里 `queuedAt = 0` 不能少。**
     * 「稍后读」队列里的文章是用户**明确表态要读**的，它和收藏一样属于「用户资产」，
     * 不该被「库太大，删点旧的」这种纯运维动作带走。少了这个条件，
     * 队列里排到第 1500 条以外的文章会在某次刷新后**无声消失**，
     * 而用户看到的现象只是「我明明加过的，怎么没了」。
     *
     * 子查询里也带上同样的条件：否则被队列保护的那些行会占掉 :keep 的名额，
     * 使得真正被保留的普通文章变少（库会比预期小）。
     */
    @Query(
        "DELETE FROM articles WHERE isBookmarked = 0 AND queuedAt = 0 AND link NOT IN " +
                "(SELECT link FROM articles WHERE isBookmarked = 0 AND queuedAt = 0 " +
                "ORDER BY pubDate DESC LIMIT :keep)"
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

    // ==================== v2.8：稍后读队列 ====================
    //
    // 队列不是独立的一张表，只是 articles 上的 queuedAt 字段。
    // 为什么不建表：队列条目和文章是**一对一**的，而且队列项必须能跟着文章一起
    // 被刷新、被收藏、被打开 —— 拆成两张表之后每个读路径都要 join，
    // 还会多出「文章被清了、队列里只剩个死链」这种状态。
    //
    // 两条查询的差别只在排序方向。做成两个方法而不是一个带方向的参数：
    // Room 的 @Query 是编译期常量 SQL，排序方向拼不进参数。

    /** 队列，**最新加入的排在最前**（和首页 / 收藏的时间序一致，符合大多数人的直觉）。 */
    @Query("SELECT * FROM articles WHERE queuedAt > 0 ORDER BY queuedAt DESC LIMIT 500")
    fun observeQueueNewestFirst(): Flow<List<Article>>

    /**
     * 队列，**先加入的排在最前**。
     *
     * 「队列」这个词本来的语义就是这个 —— 排在前面的先读。
     * 所以这是默认顺序，[observeQueueNewestFirst] 是用户主动切的另一种看法。
     */
    @Query("SELECT * FROM articles WHERE queuedAt > 0 ORDER BY queuedAt ASC LIMIT 500")
    fun observeQueueFifo(): Flow<List<Article>>

    /** 队列长度（闻件页角标 / 小组件用）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE queuedAt > 0")
    fun observeQueueCount(): Flow<Int>

    /** 加入队列 / 改加入时间。传 0 即出队。 */
    @Query("UPDATE articles SET queuedAt = :ts WHERE link = :link")
    suspend fun setQueued(link: String, ts: Long)

    /** 出队。 */
    @Query("UPDATE articles SET queuedAt = 0 WHERE link = :link")
    suspend fun dequeue(link: String)

    @Query("UPDATE articles SET queuedAt = 0 WHERE queuedAt > 0")
    suspend fun clearQueue()

    /**
     * 清掉队列里**已经读完**的（「清理已读」按钮）。
     * 返回实际清掉几条，界面要拿这个数字做反馈。
     */
    @Query("UPDATE articles SET queuedAt = 0 WHERE queuedAt > 0 AND isRead = 1")
    suspend fun dequeueRead(): Int

    /** 备份用：整个队列（按加入顺序）。 */
    @Query("SELECT * FROM articles WHERE queuedAt > 0 ORDER BY queuedAt ASC LIMIT :limit")
    suspend fun allQueued(limit: Int = 1000): List<Article>

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

    /**
     * 清缓存：删掉全部非收藏文章。
     * ⚠️ v2.8：同样要放过「稍后读」队列里的（用户点的是「清缓存」，不是「删我的队列」）。
     */
    @Query("DELETE FROM articles WHERE isBookmarked = 0 AND queuedAt = 0")
    suspend fun clearCache()

    /** 某分类下的全部链接（详情页「左右滑切换上下篇」用）。 */
    @Query("SELECT link FROM articles WHERE (:category = '推荐' OR category = :category) ORDER BY pubDate DESC LIMIT 400")
    suspend fun linksByCategory(category: String): List<String>

    /** 未读总数（底部导航徽标用）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    // ==================== v2.8：每日摘要 ====================
    //
    // 这一组是**取数**用的：Worker 里没有 Flow 的订阅者，只能一次性读。
    // 注意四个方法都刻意用 `suspend fun` 而不是返回 Flow —— 摘要是一次快照，
    // 边推边变没有意义，反而会让「今天 N 条」在拼文案的过程中自己跳一个数。

    /**
     * 今天（`pubDate >= from`）发布了多少条。
     *
     * 按**发布时间**而不是入库时间：摘要是「今天有什么新闻」，
     * 不是「今天我的 App 抓了几条」。少数源会把老文章翻上来重发，
     * 那种情况用入库时间会虚高。
     */
    @Query("SELECT COUNT(*) FROM articles WHERE pubDate >= :from")
    suspend fun countPublishedSince(from: Long): Int

    /** 今天最新几条的标题（摘要通知的展开态用它）。 */
    @Query("SELECT title FROM articles WHERE pubDate >= :from ORDER BY pubDate DESC LIMIT :limit")
    suspend fun titlesPublishedSince(from: Long, limit: Int): List<String>

    /** 未读总数的一次性读取（与 [observeUnreadCount] 同口径）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    suspend fun unreadCount(): Int

    /** 稍后读队列长度的一次性读取（与 [observeQueueCount] 同口径）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE queuedAt > 0")
    suspend fun queuedCount(): Int

    /**
     * 桌面小组件 4×2 上要显示的两条标题（v2.9）。
     *
     * 直接映射成 [WidgetSnapshot.Item]（纯数据类，两边共用同一个定义）——
     * 不要在这里再声明一个只有 title/link 的类，那样文案规则一变就要改两处。
     *
     * `title != ''` 不能省：少数源会推来没有标题的条目，卡片上会变成一行空白，
     * 看起来像「卡片坏了」。宁可多取一条把它顶掉。
     */
    @Query(
        "SELECT title, link FROM articles WHERE isRead = 0 AND title != '' " +
                "ORDER BY pubDate DESC LIMIT :limit"
    )
    suspend fun unreadItems(limit: Int): List<WidgetSnapshot.Item>

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
     *
     * v2.8：再加 `queuedAt = 0`。删掉一个阅源时，用户排进「稍后读」的文章
     * 依然应该留着 —— 他表达的是「这篇我要读」，跟这个源还要不要订阅是两回事。
     */
    @Query(
        "DELETE FROM articles WHERE isBookmarked = 0 AND queuedAt = 0 " +
                "AND (sourceId = :id OR (:name <> '' AND sourceName = :name))"
    )
    suspend fun deleteBySource(id: String, name: String)

    /**
     * 「可以被清缓存清掉的」条数（设置页缓存管理里展示、以及点清理前提示用）。
     *
     * v2.8：跟随 [clearCache] 的口径一起排除收藏与队列 ——
     * 否则界面会说「将清理 500 篇」，实际只删了 480 篇（差的那 20 篇是队列项），
     * 数字对不上会让用户以为清缓存坏了。
     */
    @Query("SELECT COUNT(*) FROM articles WHERE isBookmarked = 0 AND queuedAt = 0")
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

    /**
     * 备份用：一次性取出全部「用户资产」文章 = **收藏 ∪ 稍后读队列**。
     *
     * ⚠️ v2.8 的关键修正。以前这条只查 `isBookmarked = 1`，而 [Article.queuedAt]
     * 是正交的新维度 —— 于是「加进稍后读但没收藏」的文章**不在备份里**。
     * 这种丢失极其隐蔽：备份文件一切正常、恢复也不报错，
     * 只是在换了手机之后，用户排了一半的队列莫名其妙少了一大截。
     *
     * 两个集合求并，交给调用方（Backup 导出）逐条写清楚 isBookmarked，
     * 恢复时才能把「哪些是收藏、哪些只是队列」原样还回来。
     */
    @Query(
        "SELECT * FROM articles WHERE isBookmarked = 1 OR queuedAt > 0 " +
                "ORDER BY pubDate DESC LIMIT :limit"
    )
    suspend fun allSaved(limit: Int = 1000): List<Article>

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
