package xyz.younglion.flexilist.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.ui.FlexiViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotebookFilters(viewModel: FlexiViewModel, notebook: NotebookEntity?) {
    val completed by viewModel.filterCompleted.collectAsState()
    val explicit by viewModel.completionFilterExplicit.collectAsState()
    val starred by viewModel.filterStarred.collectAsState()
    val priorities by viewModel.selectedPriorities.collectAsState()
    val category by viewModel.selectedCategory.collectAsState()
    val tags by viewModel.selectedTags.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val sort by viewModel.sortType.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    val effective = if (explicit || completed != null || notebook?.showCompleted != false) completed else false
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to R.string.ux_all, false to R.string.active_filter, true to if (NotebookKind.parse(notebook?.type.orEmpty()) == NotebookKind.READING) R.string.ux_read else R.string.completed_filter).forEach { (value, label) ->
            FilterChip(selected = effective == value, onClick = { viewModel.completionFilterExplicit.value = true; viewModel.filterCompleted.value = value }, label = { Text(stringResource(label)) })
        }
        IconButton(onClick = { open = true }) { Icon(Icons.Default.FilterList, stringResource(R.string.ux_advanced)) }
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ux_sort), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Manuel" to R.string.sort_manual, "A-Z" to R.string.sort_az, "Z-A" to R.string.sort_za, "Öncelik" to R.string.sort_priority, "Tarih" to R.string.sort_date, "Yıldızlı" to R.string.sort_starred).forEach { (value, label) ->
                    FilterChip(selected = sort == value, onClick = { viewModel.sortType.value = value }, label = { Text(stringResource(label)) })
                }
            }
            FilterChip(selected = starred, onClick = { viewModel.filterStarred.value = !starred }, label = { Text(stringResource(R.string.starred_filter)) })
            Text(stringResource(R.string.priority_level_label), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                notebook?.getPriorityConfig()?.filter { it.enabled }?.forEach { option ->
                    FilterChip(selected = option.level in priorities, onClick = { viewModel.selectedPriorities.value = if (option.level in priorities) priorities - option.level else priorities + option.level }, label = { Text(option.getLocalizedName()) })
                }
            }
            if (availableCategories.isNotEmpty()) {
                Text(stringResource(R.string.category), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableCategories.forEach { value -> FilterChip(selected = category == value, onClick = { viewModel.selectedCategory.value = if (category == value) null else value }, label = { Text(value) }) }
                }
            }
            if (availableTags.isNotEmpty()) {
                Text(stringResource(R.string.tags_hint), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableTags.forEach { value -> FilterChip(selected = value in tags, onClick = { viewModel.selectedTags.value = if (value in tags) tags - value else tags + value }, label = { Text(value) }) }
                }
            }
            TextButton(onClick = { viewModel.clearFilters(); open = false }) { Text(stringResource(R.string.ux_clear_filters)) }
            Button(onClick = { open = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ux_done)) }
        }
    }
}

fun entryAction(kind: NotebookKind): Int = when (kind) {
    NotebookKind.HABIT -> R.string.ux_add_habit
    NotebookKind.READING -> R.string.ux_add_book
    NotebookKind.WORKOUT -> R.string.ux_add_exercise
    else -> R.string.add
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEntrySheet(viewModel: FlexiViewModel, notebook: NotebookEntity, onDismiss: () -> Unit, onDetailed: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = viewModel.workspace
    var draft by remember(notebook.id) { mutableStateOf(controller.draft(notebook.id, 0)?.item ?: ListItemEntity(notebookId = notebook.id, title = "")) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var multiple by rememberSaveable { mutableStateOf(false) }
    var keepOpen by rememberSaveable { mutableStateOf(controller.keepAdding()) }
    var savedCount by rememberSaveable { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val titles = if (multiple) draft.title.lineSequence().map(String::trim).filter(String::isNotEmpty).toList() else listOf(draft.title.trim()).filter(String::isNotEmpty)
    val invalid = titles.size !in 1..100
    suspend fun persist(entry: ListItemEntity) {
        val entries = if (multiple) entry.title.lineSequence().map(String::trim).filter(String::isNotEmpty).map { entry.copy(title = it) }.toList() else listOf(entry)
        try { if (multiple) controller.saveBatch(entries) else controller.saveEntry(entry) }
        catch (failure: CancellationException) { throw failure }
        catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) { error = true; saving = false }
            return
        }
        withContext(Dispatchers.Main.immediate) {
            controller.clearDraft(notebook.id, 0)
            if (multiple) controller.acknowledgeBatch(notebook.id) else controller.acknowledgeEntrySave(entry)
            saving = false; attempted = false; savedCount = entries.size
            if (keepOpen) { draft = ListItemEntity(notebookId = notebook.id, title = ""); focus.requestFocus() }
            else { controller.notices.tryEmit(R.string.saved_successfully); onDismiss() }
        }
    }
    LaunchedEffect(Unit) { if (saving) persist(draft) }
    fun save() {
        if (saving) return
        attempted = true
        if (invalid) return
        saving = true; error = false
        controller.storeDraft(draft)
        scope.launch { persist(draft) }
    }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !saving || it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheet, modifier = Modifier.imePadding()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(entryAction(NotebookKind.parse(notebook.type))), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !multiple, enabled = !saving, onClick = { multiple = false }, label = { Text(stringResource(R.string.v11_single)) })
                FilterChip(selected = multiple, enabled = !saving, onClick = { multiple = true }, label = { Text(stringResource(R.string.v11_multiple)) }, modifier = Modifier.testTag("quick_entry_multiple"))
            }
            OutlinedTextField(
                value = draft.title, onValueChange = { draft = draft.copy(title = it); controller.storeDraft(draft); savedCount = 0 },
                label = { Text(stringResource(R.string.item_title_label)) }, singleLine = !multiple, minLines = if (multiple) 4 else 1, maxLines = if (multiple) 8 else 1, isError = attempted && invalid,
                supportingText = { if (attempted && invalid) Text(stringResource(if (multiple) R.string.v11_batch_limit else R.string.ux_title_required)) else if (multiple) Text(stringResource(R.string.v11_batch_hint)) },
                modifier = Modifier.fillMaxWidth().testTag("quick_entry_title").focusRequester(focus), enabled = !saving,
                keyboardOptions = KeyboardOptions(imeAction = if (multiple) ImeAction.Default else ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() })
            )
            LaunchedEffect(Unit) { withFrameNanos { }; if (!saving) focus.requestFocus() }
            if (error) Text(stringResource(R.string.ux_save_failed), color = MaterialTheme.colorScheme.error)
            if (savedCount > 0) Text(stringResource(R.string.v11_added, savedCount), color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
            if (!multiple) TextButton(enabled = !saving, onClick = { controller.storeDraft(draft); onDetailed() }, modifier = Modifier.testTag("quick_entry_details")) { Text(stringResource(R.string.detailed_add)) }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = keepOpen, enabled = !saving, role = Role.Checkbox, onValueChange = { keepOpen = it; controller.setKeepAdding(it) }).testTag("quick_keep_open"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = keepOpen, enabled = !saving, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.v11_keep_adding), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            Button(enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("quick_entry_save"), onClick = ::save) {
                Text(if (saving) stringResource(R.string.ux_working) else if (multiple) stringResource(R.string.v11_add_count, titles.size) else stringResource(R.string.add))
            }
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.ux_done)) }
        }
    }
}
