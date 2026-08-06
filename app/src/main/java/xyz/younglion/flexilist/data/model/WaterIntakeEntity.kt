package xyz.younglion.flexilist.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_intake")
data class WaterIntakeEntity(
    @PrimaryKey val dateString: String, // e.g. "yyyy-MM-dd"
    val amountMl: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
