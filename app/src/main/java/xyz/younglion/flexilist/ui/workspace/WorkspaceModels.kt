package xyz.younglion.flexilist.ui.workspace

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity

/** Navigation groups are derived from legacy notebook kinds; no database rewrite is needed. */
enum class WorkspaceId(val title: Int, val hint: Int) {
    LISTS(R.string.ux_lists, R.string.ux_lists_hint),
    HABITS(R.string.ux_habits, R.string.ux_habits_hint),
    WORKOUTS(R.string.ux_workouts, R.string.ux_workouts_hint),
    LIBRARY(R.string.ux_library, R.string.ux_library_hint);

    val kinds: List<NotebookKind> get() = NotebookKind.entries.filter { it.workspace == this && it != NotebookKind.OTHER }
    companion object {
        fun fromSaved(value: String?): WorkspaceId = entries.firstOrNull { it.name == value } ?: LISTS
    }
}

enum class NotebookKind(val wireName: String, val title: Int, val workspace: WorkspaceId) {
    NORMAL("Normal", R.string.type_standard, WorkspaceId.LISTS),
    TASK("ToDo", R.string.type_todo, WorkspaceId.LISTS),
    CHECKLIST("Checklist", R.string.type_shopping, WorkspaceId.LISTS),
    HABIT("Habit Tracker", R.string.type_habit, WorkspaceId.HABITS),
    WORKOUT("Workout Planner", R.string.type_workout, WorkspaceId.WORKOUTS),
    READING("Reading List", R.string.type_reading, WorkspaceId.LIBRARY),
    COLLECTION("Collection", R.string.type_collection, WorkspaceId.LIBRARY),
    OTHER("", R.string.ux_other, WorkspaceId.LISTS);

    companion object {
        fun parse(value: String): NotebookKind = when (value.lowercase(java.util.Locale.ROOT).filter(Char::isLetterOrDigit)) {
            "normal" -> NORMAL
            "todo" -> TASK
            "checklist" -> CHECKLIST
            "habittracker" -> HABIT
            "workoutplanner" -> WORKOUT
            "readinglist" -> READING
            "collection" -> COLLECTION
            else -> OTHER
        }
    }
}

enum class ThemeMode(val title: Int) {
    SYSTEM(R.string.ux_theme_system), LIGHT(R.string.light_mode), DARK(R.string.dark_mode);
    fun isDark(systemDark: Boolean): Boolean = when (this) { SYSTEM -> systemDark; LIGHT -> false; DARK -> true }
    companion object {
        fun read(preferences: SharedPreferences): ThemeMode {
            val saved = preferences.getString("theme_mode", null)
            if (saved != null) return entries.firstOrNull { it.name == saved } ?: SYSTEM
            val migrated = if (preferences.contains("is_dark_mode")) {
                if (preferences.getBoolean("is_dark_mode", true)) DARK else LIGHT
            } else SYSTEM
            preferences.edit().putString("theme_mode", migrated.name).apply()
            return migrated
        }
    }
}

sealed interface NotebookUiState {
    data object Loading : NotebookUiState
    data class Ready(val notebook: NotebookEntity, val items: List<ListItemEntity>) : NotebookUiState
    data object Missing : NotebookUiState
    data object Error : NotebookUiState
}

sealed interface EntryUiState {
    data object Loading : EntryUiState
    data class Ready(val item: ListItemEntity?) : EntryUiState
    data object Missing : EntryUiState
    data object Error : EntryUiState
}

/** All item fields travel together between quick entry, full editor and saved-state restoration. */
data class EntryDraft(val item: ListItemEntity) {
    fun encode(): String = adapter.toJson(item)
    companion object {
        private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(ListItemEntity::class.java)
        fun decode(json: String): EntryDraft? = runCatching { adapter.fromJson(json)?.let(::EntryDraft) }.getOrNull()
        fun key(notebookId: Long, itemId: Long) = "entry-draft-$notebookId-$itemId"
    }
}

data class UndoDeletion(val token: Long, val items: List<ListItemEntity>)
data class ImportPreview(val names: List<String>, val itemCount: Int, val logCount: Int, val waterCount: Int)
sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Preparing : ImportUiState
    data class Ready(val preview: ImportPreview) : ImportUiState
    data class Importing(val preview: ImportPreview) : ImportUiState
    data class Failed(val message: Int) : ImportUiState
}

/** Serialized only for an in-flight notebook save, so a recreated form can reattach to it. */
data class NotebookDraft(val notebook: NotebookEntity) {
    fun encode(): String = adapter.toJson(notebook)
    companion object {
        private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(NotebookEntity::class.java)
        fun decode(json: String): NotebookDraft? = runCatching { adapter.fromJson(json)?.let(::NotebookDraft) }.getOrNull()
    }
}
