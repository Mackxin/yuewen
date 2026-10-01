package com.example.yuewen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.SettingsRepository
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.repository.NewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 搜索页 ViewModel（v1.5 升级为**本地全文搜索**）。
 *
 * 之前只搜「标题 + 摘要」且限定在当前分类里 —— 用户明明读到过某句话，却搜不出来。
 * 现在搜 **标题 / 摘要 / 已抽取的正文 / 来源名**，并且不限分类。
 * 另外支持「点来源只看这个源」的快捷筛选。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class SearchViewModel(
    private val repo: NewsRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val query = MutableStateFlow("")

    /** 非空表示「只看这个来源」，此时关键词可以为空。 */
    val sourceFilter = MutableStateFlow<String?>(null)

    private data class Criteria(val q: String, val source: String?)

    private val criteria = combine(query.debounce(250), sourceFilter) { q, s -> Criteria(q, s) }

    val results: StateFlow<List<Article>> = combine(
        criteria.flatMapLatest { c ->
            when {
                // 选了来源：先取这个源的全部文章
                c.source != null -> {
                    val base = repo.observeBySource(c.source)
                    // 同时还有关键词 → 在源内再筛一层（正文也参与匹配）
                    if (c.q.isNotBlank()) {
                        base.map { list ->
                            list.filter { a ->
                                a.title.contains(c.q, true) ||
                                        a.summary.contains(c.q, true) ||
                                        a.fullText.contains(c.q, true)
                            }
                        }
                    } else base
                }
                c.q.isNotBlank() -> repo.searchAll(c.q)
                else -> flowOf(emptyList())
            }
        },
        settings.blockedSourcesFlow,
        settings.blockedKeywordsFlow
    ) { list, blockedSources, blockedKeywords ->
        list.filter { a ->
            if (blockedSources.contains(a.sourceName)) return@filter false
            if (blockedKeywords.any { kw -> a.title.contains(kw, true) || a.summary.contains(kw, true) }) return@filter false
            true
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recent: StateFlow<List<String>> = settings.recentSearchesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sources: StateFlow<List<com.example.yuewen.data.model.FeedSource>> = settings.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) {
        query.value = q
    }

    /** 点来源标签：再点一次取消筛选。 */
    fun toggleSourceFilter(name: String) {
        sourceFilter.value = if (sourceFilter.value == name) null else name
    }

    /** 回到「全部来源」。 */
    fun clearSourceFilter() {
        sourceFilter.value = null
    }

    fun clearAll() {
        query.value = ""
        sourceFilter.value = null
    }

    fun submit(q: String) {
        if (q.isNotBlank()) viewModelScope.launch { settings.addRecentSearch(q) }
    }

    fun toggleBookmark(link: String, value: Boolean) {
        viewModelScope.launch { repo.setBookmarked(link, value) }
    }

    companion object {
        fun provide(application: YuewenApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SearchViewModel(application.newsRepository, application.settingsRepository) as T
            }
    }
}
