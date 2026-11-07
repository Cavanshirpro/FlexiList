package xyz.younglion.flexilist.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import xyz.younglion.flexilist.data.backup.BackupManager
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.data.notifications.ReminderScheduler
import xyz.younglion.flexilist.data.repository.FlexiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar

class FlexiViewModel(
    application: Application,
    private val repository: FlexiRepository,
    val backupManager: BackupManager
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("flexilist_prefs", Context.MODE_PRIVATE)

    // Language state
    private val _appLanguage = MutableStateFlow(prefs.getString("app_language", "en") ?: "en")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    init {
        updateLocale(_appLanguage.value)
    }

    fun setAppLanguage(lang: String) {
        _appLanguage.value = lang
        prefs.edit().putString("app_language", lang).apply()
        updateLocale(lang)
    }

    private fun updateLocale(lang: String) {
        try {
            val locale = java.util.Locale.forLanguageTag(lang)
            java.util.Locale.setDefault(locale)
            val resources = getApplication<Application>().resources
            val config = resources.configuration
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Theme state
    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("is_dark_mode", newValue).apply()
    }

    private val _selectedAccentIndex = MutableStateFlow(prefs.getInt("accent_color_index", 0))
    val selectedAccentIndex: StateFlow<Int> = _selectedAccentIndex.asStateFlow()

    fun setAccentIndex(index: Int) {
        _selectedAccentIndex.value = index
        prefs.edit().putInt("accent_color_index", index).apply()
    }

    // Notebook Reminder Management
    fun scheduleNotebookReminder(notebookId: Long, hour: Int, minute: Int, title: String, desc: String) {
        prefs.edit()
            .putBoolean("notebook_reminder_enabled_$notebookId", true)
            .putInt("notebook_reminder_hour_$notebookId", hour)
            .putInt("notebook_reminder_min_$notebookId", minute)
            .apply()

        ReminderScheduler.scheduleNotebookReminder(
            context = getApplication(),
            notebookId = notebookId,
            title = title,
            desc = desc,
            hour = hour,
            minute = minute
        )
    }

    fun cancelNotebookReminder(notebookId: Long) {
        prefs.edit()
            .putBoolean("notebook_reminder_enabled_$notebookId", false)
            .remove("notebook_reminder_hour_$notebookId")
            .remove("notebook_reminder_min_$notebookId")
            .apply()

        ReminderScheduler.cancelNotebookReminder(getApplication(), notebookId)
    }

    fun getNotebookReminderTime(notebookId: Long): Pair<Int, Int>? {
        val enabled = prefs.getBoolean("notebook_reminder_enabled_$notebookId", false)
        if (!enabled) return null
        val h = prefs.getInt("notebook_reminder_hour_$notebookId", -1)
        val m = prefs.getInt("notebook_reminder_min_$notebookId", -1)
        return if (h >= 0 && m >= 0) Pair(h, m) else null
    }

    // All Notebooks Flow
    val notebooks: StateFlow<List<NotebookEntity>> = repository.allNotebooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Notebook State
    private val _selectedNotebookId = MutableStateFlow<Long?>(null)
    val selectedNotebookId: StateFlow<Long?> = _selectedNotebookId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedNotebook: StateFlow<NotebookEntity?> = _selectedNotebookId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.getNotebookById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Workout history log flow for the selected notebook
    @OptIn(ExperimentalCoroutinesApi::class)
    val workoutLogs: StateFlow<List<xyz.younglion.flexilist.data.model.WorkoutLogEntity>> = _selectedNotebookId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getLogsForNotebook(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Water intake tracking state
    private val _todayDate = MutableStateFlow(getTodayDateString())
    val todayDate: StateFlow<String> = _todayDate.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayWaterIntake: StateFlow<xyz.younglion.flexilist.data.model.WaterIntakeEntity?> = _todayDate
        .flatMapLatest { date ->
            repository.getWaterIntakeForDate(date)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun getTodayDateString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
        return sdf.format(java.util.Date())
    }

    fun selectNotebook(id: Long?) {
        _selectedNotebookId.value = id
        // Reset local filters on notebook change
        clearFilters()
    }

    // Filter & Search states
    val searchQuery = MutableStateFlow("")
    val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedPriorities = MutableStateFlow<Set<Int>>(emptySet())
    val selectedCategory = MutableStateFlow<String?>(null)
    val filterStarred = MutableStateFlow(false)
    val filterCompleted = MutableStateFlow<Boolean?>(null) // null = use notebook setting, true = completed, false = active
    val sortType = MutableStateFlow("Manuel") // Manuel, A-Z, Z-A, Öncelik, Tarih, Yıldızlı

    // Bulk selection state
    val selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())

    fun clearFilters() {
        searchQuery.value = ""
        selectedTags.value = emptySet()
        selectedPriorities.value = emptySet()
        selectedCategory.value = null
        filterStarred.value = false
        filterCompleted.value = null
        sortType.value = "Manuel"
        selectedItemIds.value = emptySet()
    }

    // ListItems flow for selected notebook with combining search, filters, sorting and notebook settings
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<ListItemEntity>> = _selectedNotebookId
        .flatMapLatest { id ->
            if (id == null) {
                // If no notebook is selected, show global items for cross-notebook search
                repository.getAllItemsGlobal()
            } else {
                repository.getItemsForNotebook(id)
            }
        }
        .combine(searchQuery) { list, query ->
            if (query.isBlank()) list else {
                list.filter { item ->
                    item.title.contains(query, ignoreCase = true) ||
                    item.description.contains(query, ignoreCase = true) ||
                    item.tags.contains(query, ignoreCase = true) ||
                    item.subtasksJson.contains(query, ignoreCase = true)
                }
            }
        }
        .combine(selectedTags) { list, tags ->
            if (tags.isEmpty()) list else {
                list.filter { item ->
                    val itemTags = item.tags.split(",").map { it.trim() }.toSet()
                    tags.any { it in itemTags }
                }
            }
        }
        .combine(selectedPriorities) { list, priorities ->
            if (priorities.isEmpty()) list else {
                list.filter { it.priority in priorities }
            }
        }
        .combine(selectedCategory) { list, categoryFilter ->
            if (categoryFilter == null) list else {
                list.filter { it.category == categoryFilter }
            }
        }
        .combine(filterStarred) { list, starred ->
            if (!starred) list else list.filter { it.starred }
        }
        .combine(combine(filterCompleted, selectedNotebook) { completed, notebook -> completed to notebook }) { list, (completed, notebook) ->
            val showComp = notebook?.showCompleted ?: true
            if (completed != null) {
                list.filter { it.completed == completed }
            } else if (!showComp) {
                list.filter { !it.completed }
            } else {
                list
            }
        }
        .combine(sortType) { list, sort ->
            when (sort) {
                "A-Z" -> list.sortedBy { it.title.lowercase() }
                "Z-A" -> list.sortedByDescending { it.title.lowercase() }
                "Öncelik" -> list.sortedByDescending { item -> item.priority }
                "Tarih" -> list.sortedBy { it.dueDate ?: Long.MAX_VALUE }
                "Yıldızlı" -> list.sortedByDescending { it.starred }
                else -> list.sortedBy { it.sortOrder }
            }
        }
        .combine(selectedNotebook) { list, notebook ->
            // Move completed tasks to the bottom if specified in settings
            val shouldMove = notebook?.moveCompletedToBottom ?: true
            if (shouldMove) {
                val active = list.filter { !it.completed }
                val done = list.filter { it.completed }
                active + done
            } else {
                list
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All distinct categories and tags for quick filter listings
    val availableTags: StateFlow<List<String>> = items
        .map { list ->
            list.flatMap { it.tags.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableCategories: StateFlow<List<String>> = items
        .map { list ->
            list.map { it.category.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notebook Database Operations
    fun createNotebook(name: String, desc: String, emoji: String, color: Int, type: String, priorityConfigJson: String = "") {
        viewModelScope.launch {
            repository.insertNotebook(
                NotebookEntity(
                    name = name,
                    description = desc,
                    emoji = emoji,
                    color = color,
                    type = type,
                    priorityConfigJson = priorityConfigJson
                )
            )
        }
    }

    fun updateNotebook(notebook: NotebookEntity) {
        viewModelScope.launch {
            repository.updateNotebook(notebook)
        }
    }

    fun deleteNotebook(notebook: NotebookEntity) {
        viewModelScope.launch {
            if (_selectedNotebookId.value == notebook.id) {
                _selectedNotebookId.value = null
            }
            repository.getItemsForNotebook(notebook.id).first().forEach {
                ReminderScheduler.cancel(getApplication(), it.id)
            }
            cancelNotebookReminder(notebook.id)
            resetWorkoutStepIndex(notebook.id)
            repository.deleteNotebook(notebook)
        }
    }

    fun importNotebookWithItems(
        notebook: NotebookEntity,
        itemsList: List<ListItemEntity>,
        onComplete: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val createdId = repository.withTransaction {
                    val id = repository.insertNotebook(notebook.copy(id = 0L))
                    repository.insertItems(itemsList.map { it.copy(id = 0L, notebookId = id) })
                    id
                }
                onComplete(createdId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "Import failed")
            }
        }
    }

    fun getItemsForNotebook(notebookId: Long): Flow<List<ListItemEntity>> = repository.getItemsForNotebook(notebookId)

    // ListItem Database Operations
    fun getItemById(id: Long): Flow<ListItemEntity?> = repository.getItemById(id)
    fun createListItem(
        title: String,
        description: String = "",
        priority: Int = 0,
        tags: String = "",
        category: String = "",
        color: Int = 0,
        emoji: String = "",
        dueDate: Long? = null,
        reminderTime: Long? = null,
        isRecurring: Boolean = false,
        recurrenceRule: String? = null,
        starred: Boolean = false,
        subtasks: List<ListItemEntity.Subtask> = emptyList(),
        customFields: List<ListItemEntity.CustomField> = emptyList()
    ) {
        val notebookId = _selectedNotebookId.value ?: return
        viewModelScope.launch {
            val item = ListItemEntity(
                notebookId = notebookId,
                title = title,
                description = description,
                priority = priority,
                tags = tags,
                category = category,
                color = color,
                emoji = emoji,
                dueDate = dueDate,
                reminderTime = reminderTime,
                isRecurring = isRecurring,
                recurrenceRule = recurrenceRule,
                starred = starred,
                subtasksJson = ListItemEntity.serializeSubtasks(subtasks),
                customFieldsJson = ListItemEntity.serializeCustomFields(customFields)
            )
            val newId = repository.insertItem(item)

            // Setup local notification alarm if reminder is active
            if (reminderTime != null && reminderTime > System.currentTimeMillis()) {
                ReminderScheduler.schedule(
                    context = getApplication(),
                    itemId = newId,
                    title = "Görev Hatırlatıcı: $title",
                    desc = description.ifBlank { "Liste görevinizin vakti geldi!" },
                    triggerAtMillis = reminderTime
                )
            }
        }
    }

    fun updateListItem(item: ListItemEntity) {
        viewModelScope.launch {
            repository.updateItem(item)

            // Update local notification alarm
            if (item.reminderTime != null && item.reminderTime > System.currentTimeMillis()) {
                ReminderScheduler.schedule(
                    context = getApplication(),
                    itemId = item.id,
                    title = "Görev Hatırlatıcı: ${item.title}",
                    desc = item.description.ifBlank { "Liste görevinizin vakti geldi!" },
                    triggerAtMillis = item.reminderTime
                )
            } else {
                ReminderScheduler.cancel(getApplication(), item.id)
            }
        }
    }

    fun updateItemsOrder(orderedItems: List<ListItemEntity>) {
        viewModelScope.launch {
            val updated = orderedItems.mapIndexed { index, item ->
                item.copy(sortOrder = index, updatedAt = System.currentTimeMillis())
            }
            repository.updateItems(updated)
        }
    }

    fun deleteListItem(item: ListItemEntity) {
        viewModelScope.launch {
            ReminderScheduler.cancel(getApplication(), item.id)
            repository.deleteItem(item)
        }
    }

    // Toggle complete list item (with elegant automatic recurrence trigger)
    fun toggleItemComplete(item: ListItemEntity) {
        viewModelScope.launch {
            if (item.completed) {
                // Mark active again
                val updated = item.copy(
                    completed = false,
                    completedAt = null,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateItem(updated)
            } else {
                // Completing the task
                if (item.isRecurring && !item.recurrenceRule.isNullOrBlank()) {
                    // Automatically calculate next occurrence to keep the list item active
                    val nextDue = calculateNextRecurrence(item.dueDate ?: System.currentTimeMillis(), item.recurrenceRule)
                    val updated = item.copy(
                        dueDate = nextDue,
                        completed = false, // Keep active
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateItem(updated)

                    // Re-schedule reminder for the next recurrence
                    if (item.reminderTime != null) {
                        val nextReminder = calculateNextRecurrence(item.reminderTime, item.recurrenceRule)
                        val updatedWithReminder = updated.copy(reminderTime = nextReminder)
                        repository.updateItem(updatedWithReminder)

                        if (nextReminder > System.currentTimeMillis()) {
                            ReminderScheduler.schedule(
                                context = getApplication(),
                                itemId = item.id,
                                title = "Görev Hatırlatıcı: ${item.title}",
                                desc = item.description.ifBlank { "Sıradaki tekrarlayan görevinizin vakti geldi!" },
                                triggerAtMillis = nextReminder
                            )
                        }
                    }
                } else {
                    // Normal complete
                    val updated = item.copy(
                        completed = true,
                        completedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateItem(updated)
                    ReminderScheduler.cancel(getApplication(), item.id)
                }
            }
        }
    }

    private fun calculateNextRecurrence(currentMillis: Long, rule: String): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentMillis
        }
        when (rule) {
            "DAILY" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKDAYS" -> {
                do {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                } while (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            }
            "WEEKLY" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            "MONTHLY" -> calendar.add(Calendar.MONTH, 1)
            else -> calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    // Bulk/Batch Selection Operations
    fun toggleItemSelection(id: Long) {
        val current = selectedItemIds.value
        selectedItemIds.value = if (current.contains(id)) {
            current - id
        } else {
            current + id
        }
    }

    fun clearSelections() {
        selectedItemIds.value = emptySet()
    }

    fun bulkComplete(completed: Boolean) {
        viewModelScope.launch {
            val ids = selectedItemIds.value
            val list = items.value.filter { it.id in ids }
            val updated = list.map {
                it.copy(
                    completed = completed,
                    completedAt = if (completed) System.currentTimeMillis() else null,
                    updatedAt = System.currentTimeMillis()
                )
            }
            repository.updateItems(updated)
            clearSelections()
        }
    }

    fun bulkDelete() {
        viewModelScope.launch {
            val ids = selectedItemIds.value
            val list = items.value.filter { it.id in ids }
            list.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
            repository.deleteItems(list)
            clearSelections()
        }
    }

    fun bulkChangePriority(priority: Int) {
        viewModelScope.launch {
            val ids = selectedItemIds.value
            val list = items.value.filter { it.id in ids }
            val updated = list.map {
                it.copy(
                    priority = priority,
                    updatedAt = System.currentTimeMillis()
                )
            }
            repository.updateItems(updated)
            clearSelections()
        }
    }

    fun bulkMove(targetNotebookId: Long) {
        viewModelScope.launch {
            val ids = selectedItemIds.value
            val list = items.value.filter { it.id in ids }
            val updated = list.map {
                it.copy(
                    notebookId = targetNotebookId,
                    updatedAt = System.currentTimeMillis()
                )
            }
            repository.updateItems(updated)
            clearSelections()
        }
    }

    // Workout & Water helper methods
    fun saveWorkoutSession(
        log: xyz.younglion.flexilist.data.model.WorkoutLogEntity,
        completedExerciseIds: List<Long>,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.withTransaction {
                    repository.insertWorkoutLog(log)
                    val items = repository.getItemsByIdsSuspend(completedExerciseIds)
                    val updatedItems = items.filter { it.notebookId == log.notebookId }.map { item ->
                        item.copy(completed = true, completedAt = log.endTime, updatedAt = log.endTime)
                    }
                    if (updatedItems.isNotEmpty()) {
                        repository.updateItems(updatedItems)
                    }
                }
                resetWorkoutStepIndex(log.notebookId)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError()
            }
        }
    }

    // Workout step index persistence with SharedPreferences for reboot resilience
    private val workoutPrefs = application.getSharedPreferences("flexilist_workout_prefs", Context.MODE_PRIVATE)
    private val _workoutSavedStepIndices = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val workoutSavedStepIndices: StateFlow<Map<Long, Int>> = _workoutSavedStepIndices.asStateFlow()

    fun getWorkoutQueue(notebookId: Long): List<String> =
        workoutPrefs.getString("workout_queue_$notebookId", null)?.split(",").orEmpty()

    fun getCompletedWorkoutSteps(notebookId: Long): Set<Int>? =
        workoutPrefs.getStringSet("workout_completed_$notebookId", null)?.mapNotNull { it.toIntOrNull() }?.toSet()

    fun getWorkoutStartTime(notebookId: Long): Long =
        workoutPrefs.getLong("workout_start_time_$notebookId", System.currentTimeMillis())

    fun saveWorkoutProgress(notebookId: Long, queue: List<String>, completed: Set<Int>, startTime: Long) {
        if (notebookId <= 0) return
        workoutPrefs.edit()
            .putString("workout_queue_$notebookId", queue.joinToString(","))
            .putStringSet("workout_completed_$notebookId", completed.map { it.toString() }.toSet())
            .putLong("workout_start_time_$notebookId", startTime)
            .apply()
    }

    fun isWorkoutStarted(notebookId: Long): Boolean {
        if (notebookId <= 0) return false
        return workoutPrefs.getBoolean("workout_started_$notebookId", false)
    }

    fun getSavedWorkoutStepIndex(notebookId: Long): Int {
        if (notebookId <= 0) return 0
        return workoutPrefs.getInt("workout_step_$notebookId", 0)
    }

    fun startWorkout(notebookId: Long) {
        if (notebookId > 0) {
            workoutPrefs.edit().putBoolean("workout_started_$notebookId", true).apply()
        }
    }

    fun saveWorkoutStepIndex(notebookId: Long, index: Int) {
        if (notebookId > 0) {
            workoutPrefs.edit()
                .putBoolean("workout_started_$notebookId", true)
                .putInt("workout_step_$notebookId", index)
                .apply()
            _workoutSavedStepIndices.value = _workoutSavedStepIndices.value + (notebookId to index)
        }
    }

    fun resetWorkoutStepIndex(notebookId: Long) {
        if (notebookId > 0) {
            workoutPrefs.edit()
                .remove("workout_started_$notebookId")
                .remove("workout_step_$notebookId")
                .remove("workout_queue_$notebookId")
                .remove("workout_completed_$notebookId")
                .remove("workout_start_time_$notebookId")
                .apply()
            _workoutSavedStepIndices.value = _workoutSavedStepIndices.value - notebookId
        }
    }

    // Reset from the complete database, independently of the selected notebook or UI filters.
    init {
        viewModelScope.launch {
            while (true) {
                _todayDate.value = getTodayDateString()
                val midnight = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                repository.resetDailyCompletions(midnight)
                delay(30_000)
            }
        }
    }

    fun markExercisesAsCompleted(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val items = repository.getItemsByIdsSuspend(ids)
            val updated = items.map {
                it.copy(completed = true, completedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
            }
            if (updated.isNotEmpty()) {
                repository.updateItems(updated)
            }
        }
    }

    fun resetAllExercisesInNotebook() {
        val notebookId = _selectedNotebookId.value ?: return
        viewModelScope.launch {
            val itemsList = repository.getItemsForNotebookSuspend(notebookId)
            val updated = itemsList.map { it.copy(completed = false, updatedAt = System.currentTimeMillis()) }
            repository.updateItems(updated)
        }
    }

    fun deleteWorkoutLog(id: Long) {
        viewModelScope.launch {
            repository.deleteWorkoutLogById(id)
        }
    }

    fun addWater(amountMl: Int) {
        viewModelScope.launch {
            val currentDate = getTodayDateString()
            _todayDate.value = currentDate
            repository.addWater(currentDate, amountMl)
        }
    }

    fun resetWater() {
        viewModelScope.launch {
            repository.insertWaterIntake(
                xyz.younglion.flexilist.data.model.WaterIntakeEntity(
                    dateString = _todayDate.value,
                    amountMl = 0,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // Export/Import backup trigger wrappers
    fun exportBackupJson(outputStream: OutputStream, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { outputStream.use { backupManager.exportToJson(it) } }
            if (result) onSuccess() else onError()
        }
    }

    fun importBackupJson(inputStream: InputStream, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { inputStream.use { backupManager.importFromJson(it) } }
            if (result) onSuccess() else onError()
        }
    }

    fun exportBackupCsv(outputStream: OutputStream, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { outputStream.use { backupManager.exportToCsv(it) } }
            if (result) onSuccess() else onError()
        }
    }

    private val cloudSyncManager = xyz.younglion.flexilist.data.backup.CloudSyncManager()

    // Upload backup to cloud
    fun uploadBackupToCloud(syncCode: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val jsonString = backupManager.exportToJsonString()
            if (jsonString == null) {
                onError("Veri paketleme hatası oluştu.")
                return@launch
            }
            when (val result = cloudSyncManager.uploadToCloud(syncCode, jsonString)) {
                is xyz.younglion.flexilist.data.backup.CloudSyncResult.Success -> {
                    onSuccess(result.syncCode)
                }
                is xyz.younglion.flexilist.data.backup.CloudSyncResult.Error -> {
                    onError(result.message)
                }
            }
        }
    }

    // Download and restore backup from cloud
    fun downloadBackupFromCloud(syncCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val jsonString = cloudSyncManager.downloadFromCloud(syncCode)
            if (jsonString == null) {
                onError("Veri indirilemedi. Kodun doğru olduğunu veya internete bağlı olduğunuzu kontrol edin.")
                return@launch
            }
            val result = backupManager.importFromJsonString(jsonString)
            if (result) {
                onSuccess()
            } else {
                onError("Yedek verisi çözümlenemedi veya veritabanına yazılamadı.")
            }
        }
    }
}

class FlexiViewModelFactory(
    private val application: Application,
    private val repository: FlexiRepository,
    private val backupManager: BackupManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FlexiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FlexiViewModel(application, repository, backupManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
