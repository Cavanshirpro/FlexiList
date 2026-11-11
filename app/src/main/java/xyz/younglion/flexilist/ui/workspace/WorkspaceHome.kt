package xyz.younglion.flexilist.ui.workspace

import androidx.compose.foundation.layout.*
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
    onSettings: () -> Unit
) {
    val state by viewModel.workspace.notebooks.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
    val filtered = remember(inWorkspace, query, category) {
        inWorkspace.filter { notebook ->
            (category == null || category == notebook.category) &&
                (query.isBlank() || notebook.name.contains(query, true) || (!notebook.description.startsWith("[WORKOUT_CFG]") && notebook.description.contains(query, true)))
        }.sortedWith(compareByDescending<NotebookEntity> { it.pinned }.thenBy { it.sortOrder }.thenByDescending { it.id })
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
                title = { Text(stringResource(if (archived) R.string.ux_archived else workspace.title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("general_settings")) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.ux_more_actions)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(if (archived) R.string.ux_show_active else R.string.ux_archived)) }, onClick = { archived = !archived; category = null; menu = false })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state is NotebookCollectionState.Ready && inWorkspace.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = onCreate, modifier = Modifier.testTag("add_notebook_button"), icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.ux_new_notebook)) })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (inWorkspace.isNotEmpty() || query.isNotEmpty()) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.ux_search_area)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, stringResource(R.string.ux_clear_filters)) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("notebook_search_input")
                )
            }
            if (categories.isNotEmpty() || category != null) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.ux_all)) })
                    categories.forEach { value -> FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value) }) }
                }
            }
            when (state) {
                NotebookCollectionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                NotebookCollectionState.Error -> MessageState(R.string.ux_load_failed, R.string.ux_retry, viewModel.workspace::retryNotebooks)
                is NotebookCollectionState.Ready -> {
                    if (filtered.isEmpty()) {
                        val noResults = query.isNotBlank() || category != null
                        MessageState(
                            if (noResults) R.string.ux_no_results else if (archived) R.string.ux_archive_empty else R.string.ux_no_notebooks,
                            if (noResults) R.string.ux_clear_filters else if (archived) R.string.ux_show_active else R.string.ux_new_notebook,
                            { if (noResults) { query = ""; category = null } else if (archived) archived = false else onCreate() },
                            if (noResults || archived) null else workspace.hint
                        )
                    } else LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("workspace_notebooks"),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { notebook ->
                            var actions by remember { mutableStateOf(false) }
                            val entries by remember(notebook.id) { viewModel.getItemsForNotebook(notebook.id) }.collectAsState(initial = null)
                            Card(onClick = { onOpen(notebook.id) }, modifier = Modifier.fillMaxWidth().testTag("notebook-${notebook.id}")) {
                                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                                    Text(notebook.emoji, fontSize = 28.sp, modifier = Modifier.padding(top = 4.dp, end = 12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(notebook.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(stringResource(NotebookKind.parse(notebook.type).title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        entries?.let { Text(pluralStringResource(R.plurals.ux_items_count, it.size, it.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        if (notebook.category.isNotBlank()) Text(notebook.category, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (notebook.pinned || notebook.favorite) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (notebook.pinned) Icon(Icons.Default.PushPin, stringResource(R.string.ux_pin), Modifier.size(18.dp))
                                            if (notebook.favorite) Icon(Icons.Default.Star, stringResource(R.string.ux_favorite), Modifier.size(18.dp))
                                        }
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
