package xyz.younglion.flexilist.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import xyz.younglion.flexilist.ui.theme.NotebookColors
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.backup.NotebookShareManager
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.dialogs.ListReminderDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceHome(
    workspace: WorkspaceId,
    viewModel: FlexiViewModel,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
    onSearch: (SearchFocus) -> Unit
) {
    val state by viewModel.workspace.notebooks.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val stats by viewModel.workspace.stats.collectAsState()
    val counts = remember(stats) { stats.orEmpty().associateBy { it.notebookId } }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var order by rememberSaveable { mutableStateOf("manual") }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reminderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val reminderPermission = rememberReminderPermissionAction()
    val all = (state as? NotebookCollectionState.Ready)?.notebooks.orEmpty()
    val inWorkspace = remember(all, workspace, archived) {
        all.filter { NotebookKind.parse(it.type).workspace == workspace && it.archived == archived }
    }
    val categories = remember(inWorkspace) { inWorkspace.map { it.category }.filter(String::isNotBlank).distinct().sorted() }
    val filtered = remember(inWorkspace, query, category, favoritesOnly, order) {
        inWorkspace.filter { notebook ->
            (!favoritesOnly || notebook.favorite) && (category == null || category == notebook.category) &&
                (query.isBlank() || notebook.name.contains(query, true) || (!notebook.description.startsWith("[WORKOUT_CFG]") && notebook.description.contains(query, true)))
        }.sortedWith(when (order) {
            "name" -> compareByDescending<NotebookEntity> { it.pinned }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            "newest" -> compareByDescending<NotebookEntity> { it.pinned }.thenByDescending { it.createdAt }
            else -> compareByDescending<NotebookEntity> { it.pinned }.thenBy { it.sortOrder }.thenByDescending { it.id }
        })
    }
    val listState = rememberLazyListState()
    suspend fun update(id: Long, flag: WorkspaceController.NotebookFlag) {
        try { viewModel.workspace.toggleNotebookFlag(id, flag) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { viewModel.workspace.notices.emit(R.string.ux_save_failed) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = (72 * LocalDensity.current.fontScale.coerceIn(1f, 2f)).dp,
                title = { Column {
                    Text("FlexiList", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(if (archived) R.string.ux_archived else workspace.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } },
                actions = {
                    IconButton(onClick = { onSearch(SearchFocus.ALL) }, modifier = Modifier.testTag("global_search_button")) { Icon(Icons.Default.Search, stringResource(R.string.v11_search)) }
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("general_settings")) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.ux_more_actions)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            listOf("manual" to R.string.v11_order, "name" to R.string.sort_az, "newest" to R.string.v11_newest).forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, leadingIcon = { if (order == value) Icon(Icons.Default.Check, null) }, onClick = { order = value; menu = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(stringResource(if (archived) R.string.ux_show_active else R.string.ux_archived)) }, onClick = { archived = !archived; category = null; menu = false })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state is NotebookCollectionState.Ready && inWorkspace.isNotEmpty()) {
                if (LocalDensity.current.fontScale > 1.3f) FloatingActionButton(onClick = onCreate, modifier = Modifier.testTag("add_notebook_button")) { Icon(Icons.Default.Add, stringResource(R.string.ux_new_notebook)) }
                else ExtendedFloatingActionButton(onClick = onCreate, modifier = Modifier.testTag("add_notebook_button"), icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.ux_new_notebook)) })
            }
        }
    ) { padding ->
        when (state) {
            NotebookCollectionState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            NotebookCollectionState.Error -> Box(Modifier.padding(padding)) { MessageState(R.string.ux_load_failed, R.string.ux_retry, viewModel.workspace::retryNotebooks) }
            is NotebookCollectionState.Ready -> {
                if (inWorkspace.isEmpty() && query.isBlank() && !favoritesOnly && category == null) {
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        MessageState(if (archived) R.string.ux_archive_empty else R.string.ux_no_notebooks,
                            if (archived) R.string.ux_show_active else R.string.ux_new_notebook,
                            { if (archived) archived = false else onCreate() }, if (archived) null else workspace.hint)
                    }
                } else LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).testTag("workspace_notebooks"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        val activeEntries = inWorkspace.sumOf { book -> counts[book.id]?.let { it.total - it.done } ?: 0 }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (stats != null) Text(stringResource(R.string.v11_active_summary, inWorkspace.size, activeEntries), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (workspace == WorkspaceId.LISTS || workspace == WorkspaceId.HABITS) TextButton(onClick = { onSearch(SearchFocus.TODAY) }) {
                                Icon(Icons.Default.Today, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.v11_today))
                            }
                        }
                    }
                    item {
                        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.ux_search_area)) }, singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, stringResource(R.string.ux_clear_filters)) } },
                            modifier = Modifier.fillMaxWidth().testTag("notebook_search_input"), shape = MaterialTheme.shapes.medium)
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (inWorkspace.any { it.favorite } || favoritesOnly) item { FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text(stringResource(R.string.v11_favorites_only)) }, leadingIcon = { Icon(Icons.Default.StarBorder, null, Modifier.size(18.dp)) }) }
                            if (categories.isNotEmpty() || category != null) {
                                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.ux_all)) }) }
                                items(categories) { value -> FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value) }) }
                            }
                        }
                    }
                    if (filtered.isEmpty()) item {
                        Text(stringResource(R.string.ux_no_results), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { query = ""; category = null; favoritesOnly = false }) { Text(stringResource(R.string.ux_clear_filters)) }
                    }
                    items(filtered, key = { it.id }) { notebook ->
                        var actions by remember { mutableStateOf(false) }
                        val tally = counts[notebook.id]
                        OutlinedCard(onClick = { onOpen(notebook.id) }, modifier = Modifier.fillMaxWidth().testTag("notebook-${notebook.id}"),
                            shape = MaterialTheme.shapes.large, colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(NotebookColors.getOrElse(notebook.color) { NotebookColors[0] }.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                                        Text(notebook.emoji, fontSize = 26.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(notebook.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(stringResource(NotebookKind.parse(notebook.type).title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Box {
                                        IconButton(onClick = { actions = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.ux_more_actions)) }
                                        DropdownMenu(expanded = actions, onDismissRequest = { actions = false }) {
                                            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { actions = false; onEdit(notebook.id) })
                                            DropdownMenuItem(text = { Text(stringResource(if (notebook.pinned) R.string.ux_unpin else R.string.ux_pin)) }, onClick = { actions = false; scope.launch { update(notebook.id, WorkspaceController.NotebookFlag.PINNED) } })
                                            DropdownMenuItem(text = { Text(stringResource(if (notebook.favorite) R.string.ux_unfavorite else R.string.ux_favorite)) }, onClick = { actions = false; scope.launch { update(notebook.id, WorkspaceController.NotebookFlag.FAVORITE) } })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.reminder_time)) }, onClick = { actions = false; reminderPermission { reminderId = notebook.id } })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = {
                                                actions = false
                                                scope.launch {
                                                    try { NotebookShareManager.shareNotebook(context, notebook, viewModel.getItemsForNotebook(notebook.id).first()) }
                                                    catch (e: CancellationException) { throw e }
                                                    catch (_: Exception) { viewModel.workspace.notices.emit(R.string.export_failed) }
                                                }
                                            })
                                            DropdownMenuItem(text = { Text(stringResource(if (notebook.archived) R.string.ux_unarchive else R.string.ux_archive)) }, onClick = { actions = false; scope.launch { update(notebook.id, WorkspaceController.NotebookFlag.ARCHIVED) } })
                                            HorizontalDivider()
                                            DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, onClick = { actions = false; deletingId = notebook.id })
                                        }
                                    }
                                }
                                if (tally != null && tally.total > 0) {
                                    Text(stringResource(if (NotebookKind.parse(notebook.type) == NotebookKind.READING) R.string.ux_read_summary else R.string.v11_progress, tally.done, tally.total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    LinearProgressIndicator(progress = { tally.done.toFloat() / tally.total }, modifier = Modifier.fillMaxWidth().height(4.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                                } else Text(if (stats == null) stringResource(R.string.ux_working) else pluralStringResource(R.plurals.ux_items_count, 0, 0), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (notebook.category.isNotBlank() || notebook.favorite || notebook.pinned) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (notebook.pinned) Icon(Icons.Default.PushPin, stringResource(R.string.ux_pin), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    if (notebook.favorite) Icon(Icons.Default.Star, stringResource(R.string.ux_favorite), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    if (notebook.category.isNotBlank()) Text(notebook.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    all.firstOrNull { it.id == reminderId }?.let { ListReminderDialog(it, viewModel, onDismiss = { reminderId = null }) }
    all.firstOrNull { it.id == deletingId }?.let { notebook ->
        val entries by remember(notebook.id) { viewModel.getItemsForNotebook(notebook.id) }.collectAsState(initial = null)
        AlertDialog(
            onDismissRequest = { if (!deleting) deletingId = null },
            title = { Text(stringResource(R.string.ux_delete_notebook_title)) },
            text = { Column { if (entries == null) CircularProgressIndicator() else Text(stringResource(R.string.ux_delete_notebook_message, notebook.name, entries?.size ?: 0)); if (error) Text(stringResource(R.string.ux_save_failed), color = MaterialTheme.colorScheme.error) } },
            confirmButton = { TextButton(enabled = !deleting && entries != null, onClick = {
                deleting = true
                scope.launch {
                    try {
                        viewModel.deleteNotebookConfirmed(notebook)
                        deletingId = null; error = false
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = true }
                    finally { deleting = false }
                }
            }) { Text(stringResource(if (deleting) R.string.ux_working else R.string.delete)) } },
            dismissButton = { TextButton(enabled = !deleting, onClick = { deletingId = null; error = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
fun MessageState(message: Int, action: Int, onAction: () -> Unit, hint: Int? = null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(message), style = MaterialTheme.typography.titleMedium)
        hint?.let { Spacer(Modifier.height(8.dp)); Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction, modifier = if (action == R.string.ux_new_notebook) Modifier.testTag("add_notebook_button") else Modifier) { Text(stringResource(action)) }
    }
}
