package xyz.younglion.flexilist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import xyz.younglion.flexilist.data.model.WaterIntakeEntity
import xyz.younglion.flexilist.data.model.WorkoutLogEntity
import xyz.younglion.flexilist.data.repository.FlexiRepository
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupAndSyncRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FlexiRepository
    private lateinit var backupManager: BackupManager
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FlexiRepository(db)
        backupManager = BackupManager(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testShareJsonCreationAndIntegrity() {
        val notebook = NotebookEntity(
            id = 1L,
            name = "🏋️ Türkçe & English Antrenman & To-Do",
            description = "Test açıklaması ile özel karakterler: \"quotes\", 'single', newline\nand tabs\t",
            emoji = "⚡",
            color = -123456,
            type = "Workout Planner",
            pinned = true,
            archived = false,
            favorite = true,
            showCompleted = true,
            moveCompletedToBottom = true,
            autoSort = true,
            priorityConfigJson = "[{\"level\":1,\"name\":\"Önemli\",\"colorHex\":\"#FF0000\",\"enabled\":true}]"
        )

        val items = listOf(
            ListItemEntity(
                id = 10L,
                notebookId = 1L,
                title = "Bench Press \"Max\"",
                description = "[EXERCISE_CFG]type=Reps|sets=4|reps=10|time=0|rest=90|weight=80.0|muscles=Chest,Triceps|group=A|days=Mon,Wed",
                completed = false,
                priority = 1,
                tags = "chest,heavy",
                category = "Upper Body",
                color = -65536,
                emoji = "💪",
                dueDate = 1700000000000L,
                reminderTime = 1700000005000L,
                isRecurring = true,
                recurrenceRule = "DAILY",
                starred = true,
                subtasksJson = "[{\"id\":\"st1\",\"title\":\"Warmup 40kg\",\"completed\":true}]",
                customFieldsJson = "[{\"name\":\"RPE\",\"type\":\"Number\",\"value\":\"8\"}]",
                sortOrder = 1,
                completedAt = null
            )
        )

        val json = NotebookShareManager.createFlexiJson(notebook, items)
        assertNotNull(json)
        assertTrue(json.contains("FlexiList"))
        assertTrue(json.contains("Bench Press \\\"Max\\\""))
        assertTrue(json.contains("Chest,Triceps"))
    }

    @Test
    fun testFullBackupExportAndRestore() = runBlocking {
        // 1. Seed database
        val nbId1 = repository.insertNotebook(
            NotebookEntity(
                name = "Market Listesi",
                description = "Haftalık alışveriş",
                emoji = "🛒",
                color = 1,
                type = "Shopping"
            )
        )

        val nbId2 = repository.insertNotebook(
            NotebookEntity(
                name = "Antrenman Planı",
                description = "Push/Pull",
                emoji = "🏋️",
                color = 2,
                type = "Workout Planner"
            )
        )

        repository.insertItem(
            ListItemEntity(
                notebookId = nbId1,
                title = "Süt & Yumurta",
                description = "Organik",
                completed = true,
                priority = 2,
                tags = "market,kahvaltı",
                completedAt = 1690000000000L
            )
        )

        repository.insertItem(
            ListItemEntity(
                notebookId = nbId2,
                title = "Incline Dumbbell Press",
                description = "[EXERCISE_CFG]type=Reps|sets=3|reps=12|time=0|rest=60|weight=24.0|muscles=Chest|group=|days=Mon",
                completed = false,
                priority = 3,
                starred = true
            )
        )

        repository.insertWorkoutLog(
            WorkoutLogEntity(
                notebookId = nbId2,
                workoutName = "Antrenman Planı - Push Day",
                startTime = 10000L,
                endTime = 13600L,
                totalDurationSeconds = 3600L,
                totalSetsCompleted = 12,
                totalWeightLiftedKg = 2400.0,
                exerciseStatsJson = "{}",
                createdAt = System.currentTimeMillis()
            )
        )

        repository.insertWaterIntake(
            WaterIntakeEntity(
                dateString = "2026-08-23",
                amountMl = 2500,
                updatedAt = System.currentTimeMillis()
            )
        )

        // 2. Export to JSON
        val exportedJson = backupManager.exportToJsonString()
        assertNotNull(exportedJson)
        assertTrue(exportedJson!!.contains("Market Listesi"))
        assertTrue(exportedJson.contains("Antrenman Planı"))
        assertTrue(exportedJson.contains("Incline Dumbbell Press"))
        assertTrue(exportedJson.contains("2500"))

        // 3. Clear database (by creating fresh in-memory db)
        val freshDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val freshRepo = FlexiRepository(freshDb)
        val freshBackupManager = BackupManager(freshRepo)

        // 4. Import into fresh database
        val importSuccess = freshBackupManager.importFromJsonString(exportedJson)
        assertTrue("Import should succeed", importSuccess)

        // 5. Verify restored state
        val restoredNotebooks = freshRepo.allNotebooks.first()
        assertEquals(2, restoredNotebooks.size)

        val restoredNb1 = restoredNotebooks.first { it.name == "Market Listesi" }
        val itemsNb1 = freshRepo.getItemsForNotebook(restoredNb1.id).first()
        assertEquals(1, itemsNb1.size)
        assertEquals("Süt & Yumurta", itemsNb1[0].title)
        assertTrue(itemsNb1[0].completed)
        assertEquals(1690000000000L, itemsNb1[0].completedAt)

        val restoredNb2 = restoredNotebooks.first { it.name == "Antrenman Planı" }
        val itemsNb2 = freshRepo.getItemsForNotebook(restoredNb2.id).first()
        assertEquals(1, itemsNb2.size)
        assertEquals("Incline Dumbbell Press", itemsNb2[0].title)
        assertTrue(itemsNb2[0].starred)

        val restoredLogs = freshRepo.getAllWorkoutLogs().first()
        assertEquals(1, restoredLogs.size)
        assertEquals("Antrenman Planı - Push Day", restoredLogs[0].workoutName)
        assertEquals(restoredNb2.id, restoredLogs[0].notebookId)

        val restoredWaters = freshRepo.getAllWaterIntakes().first()
        assertEquals(1, restoredWaters.size)
        assertEquals(2500, restoredWaters[0].amountMl)

        freshDb.close()
    }

    @Test
    fun testCsvExportFormat() = runBlocking {
        val nbId = repository.insertNotebook(
            NotebookEntity(
                name = "İş Listesi",
                description = "Günlük görevler",
                emoji = "💼"
            )
        )

        repository.insertItem(
            ListItemEntity(
                notebookId = nbId,
                title = "Rapor hazırla, \"Önemli\"",
                description = "Satış & pazarlama verileri",
                completed = false,
                priority = 3,
                tags = "is,rapor",
                category = "Ofis"
            )
        )

        val out = ByteArrayOutputStream()
        val success = backupManager.exportToCsv(out)
        assertTrue(success)

        val csvContent = out.toString("UTF-8")
        assertTrue(csvContent.startsWith("Defter,Baslik,Aciklama,Tamamlandi,Oncelik,Etiketler,Kategori,Tarih"))
        assertTrue(csvContent.contains("\"İş Listesi\""))
        assertTrue(csvContent.contains("\"Rapor hazırla, \"\"Önemli\"\"\""))
        assertTrue(csvContent.contains("\"Yüksek\""))
    }

    @Test
    fun testMigration3To4WithIndices() {
        val dbHelper = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration_db")
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val repo = FlexiRepository(dbHelper)
        val nbId = runBlocking {
            repo.insertNotebook(NotebookEntity(name = "Index Test", type = "Normal"))
        }
        assertTrue(nbId > 0)
        val itemId = runBlocking {
            repo.insertItem(ListItemEntity(notebookId = nbId, title = "Indexed item", sortOrder = 1))
        }
        assertTrue(itemId > 0)
        dbHelper.close()
    }
}
