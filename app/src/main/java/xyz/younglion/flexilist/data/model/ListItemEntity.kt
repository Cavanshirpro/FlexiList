package xyz.younglion.flexilist.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "list_items",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["notebookId"])]
)
data class ListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val title: String,
    val description: String = "",
    val completed: Boolean = false,
    val priority: Int = 0, // 0 = None, 1 = Low, 2 = Medium, 3 = High
    val tags: String = "", // Comma-separated strings, e.g., "Work,Home"
    val category: String = "",
    val color: Int = 0,
    val emoji: String = "",
    val dueDate: Long? = null,
    val reminderTime: Long? = null,
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null, // "DAILY", "WEEKDAYS", "WEEKLY", "MONTHLY"
    val starred: Boolean = false,
    
    // Serialized lightweight structures
    val subtasksJson: String = "", // Format: "id|completed|title;id2|completed|title2"
    val customFieldsJson: String = "", // Format: "name|type|value;name2|type2|value2"
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val completedAt: Long? = null
) {
    // Nested domain classes for clean business logic mapping
    data class Subtask(
        val id: String,
        val title: String,
        val completed: Boolean
    )

    data class CustomField(
        val name: String,
        val type: String, // "Text", "Number", "Boolean", "Date", "Dropdown", "Rating", "URL"
        val value: String
    )

    // Ultra-fast zero-reflection string converters
    companion object {
        fun serializeSubtasks(list: List<Subtask>): String {
            return list.joinToString(";;") { subtask ->
                val escapedTitle = subtask.title.replace("|", "").replace(";", "")
                "${subtask.id}|${subtask.completed}|$escapedTitle"
            }
        }

        fun deserializeSubtasks(str: String): List<Subtask> {
            if (str.isBlank()) return emptyList()
            return str.split(";;").mapNotNull { part ->
                val segments = part.split("|")
                if (segments.size >= 3) {
                    Subtask(
                        id = segments[0],
                        completed = segments[1].toBoolean(),
                        title = segments[2]
                    )
                } else null
            }
        }

        fun serializeCustomFields(list: List<CustomField>): String {
            return list.joinToString(";;") { field ->
                val escapedName = field.name.replace("|", "").replace(";", "")
                val escapedValue = field.value.replace("|", "").replace(";", "")
                "$escapedName|${field.type}|$escapedValue"
            }
        }

        fun deserializeCustomFields(str: String): List<CustomField> {
            if (str.isBlank()) return emptyList()
            return str.split(";;").mapNotNull { part ->
                val segments = part.split("|")
                if (segments.size >= 3) {
                    CustomField(
                        name = segments[0],
                        type = segments[1],
                        value = segments[2]
                    )
                } else null
            }
        }
    }
}
