package com.example.yuewen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.model.Note
import com.example.yuewen.data.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 详情页 ViewModel。
 *
 * 三件事：
 * 1. 取出「同分类的链接列表」→ 支撑左右滑动切换上一篇/下一篇；
 * 2. 按 link 订阅文章（每个页面各自订阅，互不干扰）；
 * 3. 懒加载正文全文 —— 只对「正在看的那一篇」发起网络请求，滑动时才不浪费流量。
 */
class DetailViewModel(
    private val initialLink: String,
    private val repo: NewsRepository
) : ViewModel() {

    /** 同分类的链接列表（进入时一次性冻结，避免后台刷新插入新文章导致页码错位）。 */
    private val _links = MutableStateFlow<List<String>>(emptyList())
    val links: StateFlow<List<String>> = _links

    /** 初始停留的页码。 */
    private val _startIndex = MutableStateFlow(0)
    val startIndex: StateFlow<Int> = _startIndex

    /** 正文全文正在联网抽取的 link 集合。 */
    private val _loading = MutableStateFlow<Set<String>>(emptySet())
    val loading: StateFlow<Set<String>> = _loading

    /** 正文抽取失败的 link 集合（用于展示「点此重试」）。 */
    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed

    init {
        viewModelScope.launch {
            val article = runCatching { repo.getByLink(initialLink) }.getOrNull()
            val category = article?.category ?: "推荐"
            val siblings = runCatching { repo.linksByCategory(category) }.getOrDefault(emptyList())
            val finalList = when {
                siblings.isEmpty() -> listOf(initialLink)
                siblings.contains(initialLink) -> siblings
                else -> listOf(initialLink) + siblings
            }
            _links.value = finalList
            _startIndex.value = finalList.indexOf(initialLink).coerceAtLeast(0)
        }
    }

    /** 订阅某一篇文章（每页各自调用）。 */
    fun observe(link: String): Flow<Article?> = repo.observeByLink(link)

    /**
     * 只对「当前正在看的那篇」调用：
     * 若本地还没有正文全文、且源里给的只是摘要，就联网抽取一次。
     * 已有全文 / 已尝试过 / 源本身已给长正文 → 不重复请求。
     */
    fun ensureFullText(link: String) {
        if (link in _loading.value) return
        viewModelScope.launch {
            val a = runCatching { repo.getByLink(link) }.getOrNull() ?: return@launch
            if (a.fullText.isNotBlank()) return@launch     // 已有全文
            if (a.fullFetched) return@launch               // 已尝试过（失败则等用户手动重试）
            if (a.content.length >= 1200) return@launch    // 源本身已给长正文
            extract(link)
        }
    }

    /** 用户点「重新获取全文」：先清掉尝试标记再抽一次。 */
    fun retryFullText(link: String) {
        if (link in _loading.value) return
        viewModelScope.launch {
            runCatching { repo.resetFullText(link) }
            extract(link)
        }
    }

    private fun extract(link: String) {
        viewModelScope.launch {
            _loading.value = _loading.value + link
            _failed.value = _failed.value - link
            try {
                val text = repo.fetchFullText(link)
                if (text.isNullOrBlank()) _failed.value = _failed.value + link
            } finally {
                _loading.value = _loading.value - link
            }
        }
    }

    fun markRead(link: String) {
        viewModelScope.launch { repo.markRead(link) }
    }

    fun setRead(link: String, value: Boolean) {
        viewModelScope.launch { repo.setRead(link, value) }
    }

    fun toggleBookmark(link: String, value: Boolean) {
        viewModelScope.launch { repo.setBookmarked(link, value) }
    }

    /** 保存阅读进度（千分比），下次打开这篇能接着读。 */
    fun saveProgress(link: String, value: Int) {
        viewModelScope.launch { repo.setReadProgress(link, value) }
    }

    // ==================== v2.0：摘录 / 笔记 ====================

    /** 订阅「这一篇」的全部摘录与笔记（详情页侧栏列表）。 */
    fun observeNotes(link: String): Flow<List<Note>> = repo.observeNotesOf(link)

    /**
     * 存一条摘录 / 笔记。
     *
     * [article] 只用来**快照**标题与来源名 —— 文章被清缓存删掉之后，
     * 笔记仍然知道自己是从哪篇文章摘的，不会变成一条无主数据。
     */
    fun addNote(article: Article?, link: String, quote: String, note: String) {
        viewModelScope.launch { runCatching { repo.addNote(article, link, quote, note) } }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch { runCatching { repo.updateNote(note) } }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch { runCatching { repo.deleteNote(id) } }
    }

    companion object {
        fun provide(link: String, application: YuewenApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DetailViewModel(link, application.newsRepository) as T
            }
    }
}
