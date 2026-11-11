package xyz.younglion.flexilist.ui.workspace

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.backup.BackupManager
import xyz.younglion.flexilist.data.backup.CloudSyncManager
import xyz.younglion.flexilist.data.backup.CloudSyncResult
import xyz.younglion.flexilist.data.backup.NotebookShareManager
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.data.notifications.ReminderScheduler
import xyz.younglion.flexilist.data.repository.FlexiRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

sealed interface NotebookCollectionState {
    data object Loading : NotebookCollectionState
    data class Ready(val notebooks: List<NotebookEntity>) : NotebookCollectionState
    data object Error : NotebookCollectionState
}

class WorkspaceController(
    private val application: Application,
    private val repository: FlexiRepository,
    private val backups: BackupManager,
    private val scope: CoroutineScope,
    private val savedState: SavedStateHandle
) {
    private val preferences = application.getSharedPreferences("flexilist_prefs", Context.MODE_PRIVATE)
    val themeMode = MutableStateFlow(ThemeMode.read(preferences))
    val notices = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val deletion = MutableStateFlow<UndoDeletion?>(null)
    private val deletionMutex = Mutex()
    private var deletionToken = 0L
    private data class SaveWork(val payload: Any, val result: Deferred<Long>)
    private val saves = mutableMapOf<String, SaveWork>()
    private fun notebookSaveKey(notebook: NotebookEntity) = if (notebook.id > 0) "notebook-${notebook.id}" else "notebook-new-${NotebookKind.parse(notebook.type).workspace.name}"
    private fun entrySaveKey(item: ListItemEntity) = "entry-${item.notebookId}-${item.id}"
    private suspend fun saveOnce(key: String, payload: Any, commit: suspend () -> Long): Long {
        val result = synchronized(saves) {
            val existing = saves[key]
            if (existing != null && existing.payload == payload && !existing.result.isCancelled) existing.result
            else scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { commit() }.also { saves[key] = SaveWork(payload, it) }
        }
        return result.await()
    }
    fun acknowledgeEntrySave(item: ListItemEntity) { synchronized(saves) { saves.remove(entrySaveKey(item)) } }
    fun acknowledgeNotebookSave(notebook: NotebookEntity) {
        val key = notebookSaveKey(notebook)
        synchronized(saves) { saves.remove(key) }
        savedState.remove<String>("pending-$key")
    }
    fun pendingNotebookSave(notebook: NotebookEntity): NotebookEntity? = savedState.get<String>("pending-${notebookSaveKey(notebook)}")?.let(NotebookDraft::decode)?.notebook

    val importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val cloudBusy = MutableStateFlow(false)
    val cloudCode = MutableStateFlow(preferences.getString("last_backup_code", "").orEmpty())
    private var prepared: String? = null
    private var lastReader: (suspend () -> String)? = null
    val canRetryImport: Boolean get() = savedState.contains("pending-import") || lastReader != null
    private var importJob: Job? = null

    private val collectionRetry = MutableStateFlow(0)
    fun retryNotebooks() { collectionRetry.value++ }
    @OptIn(ExperimentalCoroutinesApi::class)
    val notebooks: StateFlow<NotebookCollectionState> = collectionRetry.flatMapLatest { repository.allNotebooks
        .map<List<NotebookEntity>, NotebookCollectionState> { NotebookCollectionState.Ready(it) }
        .onStart { emit(NotebookCollectionState.Loading) }
        .catch { emit(NotebookCollectionState.Error) }
    }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), NotebookCollectionState.Loading)


    init {
        savedState.get<String>("pending-import")?.let { path ->
            val file = File(application.cacheDir, path)
            importJob = scope.launch {
                importState.value = ImportUiState.Preparing
                try { review(withContext(Dispatchers.IO) { file.inputStream().use(::readImport) }, stage = false) }
                catch (e: CancellationException) { throw e }
                catch (_: ImportTooLargeException) { importState.value = ImportUiState.Failed(R.string.ux_import_too_large) }
                catch (_: Exception) { importState.value = ImportUiState.Failed(R.string.ux_import_failed) }
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        preferences.edit().putString("theme_mode", mode.name).apply()
        themeMode.value = mode
    }

    fun migrateWorkspace(notebooks: List<NotebookEntity>): WorkspaceId? {
        if (preferences.contains("workspace")) return null
        val id = preferences.getLong("selected_notebook_id", 0)
        val previous = notebooks.firstOrNull { it.id == id }
        val workspace = previous?.let { NotebookKind.parse(it.type).workspace } ?: WorkspaceId.LISTS
        rememberWorkspace(workspace)
        previous?.let { rememberNotebook(workspace, it.id) }
        return workspace
    }

    fun lastWorkspace(): WorkspaceId = WorkspaceId.fromSaved(preferences.getString("workspace", null))
    fun rememberWorkspace(workspace: WorkspaceId) { preferences.edit().putString("workspace", workspace.name).apply() }
    fun lastNotebook(workspace: WorkspaceId): Long? = preferences.getLong("workspace-${workspace.name}", 0).takeIf { it > 0 }
    fun rememberNotebook(workspace: WorkspaceId, id: Long?) {
        preferences.edit().putLong("workspace-${workspace.name}", id ?: 0).apply()
    }

    fun notebook(id: Long): Flow<NotebookUiState> = combine(repository.getNotebookById(id), repository.getItemsForNotebook(id)) { notebook, items ->
        if (notebook == null) NotebookUiState.Missing else NotebookUiState.Ready(notebook, items)
    }.onStart { emit(NotebookUiState.Loading) }.catch { emit(NotebookUiState.Error) }

    fun draft(notebookId: Long, itemId: Long): EntryDraft? = savedState.get<String>(EntryDraft.key(notebookId, itemId))?.let(EntryDraft::decode)
    fun storeDraft(item: ListItemEntity) { savedState[EntryDraft.key(item.notebookId, item.id)] = EntryDraft(item).encode() }
    fun clearDraft(notebookId: Long, itemId: Long) { savedState.remove<String>(EntryDraft.key(notebookId, itemId)) }

    suspend fun saveNotebook(notebook: NotebookEntity): Long {
        require(notebook.name.isNotBlank())
        val key = notebookSaveKey(notebook)
        savedState["pending-$key"] = NotebookDraft(notebook).encode()
        return saveOnce(key, notebook.copy(createdAt = 0, updatedAt = 0)) {
            repository.withTransaction {
                if (notebook.id == 0L) repository.insertNotebook(notebook.copy(name = notebook.name.trim()))
                else {
                    val original = checkNotNull(repository.getNotebookByIdSuspend(notebook.id))
                    repository.updateNotebook(original.copy(
                        name = notebook.name.trim(), description = notebook.description, category = notebook.category,
                        emoji = notebook.emoji, color = notebook.color, type = notebook.type,
                        priorityConfigJson = notebook.priorityConfigJson, updatedAt = System.currentTimeMillis()
                    ))
                    notebook.id
                }
            }
        }
    }

    enum class NotebookFlag { PINNED, FAVORITE, ARCHIVED }
    suspend fun toggleNotebookFlag(id: Long, flag: NotebookFlag) = repository.withTransaction {
        val current = checkNotNull(repository.getNotebookByIdSuspend(id))
        repository.updateNotebook(when (flag) {
            NotebookFlag.PINNED -> current.copy(pinned = !current.pinned)
            NotebookFlag.FAVORITE -> current.copy(favorite = !current.favorite)
            NotebookFlag.ARCHIVED -> current.copy(archived = !current.archived)
        }.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveEntry(item: ListItemEntity): Long {
        require(item.title.isNotBlank())
        return saveOnce(entrySaveKey(item), item.copy(createdAt = 0, updatedAt = 0)) {
            val id = repository.withTransaction {
                check(repository.getNotebookByIdSuspend(item.notebookId) != null)
                if (item.id == 0L) repository.insertItem(item.copy(title = item.title.trim()))
                else {
                    val original = repository.getItemByIdSuspend(item.id)
                    check(original?.notebookId == item.notebookId)
                    repository.updateItem(item.copy(
                        title = item.title.trim(), createdAt = original!!.createdAt, sortOrder = original.sortOrder,
                        completed = original.completed, completedAt = original.completedAt,
                        color = original.color, emoji = original.emoji, updatedAt = System.currentTimeMillis()
                    ))
                    item.id
                }
            }
            synchronizeReminder(item.copy(id = id))
            id
        }
    }

    private fun synchronizeReminder(item: ListItemEntity) {
        // A denied alarm/notification must not turn a committed save into a failed save (or duplicate).
        runCatching {
            val time = item.reminderTime
            if (time != null && time > System.currentTimeMillis()) {
                ReminderScheduler.schedule(application, item.id, item.title, item.description.takeUnless { it.startsWith("[EXERCISE_CFG]") }.orEmpty(), time)
            } else ReminderScheduler.cancel(application, item.id)
        }.onFailure { notices.tryEmit(R.string.ux_permission_needed) }
    }

    suspend fun deleteItems(ids: Set<Long>) = deletionMutex.withLock {
        if (ids.isEmpty()) return@withLock
        val snapshot = repository.withTransaction {
            val items = repository.getItemsByIdsSuspend(ids.toList())
            repository.deleteItems(items)
            items
        }
        snapshot.forEach { runCatching { ReminderScheduler.cancel(application, it.id) } }
        if (snapshot.isNotEmpty()) deletion.value = UndoDeletion(++deletionToken, snapshot)
    }

    suspend fun undoDelete(token: Long): Boolean = deletionMutex.withLock {
        val batch = deletion.value?.takeIf { it.token == token } ?: return@withLock false
        try {
            repository.withTransaction {
                check(batch.items.all { repository.getNotebookByIdSuspend(it.notebookId) != null })
                check(repository.getItemsByIdsSuspend(batch.items.map { it.id }).isEmpty())
                repository.insertItems(batch.items)
            }
            batch.items.forEach(::synchronizeReminder)
            deletion.value = null
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { notices.tryEmit(R.string.ux_undo_failed); false }
    }

    suspend fun expireDeletion(token: Long) = deletionMutex.withLock {
        if (deletion.value?.token == token) deletion.value = null
    }

    suspend fun deleteNotebook(notebook: NotebookEntity) {
        val entries = repository.withTransaction {
            val entries = repository.getItemsForNotebookSuspend(notebook.id)
            repository.deleteNotebook(notebook)
            entries
        }
        preferences.edit().putBoolean("notebook_reminder_enabled_${notebook.id}", false)
            .remove("notebook_reminder_hour_${notebook.id}").remove("notebook_reminder_min_${notebook.id}").apply()
        runCatching { ReminderScheduler.cancelNotebookReminder(application, notebook.id) }
        entries.forEach { runCatching { ReminderScheduler.cancel(application, it.id) } }
    }

    fun prepareImport(uri: Uri) = prepare { application.contentResolver.openInputStream(uri)?.use(::readImport) ?: error("Unreadable import") }
    fun prepareIntent(intent: Intent) = prepare {
        val uri = if (intent.action == Intent.ACTION_SEND) {
            if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } else null
        val source = uri ?: intent.data
        source?.let { application.contentResolver.openInputStream(it)?.use(::readImport) }
            ?: requireNotNull(intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    fun prepareCloud(code: String) = prepare {
        requireNotNull(CloudSyncManager().downloadFromCloud(code.trim()))
    }

    private fun prepare(clearExisting: Boolean = true, read: suspend () -> String) {
        if (importState.value is ImportUiState.Importing) return
        importJob?.cancel()
        prepared = null
        lastReader = read
        if (clearExisting) savedState.remove<String>("pending-import")?.let { runCatching { File(application.cacheDir, it).delete() } }
        importJob = scope.launch {
            importState.value = ImportUiState.Preparing
            try { review(withContext(Dispatchers.IO) { read() }) }
            catch (e: CancellationException) { throw e }
            catch (_: ImportTooLargeException) { importState.value = ImportUiState.Failed(R.string.ux_import_too_large) }
            catch (_: Exception) { importState.value = ImportUiState.Failed(R.string.ux_import_failed) }
        }
    }

    private suspend fun review(text: String, stage: Boolean = true) {
        val preview = withContext(Dispatchers.IO) {
            if (text.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) throw ImportTooLargeException()
            val root = JSONObject(text)
            if (root.has("notebooks")) {
                val backup = backups.decodeBackup(text)
                ImportPreview(backup.notebooks.map { it.name }, backup.notebooks.sumOf { it.items.size }, backup.workoutLogs.orEmpty().size, backup.waterIntakes.orEmpty().size)
            } else {
                val (notebook, items) = NotebookShareManager.parseFlexiJson(text)
                ImportPreview(listOf(notebook.name), items.size, 0, 0)
            }
        }
        if (stage) {
            val file = withContext(Dispatchers.IO) {
                File.createTempFile("import-", ".json", application.cacheDir).also { it.writeText(text, Charsets.UTF_8) }
            }
            savedState.get<String>("pending-import")?.let { runCatching { File(application.cacheDir, it).delete() } }
            savedState["pending-import"] = file.name
        }
        prepared = text
        importState.value = ImportUiState.Ready(preview)
    }

    fun dismissImport() {
        if (importState.value is ImportUiState.Importing) return
        importJob?.cancel()
        finishImport()
    }
    private fun finishImport() {
        savedState.remove<String>("pending-import")?.let { runCatching { File(application.cacheDir, it).delete() } }
        prepared = null
        lastReader = null
        importState.value = ImportUiState.Idle
    }
    fun retryImport() {
        val path = savedState.get<String>("pending-import")
        if (path != null) prepare(clearExisting = false) { File(application.cacheDir, path).inputStream().use(::readImport) }
        else lastReader?.let { prepare(read = it) }
    }
    fun confirmImport() {
        val ready = importState.value as? ImportUiState.Ready ?: return
        val text = prepared ?: return
        importState.value = ImportUiState.Importing(ready.preview)
        importJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (JSONObject(text).has("notebooks")) check(backups.importFromJsonString(text))
                    else {
                        val (notebook, items) = NotebookShareManager.parseFlexiJson(text)
                        repository.withTransaction {
                            val id = repository.insertNotebook(notebook.copy(id = 0))
                            repository.insertItems(items.map { it.copy(id = 0, notebookId = id) })
                        }
                    }
                }
                finishImport()
                notices.emit(R.string.ux_import_success)
            } catch (e: CancellationException) { throw e }
            catch (_: ImportTooLargeException) { importState.value = ImportUiState.Failed(R.string.ux_import_too_large) }
            catch (_: Exception) { importState.value = ImportUiState.Failed(R.string.ux_import_failed) }
        }
    }

    fun uploadCloud(code: String) {
        if (cloudBusy.value) return
        cloudBusy.value = true
        scope.launch {
            try {
                val json = requireNotNull(backups.exportToJsonString())
                when (val result = CloudSyncManager().uploadToCloud(code.trim(), json)) {
                    is CloudSyncResult.Success -> {
                        preferences.edit().putString("last_backup_code", result.syncCode).apply()
                        cloudCode.value = result.syncCode
                        notices.emit(R.string.ux_cloud_saved)
                    }
                    is CloudSyncResult.Error -> notices.emit(R.string.ux_cloud_failed)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { notices.emit(R.string.ux_cloud_failed) }
            finally { cloudBusy.value = false }
        }
    }

    class ImportTooLargeException : IllegalArgumentException()

    companion object {
        // Keep imported data out of Android's saved-state Bundle and bound its memory footprint.
        const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
        fun readImport(input: InputStream): String {
            val buffer = ByteArray(8192)
            val bytes = ByteArrayOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (bytes.size().toLong() + read > MAX_IMPORT_BYTES) throw ImportTooLargeException()
                bytes.write(buffer, 0, read)
            }
            return bytes.toString("UTF-8")
        }
    }
}
