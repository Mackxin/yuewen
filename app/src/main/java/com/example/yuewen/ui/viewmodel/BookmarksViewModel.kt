package com.example.yuewen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.SettingsRepository
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.repository.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BookmarksViewModel(
    private val repo: NewsRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _selectedFolder = MutableStateFlow("全部")
    val selectedFolder: StateFlow<String> = _selectedFolder

    /**
     * 收藏夹列表。
     *
     * 收藏夹不是独立的表，只是文章上的 `folder` 字段，所以「顺序」这种纯展示偏好
     * 单独存在 DataStore 里。这里把两边合起来：
     * 顺序表里有的按用户排的来，库里新出现、顺序表里没有的自动接在后面。
     */
    val folders: StateFlow<List<String>> = combine(
        repo.observeBookmarkFolders(),
        settings.folderOrderFlow
    ) { db, order ->
        val known = order.filter { it in db }
        val rest = db.filter { it !in known }
        known + rest
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<Article>> = _selectedFolder.flatMapLatest { folder ->
        repo.observeBookmarksByFolder(folder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<Article>> = repo.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 管理面板用：每个收藏夹里有多少篇。 */
    private val _folderSizes = MutableStateFlow<Map<String, Int>>(emptyMap())
    val folderSizes: StateFlow<Map<String, Int>> = _folderSizes

    fun selectFolder(folder: String) { _selectedFolder.value = folder }

    fun loadFolderSizes() {
        viewModelScope.launch {
            _folderSizes.value = runCatching { repo.folderSizes().toMap() }.getOrDefault(emptyMap())
        }
    }

    fun toggleBookmark(link: String, value: Boolean) {
        viewModelScope.launch { repo.setBookmarked(link, value) }
    }

    fun moveToFolder(link: String, folder: String) {
        viewModelScope.launch { repo.setFolder(link, folder) }
    }

    fun clearHistory(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val n = repo.clearHistory()
            onResult(n)
        }
    }

    // ==================== v1.6：收藏夹管理 ====================

    /**
     * 改名。
     * 顺序表也要一起改——否则新名字在顺序表里查不到，会被当成新夹甩到队尾，
     * 用户改个名结果位置变了会很困惑。
     */
    fun renameFolder(from: String, to: String) {
        val target = to.trim()
        if (target.isBlank() || target == from) return
        viewModelScope.launch {
            runCatching {
                repo.renameFolder(from, target)
                settings.setFolderOrder(folders.value.map { if (it == from) target else it })
            }
            if (_selectedFolder.value == from) _selectedFolder.value = target
            loadFolderSizes()
        }
    }

    /** 解散：文章退回「默认」，但仍然是收藏状态（不会丢收藏）。 */
    fun dissolveFolder(folder: String) {
        viewModelScope.launch {
            runCatching {
                repo.dissolveFolder(folder)
                settings.setFolderOrder(folders.value.filter { it != folder })
            }
            if (_selectedFolder.value == folder) _selectedFolder.value = "全部"
            loadFolderSizes()
        }
    }

    /** 删除：连收藏一起取消（文章本体仍留在首页，只是不再收藏）。 */
    fun deleteFolder(folder: String) {
        viewModelScope.launch {
            runCatching {
                repo.unbookmarkFolder(folder)
                settings.setFolderOrder(folders.value.filter { it != folder })
            }
            if (_selectedFolder.value == folder) _selectedFolder.value = "全部"
            loadFolderSizes()
        }
    }

    /** 上移 / 下移：只动顺序表，不碰数据库。 */
    fun moveFolder(index: Int, delta: Int) {
        val list = folders.value.toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        viewModelScope.launch { settings.setFolderOrder(list) }
    }

    companion object {
        fun provide(application: YuewenApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BookmarksViewModel(
                        application.newsRepository,
                        application.settingsRepository
                    ) as T
            }
    }
}
