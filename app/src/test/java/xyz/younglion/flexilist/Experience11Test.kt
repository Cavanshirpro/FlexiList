package xyz.younglion.flexilist

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.younglion.flexilist.data.backup.BackupManager
import xyz.younglion.flexilist.data.db.AppDatabase
import xyz.younglion.flexilist.data.model.*
import xyz.younglion.flexilist.data.repository.FlexiRepository
import xyz.younglion.flexilist.ui.workspace.*
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Experience11Test {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repo: FlexiRepository
    private lateinit var scope: CoroutineScope
    private lateinit var controller: WorkspaceController
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FlexiRepository(db)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        controller = WorkspaceController(app, repo, BackupManager(repo), scope, SavedStateHandle())
    }
    @After fun cleanup() = runBlocking { scope.coroutineContext[Job]?.cancelAndJoin(); db.close() }

    @Test fun searchIsLiteralUnicodeAwareAndDoesNotExposeStoredWorkoutDsl() {
        val book = NotebookEntity(id = 1, name = "Чтение %_", type = "ReadingList")
        val item = ListItemEntity(id = 2, notebookId = 1, title = "İşlər", description = "[EXERCISE_CFG]sets=3", tags = "личное")
        val library = LibraryState.Ready(listOf(book), listOf(item))
        assertEquals(1, searchLibrary(library, "личное", SearchFocus.ALL, null, false, 0).items.size)
        assertEquals(1, searchLibrary(library, "%_", SearchFocus.ALL, null, false, 0).notebooks.size)
        assertTrue(searchLibrary(library, "sets=3", SearchFocus.ALL, null, false, 0).items.isEmpty())
        assertTrue(searchLibrary(library, "", SearchFocus.ALL, WorkspaceId.LISTS, false, 0).items.isEmpty())
    }

    @Test fun archiveIsOptInAndFavoritesIncludeStarredEntriesAndFavoriteNotebooks() {
        val a = NotebookEntity(id = 1, name = "Active", favorite = true)
        val b = NotebookEntity(id = 2, name = "Archive", archived = true)
        val library = LibraryState.Ready(listOf(a, b), listOf(ListItemEntity(id = 1, notebookId = 1, title = "Entry"), ListItemEntity(id = 2, notebookId = 2, title = "Archived", starred = true)))
        assertEquals(listOf(1L), searchLibrary(library, "", SearchFocus.STARRED, null, false, 0).items.map { it.item.id })
        assertEquals(2, searchLibrary(library, "", SearchFocus.STARRED, null, true, 0).items.size)
    }

    @Test fun todayIncludesOverdueAndTodaysOpenTasksAndHabitsButNotTomorrowOrCompleted() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 11, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val tomorrow = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DATE, 1) }.timeInMillis
        val library = LibraryState.Ready(listOf(NotebookEntity(id = 1, name = "Tasks"), NotebookEntity(id = 2, name = "Habits", type = "HabitTracker")), listOf(
            ListItemEntity(id = 1, notebookId = 1, title = "Overdue", dueDate = now - 86400000),
            ListItemEntity(id = 2, notebookId = 1, title = "Today", dueDate = now),
            ListItemEntity(id = 3, notebookId = 1, title = "Tomorrow", dueDate = tomorrow),
            ListItemEntity(id = 4, notebookId = 1, title = "Done", dueDate = now, completed = true),
            ListItemEntity(id = 5, notebookId = 2, title = "Habit"),
            ListItemEntity(id = 6, notebookId = 1, title = "Undated")))
        assertEquals(listOf(1L, 2L, 5L), searchLibrary(library, "", SearchFocus.TODAY, null, false, now).items.map { it.item.id })
    }

    @Test fun previewedStarterCreatesNotebookAndOrderedEntriesTogetherAndReattachesOnce() = runBlocking {
        val notebook = NotebookEntity(name = "Weekly", type = NotebookKind.TASK.wireName)
        val entries = NotebookStarter.WEEKLY.items(app)
        val id = controller.saveNotebook(notebook, entries)
        assertEquals(id, controller.saveNotebook(notebook, NotebookStarter.WEEKLY.items(app)))
        assertEquals(1, repo.getAllNotebooksSuspend().size)
        assertEquals(entries.map { it.title }, repo.getItemsForNotebookSuspend(id).map { it.title })
        assertEquals(listOf(0, 1, 2), repo.getItemsForNotebookSuspend(id).map { it.sortOrder })
    }

    @Test fun batchPreservesIntentionalDuplicatesAndDetailsAndAppendsInLineOrder() = runBlocking {
        val id = repo.insertNotebook(NotebookEntity(name = "Batch"))
        repo.insertItem(ListItemEntity(notebookId = id, title = "Existing", sortOrder = 4))
        val items = listOf("Milk", "Milk", "Bread").map { ListItemEntity(notebookId = id, title = it, category = "Groceries", tags = "weekly") }
        controller.saveBatch(items); controller.saveBatch(items)
        val rows = repo.getItemsForNotebookSuspend(id)
        assertEquals(listOf("Existing", "Milk", "Milk", "Bread"), rows.map { it.title })
        assertEquals(listOf(4, 5, 6, 7), rows.map { it.sortOrder })
        assertTrue(rows.drop(1).all { it.category == "Groceries" && it.tags == "weekly" })
    }

    @Test fun failedOrOversizedBatchDoesNotWriteAnyRows() = runBlocking {
        val id = repo.insertNotebook(NotebookEntity(name = "Keep"))
        try { controller.saveBatch((1..101).map { ListItemEntity(notebookId = id, title = "$it") }); fail() } catch (_: IllegalArgumentException) { }
        try { controller.saveBatch(listOf(ListItemEntity(notebookId = id, title = "First"), ListItemEntity(notebookId = 999, title = "Wrong parent"))); fail() } catch (_: IllegalArgumentException) { }
        assertTrue(repo.getItemsForNotebookSuspend(id).isEmpty())
        try { controller.saveBatch(listOf(ListItemEntity(notebookId = 999, title = "Deleted parent"))); fail() } catch (_: IllegalStateException) { }
        assertTrue(repo.getItemsForNotebookSuspend(id).isEmpty())
    }

    @Test fun consecutiveDeletesShareOneUndoWithoutLosingTheFirstSnapshot() = runBlocking {
        val id = repo.insertNotebook(NotebookEntity(name = "Undo"))
        val first = repo.insertItem(ListItemEntity(notebookId = id, title = "First"))
        val second = repo.insertItem(ListItemEntity(notebookId = id, title = "Second"))
        controller.deleteItems(setOf(first)); val oldToken = controller.deletion.value!!.token
        controller.deleteItems(setOf(second)); controller.expireDeletion(oldToken)
        assertEquals(2, controller.deletion.value!!.items.size)
        assertTrue(controller.undoDelete(controller.deletion.value!!.token))
        assertEquals(setOf(first, second), repo.getItemsForNotebookSuspend(id).map { it.id }.toSet())
    }

    @Test fun overviewCountsFollowCompletionAndDeletionWithoutReadingEntryBodies() = runBlocking {
        val id = repo.insertNotebook(NotebookEntity(name = "Stats"))
        val a = repo.insertItem(ListItemEntity(notebookId = id, title = "A"))
        repo.insertItem(ListItemEntity(notebookId = id, title = "B", completed = true))
        assertEquals(NotebookStats(id, 2, 1), repo.notebookStats().first().single())
        repo.deleteItem(repo.getItemByIdSuspend(a)!!)
        assertEquals(NotebookStats(id, 1, 1), repo.notebookStats().first().single())
    }
}
