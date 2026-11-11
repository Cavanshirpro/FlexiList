package xyz.younglion.flexilist.ui.detail

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import xyz.younglion.flexilist.ui.workspace.EntryUiState
import kotlinx.coroutines.launch
import xyz.younglion.flexilist.ui.workspace.DiscardChangesDialog
import xyz.younglion.flexilist.ui.workspace.MessageState
import xyz.younglion.flexilist.ui.workspace.rememberReminderPermissionAction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import xyz.younglion.flexilist.data.model.NotebookEntity

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.notebooks.varRemember
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemDetailScreen(
    itemId: Long,
    viewModel: FlexiViewModel,
    onNavigateBack: () -> Unit,
    notebookId: Long = viewModel.selectedNotebookId.value ?: 0L
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val existingItemFlow = remember(itemId, notebookId) {
        if (itemId > 0L) viewModel.getItemById(itemId).map<ListItemEntity?, EntryUiState> {
            if (it == null || it.notebookId != notebookId) EntryUiState.Missing else EntryUiState.Ready(it)
        }.onStart { emit(EntryUiState.Loading) }.catch { emit(EntryUiState.Error) }
        else kotlinx.coroutines.flow.flowOf<EntryUiState>(EntryUiState.Ready(null))
    }
    val entryState by existingItemFlow.collectAsState(initial = EntryUiState.Loading)
    val existingItem = (entryState as? EntryUiState.Ready)?.item
    val loadedRecord = (entryState is EntryUiState.Ready) to existingItem
    val newItem = remember(notebookId) { ListItemEntity(notebookId = notebookId, title = "") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var finalized by remember { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val reminderPermission = rememberReminderPermissionAction()

    var hasLoadedInitialData by rememberSaveable { mutableStateOf(false) }

    var itemTitle by rememberSaveable { mutableStateOf("") }
    var itemDescription by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(0) }
    var tagsInput by rememberSaveable { mutableStateOf("") } // comma-separated
    var itemCategory by rememberSaveable { mutableStateOf("") }
    var starred by rememberSaveable { mutableStateOf(false) }

    // Date/Time triggers
    var dueDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var reminderTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var isRecurring by rememberSaveable { mutableStateOf(false) }
    var recurrenceRule by rememberSaveable { mutableStateOf<String?>(null) }

    // Structured subtasks and custom fields using custom listSavers
    val subtasksSaver = remember {
        listSaver<List<ListItemEntity.Subtask>, String>(
            save = { list -> listOf(ListItemEntity.serializeSubtasks(list)) },
            restore = { saved -> ListItemEntity.deserializeSubtasks(saved.firstOrNull().orEmpty()) }
        )
    }
    var subtasks by rememberSaveable(stateSaver = subtasksSaver) { mutableStateOf(emptyList()) }

    val customFieldsSaver = remember {
        listSaver<List<ListItemEntity.CustomField>, String>(
            save = { list -> listOf(ListItemEntity.serializeCustomFields(list)) },
            restore = { saved -> ListItemEntity.deserializeCustomFields(saved.firstOrNull().orEmpty()) }
        )
    }
    var customFields by rememberSaveable(stateSaver = customFieldsSaver) { mutableStateOf(emptyList()) }

    // Workout specific states
    val isWorkoutNotebook = xyz.younglion.flexilist.ui.workspace.NotebookKind.parse(viewModel.selectedNotebook.collectAsState().value?.type.orEmpty()) == xyz.younglion.flexilist.ui.workspace.NotebookKind.WORKOUT
    var exerciseType by rememberSaveable { mutableStateOf("Reps") }
    var setsCount by rememberSaveable { mutableStateOf(3) }
    var targetReps by rememberSaveable { mutableStateOf(10) }
    var targetTimeSeconds by rememberSaveable { mutableStateOf(30) }
    var restTimeSeconds by rememberSaveable { mutableStateOf(60) }
    var weightInput by rememberSaveable { mutableStateOf("0") }
    val weightKg = weightInput.replace(',', '.').toDoubleOrNull()
    val invalidWeight = isWorkoutNotebook && (weightKg == null || !weightKg.isFinite() || weightKg < 0)
    var targetMuscles by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var supersetGroupId by rememberSaveable { mutableStateOf("") }
    var workoutDays by rememberSaveable { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(loadedRecord, isWorkoutNotebook) {
        if (!hasLoadedInitialData && loadedRecord.first) {
            val item = viewModel.workspace.draft(notebookId, itemId)?.item ?: existingItem ?: newItem
            if (item.notebookId == notebookId) {
                itemTitle = item.title
                itemDescription = item.description
                priority = item.priority
                tagsInput = item.tags
                itemCategory = item.category
                starred = item.starred
                dueDate = item.dueDate
                reminderTime = item.reminderTime
                isRecurring = item.isRecurring
                recurrenceRule = item.recurrenceRule
                subtasks = ListItemEntity.deserializeSubtasks(item.subtasksJson)
                customFields = ListItemEntity.deserializeCustomFields(item.customFieldsJson)
                if (isWorkoutNotebook) {
                    val cfg = parseExerciseConfig(item.description)
                    exerciseType = cfg.type; setsCount = cfg.setsCount; targetReps = cfg.targetReps
                    targetTimeSeconds = cfg.targetTimeSeconds; restTimeSeconds = cfg.restTimeSeconds
                    weightInput = cfg.weightKg.toString().replace('.', java.text.DecimalFormatSymbols.getInstance().decimalSeparator); targetMuscles = cfg.targetMuscles.toSet()
                    supersetGroupId = cfg.supersetGroupId; workoutDays = cfg.workoutDays.toSet()
                }
                hasLoadedInitialData = true
            }
        }
    }
    if (entryState == EntryUiState.Missing || entryState == EntryUiState.Error) {
        MessageState(if (entryState == EntryUiState.Missing) R.string.ux_missing_entry else R.string.ux_load_failed, R.string.back, onNavigateBack)
        return
    }
    if (!loadedRecord.first || !hasLoadedInitialData) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    fun currentEntry(): ListItemEntity = (existingItem ?: newItem).copy(
        title = itemTitle, description = if (isWorkoutNotebook) {
            "[EXERCISE_CFG]type=$exerciseType|sets=$setsCount|reps=$targetReps|time=$targetTimeSeconds|rest=$restTimeSeconds|weight=$weightKg|muscles=${targetMuscles.joinToString(",")}|group=$supersetGroupId|days=${workoutDays.joinToString(",")}"
        } else itemDescription,
        priority = priority, tags = tagsInput, category = itemCategory, starred = starred,
        dueDate = dueDate, reminderTime = reminderTime, isRecurring = isRecurring, recurrenceRule = recurrenceRule,
        subtasksJson = ListItemEntity.serializeSubtasks(subtasks), customFieldsJson = ListItemEntity.serializeCustomFields(customFields)
    )
    fun fields(value: ListItemEntity): List<Any?> = listOf(
        value.title, if (isWorkoutNotebook) parseExerciseConfig(value.description) else value.description,
        value.priority, value.tags, value.category, value.starred, value.dueDate, value.reminderTime,
        value.isRecurring, value.recurrenceRule, ListItemEntity.deserializeSubtasks(value.subtasksJson), ListItemEntity.deserializeCustomFields(value.customFieldsJson)
    )
    val dirty = invalidWeight || fields(currentEntry()) != fields(existingItem ?: newItem)
    val close = { if (!saving) { if (dirty) discard = true else onNavigateBack() } }
    BackHandler { close() }
    SideEffect {
        if (!finalized) {
            if (dirty && !saving) viewModel.workspace.storeDraft(currentEntry())
            else if (!saving) viewModel.workspace.clearDraft(notebookId, itemId)
        }
    }
    if (discard) DiscardChangesDialog(
        onDiscard = { finalized = true; viewModel.workspace.clearDraft(notebookId, itemId); discard = false; onNavigateBack() },
        onContinue = { discard = false }
    )

    suspend fun persist(entry: ListItemEntity) {
        try { viewModel.workspace.saveEntry(entry) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) { saveError = true; saving = false }
            return
        }
        withContext(Dispatchers.Main.immediate) {
            finalized = true; saving = false
            viewModel.workspace.clearDraft(notebookId, itemId)
            viewModel.workspace.acknowledgeEntrySave(entry)
            viewModel.workspace.notices.tryEmit(R.string.saved_successfully)
            onNavigateBack()
        }
    }
    LaunchedEffect(Unit) {
        if (saving) persist(viewModel.workspace.draft(notebookId, itemId)?.item ?: currentEntry())
    }

    // Date & Time picker helpers
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            dueDate = calendar.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val reminderDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            // Immediately open time picker for reminder
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    reminderTime = calendar.timeInMillis
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (itemId > 0L) stringResource(R.string.edit_item_title) else stringResource(R.string.new_item_detail_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = close, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.discard))
                    }
                },
                actions = {
                    // Star toggler
                    IconButton(enabled = !saving, onClick = { starred = !starred }) {
                        Icon(
                            imageVector = if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(R.string.star),
                            tint = if (starred) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            attempted = true
                            if (itemTitle.isBlank() || invalidWeight) return@TextButton
                            saving = true; saveError = false
                            val entry = currentEntry()
                            viewModel.workspace.storeDraft(entry)
                            scope.launch { persist(entry) }
                        }, modifier = Modifier.testTag("save_item_button")
                    ) { Text(stringResource(if (saving) R.string.ux_working else R.string.save)) }

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (saveError) item { Text(stringResource(R.string.ux_save_failed), color = MaterialTheme.colorScheme.error) }
            if (isWorkoutNotebook) {
                // Title Inputs for workout
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(enabled = !saving,
                            value = itemTitle,
                            onValueChange = { itemTitle = it },
                            label = { Text(stringResource(R.string.exercise_name_placeholder)) },
                            isError = attempted && itemTitle.isBlank(),
                            supportingText = { if (attempted && itemTitle.isBlank()) Text(stringResource(R.string.ux_title_required)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("item_title_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                // Workout specific details card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(stringResource(R.string.exercise_details), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                            // Exercise type
                            Column {
                                Text(stringResource(R.string.workout_type), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Reps" to stringResource(R.string.type_reps), "Timer" to stringResource(R.string.type_timer)).forEach { (key, label) ->
                                        FilterChip(enabled = !saving,
                                            selected = exerciseType == key,
                                            onClick = { exerciseType = key },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(enabled = !saving,
                                    value = setsCount.toString(),
                                    onValueChange = { setsCount = it.toIntOrNull() ?: 1 },
                                    label = { Text(stringResource(R.string.sets_count_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )

                                if (exerciseType == "Reps") {
                                    OutlinedTextField(enabled = !saving,
                                        value = targetReps.toString(),
                                        onValueChange = { targetReps = it.toIntOrNull() ?: 1 },
                                        label = { Text(stringResource(R.string.target_reps_label)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    OutlinedTextField(enabled = !saving,
                                        value = targetTimeSeconds.toString(),
                                        onValueChange = { targetTimeSeconds = it.toIntOrNull() ?: 1 },
                                        label = { Text(stringResource(R.string.duration_seconds_label)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(enabled = !saving,
                                    value = weightInput,
                                    onValueChange = { weightInput = it },
                                    isError = attempted && invalidWeight,
                                    supportingText = { if (attempted && invalidWeight) Text(stringResource(R.string.ux_invalid_weight)) },
                                    label = { Text(stringResource(R.string.weight_kg_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(enabled = !saving,
                                    value = restTimeSeconds.toString(),
                                    onValueChange = { restTimeSeconds = it.toIntOrNull() ?: 1 },
                                    label = { Text(stringResource(R.string.rest_seconds_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Superset Group ID (Hibrit Çalışma için)
                            OutlinedTextField(enabled = !saving,
                                value = supersetGroupId,
                                onValueChange = { supersetGroupId = it },
                                label = { Text(stringResource(R.string.hybrid_group_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Target muscles
                            Column {
                                Text(stringResource(R.string.target_muscle_groups), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                val musclesList = listOf(
                                    "Göğüs" to stringResource(R.string.muscle_chest),
                                    "Sırt" to stringResource(R.string.muscle_back),
                                    "Bacak" to stringResource(R.string.muscle_legs),
                                    "Omuz" to stringResource(R.string.muscle_shoulders),
                                    "Kollar" to stringResource(R.string.muscle_arms),
                                    "Karın" to stringResource(R.string.muscle_core)
                                )
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    musclesList.forEach { (muscleKey, muscleLabel) ->
                                        val isSel = targetMuscles.contains(muscleKey)
                                        FilterChip(enabled = !saving,
                                            selected = isSel,
                                            onClick = {
                                                targetMuscles = if (isSel) targetMuscles - muscleKey else targetMuscles + muscleKey
                                            },
                                            label = { Text(muscleLabel, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }

                            // Workout Days Selection
                            Column {
                                Text(stringResource(R.string.workout_days_label), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val daysMap = listOf(
                                        "Pzt" to stringResource(R.string.day_mon_short),
                                        "Sal" to stringResource(R.string.day_tue_short),
                                        "Çar" to stringResource(R.string.day_wed_short),
                                        "Per" to stringResource(R.string.day_thu_short),
                                        "Cum" to stringResource(R.string.day_fri_short),
                                        "Cmt" to stringResource(R.string.day_sat_short),
                                        "Paz" to stringResource(R.string.day_sun_short)
                                    )
                                    daysMap.forEach { (dayKey, dayLabel) ->
                                        val isSel = workoutDays.contains(dayKey)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    workoutDays = if (isSel) workoutDays - dayKey else workoutDays + dayKey
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayLabel,
                                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Title & description Inputs
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(enabled = !saving,
                            value = itemTitle,
                            onValueChange = { itemTitle = it },
                            label = { Text(stringResource(R.string.item_title_label)) },
                            isError = attempted && itemTitle.isBlank(),
                            supportingText = { if (attempted && itemTitle.isBlank()) Text(stringResource(R.string.ux_title_required)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("item_title_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )

                        OutlinedTextField(enabled = !saving,
                            value = itemDescription,
                            onValueChange = { itemDescription = it },
                            label = { Text(stringResource(R.string.item_description_placeholder)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

            item {
                TextButton(onClick = { advanced = !advanced }) { Text(stringResource(if (advanced) R.string.ux_hide_advanced else R.string.ux_advanced)) }
            }
            if (advanced) {
            // Priority Selection
            item {
                Column {
                    Text(stringResource(R.string.priority_level_label), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    val currentNotebook = viewModel.selectedNotebook.collectAsState().value
                    val pOpts = currentNotebook?.getPriorityConfig() ?: NotebookEntity.DEFAULT_PRIORITIES
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pOpts.filter { it.enabled }.forEach { opt ->
                            val pColor = if (opt.level > 0 && opt.colorHex.isNotBlank() && opt.colorHex != "#00000000") {
                                try {
                                    Color(android.graphics.Color.parseColor(opt.colorHex))
                                } catch (e: Exception) {
                                    Color.Transparent
                                }
                            } else Color.Transparent

                            FilterChip(enabled = !saving,
                                selected = priority == opt.level,
                                onClick = { priority = opt.level },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (opt.level > 0 && pColor != Color.Transparent) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(pColor)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(opt.getLocalizedName())
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Category & Tags Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(enabled = !saving,
                            value = itemCategory,
                            onValueChange = { itemCategory = it },
                            label = { Text(stringResource(R.string.category)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(enabled = !saving,
                            value = tagsInput,
                            onValueChange = { tagsInput = it },
                            label = { Text(stringResource(R.string.tags_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Quick category pills
                    val quickItemCategories by viewModel.availableCategories.collectAsState()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        quickItemCategories.forEach { cat ->
                            val isSelected = itemCategory.equals(cat, ignoreCase = true)
                            val catEmoji = when (cat.lowercase(Locale.ROOT)) {
                                "work" -> "💼"
                                "personal" -> "👤"
                                "groceries" -> "🛒"
                                "fitness" -> "🏋️"
                                "study" -> "📚"
                                else -> "🏷️"
                            }
                            FilterChip(enabled = !saving,
                                selected = isSelected,
                                onClick = { itemCategory = if (isSelected) "" else cat },
                                label = { Text("$catEmoji $cat", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Date Deadlines & Reminders Row
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.timing_and_reminder), fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Due Date trigger
                            Button(
                                onClick = { datePickerDialog.show() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Event, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (dueDate == null) stringResource(R.string.due_date) else SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(dueDate!!)),
                                    fontSize = 12.sp
                                )
                            }

                            // Reminder alarm trigger
                            Button(
                                onClick = { reminderPermission { reminderDatePickerDialog.show() } },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (reminderTime == null) stringResource(R.string.set_reminder) else SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(reminderTime!!)),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Clears
                        if (dueDate != null || reminderTime != null) {
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = {
                                    dueDate = null
                                    reminderTime = null
                                    isRecurring = false
                                    recurrenceRule = null
                                }) {
                                    Text(stringResource(R.string.reset_times), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Recurring Rules Row
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = isRecurring, onCheckedChange = {
                            isRecurring = it
                            if (it && recurrenceRule == null) recurrenceRule = "DAILY"
                        })
                        Text(stringResource(R.string.recurring_task_active), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    if (isRecurring) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("DAILY" to stringResource(R.string.recurrence_daily), "WEEKDAYS" to stringResource(R.string.recurrence_weekdays), "WEEKLY" to stringResource(R.string.recurrence_weekly), "MONTHLY" to stringResource(R.string.recurrence_monthly)).forEach { (rule, label) ->
                                FilterChip(enabled = !saving,
                                    selected = recurrenceRule == rule,
                                    onClick = { recurrenceRule = rule },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // Subtasks checklist manager section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.subtasks_header, subtasks.filter { it.completed }.size, subtasks.size), fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        // Subtasks list renderer
                        subtasks.forEachIndexed { index, sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = sub.completed,
                                    onCheckedChange = { checked ->
                                        val mutable = subtasks.toMutableList()
                                        mutable[index] = sub.copy(completed = checked)
                                        subtasks = mutable
                                    }
                                )
                                Text(
                                    text = sub.title,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp
                                )
                                IconButton(onClick = {
                                    subtasks = subtasks.filterIndexed { i, _ -> i != index }
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Add subtask input field
                        var newSubtaskTitle by varRemember { mutableStateOf("") }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(enabled = !saving,
                                value = newSubtaskTitle,
                                onValueChange = { newSubtaskTitle = it },
                                placeholder = { Text(stringResource(R.string.subtask_placeholder)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newSubtaskTitle.isNotBlank()) {
                                        subtasks = subtasks + ListItemEntity.Subtask(
                                            id = UUID.randomUUID().toString(),
                                            title = newSubtaskTitle,
                                            completed = false
                                        )
                                        newSubtaskTitle = ""
                                    }
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }

            // Custom fields system manager section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.custom_dynamic_fields), fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        // Render each dynamic custom field depending on its type
                        customFields.forEachIndexed { index, field ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${field.name} (${field.type})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)

                                    // Custom field values based on types
                                    when (field.type) {
                                        "Boolean" -> {
                                            val checked = field.value.toBoolean()
                                            Switch(enabled = !saving,
                                                checked = checked,
                                                onCheckedChange = { checkedNew ->
                                                    val mutable = customFields.toMutableList()
                                                    mutable[index] = field.copy(value = checkedNew.toString())
                                                    customFields = mutable
                                                }
                                            )
                                        }
                                        "Rating" -> {
                                            val rating = field.value.toFloatOrNull() ?: 0f
                                            Row {
                                                (1..5).forEach { star ->
                                                    Icon(
                                                        imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                                        contentDescription = null,
                                                        tint = if (star <= rating) Color(0xFFFFD600) else MaterialTheme.colorScheme.outline,
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clickable {
                                                                val mutable = customFields.toMutableList()
                                                                mutable[index] = field.copy(value = star.toFloat().toString())
                                                                customFields = mutable
                                                            }
                                                    )
                                                }
                                            }
                                        }
                                        else -> {
                                            // Text, Number, Dropdown, Date, URL
                                            OutlinedTextField(enabled = !saving,
                                                value = field.value,
                                                onValueChange = { newVal ->
                                                    val mutable = customFields.toMutableList()
                                                    mutable[index] = field.copy(value = newVal)
                                                    customFields = mutable
                                                },
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = if (field.type == "Number") KeyboardType.Number else KeyboardType.Text
                                                ),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = {
                                    customFields = customFields.filterIndexed { i, _ -> i != index }
                                }) {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Add custom field system inputs
                        var newFieldName by varRemember { mutableStateOf("") }
                        var newFieldType by varRemember { mutableStateOf("Text") }
                        var isTypeExpanded by varRemember { mutableStateOf(false) }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(enabled = !saving,
                                value = newFieldName,
                                onValueChange = { newFieldName = it },
                                placeholder = { Text(stringResource(R.string.field_name_placeholder)) },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f)
                            )

                            // Type dropdown selection chip
                            Box(modifier = Modifier.weight(1f)) {
                                FilterChip(enabled = !saving,
                                    selected = true,
                                    onClick = { isTypeExpanded = !isTypeExpanded },
                                    label = { Text(customFieldTypeLabel(newFieldType)) },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                                )
                                DropdownMenu(expanded = isTypeExpanded, onDismissRequest = { isTypeExpanded = false }) {
                                    listOf("Text", "Number", "Boolean", "Date", "Dropdown", "Rating", "URL").forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text(customFieldTypeLabel(t)) },
                                            onClick = {
                                                newFieldType = t
                                                isTypeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (newFieldName.isNotBlank()) {
                                        val defaultValue = when (newFieldType) {
                                            "Boolean" -> "false"
                                            "Rating" -> "0.0"
                                            else -> ""
                                        }
                                        customFields = customFields + ListItemEntity.CustomField(
                                            name = newFieldName,
                                            type = newFieldType,
                                            value = defaultValue
                                        )
                                        newFieldName = ""
                                    }
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
            } // Advanced options
            } // Close standard layout else block
        }
    }
}

data class ExerciseConfig(
    val type: String = "Reps", // "Timer", "Reps"
    val setsCount: Int = 3,
    val targetReps: Int = 10,
    val targetTimeSeconds: Int = 30,
    val restTimeSeconds: Int = 60,
    val weightKg: Double = 0.0,
    val targetMuscles: List<String> = emptyList(),
    val supersetGroupId: String = "",
    val workoutDays: List<String> = emptyList()
)

fun parseExerciseConfig(desc: String): ExerciseConfig {
    if (!desc.startsWith("[EXERCISE_CFG]")) {
        return ExerciseConfig()
    }
    return try {
        val clean = desc.removePrefix("[EXERCISE_CFG]")
        val parts = clean.split("|").associate {
            val s = it.split("=", limit = 2)
            if (s.size >= 2) s[0] to s[1] else s[0] to ""
        }
        val type = parts["type"]?.takeIf { it == "Timer" || it == "Reps" } ?: "Reps"
        val sets = (parts["sets"]?.toIntOrNull() ?: 3).coerceIn(1, 100)
        val reps = (parts["reps"]?.toIntOrNull() ?: 10).coerceIn(1, 10000)
        val time = (parts["time"]?.toIntOrNull() ?: 30).coerceIn(1, 86400)
        val rest = (parts["rest"]?.toIntOrNull() ?: 60).coerceIn(0, 86400)
        val weight = parts["weight"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val muscles = parts["muscles"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val group = parts["group"] ?: ""
        val days = parts["days"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        ExerciseConfig(type, sets, reps, time, rest, weight, muscles, group, days)
    } catch (e: Exception) {
        ExerciseConfig()
    }
}


@Composable
private fun customFieldTypeLabel(type: String): String = stringResource(when (type) {
    "Number" -> R.string.ux_field_number
    "Boolean" -> R.string.ux_field_boolean
    "Date" -> R.string.ux_field_date
    "Dropdown" -> R.string.ux_field_dropdown
    "Rating" -> R.string.ux_field_rating
    "URL" -> R.string.ux_field_url
    else -> R.string.ux_field_text
})
