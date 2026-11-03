package com.example.yuewen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.SettingsRepository
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.repository.NewsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 「闻件 → 稍后读」这一栏（v2.8）。
 *
 * 队列本身没有独立表，就是 `articles.queuedAt` 一个字段，
 * 所以这个 ViewModel 薄得很 —— 唯一的「逻辑」是排序方向：
 * 它存在 DataStore 里（用户偏好），但决定的是**查哪条 SQL**，
 * 于是用 `flatMapLatest` 把两者接起来：偏好一变，自动换到另一条查询重新订阅。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModel(
    private val repo: NewsRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    /**
     * 排序方向：true = 最新加入的排最前。
     *
     * 默认 false（先加入的排最前）—— 这是「队列」这个词本来的语义。
     */
    val newestFirst: StateFlow<Boolean> = settings.queueNewestFirstFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val items: StateFlow<List<Article>> = settings.queueNewestFirstFlow
        .flatMapLatest { repo.observeQueue(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 读完是否自动出队（这一栏的说明文字要跟着变，所以也订阅一下）。 */
    val autoDequeue: StateFlow<Boolean> = settings.queueAutoDequeueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setNewestFirst(v: Boolean) {
        viewModelScope.launch { runCatching { settings.setQueueNewestFirst(v) } }
    }

    fun setAutoDequeue(v: Boolean) {
        viewModelScope.launch { runCatching { settings.setQueueAutoDequeue(v) } }
    }

    /** 移出队列（卡片右下角那颗按钮）。 */
    fun remove(link: String) {
        viewModelScope.launch { runCatching { repo.setQueued(link, false) } }
    }

    /** 顺手收藏（队列里的长按菜单用）。 */
    fun setBookmarked(link: String, value: Boolean) {
        viewModelScope.launch { runCatching { repo.setBookmarked(link, value) } }
    }

    /** 手动切已读 / 未读（长按菜单用）。 */
    fun setRead(link: String, value: Boolean) {
        viewModelScope.launch { runCatching { repo.setRead(link, value) } }
    }

    /**
     * 清空整个队列。
     *
     * 先取当前条数再清 —— 清完之后列表已经空了，拿不到「清掉几条」用来做反馈。
     */
    fun clearAll(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val n = items.value.size
            runCatching { repo.clearQueue() }
            onResult(n)
        }
    }

    /** 只清掉队列里已经读完的，返回清掉几条。 */
    fun clearRead(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val n = runCatching { repo.dequeueRead() }.getOrDefault(0)
            onResult(n)
        }
    }

    companion object {
        fun provide(application: YuewenApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    QueueViewModel(
                        application.newsRepository,
                        application.settingsRepository
                    ) as T
            }
    }
}
