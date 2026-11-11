package xyz.younglion.flexilist.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.ui.FlexiViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

object NotebookShareManager {

    /**
     * Serializes notebook and its items into a JSON string format (.flexi)
     */
    private val notebookAdapter = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
        .adapter(BackupManager.NotebookBackup::class.java)

    fun createFlexiJson(notebook: NotebookEntity, items: List<ListItemEntity>): String {
        val data = JSONObject(notebookAdapter.toJson(BackupManager.notebookBackup(notebook, items)))
        val itemData = data.getJSONArray("items")
        data.remove("items")
        return JSONObject().apply {
            put("version", 1)
            put("app", "FlexiList")
            put("notebook", data)
            put("items", itemData)
        }.toString(2)
    }

    fun parseFlexiJson(json: String): Pair<NotebookEntity, List<ListItemEntity>> {
        val root = JSONObject(json)
        require(root.optInt("version", 1) == 1) { "Unsupported .flexi version" }
        val data = root.getJSONObject("notebook")
        data.put("items", root.optJSONArray("items") ?: JSONArray())
        val backup = requireNotNull(notebookAdapter.fromJson(data.toString()))
        return BackupManager.notebookEntity(backup) to backup.items.map { BackupManager.itemEntity(it, 0L) }
    }

    /**
     * Writes JSON to cache directory with .flexi extension and shares via system intent
     */
    suspend fun shareNotebook(context: Context, notebook: NotebookEntity, items: List<ListItemEntity>) {
        val shareIntent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val jsonContent = createFlexiJson(notebook, items)
            val cleanName = notebook.name.replace(Regex("[^\\p{L}\\p{N}_ -]"), "_").trim().take(64).ifEmpty { "Notebook" }
            val file = File.createTempFile("flx-$cleanName-", ".flexi", context.cacheDir)
            file.writeText(jsonContent, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, notebook.name)
                // Large notebooks travel through the file grant, never through Binder's text payload.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri(notebook.name, uri)
            }
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            context.startActivity(Intent.createChooser(shareIntent, context.getString(xyz.younglion.flexilist.R.string.share)))
        }
    }

    /**
     * Imports notebook or full backup from an incoming Intent (e.g. ACTION_VIEW or ACTION_SEND) or Uri
     */
    fun importFlexiNotebookFromIntent(
        context: Context,
        intent: Intent,
        viewModel: FlexiViewModel,
        onSuccess: (String, Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModel.viewModelScope.launch {
            try {
                val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val uri: Uri? = when (intent.action) {
                        Intent.ACTION_SEND -> {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(Intent.EXTRA_STREAM)
                            } ?: intent.data
                        }
                        else -> intent.data
                    }
                    if (uri != null) {
                        requireNotNull(context.contentResolver.openInputStream(uri)).use {
                            it.bufferedReader(Charsets.UTF_8).readText()
                        }
                    } else {
                        requireNotNull(intent.getStringExtra(Intent.EXTRA_TEXT))
                    }
                }

                val trimmed = json.trim()
                if (trimmed.contains("\"notebooks\"") && trimmed.contains("\"schemaVersion\"")) {
                    // Full system backup format
                    val success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        viewModel.backupManager.importFromJsonString(trimmed)
                    }
                    if (success) {
                        onSuccess("Tüm Yedek", 0)
                    } else {
                        onError("Yedek dosyası çözümlenemedi.")
                    }
                } else {
                    // Single notebook format (.flexi)
                    val (notebook, items) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        parseFlexiJson(trimmed)
                    }
                    viewModel.importNotebookWithItems(
                        notebook,
                        items,
                        onComplete = { newId ->
                            viewModel.selectNotebook(newId)
                            onSuccess(notebook.name, items.size)
                        },
                        onError = onError
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "İçe aktarım başarısız oldu.")
            }
        }
    }

    /**
     * Parses a .flexi file input stream and imports notebook + items into database
     */
    fun importFlexiNotebook(
        context: Context,
        uri: Uri,
        viewModel: FlexiViewModel,
        onSuccess: (String, Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val dummyIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = uri
        }
        importFlexiNotebookFromIntent(context, dummyIntent, viewModel, onSuccess, onError)
    }
}
