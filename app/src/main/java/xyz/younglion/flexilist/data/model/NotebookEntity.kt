package xyz.younglion.flexilist.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val emoji: String = "📝",
    val color: Int = 0, // Index of color palette or specific Color Int
    val type: String = "Normal", // Normal, ToDo, Checklist, HabitTracker, WorkoutPlanner, ReadingList, Collection
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    
    // Per-notebook settings
    val showCompleted: Boolean = true,
    val moveCompletedToBottom: Boolean = true,
    val autoSort: Boolean = true,
    val priorityConfigJson: String = ""
) {
    data class PriorityOption(
        val level: Int,
        val name: String,
        val colorHex: String,
        val enabled: Boolean = true
    )

    companion object {
        val DEFAULT_PRIORITIES = listOf(
            PriorityOption(0, "Yok", "#00000000", true),
            PriorityOption(1, "Düşük", "#81C784", true),
            PriorityOption(2, "Orta", "#FFB74D", true),
            PriorityOption(3, "Yüksek", "#E57373", true)
        )

        fun deserializePriorityConfig(json: String): List<PriorityOption> {
            if (json.isBlank()) return DEFAULT_PRIORITIES
            val parsed = json.split(";;").mapNotNull { part ->
                val segments = part.split("|")
                if (segments.size >= 4) {
                    PriorityOption(
                        level = segments[0].toIntOrNull() ?: 0,
                        name = segments[1],
                        colorHex = segments[2],
                        enabled = segments[3].toBooleanStrictOrNull() ?: true
                    )
                } else null
            }
            return if (parsed.isEmpty()) DEFAULT_PRIORITIES else {
                // Ensure level 0 "Yok" is always present at start
                val hasZero = parsed.any { it.level == 0 }
                if (!hasZero) listOf(PriorityOption(0, "Yok", "#00000000", true)) + parsed else parsed
            }
        }

        fun serializePriorityConfig(list: List<PriorityOption>): String {
            return list.joinToString(";;") { p ->
                val escapedName = p.name.replace("|", "").replace(";", "")
                "${p.level}|$escapedName|${p.colorHex}|${p.enabled}"
            }
        }

        fun parseWorkoutScheduleConfig(description: String?): WorkoutScheduleConfig {
            if (description == null || !description.startsWith("[WORKOUT_CFG]")) {
                return WorkoutScheduleConfig()
            }
            val clean = description.removePrefix("[WORKOUT_CFG]")
            val parts = clean.split("|").associate {
                val s = it.split("=")
                if (s.size >= 2) s[0] to s[1] else s[0] to ""
            }
            val freq = parts["frequency"] ?: "Weekly"
            val wd = parts["workoutDays"]?.toIntOrNull() ?: 3
            val rd = parts["restDays"]?.toIntOrNull() ?: 2
            val start = parts["startDate"]?.toLongOrNull() ?: System.currentTimeMillis()
            val weekly = parts["weekly"]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: listOf(1, 3, 5)
            val monthly = parts["monthly"]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: listOf(1, 15)
            return WorkoutScheduleConfig(freq, wd, rd, start, weekly, monthly)
        }
    }

    fun getPriorityConfig(): List<PriorityOption> {
        return deserializePriorityConfig(priorityConfigJson)
    }

    fun getWorkoutScheduleConfig(): WorkoutScheduleConfig {
        return parseWorkoutScheduleConfig(description)
    }

    fun getCleanDescription(): String {
        if (description.startsWith("[WORKOUT_CFG]")) {
            val config = getWorkoutScheduleConfig()
            return when (config.frequency) {
                "Daily" -> "Döngüsel Program (${config.workoutDurationDays} Gün Spor / ${config.restDurationDays} Gün Dinlenme)"
                "Weekly" -> "Haftalık Program (${config.weeklyDays.size} Gün)"
                "Monthly" -> "Aylık Program (${config.monthlyDays.size} Gün)"
                else -> "Antrenman ve Egzersiz Programı"
            }
        }
        return description.ifBlank { "Açıklama girilmedi." }
    }
}

data class WorkoutScheduleConfig(
    val frequency: String = "Weekly",
    val workoutDurationDays: Int = 3,
    val restDurationDays: Int = 2,
    val startDate: Long = System.currentTimeMillis(),
    val weeklyDays: List<Int> = listOf(1, 3, 5),
    val monthlyDays: List<Int> = listOf(1, 15)
)
