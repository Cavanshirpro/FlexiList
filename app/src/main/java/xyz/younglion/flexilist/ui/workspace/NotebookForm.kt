package xyz.younglion.flexilist.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.ui.dialogs.PriorityCustomizationDialog
import xyz.younglion.flexilist.ui.theme.NotebookColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotebookForm(
    controller: WorkspaceController,
    notebook: NotebookEntity? = null,
    workspace: WorkspaceId = WorkspaceId.LISTS,
    onDismiss: () -> Unit,
    onSaved: (NotebookEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var starterName by rememberSaveable { mutableStateOf(NotebookStarter.BLANK.name) }
    val starter = NotebookStarter.valueOf(starterName)
    val initialConfig = remember(notebook?.id) { notebook?.getWorkoutScheduleConfig() ?: xyz.younglion.flexilist.data.model.WorkoutScheduleConfig() }
    val startDate = rememberSaveable { initialConfig.startDate }
    val initialKind = notebook?.let { NotebookKind.parse(it.type) } ?: if (workspace == WorkspaceId.LISTS) NotebookKind.TASK else workspace.kinds.first()
    var name by rememberSaveable { mutableStateOf(notebook?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(notebook?.description?.takeUnless { it.startsWith("[WORKOUT_CFG]") }.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(notebook?.category.orEmpty()) }
    var emoji by rememberSaveable { mutableStateOf(notebook?.emoji ?: when (workspace) { WorkspaceId.LISTS -> "📝"; WorkspaceId.HABITS -> "🌱"; WorkspaceId.WORKOUTS -> "🏋"; WorkspaceId.LIBRARY -> "📚" }) }
    var color by rememberSaveable { mutableStateOf(notebook?.color ?: 0) }
    var kindName by rememberSaveable { mutableStateOf(initialKind.name) }
    val kind = NotebookKind.valueOf(kindName)
    var advanced by rememberSaveable { mutableStateOf(false) }
    var priorityJson by rememberSaveable { mutableStateOf(notebook?.priorityConfigJson.orEmpty()) }
    var prioritiesOpen by rememberSaveable { mutableStateOf(false) }
    var frequency by rememberSaveable { mutableStateOf(initialConfig.frequency) }
    var trainingDays by rememberSaveable { mutableStateOf(initialConfig.workoutDurationDays.toString()) }
    var restDays by rememberSaveable { mutableStateOf(initialConfig.restDurationDays.toString()) }
    var weekly by rememberSaveable { mutableStateOf(ArrayList(initialConfig.weeklyDays)) }
    var monthly by rememberSaveable { mutableStateOf(ArrayList(initialConfig.monthlyDays)) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val close = { if (!saving) { if (dirty) discard = true else onDismiss() } }
    BackHandler { close() }
    val invalidSchedule = kind == NotebookKind.WORKOUT && when (frequency) {
        "Daily" -> trainingDays.toIntOrNull() !in 1..365 || restDays.toIntOrNull() !in 0..365
        "Monthly" -> monthly.isEmpty()
        else -> weekly.isEmpty()
    }
    suspend fun persist(value: NotebookEntity) {
        val id = try { controller.saveNotebook(value, if (notebook == null) starter.items(context) else emptyList()) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) { saveFailed = true; saving = false }
            return
        }
        withContext(Dispatchers.Main.immediate) {
            saving = false; dirty = false
            controller.acknowledgeNotebookSave(value)
            onSaved(value.copy(id = id))
        }
    }
    LaunchedEffect(Unit) {
        if (saving) {
            val keyRecord = notebook ?: NotebookEntity(name = name, type = if (kind == NotebookKind.OTHER) notebook?.type.orEmpty() else kind.wireName)
            val pending = controller.pendingNotebookSave(keyRecord)
            if (pending != null) persist(pending) else saving = false
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = (64 * LocalDensity.current.fontScale.coerceIn(1f, 2f)).dp,
                title = { Text(stringResource(if (notebook == null) R.string.ux_new_notebook else R.string.edit_notebook)) },
                navigationIcon = { IconButton(onClick = close, enabled = !saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    TextButton(enabled = !saving, modifier = Modifier.testTag("save_notebook_button"), onClick = {
                        attempted = true
                        if (name.isBlank() || invalidSchedule) return@TextButton
                        saving = true; saveFailed = false
                        val desc = if (kind == NotebookKind.WORKOUT) {
                            "[WORKOUT_CFG]frequency=$frequency|workoutDays=${trainingDays.toIntOrNull() ?: 3}|restDays=${restDays.toIntOrNull() ?: 2}|startDate=$startDate|weekly=${weekly.joinToString(",")}|monthly=${monthly.joinToString(",")}"
                        } else description
                        val value = (notebook ?: NotebookEntity(name = name)).copy(
                            name = name, description = desc, category = category.trim(), emoji = emoji.ifBlank { "📝" }, color = color,
                            type = if (kind == NotebookKind.OTHER) notebook?.type.orEmpty() else kind.wireName, priorityConfigJson = priorityJson
                        )
                        scope.launch { persist(value) }
                    }) { Text(stringResource(if (saving) R.string.ux_working else R.string.save)) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(enabled = !saving,
                value = name, onValueChange = { name = it; dirty = true }, label = { Text(stringResource(R.string.notebook_name)) },
                isError = attempted && name.isBlank(), singleLine = true,
                supportingText = { if (attempted && name.isBlank()) Text(stringResource(R.string.ux_title_required)) },
                modifier = Modifier.fillMaxWidth().testTag("notebook_name_input")
            )
            if (notebook == null) {
                Text(stringResource(R.string.v11_starting_point), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.v11_template_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotebookStarter.entries.filter { it.kind == null || it.kind.workspace == workspace }.forEach { option ->
                        FilterChip(selected = starter == option, enabled = !saving, onClick = {
                            starterName = option.name
                            option.kind?.let { kindName = it.name; emoji = option.emoji; if (name.isBlank()) name = context.getString(option.label) }
                            dirty = true
                        }, label = { Text(stringResource(option.label)) }, modifier = Modifier.testTag("starter-${option.name}"))
                    }
                }
                if (starter.entries.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.v11_preview), style = MaterialTheme.typography.labelLarge)
                        starter.entries.forEach { Text("• ${stringResource(it)}", style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
            val choices = if (notebook == null) workspace.kinds else NotebookKind.entries.filter { it != NotebookKind.OTHER }
            if (choices.size > 1) {
                Text(stringResource(R.string.ux_notebook_kind), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { option -> FilterChip(enabled = !saving, selected = kind == option, onClick = { kindName = option.name; if (starter.kind != option) starterName = NotebookStarter.BLANK.name; dirty = true }, label = { Text(stringResource(option.title)) }) }
                }
            }
            TextButton(enabled = !saving, onClick = { advanced = !advanced }, modifier = Modifier.testTag("notebook_advanced")) { Text(stringResource(if (advanced) R.string.ux_hide_advanced else R.string.ux_advanced)) }
            if (advanced) {
                if (kind != NotebookKind.WORKOUT) OutlinedTextField(enabled = !saving, value = description, onValueChange = { description = it; dirty = true }, label = { Text(stringResource(R.string.notebook_description)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(enabled = !saving, value = category, onValueChange = { category = it; dirty = true }, label = { Text(stringResource(R.string.category)) }, modifier = Modifier.fillMaxWidth().testTag("notebook_category_input"))
                OutlinedTextField(enabled = !saving, value = emoji, onValueChange = { emoji = it; dirty = true }, label = { Text(stringResource(R.string.cover_icon_emoji)) }, singleLine = true)
                Text(stringResource(R.string.notebook_cover_color), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotebookColors.forEachIndexed { index, value ->
                        FilterChip(enabled = !saving, selected = color == index, onClick = { color = index; dirty = true }, label = { Text(stringResource(R.string.ux_color_option, index + 1)) }, leadingIcon = { Box(Modifier.size(16.dp).clip(CircleShape).background(value)) })
                    }
                }
                TextButton(enabled = !saving, onClick = { prioritiesOpen = true }) { Text(stringResource(R.string.priority_level_label)) }
                if (kind == NotebookKind.WORKOUT) {
                    Text(stringResource(R.string.workout_frequency_setting), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Daily" to R.string.recurrence_daily, "Weekly" to R.string.recurrence_weekly, "Monthly" to R.string.recurrence_monthly).forEach { (id, label) ->
                            FilterChip(enabled = !saving, selected = frequency == id, onClick = { frequency = id; dirty = true }, label = { Text(stringResource(label)) })
                        }
                    }
                    when (frequency) {
                        "Daily" -> {
                            OutlinedTextField(enabled = !saving, value = trainingDays, onValueChange = { trainingDays = it.filter(Char::isDigit); dirty = true }, label = { Text(stringResource(R.string.ux_training_days)) }, isError = attempted && trainingDays.toIntOrNull() !in 1..365, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(enabled = !saving, value = restDays, onValueChange = { restDays = it.filter(Char::isDigit); dirty = true }, label = { Text(stringResource(R.string.ux_rest_days)) }, isError = attempted && restDays.toIntOrNull() !in 0..365, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        "Monthly" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..31).forEach { day -> FilterChip(enabled = !saving, selected = day in monthly, onClick = { monthly = ArrayList(if (day in monthly) monthly - day else monthly + day); dirty = true }, label = { Text(java.text.NumberFormat.getIntegerInstance().format(day)) }) }
                        }
                        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(R.string.day_mon_short, R.string.day_tue_short, R.string.day_wed_short, R.string.day_thu_short, R.string.day_fri_short, R.string.day_sat_short, R.string.day_sun_short).forEachIndexed { index, label ->
                                val day = index + 1
                                FilterChip(enabled = !saving, selected = day in weekly, onClick = { weekly = ArrayList(if (day in weekly) weekly - day else weekly + day); dirty = true }, label = { Text(stringResource(label)) })
                            }
                        }
                    }
                }
            }
            if (attempted && invalidSchedule) Text(stringResource(R.string.ux_invalid_schedule), color = MaterialTheme.colorScheme.error)
            if (saveFailed) Text(stringResource(R.string.ux_save_failed), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (prioritiesOpen) PriorityCustomizationDialog(
        initialOptions = NotebookEntity.deserializePriorityConfig(priorityJson),
        onDismiss = { prioritiesOpen = false },
        onSave = { priorityJson = NotebookEntity.serializePriorityConfig(it); dirty = true; prioritiesOpen = false }
    )
    if (discard) DiscardChangesDialog(onDiscard = { discard = false; onDismiss() }, onContinue = { discard = false })
}

@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text(stringResource(R.string.ux_draft_discard_title)) }, text = { Text(stringResource(R.string.ux_draft_discard_message)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.ux_keep_editing)) } }
    )
}
