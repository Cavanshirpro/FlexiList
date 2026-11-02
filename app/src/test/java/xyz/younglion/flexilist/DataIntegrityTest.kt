package xyz.younglion.flexilist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.younglion.flexilist.data.backup.BackupManager
import xyz.younglion.flexilist.data.backup.NotebookShareManager
import xyz.younglion.flexilist.data.db.AppDatabase
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.data.model.WorkoutLogEntity
import xyz.younglion.flexilist.data.repository.FlexiRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DataIntegrityTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: FlexiRepository
    private lateinit var backup: BackupManager

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = FlexiRepository(db)
        backup = BackupManager(repository)
    }

    @After fun close() = db.close()

    @Test fun dailyResetPreservesNormalTasksAndTodayCompletions() = runBlocking {
        for (type in listOf("ToDo", "Checklist", "Workout Planner", "WorkoutPlanner", "Habit Tracker")) {
            val id = repository.insertNotebook(NotebookEntity(name = type, type = type))
            repository.insertItem(ListItemEntity(notebookId = id, title = "Yesterday", completed = true, completedAt = 100))
            repository.insertItem(ListItemEntity(notebookId = id, title = "Today", completed = true, completedAt = 300))
        }
        repository.resetDailyCompletions(200)
        val items = repository.getAllItemsGlobal().first()
        assertTrue(items.filter { it.title == "Today" }.all { it.completed })
        val yesterday = items.filter { it.title == "Yesterday" }
        assertEquals(2, yesterday.count { it.completed })
        assertTrue(yesterday.filter { !it.completed }.all { it.completedAt == null })
    }

    @Test fun rapidWaterUpdatesAreAtomic() = runBlocking {
        (1..20).map { async { repository.addWater("2026-11-02", 250) } }.awaitAll()
        assertEquals(5000, repository.getWaterIntakeForDate("2026-11-02").first()?.amountMl)
        repository.addWater("2026-11-02", -10000)
        assertEquals(0, repository.getWaterIntakeForDate("2026-11-02").first()?.amountMl)
    }

    @Test fun backupRoundTripPreservesAssociationsOrderAndCompletionTimes() = runBlocking {
        val first = repository.insertNotebook(NotebookEntity(name = "Strength", type = "Workout Planner", sortOrder = 5))
        val second = repository.insertNotebook(NotebookEntity(name = "Strength", type = "Workout Planner", sortOrder = 2))
        repository.insertItem(ListItemEntity(notebookId = second, title = "Later", sortOrder = 7, completed = true,
            completedAt = 12345, createdAt = 100, updatedAt = 200, customFieldsJson = "x|Text|value"))
        repository.insertItem(ListItemEntity(notebookId = second, title = "First", sortOrder = 1))
        for (id in listOf(first, second)) repository.insertWorkoutLog(WorkoutLogEntity(
            notebookId = id, workoutName = "Strength", startTime = id, endTime = 10, totalDurationSeconds = 9,
            totalSetsCompleted = 3, totalWeightLiftedKg = 120.0))
        val json = requireNotNull(backup.exportToJsonString())
        db.clearAllTables()
        // Occupy a local ID to ensure backup source IDs are remapped, not reused.
        repository.insertNotebook(NotebookEntity(name = "Existing"))
        assertTrue(backup.importFromJsonString(json))
        val notebooks = repository.allNotebooks.first()
        val restoredSecond = notebooks.single { it.name == "Strength" && it.sortOrder == 2 }
        val items = repository.getItemsForNotebook(restoredSecond.id).first()
        assertEquals(listOf("First", "Later"), items.map { it.title })
        assertEquals(12345L, items.last().completedAt)
        assertEquals(100L, items.last().createdAt)
        assertEquals("x|Text|value", items.last().customFieldsJson)
        val logs = repository.getAllWorkoutLogs().first()
        assertEquals(2, logs.map { it.notebookId }.distinct().size)
        assertEquals(restoredSecond.id, logs.single { it.startTime == second }.notebookId)
    }

    @Test fun databaseFailureRollsBackEntireImport() = runBlocking {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_item BEFORE INSERT ON list_items
            BEGIN SELECT RAISE(ABORT, 'simulated storage failure'); END
        """.trimIndent())
        assertFalse(backup.importFromJsonString("""{"notebooks":[{"name":"Partial","items":[{"title":"Fail"}]}]}"""))
        assertTrue(repository.allNotebooks.first().isEmpty())
    }

    @Test fun legacyBackupsAndMalformedFilesAreHandledWithoutDataLoss() = runBlocking {
        assertTrue(backup.importFromJsonString("""{"notebooks":[{"name":"Legacy","items":[{"title":"First"},{"title":"Second"}]}]}"""))
        val legacyId = repository.allNotebooks.first().single().id
        assertEquals(listOf("First", "Second"), repository.getItemsForNotebook(legacyId).first().map { it.title })
        assertFalse(backup.importFromJsonString("""{"schemaVersion":999,"notebooks":[]}"""))
        assertFalse(backup.importFromJsonString("{invalid"))
        assertEquals(1, repository.allNotebooks.first().size)
    }

    @Test fun sharedNotebookPreservesOptionalFieldsAndNullDates() {
        val notebook = NotebookEntity(name = "Shared", archived = true, showCompleted = false, autoSort = false)
        val item = ListItemEntity(notebookId = 4, title = "Task", reminderTime = 123L, dueDate = null,
            isRecurring = true, recurrenceRule = "WEEKLY", color = 3, emoji = "★", completedAt = 77,
            subtasksJson = "a|false|Child", customFieldsJson = "x|Text|value")
        val (restored, items) = NotebookShareManager.parseFlexiJson(NotebookShareManager.createFlexiJson(notebook, listOf(item)))
        assertEquals(notebook, restored)
        assertEquals(item.copy(notebookId = 0), items.single())
        val (_, legacy) = NotebookShareManager.parseFlexiJson("""{"notebook":{"name":"Old"},"items":[{"title":"Task","dueDate":null}]}""")
        assertNull(legacy.single().dueDate)
    }
}
