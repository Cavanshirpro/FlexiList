package xyz.younglion.flexilist

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.younglion.flexilist.data.db.AppDatabase
import xyz.younglion.flexilist.data.backup.NotebookShareManager
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-mdpi", sdk = [36])
class WorkspaceFlowTest {
    @get:Rule val ui = createEmptyComposeRule()
    private lateinit var app: Application
    private var scenario: ActivityScenario<MainActivity>? = null
    private fun resetDatabase() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        (field.get(null) as? AppDatabase)?.close()
        field.set(null, null)
    }
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        resetDatabase()
        app.deleteDatabase("flexilist_database")
        app.getSharedPreferences("flexilist_prefs", Context.MODE_PRIVATE).edit().clear().putString("app_language", "en").commit()
        app.getSharedPreferences("flexilist_workout_state", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun cleanup() {
        scenario?.close()
        resetDatabase()
    }
    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.mainClock.autoAdvance = false
        awaitTag("add_notebook_button")
    }
    private fun frame() { ui.mainClock.advanceTimeBy(1000); ui.waitForIdle() }
    private fun awaitTag(tag: String) {
        try {
            ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            if (System.getProperty("flexilist.captureUx") == "true") runCatching { ui.onRoot().captureRoboImage("build/outputs/ux/failure-$tag.png") }
            println(ui.onRoot().printToString())
            throw failure
        }
    }
    private fun click(tag: String) { frame(); ui.onNodeWithTag(tag).performClick(); frame() }
    private fun shot(name: String, dialog: Boolean = false) {
        if (System.getProperty("flexilist.captureUx") != "true") return
        frame()
        (if (dialog) ui.onNode(isDialog()) else ui.onRoot()).captureRoboImage("build/outputs/ux/$name.png")
    }

    @Test fun workspaceCreationDraftTransferRecreationAndThemes() {
        launch()
        listOf("LISTS", "HABITS", "WORKOUTS", "LIBRARY").forEach { ui.onNodeWithTag("workspace-$it").assertExists() }
        shot("01-empty-light")
        click("add_notebook_button")
        ui.onNodeWithTag("notebook_name_input").performTextInput("My weekly tasks with a deliberately long notebook title")
        click("save_notebook_button")
        awaitTag("add_entry_button")
        shot("02-notebook")
        click("add_entry_button")
        awaitTag("quick_entry_title")
        ui.onNodeWithTag("quick_entry_title").performTextInput("A draft that must survive the full editor")
        click("quick_entry_details")
        awaitTag("save_item_button")
        ui.onNodeWithText("A draft that must survive the full editor").assertExists()
        scenario!!.recreate()
        frame()
        awaitTag("save_item_button")
        ui.onNodeWithText("A draft that must survive the full editor").assertExists()
        shot("03-editor-draft")
        click("save_item_button")
        awaitTag("add_entry_button")
        val database = AppDatabase.getDatabase(app)
        val entry = runBlocking(Dispatchers.IO) { database.listItemDao().getItemsForNotebookSuspend(database.notebookDao().getAllNotebooksSuspend().single().id).single() }
        assertEquals("A draft that must survive the full editor", entry.title)
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); ui.onAllNodesWithTag("complete-item-${entry.id}").fetchSemanticsNodes().isNotEmpty() }
        click("complete-item-${entry.id}")
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); ui.onNodeWithTag("complete-item-${entry.id}").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] == androidx.compose.ui.state.ToggleableState.On }
        shot("04-completed-task")
        click("workspace-HABITS")
        awaitTag("add_notebook_button")
        shot("05-habits-empty")
        click("workspace-LISTS")
        awaitTag("add_entry_button")
        ui.onNodeWithText(entry.title).assertExists()
        ui.onNodeWithContentDescription("Back").performClick(); frame()
        awaitTag("general_settings")
        shot("06-overview")
        click("general_settings")
        click("appearance_settings")
        click("theme-DARK")
        shot("07-appearance-dark", dialog = true)
        click("theme-SYSTEM")
        ui.onNodeWithTag("theme-SYSTEM").assertIsSelected()
        RuntimeEnvironment.setQualifiers("+night")
        scenario!!.recreate(); frame()
        ui.onNodeWithTag("theme-SYSTEM").assertIsSelected()
        shot("08-system-night", dialog = true)
        click("theme-LIGHT")
        shot("09-appearance-light", dialog = true)
    }

    @Test
    @Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36])
    fun narrowLargeTextStillAllowsNotebookCreation() {
        val configuration = Configuration(app.resources.configuration).apply { fontScale = 2f }
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(configuration, app.resources.displayMetrics)
        launch()
        shot("10-narrow-large-text")
        click("add_notebook_button")
        ui.onNodeWithTag("notebook_name_input").performTextInput("A large text notebook")
        click("save_notebook_button")
        awaitTag("add_entry_button")
        shot("11-narrow-notebook")
        assertEquals(1, runBlocking(Dispatchers.IO) { AppDatabase.getDatabase(app).notebookDao().getAllNotebooksSuspend().size })
    }

    @Test
    @Config(qualifiers = "w900dp-h900dp-mdpi", sdk = [36])
    fun expandedLayoutShowsMasterAndDetailAndSwitchesWorkspaces() {
        val database = AppDatabase.getDatabase(app)
        runBlocking(Dispatchers.IO) {
            val id = database.notebookDao().insertNotebook(NotebookEntity(name = "Tablet tasks", type = "ToDo"))
            database.listItemDao().insertItem(ListItemEntity(notebookId = id, title = "Tablet entry"))
            database.notebookDao().insertNotebook(NotebookEntity(name = "Reading collection", type = "ReadingList"))
        }
        launch()
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); ui.onAllNodesWithText("Tablet tasks").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Tablet tasks").performClick(); frame()
        awaitTag("add_entry_button")
        ui.onNodeWithTag("workspace_notebooks").assertExists()
        ui.onNodeWithText("Tablet entry").assertExists()
        shot("12-expanded-master-detail")
        click("workspace-LIBRARY")
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); ui.onAllNodesWithText("Reading collection").fetchSemanticsNodes().isNotEmpty() }
        shot("13-library")
    }
    @Test fun incomingShareRequiresConfirmationAndCanBeCancelledAfterRecreation() {
        val notebook = NotebookEntity(id = 78, name = "Shared shopping list", type = "Checklist")
        val json = NotebookShareManager.createFlexiJson(notebook, listOf(ListItemEntity(notebookId = 78, title = "Milk")))
        val intent = Intent(app, MainActivity::class.java).setAction(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, json)
        scenario = ActivityScenario.launch(intent)
        ui.mainClock.autoAdvance = false
        awaitTag("confirm_import")
        val database = AppDatabase.getDatabase(app)
        assertTrue(runBlocking(Dispatchers.IO) { database.notebookDao().getAllNotebooksSuspend().isEmpty() })
        shot("14-import-review", dialog = true)
        scenario!!.recreate(); frame(); awaitTag("confirm_import")
        click("cancel_import")
        assertTrue(runBlocking(Dispatchers.IO) { database.notebookDao().getAllNotebooksSuspend().isEmpty() })
        scenario!!.onActivity { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, intent) }
        awaitTag("confirm_import"); click("confirm_import")
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); runBlocking(Dispatchers.IO) { database.notebookDao().getAllNotebooksSuspend().size == 1 } }
        assertEquals("Milk", runBlocking(Dispatchers.IO) { database.listItemDao().getItemsForNotebookSuspend(database.notebookDao().getAllNotebooksSuspend().single().id).single().title })
    }

    @Test fun bulkDeletionCanBeUndoneFromTheVisibleSnackbar() {
        val database = AppDatabase.getDatabase(app)
        val itemId = runBlocking(Dispatchers.IO) {
            val notebook = database.notebookDao().insertNotebook(NotebookEntity(name = "Undo tasks", type = "ToDo"))
            database.listItemDao().insertItem(ListItemEntity(notebookId = notebook, title = "Restore this task"))
        }
        launch()
        ui.onNodeWithText("Undo tasks").performClick(); frame(); awaitTag("add_entry_button")
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); ui.onAllNodesWithText("Restore this task").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Restore this task").performTouchInput { longClick() }; frame()
        ui.onNodeWithTag("bulk_delete").performClick()
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(50); ui.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        assertNull(runBlocking(Dispatchers.IO) { database.listItemDao().getItemByIdSuspend(itemId) })
        shot("15-deletion-undo")
        ui.onNodeWithText("Undo").performClick()
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(50); runBlocking(Dispatchers.IO) { database.listItemDao().getItemByIdSuspend(itemId) != null } }
        assertEquals("Restore this task", runBlocking(Dispatchers.IO) { database.listItemDao().getItemByIdSuspend(itemId)?.title })
    }

    @Test fun workoutWorkspaceRetainsExerciseWaterAnalyticsAndPlayer() {
        val database = AppDatabase.getDatabase(app)
        runBlocking(Dispatchers.IO) {
            val notebook = database.notebookDao().insertNotebook(NotebookEntity(name = "Full body training", type = "WorkoutPlanner",
                description = "[WORKOUT_CFG]frequency=Weekly|weekly=1,2,3,4,5,6,7"))
            database.listItemDao().insertItem(ListItemEntity(notebookId = notebook, title = "Squat",
                description = "[EXERCISE_CFG]type=Reps|sets=3|reps=10|time=30|rest=60|weight=12.5|muscles=Bacak|group=|days="))
        }
        launch(); click("workspace-WORKOUTS")
        ui.onNodeWithText("Full body training").performClick(); frame(); awaitTag("start_workout_button")
        shot("16-workout")
        ui.onNodeWithText(app.getString(R.string.water_tracker)).performScrollTo().performClick(); frame()
        ui.onNodeWithTag("add_entry_button").assertDoesNotExist()
        ui.onNodeWithTag("surahi_750").performClick(); frame()
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); ui.onAllNodesWithText("750 / 2,500 ml").fetchSemanticsNodes().isNotEmpty() }
        shot("17-water")
        ui.onNodeWithText(app.getString(R.string.analytics_report)).performScrollTo().performClick(); frame(); shot("18-analytics")
        ui.onNodeWithText(app.getString(R.string.exercises)).performScrollTo().performClick(); frame()
        ui.onNodeWithTag("start_workout_button").assertIsEnabled().performClick(); frame()
        shot("19-workout-player")
        ui.onNodeWithTag("workspace-WORKOUTS").assertDoesNotExist()
    }

    @Test fun notebookSearchIsScopedAndHabitAndReadingViewsShowRealProgress() {
        val database = AppDatabase.getDatabase(app)
        runBlocking(Dispatchers.IO) {
            listOf(
                Triple("First tasks", "ToDo", "Alpha task"),
                Triple("Second tasks", "ToDo", "Beta task"),
                Triple("Daily habits", "HabitTracker", "Read each morning"),
                Triple("Books", "ReadingList", "A book to read")
            ).forEach { (name, type, title) ->
                val id = database.notebookDao().insertNotebook(NotebookEntity(name = name, type = type))
                database.listItemDao().insertItem(ListItemEntity(notebookId = id, title = title))
            }
        }
        launch(); ui.onNodeWithText("First tasks").performClick(); frame(); awaitTag("item_search_input")
        ui.onNodeWithTag("item_search_input").performTextInput("Alpha"); frame()
        click("workspace-HABITS")
        ui.onNodeWithText("Daily habits").performClick(); frame(); awaitTag("item_search_input")
        ui.onNodeWithText("Read each morning").assertExists(); shot("20-habits")
        click("workspace-LISTS"); awaitTag("item_search_input")
        ui.onNodeWithTag("item_search_input").assertTextContains("Alpha")
        ui.onNodeWithContentDescription("Back").performClick(); frame()
        ui.onNodeWithText("Second tasks").performClick(); frame(); awaitTag("item_search_input")
        ui.onNodeWithText("Beta task").assertExists(); ui.onNodeWithText("Alpha task").assertDoesNotExist()
        ui.onNodeWithContentDescription("Back").performClick(); frame()
        ui.onNodeWithText("First tasks").performClick(); frame(); awaitTag("item_search_input")
        ui.onNodeWithTag("item_search_input").assertTextContains("Alpha")
        scenario!!.recreate(); frame(); awaitTag("item_search_input")
        ui.onNodeWithTag("item_search_input").assertTextContains("Alpha")
        click("workspace-LIBRARY")
        ui.onNodeWithText("Books").performClick(); frame(); awaitTag("item_search_input")
        ui.onNodeWithText("A book to read").assertExists()
        ui.onNodeWithText("Read", substring = false).assertExists()
        shot("21-reading")
    }

    @Test fun previewedStarterAndBatchEntrySurviveRecreationAndKeepAdding() {
        launch(); click("add_notebook_button")
        ui.onNodeWithTag("starter-GROCERIES").performScrollTo().performClick(); frame()
        shot("22-starter-form")
        click("save_notebook_button"); awaitTag("add_entry_button")
        val database = AppDatabase.getDatabase(app)
        val notebook = runBlocking(Dispatchers.IO) { database.notebookDao().getAllNotebooksSuspend().single() }
        assertEquals(3, runBlocking(Dispatchers.IO) { database.listItemDao().getItemsForNotebookSuspend(notebook.id).size })
        click("add_entry_button"); click("quick_entry_multiple")
        ui.onNodeWithTag("quick_entry_title").performTextInput("Tea\nCoffee\nTea")
        ui.onNodeWithTag("quick_keep_open").performScrollTo().performClick(); frame()
        scenario!!.recreate(); frame(); awaitTag("quick_entry_title")
        ui.onNodeWithTag("quick_entry_title").assertTextContains("Tea\nCoffee\nTea")
        shot("23-batch-entry", dialog = true)
        ui.onNodeWithTag("quick_entry_save").performScrollTo().performClick(); frame()
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); runBlocking(Dispatchers.IO) { database.listItemDao().getItemsForNotebookSuspend(notebook.id).size == 6 } }
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); ui.onNodeWithTag("quick_entry_title").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty() }
        ui.onNodeWithTag("quick_entry_title").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        ui.onNodeWithTag("quick_entry_title").performTextInput("Rice")
        ui.onNodeWithTag("quick_entry_save").performScrollTo().performClick(); frame()
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); runBlocking(Dispatchers.IO) { database.listItemDao().getItemsForNotebookSuspend(notebook.id).size == 7 } }
        val entries = runBlocking(Dispatchers.IO) { database.listItemDao().getItemsForNotebookSuspend(notebook.id) }
        assertEquals(listOf("Tea", "Coffee", "Tea", "Rice"), entries.takeLast(4).map { it.title })
        ui.onNodeWithText("Done").performScrollTo().performClick(); frame()
        ui.onNodeWithContentDescription("Back").performClick(); frame(); awaitTag("global_search_button")
        shot("24-v11-overview")
        click("global_search_button"); awaitTag("global_search_input")
        ui.onNodeWithTag("global_search_input").performTextInput("Tea")
        awaitTag("search-entry-${entries.first { it.title == "Tea" }.id}")
        shot("25-search", dialog = true)
        scenario!!.recreate(); frame(); awaitTag("global_search_input")
        ui.onNodeWithTag("global_search_input").assertTextContains("Tea")
        awaitTag("search-entry-${entries.first { it.title == "Tea" }.id}")
        ui.onNodeWithTag("search-entry-${entries.first { it.title == "Tea" }.id}").performScrollTo().performClick(); frame(); awaitTag("save_item_button")
        ui.onNodeWithText("Tea").assertExists()
    }

    @Test fun globalTodayAndArchivedSearchOpenTheCorrectWorkspaceEntry() {
        val database = AppDatabase.getDatabase(app)
        var archivedEntry = 0L
        runBlocking(Dispatchers.IO) {
            val tasks = database.notebookDao().insertNotebook(NotebookEntity(name = "Daily tasks", type = "ToDo"))
            database.listItemDao().insertItem(ListItemEntity(notebookId = tasks, title = "Due task", dueDate = System.currentTimeMillis() - 1000))
            val habits = database.notebookDao().insertNotebook(NotebookEntity(name = "Routine", type = "HabitTracker"))
            database.listItemDao().insertItem(ListItemEntity(notebookId = habits, title = "Stretch"))
            val archive = database.notebookDao().insertNotebook(NotebookEntity(name = "Archived books", type = "ReadingList", archived = true))
            archivedEntry = database.listItemDao().insertItem(ListItemEntity(notebookId = archive, title = "Hidden chapter"))
        }
        app.getSharedPreferences("flexilist_prefs", Context.MODE_PRIVATE).edit().putString("app_language", "tr").putString("theme_mode", "DARK").commit()
        launch(); click("global_search_button"); awaitTag("global_search_input")
        click("search-focus-TODAY")
        ui.waitUntil(10000) { ui.mainClock.advanceTimeBy(100); ui.onAllNodesWithText("Due task").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Stretch").assertExists(); shot("26-today", dialog = true)
        click("search-focus-ALL")
        ui.onNodeWithTag("global_search_input").performTextInput("Hidden")
        frame(); ui.onNodeWithTag("search-entry-$archivedEntry").assertDoesNotExist()
        ui.onNodeWithTag("search_include_archive").performScrollTo().performClick(); frame()
        awaitTag("search-entry-$archivedEntry")
        ui.onNodeWithTag("search-entry-$archivedEntry").performScrollTo().performClick(); frame(); awaitTag("save_item_button")
        ui.onNodeWithText("Hidden chapter").assertExists()
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }; frame(); awaitTag("add_entry_button")
        ui.onNodeWithText("Archived books").assertExists()
        ui.onNodeWithTag("workspace-LIBRARY").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36])
    fun largeTextWorkoutKeepsTheProgramAndExercisesReachableInOneScroller() {
        val configuration = Configuration(app.resources.configuration).apply { fontScale = 2f }
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(configuration, app.resources.displayMetrics)
        val database = AppDatabase.getDatabase(app)
        runBlocking(Dispatchers.IO) {
            val id = database.notebookDao().insertNotebook(NotebookEntity(name = "Large text training", type = "WorkoutPlanner", description = "[WORKOUT_CFG]frequency=Weekly|weekly=1,2,3,4,5,6,7"))
            database.listItemDao().insertItem(ListItemEntity(notebookId = id, title = "Squat", description = "[EXERCISE_CFG]type=Reps|sets=2|reps=10|rest=30"))
        }
        launch(); click("workspace-WORKOUTS")
        ui.mainClock.autoAdvance = true
        shot("28-large-overview")
        ui.onNodeWithText("Large text training").performScrollTo().performClick(); frame(); awaitTag("notebook_content")
        ui.onNodeWithTag("notebook_content").performScrollToNode(hasText("Squat")); frame()
        ui.onNodeWithText("Squat").assertIsDisplayed(); shot("27-large-workout")
        ui.onNodeWithTag("notebook_content").performScrollToNode(hasTestTag("start_workout_button")); frame()
        ui.onNodeWithTag("start_workout_button").performScrollTo().assertIsEnabled()
        ui.mainClock.autoAdvance = false
        ui.onNodeWithTag("start_workout_button").performClick(); frame()
        ui.onNodeWithTag("workspace-WORKOUTS").assertDoesNotExist()
    }

}
