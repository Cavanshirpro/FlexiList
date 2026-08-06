package xyz.younglion.flexilist.data.repository

import xyz.younglion.flexilist.data.db.ListItemDao
import xyz.younglion.flexilist.data.db.NotebookDao
import xyz.younglion.flexilist.data.db.WorkoutLogDao
import xyz.younglion.flexilist.data.db.WaterIntakeDao
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.data.model.WorkoutLogEntity
import xyz.younglion.flexilist.data.model.WaterIntakeEntity
import kotlinx.coroutines.flow.Flow

class FlexiRepository(
    private val notebookDao: NotebookDao,
    private val listItemDao: ListItemDao,
    private val workoutLogDao: WorkoutLogDao,
    private val waterIntakeDao: WaterIntakeDao
) {
    // WorkoutLog Actions
    fun getLogsForNotebook(notebookId: Long): Flow<List<WorkoutLogEntity>> =
        workoutLogDao.getLogsForNotebook(notebookId)

    fun getAllWorkoutLogs(): Flow<List<WorkoutLogEntity>> =
        workoutLogDao.getAllLogs()

    suspend fun insertWorkoutLog(log: WorkoutLogEntity): Long =
        workoutLogDao.insertLog(log)

    suspend fun deleteWorkoutLogById(id: Long) =
        workoutLogDao.deleteLogById(id)

    // WaterIntake Actions
    fun getWaterIntakeForDate(dateString: String): Flow<WaterIntakeEntity?> =
        waterIntakeDao.getWaterIntakeForDate(dateString)

    fun getAllWaterIntakes(): Flow<List<WaterIntakeEntity>> =
        waterIntakeDao.getAllWaterIntake()

    suspend fun insertWaterIntake(intake: WaterIntakeEntity) =
        waterIntakeDao.insertWaterIntake(intake)

    // Notebook actions
    val allNotebooks: Flow<List<NotebookEntity>> = notebookDao.getAllNotebooks()

    fun getNotebookById(id: Long): Flow<NotebookEntity?> = notebookDao.getNotebookById(id)

    suspend fun getNotebookByIdSuspend(id: Long): NotebookEntity? = notebookDao.getNotebookByIdSuspend(id)

    suspend fun insertNotebook(notebook: NotebookEntity): Long = notebookDao.insertNotebook(notebook)

    suspend fun updateNotebook(notebook: NotebookEntity) = notebookDao.updateNotebook(notebook)

    suspend fun deleteNotebook(notebook: NotebookEntity) = notebookDao.deleteNotebook(notebook)

    // ListItem actions
    fun getItemsForNotebook(notebookId: Long): Flow<List<ListItemEntity>> =
        listItemDao.getAllItemsForNotebook(notebookId)

    fun getAllItemsGlobal(): Flow<List<ListItemEntity>> = listItemDao.getAllItemsGlobal()

    fun getItemById(id: Long): Flow<ListItemEntity?> = listItemDao.getItemById(id)

    suspend fun getItemByIdSuspend(id: Long): ListItemEntity? = listItemDao.getItemByIdSuspend(id)

    suspend fun insertItem(item: ListItemEntity): Long = listItemDao.insertItem(item)

    suspend fun insertItems(items: List<ListItemEntity>): List<Long> = listItemDao.insertItems(items)

    suspend fun updateItem(item: ListItemEntity) = listItemDao.updateItem(item)

    suspend fun updateItems(items: List<ListItemEntity>) = listItemDao.updateItems(items)

    suspend fun deleteItem(item: ListItemEntity) = listItemDao.deleteItem(item)

    suspend fun deleteItems(items: List<ListItemEntity>) = listItemDao.deleteItems(items)

    suspend fun deleteAllItemsInNotebook(notebookId: Long) = listItemDao.deleteAllItemsInNotebook(notebookId)
}
