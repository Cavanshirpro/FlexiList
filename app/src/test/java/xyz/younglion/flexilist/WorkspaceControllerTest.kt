package xyz.younglion.flexilist

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.theme.darkAccent
import xyz.younglion.flexilist.ui.theme.LightBackground
import xyz.younglion.flexilist.ui.theme.LightSurfaceElevated
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
import xyz.younglion.flexilist.data.repository.FlexiRepository
import xyz.younglion.flexilist.ui.theme.ThemeAccentOptions
import xyz.younglion.flexilist.ui.theme.contrastRatio
import xyz.younglion.flexilist.ui.theme.lightAccent
import xyz.younglion.flexilist.ui.workspace.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkspaceControllerTest {
    private lateinit var app: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: FlexiRepository
    private lateinit var backups: BackupManager
    private lateinit var scope: CoroutineScope
    private lateinit var state: SavedStateHandle
    private lateinit var controller: WorkspaceController

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("flexilist_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FlexiRepository(database)
        backups = BackupManager(repository)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        state = SavedStateHandle()
        controller = WorkspaceController(app, repository, backups, scope, state)
    }
    @After fun cleanup() = runBlocking {
        controller.dismissImport()
        scope.coroutineContext[Job]?.cancelAndJoin()
        database.close()
        app.getSharedPreferences("flexilist_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }

    @Test fun legacyKindsKeepTheirWorkspaceAndUnknownKindsRemainVisible() {
        assertEquals(WorkspaceId.HABITS, NotebookKind.parse("HabitTracker").workspace)
        assertEquals(WorkspaceId.WORKOUTS, NotebookKind.parse("Workout Planner").workspace)
        assertEquals(WorkspaceId.LIBRARY, NotebookKind.parse("ReadingList").workspace)
        assertEquals(NotebookKind.OTHER, NotebookKind.parse("An imported custom kind"))
        assertEquals(WorkspaceId.LISTS, NotebookKind.parse("An imported custom kind").workspace)
    }

    @Test fun themeMigrationRetainsAnExplicitLegacyChoiceAndNewInstallsFollowSystem() {
        val preferences = app.getSharedPreferences("theme-fixture", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        assertEquals(ThemeMode.SYSTEM, ThemeMode.read(preferences))
        assertTrue(ThemeMode.SYSTEM.isDark(true)); assertFalse(ThemeMode.SYSTEM.isDark(false))
        preferences.edit().clear().putBoolean("is_dark_mode", false).commit()
        assertEquals(ThemeMode.LIGHT, ThemeMode.read(preferences))
        preferences.edit().putBoolean("is_dark_mode", true).commit()
        assertEquals(ThemeMode.LIGHT, ThemeMode.read(preferences)) // new preference remains authoritative
    }

    @Test fun everyAccentSupportsReadableTextOnLightAndDarkSurfaces() {
        ThemeAccentOptions.forEach { accent ->
            listOf(accent.primary, accent.secondary, accent.tertiary).forEach { color ->
                listOf(Color.White, LightBackground, LightSurfaceElevated).forEach { surface ->
                    assertTrue("Light accent ${accent.id}", contrastRatio(lightAccent(color), surface) >= 4.4999f)
                }
                assertTrue("Dark accent ${accent.id}", contrastRatio(darkAccent(color), Color(0xFF403E45)) >= 4.4999f)
            }
        }
    }

    @Test fun completeDraftSurvivesSavedStateRecreationWithoutWritingAnItem() = runBlocking {
        val id = repository.insertNotebook(NotebookEntity(name = "Notebook"))
        val draft = ListItemEntity(notebookId = id, title = "Taslak ə ı", priority = 3, tags = "one,two", category = "Custom", reminderTime = 12345L,
            isRecurring = true, recurrenceRule = "WEEKLY", customFieldsJson = "extra|Text|value", subtasksJson = "a|false|Draft subtask")
        controller.storeDraft(draft)
        val restored = SavedStateHandle(mapOf(EntryDraft.key(id, 0) to state.get<String>(EntryDraft.key(id, 0))))
        val recreated = WorkspaceController(app, repository, backups, scope, restored)
        assertEquals(draft, recreated.draft(id, 0)?.item)
        assertTrue(repository.getItemsForNotebookSuspend(id).isEmpty())
        assertNull(recreated.draft(id, 999))
    }

    @Test fun deletionUndoRestoresIdentityOrderAndAllItemFields() = runBlocking {
        val id = repository.insertNotebook(NotebookEntity(name = "Notebook"))
        val record = ListItemEntity(id = 41, notebookId = id, title = "Restore exactly", completed = true, completedAt = 900,
            priority = 2, sortOrder = 7, createdAt = 100, updatedAt = 800, starred = true, tags = "custom", category = "Personal",
            subtasksJson = "a|true|Keep", customFieldsJson = "field|Text|Keep", dueDate = 1234, reminderTime = 1235)
        repository.insertItem(record)
        controller.deleteItems(setOf(record.id))
        assertNull(repository.getItemByIdSuspend(record.id))
        val token = requireNotNull(controller.deletion.value).token
        assertTrue(controller.undoDelete(token))
        assertEquals(record, repository.getItemByIdSuspend(record.id))
    }

    @Test fun undoDoesNotRecreateADeletedNotebookOrOverwriteNewData() = runBlocking {
        val id = repository.insertNotebook(NotebookEntity(name = "Notebook"))
        val item = ListItemEntity(id = 11, notebookId = id, title = "Original")
        repository.insertItem(item)
        controller.deleteItems(setOf(item.id))
        repository.insertItem(item.copy(title = "Newer record with the same key"))
        assertFalse(controller.undoDelete(requireNotNull(controller.deletion.value).token))
        assertEquals("Newer record with the same key", repository.getItemByIdSuspend(item.id)?.title)
        repository.deleteNotebook(requireNotNull(repository.getNotebookByIdSuspend(id)))
        assertFalse(controller.undoDelete(requireNotNull(controller.deletion.value).token))
        assertTrue(repository.getAllNotebooksSuspend().isEmpty())
    }

    @Test fun savingAfterNotebookDeletionFailsAndKeepsTheDraft() = runBlocking {
        val id = repository.insertNotebook(NotebookEntity(name = "Notebook"))
        val record = ListItemEntity(notebookId = id, title = "Unsaved")
        controller.storeDraft(record)
        repository.deleteNotebook(requireNotNull(repository.getNotebookByIdSuspend(id)))
        try { controller.saveEntry(record); fail("Must reject a missing notebook") }
        catch (_: IllegalStateException) { }
        assertEquals(record, controller.draft(id, 0)?.item)
    }

    @Test fun sharePreviewDoesNotWriteUntilConfirmationAndUsesTheReviewedPayload() = runBlocking {
        val notebook = NotebookEntity(id = 88, name = "Reviewed notebook", category = "Custom")
        val item = ListItemEntity(notebookId = 88, title = "Reviewed item", sortOrder = 4)
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, NotebookShareManager.createFlexiJson(notebook, listOf(item)))
        controller.prepareIntent(intent)
        val ready = withTimeout(5000) { controller.importState.first { it is ImportUiState.Ready || it is ImportUiState.Failed } }
        assertTrue(ready is ImportUiState.Ready)
        assertEquals(1, (ready as ImportUiState.Ready).preview.itemCount)
        assertTrue(repository.getAllNotebooksSuspend().isEmpty())
        intent.putExtra(Intent.EXTRA_TEXT, "Changed after review")
        controller.confirmImport()
        assertEquals(ImportUiState.Idle, withTimeout(5000) { controller.importState.first { it == ImportUiState.Idle || it is ImportUiState.Failed } })
        val created = repository.getAllNotebooksSuspend().single()
        assertEquals(notebook.name, created.name)
        assertEquals(item.title, repository.getItemsForNotebookSuspend(created.id).single().title)
        assertFalse(state.contains("pending-import"))
    }

    @Test fun backupPreviewAndPendingReviewSurviveControllerRecreation() = runBlocking {
        val json = """{"schemaVersion":2,"notebooks":[{"name":"Backup copy","items":[{"title":"Entry"}]}]}"""
        controller.prepareIntent(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, json))
        assertTrue(withTimeout(5000) { controller.importState.first { it is ImportUiState.Ready || it is ImportUiState.Failed } } is ImportUiState.Ready)
        scope.coroutineContext[Job]?.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        controller = WorkspaceController(app, repository, backups, scope, state)
        assertTrue(withTimeout(5000) { controller.importState.first { it is ImportUiState.Ready || it is ImportUiState.Failed } } is ImportUiState.Ready)
        assertTrue(repository.getAllNotebooksSuspend().isEmpty())
        controller.confirmImport()
        assertEquals(ImportUiState.Idle, withTimeout(5000) { controller.importState.first { it == ImportUiState.Idle || it is ImportUiState.Failed } })
        assertEquals("Backup copy", repository.getAllNotebooksSuspend().single().name)
    }

    @Test fun interruptedSaveReattachesWithoutCreatingDuplicateRows() = runBlocking {
        val notebook = NotebookEntity(name = "Create once", type = "ToDo")
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) { controller.saveNotebook(notebook) }
        waiter.cancelAndJoin()
        val id = controller.saveNotebook(notebook.copy(createdAt = 9999, updatedAt = 9999))
        assertEquals(1, repository.getAllNotebooksSuspend().size)
        controller.acknowledgeNotebookSave(notebook)
        assertNotEquals(id, controller.saveNotebook(notebook))
        val item = ListItemEntity(notebookId = id, title = "Create entry once")
        val first = controller.saveEntry(item)
        assertEquals(first, controller.saveEntry(item.copy(createdAt = 1234)))
        assertEquals(1, repository.getItemsForNotebookSuspend(id).size)
        controller.acknowledgeEntrySave(item)
        assertNotEquals(first, controller.saveEntry(item))
    }

    @Test fun editingKeepsConcurrentCompletionOrderAndNotebookFlags() = runBlocking {
        val id = repository.insertNotebook(NotebookEntity(name = "Before"))
        val original = requireNotNull(repository.getNotebookByIdSuspend(id))
        repository.updateNotebook(original.copy(pinned = true, archived = true, showCompleted = false))
        controller.saveNotebook(original.copy(name = "After"))
        val edited = requireNotNull(repository.getNotebookByIdSuspend(id))
        assertTrue(edited.pinned); assertTrue(edited.archived); assertFalse(edited.showCompleted)
        val entryId = repository.insertItem(ListItemEntity(notebookId = id, title = "Original"))
        val snapshot = requireNotNull(repository.getItemByIdSuspend(entryId))
        repository.updateItem(snapshot.copy(completed = true, completedAt = 55, sortOrder = 9))
        controller.saveEntry(snapshot.copy(title = "Edited title"))
        val entry = requireNotNull(repository.getItemByIdSuspend(entryId))
        assertEquals("Edited title", entry.title)
        assertTrue(entry.completed); assertEquals(55L, entry.completedAt); assertEquals(9, entry.sortOrder)
    }

    @Test fun invalidBackupHasNoDatabaseEffects() = runBlocking {
        controller.prepareIntent(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, """{"schemaVersion":99,"notebooks":[{"name":"Never written"}]}"""))
        assertTrue(withTimeout(5000) { controller.importState.first { it is ImportUiState.Failed || it is ImportUiState.Ready } } is ImportUiState.Failed)
        assertTrue(repository.getAllNotebooksSuspend().isEmpty())
    }

    @Test fun legacySelectionMovesToItsDerivedWorkspaceWithoutChangingStoredKind() = runBlocking {
        val id = repository.insertNotebook(NotebookEntity(name = "Training", type = "WorkoutPlanner"))
        app.getSharedPreferences("flexilist_prefs", Context.MODE_PRIVATE).edit().putLong("selected_notebook_id", id).commit()
        assertEquals(WorkspaceId.WORKOUTS, controller.migrateWorkspace(repository.getAllNotebooksSuspend()))
        assertEquals(id, controller.lastNotebook(WorkspaceId.WORKOUTS))
        assertEquals("WorkoutPlanner", repository.getNotebookByIdSuspend(id)?.type)
    }
    @Test fun notebookFlagCommandsReadTheLatestRecordInsteadOfOverwritingIt() = runBlocking {
        val id = repository.insertNotebook(NotebookEntity(name = "Latest title", description = "Keep description"))
        controller.toggleNotebookFlag(id, WorkspaceController.NotebookFlag.PINNED)
        controller.toggleNotebookFlag(id, WorkspaceController.NotebookFlag.FAVORITE)
        controller.toggleNotebookFlag(id, WorkspaceController.NotebookFlag.ARCHIVED)
        val record = requireNotNull(repository.getNotebookByIdSuspend(id))
        assertTrue(record.pinned); assertTrue(record.favorite); assertTrue(record.archived)
        assertEquals("Latest title", record.name); assertEquals("Keep description", record.description)
        controller.toggleNotebookFlag(id, WorkspaceController.NotebookFlag.PINNED)
        assertFalse(requireNotNull(repository.getNotebookByIdSuspend(id)).pinned)
    }

    @Test fun oversizedImportIsRejectedWithAnActionableMessageAndNoDatabaseWrites() = runBlocking {
        controller.prepareIntent(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, " ".repeat(WorkspaceController.MAX_IMPORT_BYTES + 1)))
        val error = withTimeout(5000) { controller.importState.first { it is ImportUiState.Failed } } as ImportUiState.Failed
        assertEquals(R.string.ux_import_too_large, error.message)
        assertTrue(repository.getAllNotebooksSuspend().isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun activeAndBackgroundNotebookFiltersSurviveSwitchesAndSavedStateRecreation() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val first = repository.insertNotebook(NotebookEntity(name = "First"))
        val second = repository.insertNotebook(NotebookEntity(name = "Second"))
        val firstState = SavedStateHandle()
        var viewModel: FlexiViewModel? = null
        try {
            val original = FlexiViewModel(app, repository, backups, firstState).also { viewModel = it }
            original.selectNotebook(first)
            original.searchQuery.value = "First query"
            original.selectedTags.value = setOf("First tag")
            original.filterCompleted.value = true
            original.completionFilterExplicit.value = true
            original.selectNotebook(second)
            original.searchQuery.value = "Second query"
            original.selectNotebook(first)
            assertEquals("First query", original.searchQuery.value)
            assertEquals(setOf("First tag"), original.selectedTags.value)
            assertEquals(true, original.filterCompleted.value)
            assertTrue(original.completionFilterExplicit.value)
            val snapshot = firstState.keys().associateWith { firstState.get<Any?>(it) }
            original.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            val restored = FlexiViewModel(app, repository, backups, SavedStateHandle(snapshot)).also { viewModel = it }
            assertEquals("First query", restored.searchQuery.value)
            restored.selectNotebook(second)
            assertEquals("Second query", restored.searchQuery.value)
        } finally {
            viewModel?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

}
