package xyz.younglion.flexilist.data.db

import androidx.room.*
import xyz.younglion.flexilist.data.model.WaterIntakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterIntakeDao {
    @Query("SELECT * FROM water_intake WHERE dateString = :dateString")
    fun getWaterIntakeForDate(dateString: String): Flow<WaterIntakeEntity?>

    @Query("SELECT * FROM water_intake WHERE dateString = :dateString")
    suspend fun getWaterIntakeForDateSuspend(dateString: String): WaterIntakeEntity?

    @Query("SELECT * FROM water_intake")
    fun getAllWaterIntake(): Flow<List<WaterIntakeEntity>>

    @Query("SELECT * FROM water_intake")
    suspend fun getAllWaterIntakeSuspend(): List<WaterIntakeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterIntake(intake: WaterIntakeEntity)
}
