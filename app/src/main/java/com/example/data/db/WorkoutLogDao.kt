package com.example.data.db

import androidx.room.*
import com.example.data.model.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutLogDao {
    @Query("SELECT * FROM workout_logs WHERE notebookId = :notebookId ORDER BY createdAt DESC")
    fun getLogsForNotebook(notebookId: Long): Flow<List<WorkoutLogEntity>>

    @Query("SELECT * FROM workout_logs ORDER BY createdAt DESC")
    fun getAllLogs(): Flow<List<WorkoutLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: WorkoutLogEntity): Long

    @Query("DELETE FROM workout_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)
}
