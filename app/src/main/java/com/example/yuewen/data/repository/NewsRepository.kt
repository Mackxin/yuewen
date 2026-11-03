package com.example.yuewen.data.repository

import android.content.Context
import com.example.yuewen.data.SettingsRepository
import com.example.yuewen.data.db.ArticleDao
import com.example.yuewen.data.db.NoteDao
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.model.Note
import com.example.yuewen.data.model.ReadStats
import com.example.yuewen.data.model.ReadStatsCalc
import com.example.yuewen.data.reader.ArticleExtractor
import com.example.yuewen.data.rss.RawItem
import com.example.yuewen.data.rss.RssFetcher
import com.example.yuewen.data.util.DailyDigest.DigestFacts
import com.example.yuewen.data.util.WidgetSnapshot
import com.example.yuewen.data.util.likePattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * 新闻数据仓库：负责拉取 RSS、写入本地库（离线缓存）、书签/已读、分类与搜索。
 */
class NewsRepository(
    private val dao: ArticleDao,
    /** v2.0：摘录 / 笔记。和文章共用同一个 Room 库，但各自一张表。 */
    private val noteDao: NoteDao,
    private val settingsRepository: SettingsRepository,
    context: Context
) {
    private val fetcher = RssFetcher(context)
    private val extractor = ArticleExtractor()

    /** 本地库最多保留的非收藏文章条数（控制库大小，保证列表查询始终很快）。 */
    private companion object {
        const val KEEP_ARTICLES = 1500

        /** 一次预加载最多抓多少篇（防止一次点下去把整个库都爬一遍）。 */
        const val PRELOAD_LIMIT = 300

        /** 预加载并发数。比刷新源的并发低：这是在抓第三方网页，太猛容易被封。 */
        const val PRELOAD_CONCURRENCY = 3

        /** 两篇之间的间隔。既给目标站点留口气，也让「取消」能较快生效。 */
        const val PRELOAD_GAP_MS = 150L

        /**
         * v2.8：每日摘要的展开态里最多列几条标题。
         *
         * 3 条是通知展开后**一次能看完**的量。列到 10 条时，通知会变成一个
         * 需要滚动的列表 —— 那是打开 App 该干的事，不是通知该干的事。
         */
        const val DIGEST_TITLE_LIMIT = 3
    }

    /**
     * 刷新所有启用的新闻源，返回本次新增的文章数量。
     *
     * 性能要点（之前「点刷新要等很久」的原因）：
     * 1. **并行抓取**：所有源同时请求，而不是一个抓完再抓下一个——6 个源从十几秒降到 1~3 秒；
     * 2. **一次查询去重**：原来每条文章都要 `getByLink` 查一次库（N+1），现在一次 `allLinks()` 搞定；
     * 3. **只插新文章**（INSERT OR IGNORE）：不再 REPLACE 覆盖整行，
     *    因此刷新既不会冲掉已读/收藏/收藏夹/正文全文，也不会做无谓的写放大。
     */
    suspend fun refreshAll(): Int = withContext(Dispatchers.IO) {
        val sources = settingsRepository.getSources().filter { it.enabled }
        if (sources.isEmpty()) return@withContext 0

        // 1) 并行抓取所有源（单源失败不影响其它源）
        val fetched: List<Pair<com.example.yuewen.data.model.FeedSource, List<RawItem>>> = coroutineScope {
            sources.map { src ->
                async { src to runCatching { fetcher.fetch(src) }.getOrDefault(emptyList()) }
            }.awaitAll()
        }

        // 2) 一次查出本地已有链接
        val known = dao.allLinks().toHashSet()

        // 2.5) 顺带回填图片：老版本解析不出图的文章，这次刷新拿到了图就补上
        backfillMissingImages(fetched)

        // 3) 只挑出「本地还没有」的文章
        val fresh = ArrayList<Article>()
        for ((src, items) in fetched) {
            for (item in items) {
                if (!known.add(item.link)) continue
                fresh.add(
                    Article(
                        link = item.link,
                        title = item.title,
                        summary = item.summary,
                        content = item.content,
                        imageUrl = item.imageUrl,
                        sourceName = src.name,
                        category = src.category,
                        pubDate = item.pubDate,
                        sourceId = src.id
                    )
                )
            }
        }
        if (fresh.isNotEmpty()) {
            dao.insertNew(fresh)
            dao.pruneTo(KEEP_ARTICLES)
        }
        fresh.size
    }

    /**
     * 回填缺图文章。
     *
     * 为什么需要：刷新用的是 INSERT OR IGNORE（为了不冲掉已读/收藏状态），
     * 所以「库里已存在、但当初没解析出图片」的文章永远拿不到图。
     * 这里在刷新后顺手把新解析到的图片补进空字段，用户不用手动清缓存。
     */
    private suspend fun backfillMissingImages(
        fetched: List<Pair<com.example.yuewen.data.model.FeedSource, List<RawItem>>>
    ) {
        val missing = runCatching { dao.linksWithoutImage().toHashSet() }.getOrNull() ?: return
        if (missing.isEmpty()) return
        for ((_, items) in fetched) {
            for (item in items) {
                val img = item.imageUrl ?: continue
                if (missing.remove(item.link)) {
                    runCatching { dao.backfillImage(item.link, img) }
                }
            }
        }
    }

    /**
     * 抓取并抽取某篇文章的正文全文，写入本地库并返回。
     * 失败返回 null（同时标记 fullFetched，避免每次进详情都重试；用户可手动重试）。
     */
    suspend fun fetchFullText(link: String): String? = withContext(Dispatchers.IO) {
        val text = try {
            extractor.extract(link)
        } catch (_: Exception) {
            null
        }
        if (!text.isNullOrBlank()) {
            dao.setFullText(link, text)
            text
        } else {
            dao.setFullText(link, "") // 标记已尝试
            null
        }
    }

    /** 重置抽取标记，便于用户点「重新获取全文」。 */
    suspend fun resetFullText(link: String) = dao.resetFullFetched(link)

    /**
     * 首页信息流（v2.0.2 起支持两级筛选）。
     *
     * @param category 「推荐」= 不限分类，其余按分类过滤
     * @param source   空 = 不限阅源，否则只看这个来源
     */
    fun observeHomeFeed(category: String, source: String = ""): Flow<List<Article>> =
        dao.observeHomeFeed(category, source)

    /** 测试某个 RSS 地址是否可用。null = 可用。 */
    suspend fun testSource(url: String): String? = withContext(Dispatchers.IO) { fetcher.test(url) }

    /** 测试地址并取回源名称（加源界面「粘贴地址自动显示名称」用）。 */
    suspend fun probeSource(url: String): com.example.yuewen.data.rss.FeedProbe =
        withContext(Dispatchers.IO) { fetcher.probe(url) }

    /** 自动发现网站首页里的 RSS 订阅地址。 */
    suspend fun discoverFeeds(homeUrl: String): List<com.example.yuewen.data.rss.FeedCandidate> =
        withContext(Dispatchers.IO) { fetcher.discoverFeeds(homeUrl) }

    fun observeBookmarks(): Flow<List<Article>> = dao.observeBookmarks()

    fun observeBookmarksByFolder(folder: String): Flow<List<Article>> = dao.observeBookmarksByFolder(folder)

    fun observeBookmarkFolders(): Flow<List<String>> = dao.observeBookmarkFolders()

    fun observeHistory(): Flow<List<Article>> = dao.observeHistory()

    // ==================== v2.8：稍后读队列 ====================

    /**
     * 队列内容。
     * @param newestFirst true = 最新加入的排最前；false = 先加入的排最前（默认，队列语义）
     */
    fun observeQueue(newestFirst: Boolean): Flow<List<Article>> =
        if (newestFirst) dao.observeQueueNewestFirst() else dao.observeQueueFifo()

    fun observeQueueCount(): Flow<Int> = dao.observeQueueCount()

    /**
     * 加入 / 移出队列。
     *
     * 加入时用的是**当前时间戳**（不是文章的 pubDate）—— 队列排的是
     * 「我什么时候决定要读它」，和文章什么时候发布没关系。
     */
    suspend fun setQueued(link: String, value: Boolean) = withContext(Dispatchers.IO) {
        dao.setQueued(link, if (value) System.currentTimeMillis() else 0L)
    }

    /** 这条现在在不在队列里（长按菜单 / 详情页按钮的当前态）。 */
    suspend fun isQueued(link: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { (dao.getByLink(link)?.queuedAt ?: 0L) > 0L }.getOrDefault(false)
    }

    suspend fun clearQueue() = withContext(Dispatchers.IO) { runCatching { dao.clearQueue() } }

    /** 清掉队列里已读完的，返回清掉几条。 */
    suspend fun dequeueRead(): Int = withContext(Dispatchers.IO) {
        runCatching { dao.dequeueRead() }.getOrDefault(0)
    }

    /** 备份用：整个稍后读队列。 */
    suspend fun allQueued(): List<Article> = withContext(Dispatchers.IO) {
        runCatching { dao.allQueued(1000) }.getOrDefault(emptyList())
    }

    // v2.3：关键词一律先过 `likePattern()` 转义，再交给 DAO。
    // 直接 `"%$q%"` 的话，用户输入 `%` / `_` 会变成通配符 → 搜索命中全库。
    fun search(category: String, q: String): Flow<List<Article>> = dao.search(category, likePattern(q))

    /** 本地全文搜索（标题/摘要/正文/来源，不限分类）。 */
    fun searchAll(q: String): Flow<List<Article>> = dao.searchAll(likePattern(q))

    /** 只看某个来源的文章。 */
    fun observeBySource(name: String): Flow<List<Article>> = dao.observeBySource(name)

    /** 保存阅读进度（千分比，0~1000）。 */
    suspend fun setReadProgress(link: String, value: Int) {
        if (value < 0 || value > 1000) return
        dao.setReadProgress(link, value)
    }

    fun observeByLink(link: String): Flow<Article?> = dao.observeByLink(link)

    suspend fun setBookmarked(link: String, value: Boolean) = dao.setBookmarked(link, value)

    /**
     * 标记已读（打开文章时触发）。
     *
     * ⚠️ v2.8：这里顺带处理「读完自动出队」。
     *
     * 为什么把出队挂在**这一条路径**上，而不是挂在「批量标为已读」上：
     * - 打开一篇文章 = 用户确实读了它（`autoread` 开关就是干这个的）→ 该出队；
     * - 「全部标为已读」是清理收件箱的动作，用户只是不想再看见那 200 条红的，
     *   并没有读。如果那边也出队，一次误点就能把辛苦排好的队列清空一大半。
     *
     * 出队前先记下 `queuedAt` 并返回出去，**调用方靠它做「撤销」**——
     * 一条东西自己从列表里消失而没有任何提示，是很吓人的。
     */
    suspend fun markRead(link: String): Long = withContext(Dispatchers.IO) {
        val wasQueued = runCatching { dao.getByLink(link)?.queuedAt ?: 0L }.getOrDefault(0L)
        dao.markRead(link, System.currentTimeMillis())
        val auto = runCatching { settingsRepository.queueAutoDequeueFlow.first() }.getOrDefault(true)
        if (auto && wasQueued > 0L) runCatching { dao.dequeue(link) }
        if (auto) wasQueued else 0L
    }

    /** 撤销「读完自动出队」：把加入时间原样放回去（不是「重新加一次」，顺序不该变）。 */
    suspend fun requeueAt(link: String, queuedAt: Long) = withContext(Dispatchers.IO) {
        if (queuedAt > 0L) runCatching { dao.setQueued(link, queuedAt) }
    }

    suspend fun setFolder(link: String, folder: String) = dao.setFolder(link, folder)

    /** 把当前筛选范围（分类 + 阅源）内的文章全部标为已读。 */
    suspend fun markAllRead(category: String, source: String = "") =
        dao.markAllReadFiltered(category, source, System.currentTimeMillis())

    suspend fun clearHistory(): Int {
        val n = dao.observeHistoryCount()
        dao.clearHistory()
        return n
    }

    suspend fun clearCache(): Int {
        val n = dao.cachedCount()
        dao.clearCache()
        return n
    }

    /**
     * 只刷新某一个新闻源（源管理里点「刷新」时用）。
     * 返回本次新增的文章数量。
     */
    suspend fun refreshSource(id: String): Int = withContext(Dispatchers.IO) {
        val src = settingsRepository.getSources().firstOrNull { it.id == id } ?: return@withContext 0
        val items = runCatching { fetcher.fetch(src) }.getOrDefault(emptyList())
        if (items.isEmpty()) return@withContext 0

        backfillMissingImages(listOf(src to items))

        val known = dao.allLinks().toHashSet()
        val fresh = items.filter { known.add(it.link) }.map { item ->
            Article(
                link = item.link,
                title = item.title,
                summary = item.summary,
                content = item.content,
                imageUrl = item.imageUrl,
                sourceName = src.name,
                category = src.category,
                pubDate = item.pubDate,
                sourceId = src.id
            )
        }
        if (fresh.isNotEmpty()) {
            dao.insertNew(fresh)
            dao.pruneTo(KEEP_ARTICLES)
        }
        fresh.size
    }

    /** 未读总数（底部导航徽标）。 */
    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    /** 手动切换已读 / 未读。 */
    suspend fun setRead(link: String, value: Boolean) =
        dao.setRead(link, value, System.currentTimeMillis())

    /** 详情页「左右滑切换上下篇」用的同分类链接列表。 */
    suspend fun linksByCategory(category: String): List<String> = dao.linksByCategory(category)

    suspend fun getByLink(link: String): Article? = dao.getByLink(link)

    suspend fun countBySource(id: String): Int = dao.countBySource(id)

    /** 删除某个阅源对应的全部文章（保留该源下被收藏的，避免误删收藏）。 */
    suspend fun deleteBySource(id: String, name: String = "") = dao.deleteBySource(id, name)

    // ==================== v1.6：阅读统计 ====================

    /**
     * 一次性取回统计页需要的全部数据。
     * 刻意做成「一次调用」而不是让 ViewModel 串行 await 一堆小查询——
     * 后者在 Room 里是 8 次独立事务，页面会明显发涩。
     */
    suspend fun loadStats(): ReadStats = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dayStart = ReadStatsCalc.startOfDay(now)
        val weekStart = ReadStatsCalc.weekStart(now)
        val stamps = dao.readTimestamps()

        ReadStats(
            totalRead = stamps.size,
            todayRead = dao.readCountSince(dayStart),
            weekRead = dao.readCountSince(weekStart),
            chars = dao.readChars(),
            last7 = ReadStatsCalc.last7Buckets(stamps, now),
            topSources = dao.topSources(6),
            bookmarked = dao.bookmarkCount(),
            cached = dao.totalCount(),
            sourceCount = settingsRepository.getSources().count { it.enabled }
        )
    }

    /** 连续阅读天数（首页副标题 / 统计页用）。 */
    suspend fun readStreak(): Int = withContext(Dispatchers.IO) {
        ReadStatsCalc.streak(dao.readTimestamps(), System.currentTimeMillis())
    }

    // ==================== v1.6：收藏夹管理 ====================

    /** 收藏夹列表（带上每个夹的文章数），管理面板用。 */
    suspend fun folderSizes(): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        dao.folderNames().map { it to dao.countInFolder(it) }
    }

    suspend fun renameFolder(from: String, to: String) = dao.renameFolder(from, to)

    /** 解散收藏夹：里面文章退回「默认」但保持收藏。 */
    suspend fun dissolveFolder(folder: String) = dao.dissolveFolder(folder)

    /** 删除收藏夹：连收藏一起取消（文章本体还在首页）。 */
    suspend fun unbookmarkFolder(folder: String) = dao.unbookmarkFolder(folder)

    // ==================== v1.6：缓存管理 ====================

    /** 已缓存的正文全文占用（字符数）与条数。 */
    suspend fun fullTextUsage(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        dao.fullTextChars() to dao.fullTextCount()
    }

    /** (库内文章总数, 其中未收藏的条数)。缓存管理页展示与「清理」前提示用。 */
    suspend fun articleCounts(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        dao.totalCount() to dao.cachedCount()
    }

    /** 清空正文缓存，返回清掉了几篇。 */
    suspend fun clearFullText(): Int = withContext(Dispatchers.IO) {
        val n = dao.fullTextCount()
        dao.clearFullText()
        n
    }

    // ==================== v1.9：离线预加载 ====================

    /**
     * 批量把文章正文抓下来写进本地库（「离线阅读」用）。
     *
     * 和详情页按需抽取的区别：
     * - 这是**后台批量**动作，所以并发压到 [PRELOAD_CONCURRENCY]（3），
     *   每篇之间还留一点间隔 —— 一口气并发几十个请求抓第三方网页，很容易被站点封 IP；
     * - 提供 [shouldStop] 让用户随时取消（取消后当前正在抓的几篇会尽快收尾退出）；
     * - 抓不到的也会被标记（`setFullText(link, "")` 即 fullFetched=1），
     *   下次不会再重复撞同一批死链接。
     *
     * @param onProgress 回调 (已完成, 总数)，跑在 IO 线程，调用方自己切主线程
     * @return 成功抓到并入库的篇数
     */
    suspend fun preloadFullTexts(
        limit: Int = PRELOAD_LIMIT,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        shouldStop: () -> Boolean = { false }
    ): Int = withContext(Dispatchers.IO) {
        val links = runCatching { dao.linksNeedingFullText(limit) }.getOrDefault(emptyList())
        if (links.isEmpty()) return@withContext 0

        val total = links.size
        val done = AtomicInteger(0)
        val saved = AtomicInteger(0)
        val gate = Semaphore(PRELOAD_CONCURRENCY)

        coroutineScope {
            links.map { link ->
                async {
                    gate.withPermit {
                        if (shouldStop()) return@withPermit
                        val text = runCatching { extractor.extract(link) }.getOrNull()
                        if (!text.isNullOrBlank()) {
                            runCatching { dao.setFullText(link, text) }
                            saved.incrementAndGet()
                        } else {
                            // 标记「试过了」，否则下一次预加载还会来撞一遍
                            runCatching { dao.setFullText(link, "") }
                        }
                        onProgress(done.incrementAndGet(), total)
                        // 给目标站点留口气，也顺便让「取消」能较快生效
                        delay(PRELOAD_GAP_MS)
                    }
                }
            }.awaitAll()
        }
        saved.get()
    }

    /** 还有多少篇「可以抓但还没抓」的文章（设置页显示待缓存数量）。 */
    suspend fun pendingPreloadCount(): Int = withContext(Dispatchers.IO) {
        runCatching { dao.linksNeedingFullText(PRELOAD_LIMIT).size }.getOrDefault(0)
    }

    // ==================== v2.8：每日摘要 ====================

    /**
     * 一次取齐摘要要用的四个数。
     *
     * 每个查询各自 `runCatching`：摘要是个「锦上添花」的后台任务，
     * 某一个查询出意外（比如某天表被并发重建）不该让整条通知消失 ——
     * 那天的摘要宁可少一个数字，也不该变成「设了 8 点却没收到」。
     * 全都失败时退化成「什么都不推」（全 0 → buildContent 返回 null），也是可接受的结果。
     */
    suspend fun digestFacts(todayStartMs: Long): DigestFacts = withContext(Dispatchers.IO) {
        DigestFacts(
            todayCount = runCatching { dao.countPublishedSince(todayStartMs) }.getOrDefault(0),
            unreadCount = runCatching { dao.unreadCount() }.getOrDefault(0),
            queuedCount = runCatching { dao.queuedCount() }.getOrDefault(0),
            topTitles = runCatching { dao.titlesPublishedSince(todayStartMs, DIGEST_TITLE_LIMIT) }
                .getOrDefault(emptyList())
        )
    }

    /**
     * 桌面小组件要的数字（v2.9）。
     *
     * 和 [digestFacts] 一样，四个查询各自 `runCatching` ——
     * 某一个查询出意外不该让整张卡片变成空白，宁可那个数字显示成 0。
     *
     * 「今天的起点」由调用方算好传进来（和每日摘要共用同一套时区算法），
     * 这里不自己算：两个功能各算一套的话，会出现「通知说今天 3 条、卡片说 2 条」。
     */
    suspend fun widgetFacts(todayStartMs: Long): WidgetSnapshot.Facts = withContext(Dispatchers.IO) {
        WidgetSnapshot.Facts(
            unread = runCatching { dao.unreadCount() }.getOrDefault(0),
            today = runCatching { dao.countPublishedSince(todayStartMs) }.getOrDefault(0),
            queued = runCatching { dao.queuedCount() }.getOrDefault(0),
            items = runCatching { dao.unreadItems(WidgetSnapshot.TITLE_LIMIT) }
                .getOrDefault(emptyList())
        )
    }

    // ==================== v2.0：摘录 / 笔记 ====================

    fun observeNotes(): Flow<List<Note>> = noteDao.observeAll()

    fun observeNotesOf(link: String): Flow<List<Note>> = noteDao.observeByArticle(link)

    fun observeNoteCountOf(link: String): Flow<Int> = noteDao.observeCountByArticle(link)

    suspend fun noteCount(): Int = withContext(Dispatchers.IO) { runCatching { noteDao.count() }.getOrDefault(0) }

    /**
     * 存一条摘录 / 批注。
     *
     * [id] 用「文章链接 + 时间戳」拼：同一篇文章可以摘很多段，
     * 同毫秒内连点两次的概率极低，不需要引入 UUID 依赖。
     */
    suspend fun addNote(article: Article?, link: String, quote: String, note: String): Note =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val n = Note(
                id = "$link#$now",
                link = link,
                articleTitle = article?.title.orEmpty(),
                sourceName = article?.sourceName.orEmpty(),
                quote = quote.trim(),
                note = note.trim(),
                createdAt = now
            )
            noteDao.upsert(n)
            n
        }

    suspend fun updateNote(note: Note) = withContext(Dispatchers.IO) { noteDao.upsert(note) }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) { noteDao.delete(id) }

    // ==================== v2.0：批量操作 & 备份 ====================

    /** 批量标已读 / 未读。 */
    suspend fun setReadBatch(links: List<String>, value: Boolean) = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        links.forEach { runCatching { dao.setRead(it, value, ts) } }
    }

    /** 批量收藏 / 取消收藏。 */
    suspend fun setBookmarkedBatch(links: List<String>, value: Boolean) = withContext(Dispatchers.IO) {
        links.forEach { runCatching { dao.setBookmarked(it, value) } }
    }

    /** 批量移动到收藏夹（顺带收藏，否则「移动」没有意义）。 */
    suspend fun setFolderBatch(links: List<String>, folder: String, bookmark: Boolean = true) =
        withContext(Dispatchers.IO) {
            links.forEach { link ->
                runCatching {
                    dao.setFolder(link, folder)
                    if (bookmark) dao.setBookmarked(link, true)
                }
            }
        }

    /**
     * 备份用：一次取回全部「用户资产」= **收藏 ∪ 稍后读队列**。
     *
     * v2.8 之前这里叫 allBookmarks、只查 isBookmarked = 1。
     * 队列是正交的新维度，继续只查收藏会让「加了稍后读但没收藏」的文章
     * 整个漏出备份 —— 换机后才被发现，且没有任何报错。
     */
    suspend fun allSaved(): List<Article> = withContext(Dispatchers.IO) {
        runCatching { dao.allSaved(1000) }.getOrDefault(emptyList())
    }

    /** 备份用：一次取回全部笔记。 */
    suspend fun allNotes(): List<Note> = withContext(Dispatchers.IO) {
        runCatching { noteDao.all(1000) }.getOrDefault(emptyList())
    }

    /**
     * 恢复：把备份里的收藏写回本地库。
     *
     * 用 REPLACE 语义（[ArticleDao.restore]）而不是 INSERT OR IGNORE ——
     * 恢复的语义就是「以备份为准」，已存在的那条应该被覆盖成备份里的状态。
     * 笔记同理。
     */
    suspend fun restore(bookmarks: List<Article>, notes: List<Note>) = withContext(Dispatchers.IO) {
        if (bookmarks.isNotEmpty()) runCatching { dao.restore(bookmarks) }
        if (notes.isNotEmpty()) runCatching { noteDao.upsertAll(notes) }
    }

    /**
     * 批量从本地移除（多选管理里的「移除」）。
     *
     * 只删本地记录，源站不受影响 —— 下次刷新如果 feed 里还有它，还会回来。
     * 所以界面上的文案是「从本地移除」而不是「删除」。
     */
    suspend fun deleteByLinks(links: List<String>): Int = withContext(Dispatchers.IO) {
        if (links.isEmpty()) return@withContext 0
        runCatching { dao.deleteByLinks(links) }
        links.size
    }
}
