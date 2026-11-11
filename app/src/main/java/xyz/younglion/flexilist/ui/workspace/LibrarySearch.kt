package xyz.younglion.flexilist.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity
import java.util.Calendar

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Ready(val notebooks: List<NotebookEntity>, val items: List<ListItemEntity>) : LibraryState
    data object Error : LibraryState
}
enum class SearchFocus(val label: Int) { ALL(R.string.ux_all), TODAY(R.string.v11_today), STARRED(R.string.v11_starred) }
data class EntryMatch(val notebook: NotebookEntity, val item: ListItemEntity)
data class SearchResults(val notebooks: List<NotebookEntity>, val items: List<EntryMatch>)

/** Pure search policy shared by native UI and tests. Stored DSL never becomes searchable user prose. */
fun searchLibrary(library: LibraryState.Ready, query: String, focus: SearchFocus, workspace: WorkspaceId?, archived: Boolean, now: Long): SearchResults {
    val text = query.trim()
    val endToday = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
    val books = library.notebooks.filter { (archived || !it.archived) && (workspace == null || NotebookKind.parse(it.type).workspace == workspace) }
    val parents = books.associateBy { it.id }
    fun matches(vararg values: String) = text.isBlank() || values.any { it.contains(text, ignoreCase = true) }
    val notebookMatches = if (text.isBlank() || focus == SearchFocus.TODAY) emptyList() else books.filter {
        (focus != SearchFocus.STARRED || it.favorite) && matches(it.name, it.category, it.description.takeUnless { d -> d.startsWith("[WORKOUT_CFG]") }.orEmpty())
    }
    val entries = library.items.mapNotNull { item -> parents[item.notebookId]?.let { notebook ->
        val eligible = when (focus) {
            SearchFocus.ALL -> true
            SearchFocus.STARRED -> item.starred || notebook.favorite
            SearchFocus.TODAY -> !item.completed && (item.dueDate?.let { it <= endToday } == true || (NotebookKind.parse(notebook.type) == NotebookKind.HABIT && item.dueDate == null))
        }
        if (eligible && matches(item.title, item.description.takeUnless { it.startsWith("[EXERCISE_CFG]") }.orEmpty(), item.tags, item.category, notebook.name)) EntryMatch(notebook, item) else null
    } }.sortedWith(if (focus == SearchFocus.TODAY) compareBy<EntryMatch> { it.item.dueDate ?: Long.MAX_VALUE }.thenByDescending { it.item.updatedAt }
        else compareByDescending<EntryMatch> { it.item.updatedAt }.thenByDescending { it.item.id })
    return SearchResults(notebookMatches, entries)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySearchDialog(controller: WorkspaceController, initialFocus: SearchFocus, onClose: () -> Unit, onOpen: (NotebookEntity, Long?) -> Unit) {
    val library by controller.library.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var focusName by rememberSaveable { mutableStateOf(initialFocus.name) }
    val focus = SearchFocus.valueOf(focusName)
    var workspaceName by rememberSaveable { mutableStateOf<String?>(null) }
    val workspace = workspaceName?.let(WorkspaceId::valueOf)
    var includeArchive by rememberSaveable { mutableStateOf(false) }
    var visible by rememberSaveable(query, focusName, workspaceName, includeArchive) { mutableIntStateOf(30) }
    val requester = remember { FocusRequester() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val results by produceState<SearchResults?>(null, library, query, focus, workspace, includeArchive, now) {
        value = null
        delay(120)
        val ready = library as? LibraryState.Ready
        if (ready != null) value = withContext(Dispatchers.Default) { searchLibrary(ready, query, focus, workspace, includeArchive, now) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            BackHandler(onBack = onClose)
            Scaffold(topBar = { TopAppBar(
                expandedHeight = (64 * LocalDensity.current.fontScale.coerceIn(1f, 2f)).dp,
                title = { Text(stringResource(R.string.v11_search), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
            ) }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().testTag("global_search_results"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.v11_search_hint)) }, singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.ux_clear_filters)) } },
                            modifier = Modifier.fillMaxWidth().focusRequester(requester).testTag("global_search_input"))
                        LaunchedEffect(Unit) { withFrameNanos { }; if (initialFocus == SearchFocus.ALL) requester.requestFocus() }
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(SearchFocus.entries) { option ->
                            FilterChip(selected = focus == option, onClick = { focusName = option.name }, label = { Text(stringResource(option.label)) }, modifier = Modifier.testTag("search-focus-${option.name}"))
                        } }
                        if (focus == SearchFocus.TODAY) Text(stringResource(R.string.v11_today_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = workspace == null, onClick = { workspaceName = null }, label = { Text(stringResource(R.string.v11_everywhere)) }) }
                            items(WorkspaceId.entries) { area -> FilterChip(selected = workspace == area, onClick = { workspaceName = area.name }, label = { Text(stringResource(area.title)) }) }
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = includeArchive, role = Role.Checkbox, onValueChange = { includeArchive = it }).testTag("search_include_archive"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = includeArchive, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.v11_include_archive), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    when {
                        library is LibraryState.Error -> item { Text(stringResource(R.string.ux_load_failed), color = MaterialTheme.colorScheme.error); TextButton(onClick = controller::retryLibrary) { Text(stringResource(R.string.ux_retry)) } }
                        results == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        else -> {
                            val found = results!!
                            item { Text(stringResource(if (query.isBlank() && focus == SearchFocus.ALL) R.string.v11_recent else R.string.v11_results,
                                found.notebooks.size + found.items.size), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (found.notebooks.isEmpty() && found.items.isEmpty()) item { Text(stringResource(R.string.v11_search_empty)) }
                            items(found.notebooks.take(visible), key = { "notebook-${it.id}" }) { book ->
                                OutlinedCard(onClick = { onOpen(book, null) }, Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp)) { Text("${book.emoji} ${book.name}", style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.v11_open_notebook), color = MaterialTheme.colorScheme.primary) }
                                }
                            }
                            items(found.items.take(visible), key = { "item-${it.item.id}" }) { match ->
                                OutlinedCard(onClick = { onOpen(match.notebook, match.item.id) }, modifier = Modifier.fillMaxWidth().testTag("search-entry-${match.item.id}")) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (match.item.completed) Text(stringResource(if (NotebookKind.parse(match.notebook.type) == NotebookKind.READING) R.string.ux_read else R.string.completed_filter), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        Text(match.item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${match.notebook.emoji} ${match.notebook.name} · ${stringResource(NotebookKind.parse(match.notebook.type).workspace.title)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        match.item.dueDate?.let { due -> Text(stringResource(R.string.v11_due, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(due))), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                            if (found.items.size > visible || found.notebooks.size > visible) item { TextButton(onClick = { visible += 30 }) { Text(stringResource(R.string.v11_more_results)) } }
                        }
                    }
                }
            }
        }
    }
}
