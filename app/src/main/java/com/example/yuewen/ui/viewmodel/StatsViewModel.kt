package com.example.yuewen.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.model.ReadStats
import com.example.yuewen.data.repository.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 阅读统计。
 *
 * 数据全部来自本地库（readAt 时间戳 + 正文字符数），不联网、不上传。
 */
class StatsViewModel(private val repo: NewsRepository) : ViewModel() {

    private val _stats = MutableStateFlow(ReadStats())
    val stats: StateFlow<ReadStats> = _stats

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak

    /** 首次进入还没读到数据时显示骨架/转圈，避免先闪一堆 0 再跳成真实值。 */
    var loading by mutableStateOf(true)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            _stats.value = runCatching { repo.loadStats() }.getOrDefault(ReadStats())
            _streak.value = runCatching { repo.readStreak() }.getOrDefault(0)
            loading = false
        }
    }

    companion object {
        fun provide(application: YuewenApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatsViewModel(application.newsRepository) as T
            }
    }
}
