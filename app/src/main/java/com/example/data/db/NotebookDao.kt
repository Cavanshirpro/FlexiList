package com.example.data.db

import androidx.room.*
import com.example.data.model.NotebookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY pinned DESC, sortOrder ASC, id DESC")
    fun getAllNotebooks(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id LIMIT 1")
    fun getNotebookById(id: Long): Flow<NotebookEntity?>

    @Query("SELECT * FROM notebooks WHERE id = :id LIMIT 1")
    suspend fun getNotebookByIdSuspend(id: Long): NotebookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: NotebookEntity): Long

    @Update
    suspend fun updateNotebook(notebook: NotebookEntity)

    @Delete
    suspend fun deleteNotebook(notebook: NotebookEntity)
}
