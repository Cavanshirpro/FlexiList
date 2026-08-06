package xyz.younglion.flexilist.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_logs")
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val workoutName: String,
    val startTime: Long,
    val endTime: Long,
    val totalDurationSeconds: Long,
    val totalSetsCompleted: Int,
    val totalWeightLiftedKg: Double,
    val exerciseStatsJson: String = "", // e.g. "Squat|3 sets|150kg; Bench Press|3 sets|80kg"
    val createdAt: Long = System.currentTimeMillis()
)
