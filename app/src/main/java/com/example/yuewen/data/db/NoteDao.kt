package com.example.yuewen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yuewen.data.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT 500")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT :limit")
    suspend fun all(limit: Int = 500): List<Note>

    @Query("SELECT * FROM notes WHERE link = :link ORDER BY createdAt DESC")
    fun observeByArticle(link: String): Flow<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE link = :link")
    fun observeCountByArticle(link: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<Note>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM notes")
    suspend fun clear()
}
