package com.example.data.db

import androidx.room.*
import com.example.data.model.WaterIntakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterIntakeDao {
    @Query("SELECT * FROM water_intake WHERE dateString = :dateString")
    fun getWaterIntakeForDate(dateString: String): Flow<WaterIntakeEntity?>

    @Query("SELECT * FROM water_intake")
    fun getAllWaterIntake(): Flow<List<WaterIntakeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterIntake(intake: WaterIntakeEntity)
}
