package com.example.data.backup

import com.example.data.model.ListItemEntity
import com.example.data.model.NotebookEntity
import com.example.data.repository.FlexiRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

class BackupManager(private val repository: FlexiRepository) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Helper classes to structure JSON export/import safely
    data class BackupWrapper(
        val notebooks: List<NotebookBackup>,
        val workoutLogs: List<WorkoutLogBackup>? = null,
        val waterIntakes: List<WaterIntakeBackup>? = null
    )

    data class WorkoutLogBackup(
        val workoutName: String,
        val startTime: Long,
        val endTime: Long,
        val totalDurationSeconds: Long,
        val totalSetsCompleted: Int,
        val totalWeightLiftedKg: Double,
        val exerciseStatsJson: String,
        val createdAt: Long
    )

    data class WaterIntakeBackup(
        val dateString: String,
        val amountMl: Int,
        val updatedAt: Long
    )

    data class NotebookBackup(
        val name: String,
        val description: String = "",
        val emoji: String = "📝",
        val color: Int = 0,
        val type: String = "Normal",
        val pinned: Boolean = false,
        val archived: Boolean = false,
        val favorite: Boolean = false,
        val showCompleted: Boolean = true,
        val moveCompletedToBottom: Boolean = true,
        val autoSort: Boolean = true,
        val priorityConfigJson: String = "",
        val items: List<ItemBackup> = emptyList()
    )

    data class ItemBackup(
        val title: String,
        val description: String = "",
        val completed: Boolean = false,
        val priority: Int = 0,
        val tags: String = "",
        val category: String = "",
        val color: Int = 0,
        val emoji: String = "",
        val dueDate: Long? = null,
        val reminderTime: Long? = null,
        val isRecurring: Boolean = false,
        val recurrenceRule: String? = null,
        val starred: Boolean = false,
        val subtasksJson: String = "",
        val customFieldsJson: String = ""
    )

    // Export all Notebooks and ListItems to JSON
    suspend fun exportToJson(outputStream: OutputStream): Boolean {
        return try {
            val notebooks = repository.allNotebooks.first()
            val backupNotebooks = notebooks.map { n ->
                val items = repository.getItemsForNotebook(n.id).first()
                NotebookBackup(
                    name = n.name,
                    description = n.description,
                    emoji = n.emoji,
                    color = n.color,
                    type = n.type,
                    pinned = n.pinned,
                    archived = n.archived,
                    favorite = n.favorite,
                    showCompleted = n.showCompleted,
                    moveCompletedToBottom = n.moveCompletedToBottom,
                    autoSort = n.autoSort,
                    priorityConfigJson = n.priorityConfigJson,
                    items = items.map { i ->
                        ItemBackup(
                            title = i.title,
                            description = i.description,
                            completed = i.completed,
                            priority = i.priority,
                            tags = i.tags,
                            category = i.category,
                            color = i.color,
                            emoji = i.emoji,
                            dueDate = i.dueDate,
                            reminderTime = i.reminderTime,
                            isRecurring = i.isRecurring,
                            recurrenceRule = i.recurrenceRule,
                            starred = i.starred,
                            subtasksJson = i.subtasksJson,
                            customFieldsJson = i.customFieldsJson
                        )
                    }
                )
            }

            val allLogs = repository.getAllWorkoutLogs().first().map {
                WorkoutLogBackup(
                    workoutName = it.workoutName,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    totalDurationSeconds = it.totalDurationSeconds,
                    totalSetsCompleted = it.totalSetsCompleted,
                    totalWeightLiftedKg = it.totalWeightLiftedKg,
                    exerciseStatsJson = it.exerciseStatsJson,
                    createdAt = it.createdAt
                )
            }

            val allWaters = repository.getAllWaterIntakes().first().map {
                WaterIntakeBackup(
                    dateString = it.dateString,
                    amountMl = it.amountMl,
                    updatedAt = it.updatedAt
                )
            }

            val wrapper = BackupWrapper(
                notebooks = backupNotebooks,
                workoutLogs = allLogs,
                waterIntakes = allWaters
            )
            val adapter = moshi.adapter(BackupWrapper::class.java)
            val jsonString = adapter.toJson(wrapper)
            outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Import from JSON
    suspend fun importFromJson(inputStream: InputStream): Boolean {
        return try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val adapter = moshi.adapter(BackupWrapper::class.java)
            val wrapper = adapter.fromJson(jsonString) ?: return false

            var firstWorkoutNotebookId: Long = 0L

            for (nb in wrapper.notebooks) {
                // Insert Notebook
                val notebookEntity = NotebookEntity(
                    name = nb.name,
                    description = nb.description,
                    emoji = nb.emoji,
                    color = nb.color,
                    type = nb.type,
                    pinned = nb.pinned,
                    archived = nb.archived,
                    favorite = nb.favorite,
                    showCompleted = nb.showCompleted,
                    moveCompletedToBottom = nb.moveCompletedToBottom,
                    autoSort = nb.autoSort,
                    priorityConfigJson = nb.priorityConfigJson
                )
                val newNotebookId = repository.insertNotebook(notebookEntity)
                if ((nb.type == "Workout Planner" || nb.type == "WorkoutPlanner") && firstWorkoutNotebookId == 0L) {
                    firstWorkoutNotebookId = newNotebookId
                }

                // Insert Items
                for (item in nb.items) {
                    val itemEntity = ListItemEntity(
                        notebookId = newNotebookId,
                        title = item.title,
                        description = item.description,
                        completed = item.completed,
                        priority = item.priority,
                        tags = item.tags,
                        category = item.category,
                        color = item.color,
                        emoji = item.emoji,
                        dueDate = item.dueDate,
                        reminderTime = item.reminderTime,
                        isRecurring = item.isRecurring,
                        recurrenceRule = item.recurrenceRule,
                        starred = item.starred,
                        subtasksJson = item.subtasksJson,
                        customFieldsJson = item.customFieldsJson
                    )
                    repository.insertItem(itemEntity)
                }
            }

            wrapper.workoutLogs?.let { logs ->
                for (log in logs) {
                    repository.insertWorkoutLog(
                        com.example.data.model.WorkoutLogEntity(
                            notebookId = firstWorkoutNotebookId,
                            workoutName = log.workoutName,
                            startTime = log.startTime,
                            endTime = log.endTime,
                            totalDurationSeconds = log.totalDurationSeconds,
                            totalSetsCompleted = log.totalSetsCompleted,
                            totalWeightLiftedKg = log.totalWeightLiftedKg,
                            exerciseStatsJson = log.exerciseStatsJson,
                            createdAt = log.createdAt
                        )
                    )
                }
            }

            wrapper.waterIntakes?.let { waters ->
                for (water in waters) {
                    repository.insertWaterIntake(
                        com.example.data.model.WaterIntakeEntity(
                            dateString = water.dateString,
                            amountMl = water.amountMl,
                            updatedAt = water.updatedAt
                        )
                    )
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Export all items to CSV
    suspend fun exportToCsv(outputStream: OutputStream): Boolean {
        return try {
            val builder = StringBuilder()
            builder.append("Defter,Baslik,Aciklama,Tamamlandi,Oncelik,Etiketler,Kategori,Tarih\n")

            val notebooks = repository.allNotebooks.first()
            for (n in notebooks) {
                val items = repository.getItemsForNotebook(n.id).first()
                for (i in items) {
                    val escapedNotebookName = n.name.replace("\"", "\"\"")
                    val escapedTitle = i.title.replace("\"", "\"\"")
                    val escapedDesc = i.description.replace("\"", "\"\"")
                    val escapedTags = i.tags.replace("\"", "\"\"")
                    val escapedCategory = i.category.replace("\"", "\"\"")
                    val priorityStr = when (i.priority) {
                        3 -> "Yüksek"
                        2 -> "Orta"
                        1 -> "Düşük"
                        else -> "Yok"
                    }
                    builder.append("\"$escapedNotebookName\",\"$escapedTitle\",\"$escapedDesc\",\"${i.completed}\",\"$priorityStr\",\"$escapedTags\",\"$escapedCategory\",\"${i.dueDate ?: ""}\"\n")
                }
            }

            outputStream.write(builder.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Export all to raw JSON string
    suspend fun exportToJsonString(): String? {
        return try {
            val notebooks = repository.allNotebooks.first()
            val backupNotebooks = notebooks.map { n ->
                val items = repository.getItemsForNotebook(n.id).first()
                NotebookBackup(
                    name = n.name,
                    description = n.description,
                    emoji = n.emoji,
                    color = n.color,
                    type = n.type,
                    pinned = n.pinned,
                    archived = n.archived,
                    favorite = n.favorite,
                    showCompleted = n.showCompleted,
                    moveCompletedToBottom = n.moveCompletedToBottom,
                    autoSort = n.autoSort,
                    priorityConfigJson = n.priorityConfigJson,
                    items = items.map { i ->
                        ItemBackup(
                            title = i.title,
                            description = i.description,
                            completed = i.completed,
                            priority = i.priority,
                            tags = i.tags,
                            category = i.category,
                            color = i.color,
                            emoji = i.emoji,
                            dueDate = i.dueDate,
                            reminderTime = i.reminderTime,
                            isRecurring = i.isRecurring,
                            recurrenceRule = i.recurrenceRule,
                            starred = i.starred,
                            subtasksJson = i.subtasksJson,
                            customFieldsJson = i.customFieldsJson
                        )
                    }
                )
            }

            val allLogs = repository.getAllWorkoutLogs().first().map {
                WorkoutLogBackup(
                    workoutName = it.workoutName,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    totalDurationSeconds = it.totalDurationSeconds,
                    totalSetsCompleted = it.totalSetsCompleted,
                    totalWeightLiftedKg = it.totalWeightLiftedKg,
                    exerciseStatsJson = it.exerciseStatsJson,
                    createdAt = it.createdAt
                )
            }

            val allWaters = repository.getAllWaterIntakes().first().map {
                WaterIntakeBackup(
                    dateString = it.dateString,
                    amountMl = it.amountMl,
                    updatedAt = it.updatedAt
                )
            }

            val wrapper = BackupWrapper(
                notebooks = backupNotebooks,
                workoutLogs = allLogs,
                waterIntakes = allWaters
            )
            val adapter = moshi.adapter(BackupWrapper::class.java)
            adapter.toJson(wrapper)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Import from raw JSON string
    suspend fun importFromJsonString(jsonString: String): Boolean {
        return try {
            val adapter = moshi.adapter(BackupWrapper::class.java)
            val wrapper = adapter.fromJson(jsonString) ?: return false

            var firstWorkoutNotebookId: Long = 0L

            for (nb in wrapper.notebooks) {
                // Insert Notebook
                val notebookEntity = NotebookEntity(
                    name = nb.name,
                    description = nb.description,
                    emoji = nb.emoji,
                    color = nb.color,
                    type = nb.type,
                    pinned = nb.pinned,
                    archived = nb.archived,
                    favorite = nb.favorite,
                    showCompleted = nb.showCompleted,
                    moveCompletedToBottom = nb.moveCompletedToBottom,
                    autoSort = nb.autoSort,
                    priorityConfigJson = nb.priorityConfigJson
                )
                val newNotebookId = repository.insertNotebook(notebookEntity)
                if ((nb.type == "Workout Planner" || nb.type == "WorkoutPlanner") && firstWorkoutNotebookId == 0L) {
                    firstWorkoutNotebookId = newNotebookId
                }

                // Insert Items
                for (item in nb.items) {
                    val itemEntity = ListItemEntity(
                        notebookId = newNotebookId,
                        title = item.title,
                        description = item.description,
                        completed = item.completed,
                        priority = item.priority,
                        tags = item.tags,
                        category = item.category,
                        color = item.color,
                        emoji = item.emoji,
                        dueDate = item.dueDate,
                        reminderTime = item.reminderTime,
                        isRecurring = item.isRecurring,
                        recurrenceRule = item.recurrenceRule,
                        starred = item.starred,
                        subtasksJson = item.subtasksJson,
                        customFieldsJson = item.customFieldsJson
                    )
                    repository.insertItem(itemEntity)
                }
            }

            wrapper.workoutLogs?.let { logs ->
                for (log in logs) {
                    repository.insertWorkoutLog(
                        com.example.data.model.WorkoutLogEntity(
                            notebookId = firstWorkoutNotebookId,
                            workoutName = log.workoutName,
                            startTime = log.startTime,
                            endTime = log.endTime,
                            totalDurationSeconds = log.totalDurationSeconds,
                            totalSetsCompleted = log.totalSetsCompleted,
                            totalWeightLiftedKg = log.totalWeightLiftedKg,
                            exerciseStatsJson = log.exerciseStatsJson,
                            createdAt = log.createdAt
                        )
                    )
                }
            }

            wrapper.waterIntakes?.let { waters ->
                for (water in waters) {
                    repository.insertWaterIntake(
                        com.example.data.model.WaterIntakeEntity(
                            dateString = water.dateString,
                            amountMl = water.amountMl,
                            updatedAt = water.updatedAt
                        )
                    )
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
