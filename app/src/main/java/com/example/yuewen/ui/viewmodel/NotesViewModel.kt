package com.example.yuewen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.model.Note
import com.example.yuewen.data.repository.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 「闻件 → 笔记」栏（v2.0）。 */
class NotesViewModel(private val repo: NewsRepository) : ViewModel() {

    val notes: StateFlow<List<Note>> = repo.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 空的搜索框 = 不过滤；只在本地过滤，不查库（笔记量本来就小）。 */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun setQuery(q: String) { _query.value = q }

    fun delete(id: String) {
        viewModelScope.launch { runCatching { repo.deleteNote(id) } }
    }

    /** 只改批注文本；摘录原文不动。 */
    fun updateNoteText(note: Note, text: String) {
        viewModelScope.launch { runCatching { repo.updateNote(note.copy(note = text.trim())) } }
    }

    fun filtered(list: List<Note>, q: String): List<Note> {
        val key = q.trim()
        if (key.isEmpty()) return list
        return list.filter {
            it.quote.contains(key, true) || it.note.contains(key, true) || it.articleTitle.contains(key, true)
        }
    }

    companion object {
        fun provide(application: YuewenApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NotesViewModel(application.newsRepository) as T
            }
    }
}
