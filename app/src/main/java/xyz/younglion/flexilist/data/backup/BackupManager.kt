package xyz.younglion.flexilist.data.backup

import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.data.model.WorkoutLogEntity
import xyz.younglion.flexilist.data.model.WaterIntakeEntity
import xyz.younglion.flexilist.data.repository.FlexiRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

class BackupManager(private val repository: FlexiRepository) {
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        .adapter(BackupWrapper::class.java)

    data class BackupWrapper(
        val notebooks: List<NotebookBackup>,
        val workoutLogs: List<WorkoutLogBackup>? = null,
        val waterIntakes: List<WaterIntakeBackup>? = null,
        val schemaVersion: Int = 1
    )

    data class WorkoutLogBackup(
        val notebookId: Long? = null,
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
        val id: Long? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val sortOrder: Int = 0,
        val name: String,
        val description: String = "",
        val emoji: String = "📝",
        val color: Int = 0,
        val type: String = "Normal",
        val category: String = "",
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
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val sortOrder: Int = 0,
        val completedAt: Long? = null,
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

    suspend fun exportToJson(outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = exportToJsonString() ?: return@withContext false
            outputStream.write(json.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importFromJson(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            importFromJsonString(inputStream.bufferedReader(Charsets.UTF_8).readText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportToJsonString(): String? = withContext(Dispatchers.IO) {
        try {
            // One Room transaction provides a consistent snapshot of every table.
            val snapshot = repository.withTransaction {
                BackupWrapper(
                    schemaVersion = 2,
                    notebooks = repository.getAllNotebooksSuspend().map { n ->
                        notebookBackup(n, repository.getItemsForNotebookSuspend(n.id))
                    },
                    workoutLogs = repository.getAllWorkoutLogsSuspend().map {
                        WorkoutLogBackup(
                            notebookId = it.notebookId,
                            workoutName = it.workoutName,
                            startTime = it.startTime,
                            endTime = it.endTime,
                            totalDurationSeconds = it.totalDurationSeconds,
                            totalSetsCompleted = it.totalSetsCompleted,
                            totalWeightLiftedKg = it.totalWeightLiftedKg,
                            exerciseStatsJson = it.exerciseStatsJson,
                            createdAt = it.createdAt
                        )
                    },
                    waterIntakes = repository.getAllWaterIntakesSuspend().map {
                        WaterIntakeBackup(it.dateString, it.amountMl, it.updatedAt)
                    }
                )
            }
            adapter.toJson(snapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importFromJsonString(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val wrapper = adapter.fromJson(jsonString) ?: return@withContext false
            require(wrapper.schemaVersion in 1..2) { "Unsupported backup version" }
            val sourceIds = wrapper.notebooks.mapNotNull { it.id }
            require(sourceIds.distinct().size == sourceIds.size) { "Duplicate notebook IDs" }
            // An import adds copies. A failed import must never leave a partial notebook behind.
            repository.withTransaction {
                val importedIds = mutableMapOf<Long, Long>()
                val importedNames = mutableMapOf<String, MutableList<Long>>()
                wrapper.notebooks.forEachIndexed { notebookIndex, nb ->
                    val restoredNotebook = notebookEntity(nb).let {
                        if (wrapper.schemaVersion == 1) it.copy(sortOrder = notebookIndex) else it
                    }
                    val newId = repository.insertNotebook(restoredNotebook)
                    nb.id?.let { importedIds[it] = newId }
                    importedNames.getOrPut(nb.name) { mutableListOf() }.add(newId)
                    repository.insertItems(nb.items.mapIndexed { index, item ->
                        itemEntity(item, newId).let { if (wrapper.schemaVersion == 1) it.copy(sortOrder = index) else it }
                    })
                }
                wrapper.workoutLogs.orEmpty().forEach { log ->
                    // Older files lack IDs. Only an unambiguous name can recover that association.
                    val notebookId = if (log.notebookId != null) importedIds[log.notebookId]
                        else importedNames[log.workoutName]?.singleOrNull()
                    repository.insertWorkoutLog(
                        WorkoutLogEntity(
                            notebookId = notebookId ?: 0L,
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
                wrapper.waterIntakes.orEmpty().forEach { water ->
                    val existing = repository.getWaterIntakeForDateSuspend(water.dateString)
                    if (existing == null || water.updatedAt > existing.updatedAt) {
                        repository.insertWaterIntake(WaterIntakeEntity(water.dateString, water.amountMl, water.updatedAt))
                    }
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportToCsv(outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            fun cell(value: Any?): String = "\"${value.toString().replace("\"", "\"\"")}\""
            val csv = repository.withTransaction {
                buildString {
                    append("Defter,Baslik,Aciklama,Tamamlandi,Oncelik,Etiketler,Kategori,Tarih\n")
                    repository.getAllNotebooksSuspend().forEach { n ->
                        repository.getItemsForNotebookSuspend(n.id).forEach { i ->
                            val priority = n.getPriorityConfig().find { it.level == i.priority }?.name ?: i.priority.toString()
                            append(listOf(n.name, i.title, i.description, i.completed, priority, i.tags, i.category, i.dueDate ?: "")
                                .joinToString(",") { cell(it) })
                            append('\n')
                        }
                    }
                }
            }
            outputStream.write(csv.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun notebookBackup(n: NotebookEntity, items: List<ListItemEntity>) = NotebookBackup(
                    id = n.id,
                    name = n.name,
                    description = n.description,
                    emoji = n.emoji,
                    color = n.color,
                    type = n.type,
                    category = n.category,
                    pinned = n.pinned,
                    archived = n.archived,
                    favorite = n.favorite,
                    showCompleted = n.showCompleted,
                    moveCompletedToBottom = n.moveCompletedToBottom,
                    autoSort = n.autoSort,
                    priorityConfigJson = n.priorityConfigJson,
                    createdAt = n.createdAt,
                    updatedAt = n.updatedAt,
                    sortOrder = n.sortOrder,
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
                            customFieldsJson = i.customFieldsJson,
                            createdAt = i.createdAt,
                            updatedAt = i.updatedAt,
                            sortOrder = i.sortOrder,
                            completedAt = i.completedAt
                        )
                    }
        )

        fun notebookEntity(nb: NotebookBackup) = NotebookEntity(
                            name = nb.name,
                            description = nb.description,
                            emoji = nb.emoji,
                            color = nb.color,
                            type = nb.type,
                            category = nb.category,
                            pinned = nb.pinned,
                            archived = nb.archived,
                            favorite = nb.favorite,
                            showCompleted = nb.showCompleted,
                            moveCompletedToBottom = nb.moveCompletedToBottom,
                            autoSort = nb.autoSort,
                            priorityConfigJson = nb.priorityConfigJson,
                            createdAt = nb.createdAt,
                            updatedAt = nb.updatedAt,
                            sortOrder = nb.sortOrder
        )

        fun itemEntity(item: ItemBackup, notebookId: Long) = ListItemEntity(
                                notebookId = notebookId,
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
                                customFieldsJson = item.customFieldsJson,
                                createdAt = item.createdAt,
                                updatedAt = item.updatedAt,
                                sortOrder = item.sortOrder,
                                completedAt = item.completedAt
        )
    }
}
