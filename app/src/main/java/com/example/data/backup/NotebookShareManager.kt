package com.example.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.ListItemEntity
import com.example.data.model.NotebookEntity
import com.example.ui.FlexiViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

object NotebookShareManager {

    /**
     * Serializes notebook and its items into a JSON string format (.flexi)
     */
    fun createFlexiJson(notebook: NotebookEntity, items: List<ListItemEntity>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "FlexiList")
        
        val nbJson = JSONObject().apply {
            put("name", notebook.name)
            put("description", notebook.description)
            put("emoji", notebook.emoji)
            put("color", notebook.color)
            put("type", notebook.type)
            put("pinned", notebook.pinned)
            put("favorite", notebook.favorite)
            put("priorityConfigJson", notebook.priorityConfigJson)
        }
        root.put("notebook", nbJson)

        val itemsArray = JSONArray()
        items.forEach { item ->
            val itemJson = JSONObject().apply {
                put("title", item.title)
                put("description", item.description)
                put("completed", item.completed)
                put("starred", item.starred)
                put("category", item.category)
                put("tags", item.tags)
                put("dueDate", item.dueDate)
                put("priority", item.priority)
                put("sortOrder", item.sortOrder)
            }
            itemsArray.put(itemJson)
        }
        root.put("items", itemsArray)

        return root.toString(2)
    }

    /**
     * Writes JSON to cache directory with .flexi extension and shares via system intent
     */
    fun shareNotebook(context: Context, notebook: NotebookEntity, items: List<ListItemEntity>) {
        try {
            val jsonContent = createFlexiJson(notebook, items)
            val cleanName = notebook.name.replace(Regex("[^a-zA-Z0-9_ -]"), "_").trim().ifEmpty { "Notebook" }
            val fileName = "$cleanName.flexi"
            val file = File(context.cacheDir, fileName)
            
            FileOutputStream(file).use { out ->
                out.write(jsonContent.toByteArray(Charsets.UTF_8))
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FlexiList Defteri: ${notebook.name}")
                putExtra(Intent.EXTRA_TEXT, "FlexiList '${notebook.name}' defteri seninle paylaşıldı. Dokunarak FlexiList uygulamasında açabilirsin!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Defteri Paylaş (.flexi)"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Defter dosyası oluşturulurken hata: ${e.message}", Toast.LENGTH_LONG).show()
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
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Dosya okunamadı!")
            
            val jsonStr = InputStreamReader(inputStream, Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonStr)

            if (!root.has("notebook")) {
                throw IllegalArgumentException("Geçersiz .flexi dosya formatı!")
            }

            val nbJson = root.getJSONObject("notebook")
            val name = nbJson.optString("name", "İçe Aktarılan Defter")
            val description = nbJson.optString("description", "")
            val emoji = nbJson.optString("emoji", "📋")
            val color = nbJson.optInt("color", 0)
            val type = nbJson.optString("type", "Standard")
            val priorityConfigJson = nbJson.optString("priorityConfigJson", "")

            val newNotebook = NotebookEntity(
                name = "$name (İçe Aktarıldı)",
                description = description,
                emoji = emoji,
                color = color,
                type = type,
                pinned = false,
                favorite = false,
                priorityConfigJson = priorityConfigJson,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val itemsList = mutableListOf<ListItemEntity>()
            if (root.has("items")) {
                val itemsArray = root.getJSONArray("items")
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    itemsList.add(
                        ListItemEntity(
                            notebookId = 0L, // Will be set after notebook creation
                            title = itemObj.optString("title", "Öğe"),
                            description = itemObj.optString("description", ""),
                            completed = itemObj.optBoolean("completed", false),
                            starred = itemObj.optBoolean("starred", false),
                            category = itemObj.optString("category", ""),
                            tags = itemObj.optString("tags", ""),
                            dueDate = if (itemObj.has("dueDate")) itemObj.optLong("dueDate") else null,
                            priority = itemObj.optInt("priority", 0),
                            sortOrder = itemObj.optInt("sortOrder", i)
                        )
                    )
                }
            }

            viewModel.importNotebookWithItems(newNotebook, itemsList) { createdNbId ->
                onSuccess(name, itemsList.size)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e.message ?: "İçe aktarım başarısız oldu.")
        }
    }
}
