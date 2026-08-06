package xyz.younglion.flexilist.data.db

import androidx.room.*
import xyz.younglion.flexilist.data.model.ListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListItemDao {
    @Query("SELECT * FROM list_items WHERE notebookId = :notebookId ORDER BY sortOrder ASC, id DESC")
    fun getAllItemsForNotebook(notebookId: Long): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items ORDER BY id DESC")
    fun getAllItemsGlobal(): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items WHERE id = :id LIMIT 1")
    fun getItemById(id: Long): Flow<ListItemEntity?>

    @Query("SELECT * FROM list_items WHERE id = :id LIMIT 1")
    suspend fun getItemByIdSuspend(id: Long): ListItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ListItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ListItemEntity>): List<Long>

    @Update
    suspend fun updateItem(item: ListItemEntity)

    @Update
    suspend fun updateItems(items: List<ListItemEntity>)

    @Delete
    suspend fun deleteItem(item: ListItemEntity)

    @Delete
    suspend fun deleteItems(items: List<ListItemEntity>)

    @Query("DELETE FROM list_items WHERE notebookId = :notebookId")
    suspend fun deleteAllItemsInNotebook(notebookId: Long)
}
