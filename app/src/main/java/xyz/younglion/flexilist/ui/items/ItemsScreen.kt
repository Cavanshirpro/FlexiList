package xyz.younglion.flexilist.ui.items

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import xyz.younglion.flexilist.util.bounceClick
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import xyz.younglion.flexilist.ui.workspace.NotebookKind
import xyz.younglion.flexilist.ui.workspace.NotebookFilters
import xyz.younglion.flexilist.ui.workspace.QuickEntrySheet
import xyz.younglion.flexilist.ui.workspace.entryAction
import xyz.younglion.flexilist.ui.workspace.rememberReminderPermissionAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import xyz.younglion.flexilist.ui.dialogs.ListReminderDialog
import xyz.younglion.flexilist.ui.dialogs.StandardListSettingsDialog
import xyz.younglion.flexilist.ui.dialogs.WorkoutPlannerSettingsDialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.notebooks.varRemember
import xyz.younglion.flexilist.ui.notebooks.PriorityCustomizationDialog
import xyz.younglion.flexilist.ui.detail.parseExerciseConfig
import xyz.younglion.flexilist.ui.theme.NotebookColors
import xyz.younglion.flexilist.ui.workout.WorkoutPlayerScreen
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ItemsScreen(
    viewModel: FlexiViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddEdit: (Long) -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    onGlobalSearch: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val notebook by viewModel.selectedNotebook.collectAsState()
    val items by viewModel.items.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()

    val isWorkout = NotebookKind.parse(notebook?.type.orEmpty()) == NotebookKind.WORKOUT
    val workoutConfig = remember(notebook?.description) {
        NotebookEntity.parseWorkoutScheduleConfig(notebook?.description)
    }

    val todayDate by viewModel.todayDate.collectAsState()
    val todayDayOfWeekAbbreviation = remember(todayDate) {
        val dayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        when (dayOfWeek) {
            java.util.Calendar.MONDAY -> "Pzt"
            java.util.Calendar.TUESDAY -> "Sal"
            java.util.Calendar.WEDNESDAY -> "Çar"
            java.util.Calendar.THURSDAY -> "Per"
            java.util.Calendar.FRIDAY -> "Cum"
            java.util.Calendar.SATURDAY -> "Cmt"
            java.util.Calendar.SUNDAY -> "Paz"
            else -> "Pzt"
        }
    }
    val todayDayOfMonthStr = remember(todayDate) {
        java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH).toString()
    }
    var selectedDayFilter by rememberSaveable(notebook?.id, todayDate) { mutableStateOf(todayDayOfWeekAbbreviation) }
    var selectedMonthlyDayFilter by rememberSaveable(notebook?.id, todayDate) { mutableStateOf(todayDayOfMonthStr) }

    // Drag-and-drop state synchronization
    var localItems by remember { mutableStateOf<List<ListItemEntity>>(emptyList()) }
    var isDragging by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableStateOf(0f) }

    LaunchedEffect(items) {
        if (!isDragging) {
            localItems = items
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val selectedPriorities by viewModel.selectedPriorities.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val filterStarred by viewModel.filterStarred.collectAsState()
    val filterCompleted by viewModel.filterCompleted.collectAsState()
    val sortType by viewModel.sortType.collectAsState()

    val displayItems = remember(localItems, selectedDayFilter, selectedMonthlyDayFilter, workoutConfig.frequency, isWorkout) {
        if (!isWorkout) {
            localItems
        } else {
            when (workoutConfig.frequency) {
                "Daily" -> localItems
                "Monthly" -> localItems.filter { item ->
                    val cfg = parseExerciseConfig(item.description)
                    cfg.workoutDays.isEmpty() || cfg.workoutDays.contains(selectedMonthlyDayFilter) || cfg.workoutDays.contains(todayDayOfWeekAbbreviation)
                }
                else -> localItems.filter { item ->
                    val cfg = parseExerciseConfig(item.description)
                    cfg.workoutDays.isEmpty() || cfg.workoutDays.contains(selectedDayFilter)
                }
            }
        }
    }

    val selectedItemIds by viewModel.selectedItemIds.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()

    var showNotebookSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showEditNotebookDialog by rememberSaveable { mutableStateOf(false) }
    var showQuickAddDialog by rememberSaveable { mutableStateOf(false) }
    var showBulkMoveDialog by rememberSaveable { mutableStateOf(false) }
    var showStatsDialog by rememberSaveable { mutableStateOf(false) }
    var showWorkoutExecution by rememberSaveable { mutableStateOf(false) }
    var showReminderDialog by rememberSaveable { mutableStateOf(false) }
    var showPriorityCustomizationDialog by rememberSaveable { mutableStateOf(false) }
    var selectedWorkoutTab by rememberSaveable { mutableStateOf(0) }

    val notebookSnapshot by viewModel.notebookContent.collectAsState()
    val unfilteredItems = (notebookSnapshot as? xyz.younglion.flexilist.ui.workspace.NotebookUiState.Ready)?.items.orEmpty()
    val hasFilter = searchQuery.isNotBlank() || selectedTags.isNotEmpty() || selectedPriorities.isNotEmpty() || selectedCategory != null || filterStarred || filterCompleted != null
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val isBulkMode = selectedItemIds.isNotEmpty()
    var reordering by rememberSaveable { mutableStateOf(false) }
    var toolbarMenu by remember { mutableStateOf(false) }
    var resetConfirmation by rememberSaveable { mutableStateOf(false) }
    var resetWaterConfirmation by rememberSaveable { mutableStateOf(false) }
    val reminderPermission = rememberReminderPermissionAction()
    LaunchedEffect(showWorkoutExecution, showEditNotebookDialog) { onFocusChanged(showWorkoutExecution || showEditNotebookDialog) }
    BackHandler(enabled = isBulkMode || reordering) { viewModel.clearSelections(); reordering = false }


    if (showEditNotebookDialog && notebook != null) {
        xyz.younglion.flexilist.ui.workspace.NotebookForm(
            viewModel.workspace, notebook, NotebookKind.parse(notebook!!.type).workspace,
            onDismiss = { showEditNotebookDialog = false },
            onSaved = { showEditNotebookDialog = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = (64 * textScale.coerceAtMost(2f)).dp,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(notebook?.emoji ?: "📝", fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text(
                                text = notebook?.name ?: stringResource(R.string.list_items),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (isBulkMode) viewModel.clearSelections() else if (reordering) reordering = false else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isBulkMode) {
                        IconButton(onClick = { viewModel.clearSelections() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selections), tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Box {
                            IconButton(onClick = { toolbarMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.ux_more_actions)) }
                            DropdownMenu(expanded = toolbarMenu, onDismissRequest = { toolbarMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.notebook_settings)) }, onClick = { toolbarMenu = false; showNotebookSettingsDialog = true })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reminder_time)) }, onClick = { toolbarMenu = false; reminderPermission { showReminderDialog = true } })
                                DropdownMenuItem(text = { Text(stringResource(R.string.stats_analytics)) }, onClick = { toolbarMenu = false; showStatsDialog = true })
                                if (onGlobalSearch != null) DropdownMenuItem(text = { Text(stringResource(R.string.v11_search)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, onClick = { toolbarMenu = false; onGlobalSearch() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.ux_reorder)) }, onClick = {
                                    toolbarMenu = false; reordering = true; viewModel.clearFilters(); viewModel.completionFilterExplicit.value = true
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.ux_select_items)) }, onClick = { toolbarMenu = false; items.firstOrNull()?.let { viewModel.toggleItemSelection(it.id) } })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!isBulkMode && !showWorkoutExecution && (!isWorkout || selectedWorkoutTab == 0)) {
                val isWorkout = NotebookKind.parse(notebook?.type.orEmpty()) == NotebookKind.WORKOUT
                if (textScale > 1.3f) FloatingActionButton(
                    onClick = { if (isWorkout) onNavigateToAddEdit(0L) else showQuickAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(8.dp).testTag("add_entry_button")
                ) { Icon(Icons.Default.Add, stringResource(entryAction(NotebookKind.parse(notebook?.type.orEmpty())))) }
                else ExtendedFloatingActionButton(
                    onClick = {
                        if (isWorkout) {
                            onNavigateToAddEdit(0L)
                        } else {
                            showQuickAddDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(8.dp).testTag("add_entry_button"),
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(entryAction(NotebookKind.parse(notebook?.type.orEmpty())))) }
                )
            }
        },
        bottomBar = {
            // Elegant Multi-Select Sliding Panel
            AnimatedVisibility(
                visible = isBulkMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { viewModel.bulkComplete(true) }) {
                                Icon(Icons.Default.CheckCircle, stringResource(R.string.complete), tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(stringResource(R.string.complete), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { viewModel.bulkComplete(false) }) {
                                Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.make_active))
                            }
                            Text(stringResource(R.string.make_active), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { showBulkMoveDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.move))
                            }
                            Text(stringResource(R.string.move), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { viewModel.bulkDelete() }, modifier = Modifier.testTag("bulk_delete")) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                            }
                            Text(stringResource(R.string.delete), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWorkout = NotebookKind.parse(notebook?.type.orEmpty()) == NotebookKind.WORKOUT

            if (isWorkout) {
                ScrollableTabRow(
                    edgePadding = 0.dp,
                    selectedTabIndex = selectedWorkoutTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        modifier = Modifier.widthIn(min = if (textScale > 1.3f) 180.dp else 100.dp),
                        selected = selectedWorkoutTab == 0,
                        onClick = { selectedWorkoutTab = 0 },
                        text = { Text(stringResource(R.string.exercises), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        modifier = Modifier.widthIn(min = if (textScale > 1.3f) 180.dp else 100.dp),
                        selected = selectedWorkoutTab == 1,
                        onClick = { selectedWorkoutTab = 1 },
                        text = { Text(stringResource(R.string.water_tracker), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(Icons.Default.LocalCafe, null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        modifier = Modifier.widthIn(min = if (textScale > 1.3f) 180.dp else 100.dp),
                        selected = selectedWorkoutTab == 2,
                        onClick = { selectedWorkoutTab = 2 },
                        text = { Text(stringResource(R.string.analytics_report), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(Icons.Default.Assessment, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            if (!isWorkout || selectedWorkoutTab == 0) {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().testTag("notebook_content"),
                    contentPadding = PaddingValues(bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "notebook-controls") {
                        Column {

                            if (isWorkout) {
                                when (workoutConfig.frequency) {
                                    "Daily" -> {
                                        // Requirement 2: Daily mode counter & progress bar, NO weekday chips
                                        val workoutLogs by viewModel.workoutLogs.collectAsState()
                                        val completedWorkoutDays = remember(workoutLogs) {
                                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                            workoutLogs.map { sdf.format(Date(it.createdAt)) }.distinct().size
                                        }
                                        val daysSinceStart = remember(workoutConfig.startDate) {
                                            val diffMs = System.currentTimeMillis() - workoutConfig.startDate
                                            maxOf(1, (diffMs / (1000 * 60 * 60 * 24)).toInt() + 1)
                                        }
                                        val progress = (completedWorkoutDays.toFloat() / daysSinceStart.toFloat()).coerceIn(0f, 1f)
                                        val progressPercent = (progress * 100).toInt()

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.DateRange,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = stringResource(R.string.daily_workout_tracking),
                                                            fontWeight = FontWeight.Bold,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.day_counter, daysSinceStart),
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.completed_days, completedWorkoutDays),
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.success_rate, progressPercent),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                )
                                            }
                                        }
                                    }
                                    "Monthly" -> {
                                        // Requirement 3: Monthly mode days of month filter chips (1..31) cleanly in LazyRow
                                        val todayDayNum = remember(todayDate) { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
                                        val todayText = stringResource(R.string.today)
                                        val dayText = stringResource(R.string.day_suffix, 0).replace("0", "").trim()
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            items((1..31).toList()) { day ->
                                                val dayStr = day.toString()
                                                val isSelected = selectedMonthlyDayFilter == dayStr
                                                val isToday = day == todayDayNum
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { selectedMonthlyDayFilter = dayStr },
                                                    label = {
                                                        Text(
                                                            text = if (isToday) "$day ($todayText)" else stringResource(R.string.day_suffix, day),
                                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 11.sp
                                                        )
                                                    },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        // "Weekly" - Days of week filter bar (Horizontally scrollable LazyRow)
                                        val dayNames = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
                                        val todayText = stringResource(R.string.today)
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            items(dayNames) { dayName ->
                                                val isSelected = selectedDayFilter == dayName
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { selectedDayFilter = dayName },
                                                    label = {
                                                        Text(
                                                            text = if (dayName == todayDayOfWeekAbbreviation) "${localizedDay(dayName)} ($todayText)" else localizedDay(dayName),
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 11.sp
                                                        )
                                                    },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }



                            if (isWorkout && displayItems.isNotEmpty()) {
                                val isToday = when (workoutConfig.frequency) {
                                    "Daily" -> true
                                    "Monthly" -> selectedMonthlyDayFilter == todayDayOfMonthStr
                                    else -> selectedDayFilter == todayDayOfWeekAbbreviation
                                }
                                val isAllCompleted = displayItems.isNotEmpty() && displayItems.all { it.completed }
                                val isWorkoutStarted = remember(notebook?.id, showWorkoutExecution) { viewModel.isWorkoutStarted(notebook?.id ?: 0L) }
                                val savedStepIndex = viewModel.getSavedWorkoutStepIndex(notebook?.id ?: 0L)
                                val isResuming = (isWorkoutStarted || savedStepIndex > 0) && !isAllCompleted

                                val cardTitle = when (workoutConfig.frequency) {
                                    "Daily" -> stringResource(R.string.daily_workout_program)
                                    "Monthly" -> stringResource(R.string.monthly_workout_program, selectedMonthlyDayFilter)
                                    else -> stringResource(R.string.weekly_workout_program, localizedDay(selectedDayFilter))
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .testTag("workout_start_card"),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.FitnessCenter,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }

                                                Column {
                                                    Text(
                                                        text = cardTitle,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    val statusSubtext = when {
                                                        isAllCompleted -> stringResource(R.string.workout_done_today)
                                                        !isToday -> if (workoutConfig.frequency == "Monthly") stringResource(R.string.not_selected_day_monthly, todayDayOfMonthStr) else stringResource(R.string.not_selected_day_weekly, todayDayOfWeekAbbreviation)
                                                        isResuming -> stringResource(R.string.workout_in_progress, savedStepIndex + 1)
                                                        else -> stringResource(R.string.exercises_ready, displayItems.size)
                                                    }
                                                    Text(
                                                        text = statusSubtext,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isAllCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    resetConfirmation = true
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(0.35f)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.reset), fontSize = 12.sp, maxLines = 1)
                                            }

                                            val buttonEnabled = isToday && !isAllCompleted
                                            val buttonText = when {
                                                isAllCompleted -> stringResource(R.string.completed_status)
                                                !isToday -> stringResource(R.string.not_today)
                                                isResuming -> stringResource(R.string.resume)
                                                else -> stringResource(R.string.start_workout)
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.startWorkout(notebook?.id ?: 0L)
                                                    showWorkoutExecution = true
                                                },
                                                enabled = buttonEnabled,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(0.65f)
                                                    .testTag("start_workout_button")
                                            ) {
                                                Icon(
                                                    imageVector = if (isAllCompleted) Icons.Default.CheckCircle else if (!isToday) Icons.Default.Lock else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }

                            if (unfilteredItems.isNotEmpty() || hasFilter) {
                                // Fast Search Bar
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.searchQuery.value = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .testTag("item_search_input"),
                                    placeholder = { Text(stringResource(R.string.search_items_placeholder)) },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                                Icon(Icons.Default.Clear, null)
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    singleLine = true
                                )

                                if (!isWorkout) {
                                    val content by viewModel.notebookContent.collectAsState()
                                    val completeList = (content as? xyz.younglion.flexilist.ui.workspace.NotebookUiState.Ready)?.items.orEmpty()
                                    val count = completeList.count { it.completed }
                                    val kind = NotebookKind.parse(notebook?.type.orEmpty())
                                    val summary = when (kind) {
                                        NotebookKind.HABIT -> stringResource(R.string.ux_today_summary, count, completeList.size)
                                        NotebookKind.READING -> stringResource(R.string.ux_read_summary, count, completeList.size)
                                        else -> stringResource(R.string.active_items_count, completeList.size - count, count)
                                    }
                                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (completeList.isNotEmpty()) LinearProgressIndicator(progress = { count.toFloat() / completeList.size }, modifier = Modifier.fillMaxWidth().height(4.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                                        }
                                    }
                                }
                                NotebookFilters(viewModel, notebook)
                                if (reordering) TextButton(onClick = { reordering = false }) { Text(stringResource(R.string.ux_done)) }

                            } // Search and filtering are unnecessary for a new empty notebook.

                        }
                    }
                    // Items List
                    val showEmptyToday = isWorkout && selectedWorkoutTab == 0 && displayItems.isEmpty() && localItems.isNotEmpty()

                    if (localItems.isEmpty()) item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth().heightIn(min = 240.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 80.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ListAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(stringResource(if (searchQuery.isNotBlank() || selectedTags.isNotEmpty() || selectedPriorities.isNotEmpty() || selectedCategory != null || filterStarred || filterCompleted != null) R.string.ux_no_results else R.string.no_items_title), fontWeight = FontWeight.Bold)
                                if (searchQuery.isNotBlank() || selectedTags.isNotEmpty() || selectedPriorities.isNotEmpty() || selectedCategory != null || filterStarred || filterCompleted != null) {
                                    TextButton(onClick = { viewModel.clearFilters() }) { Text(stringResource(R.string.ux_clear_filters)) }
                                }
                                Text(
                                    text = stringResource(if (searchQuery.isNotBlank() || selectedTags.isNotEmpty() || selectedPriorities.isNotEmpty() || selectedCategory != null || filterStarred || filterCompleted != null) R.string.ux_clear_filters else R.string.ux_empty_entries_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    } else if (showEmptyToday) item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth().heightIn(min = 240.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 80.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(stringResource(R.string.no_workout_plan_title, localizedDay(selectedDayFilter)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = stringResource(R.string.no_workout_plan_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    } else {

                        items(displayItems, key = { it.id }) { item ->
                            val currentIndex = displayItems.indexOfFirst { it.id == item.id }

                            val dragMod = Modifier.pointerInput(item.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDragging = true
                                        dragAccumulator = 0f
                                        if (sortType != "Manuel") viewModel.sortType.value = "Manuel"
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (sortType != "Manuel") viewModel.sortType.value = "Manuel"
                                        dragAccumulator += dragAmount.y
                                        val idx = localItems.indexOfFirst { it.id == item.id }
                                        if (idx != -1) {
                                            val threshold = 40f
                                            if (dragAccumulator < -threshold && idx > 0) {
                                                // Swap with previous
                                                val newList = localItems.toMutableList()
                                                val temp = newList[idx]
                                                newList[idx] = newList[idx - 1]
                                                newList[idx - 1] = temp
                                                localItems = newList
                                                dragAccumulator = 0f
                                            } else if (dragAccumulator > threshold && idx < localItems.size - 1) {
                                                // Swap with next
                                                val newList = localItems.toMutableList()
                                                val temp = newList[idx]
                                                newList[idx] = newList[idx + 1]
                                                newList[idx + 1] = temp
                                                localItems = newList
                                                dragAccumulator = 0f
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        dragAccumulator = 0f
                                        viewModel.updateItemsOrder(localItems)
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        dragAccumulator = 0f
                                        viewModel.updateItemsOrder(localItems)
                                    }
                                )
                            }

                            val onMoveUp: (() -> Unit)? = if (currentIndex > 0) {
                                {
                                    if (sortType != "Manuel") viewModel.sortType.value = "Manuel"
                                    val newList = localItems.toMutableList()
                                    val temp = newList[currentIndex]
                                    newList[currentIndex] = newList[currentIndex - 1]
                                    newList[currentIndex - 1] = temp
                                    localItems = newList
                                    viewModel.updateItemsOrder(newList)
                                }
                            } else null

                            val onMoveDown: (() -> Unit)? = if (currentIndex < localItems.size - 1) {
                                {
                                    if (sortType != "Manuel") viewModel.sortType.value = "Manuel"
                                    val newList = localItems.toMutableList()
                                    val temp = newList[currentIndex]
                                    newList[currentIndex] = newList[currentIndex + 1]
                                    newList[currentIndex + 1] = temp
                                    localItems = newList
                                    viewModel.updateItemsOrder(newList)
                                }
                            } else null

                            Box(Modifier.padding(horizontal = 16.dp)) {
                                if (isWorkout) {
                                    WorkoutExerciseCard(
                                        item = item,
                                        notebook = notebook,
                                        isSelected = selectedItemIds.contains(item.id),
                                        isBulkMode = isBulkMode,
                                        onClick = {
                                            if (isBulkMode) {
                                                viewModel.toggleItemSelection(item.id)
                                            } else {
                                                // Navigate to detailed editor
                                                onNavigateToAddEdit(item.id)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleItemSelection(item.id)
                                        },
                                        onToggleComplete = {
                                            viewModel.toggleItemComplete(item)
                                        },
                                        onToggleStar = {
                                            viewModel.updateListItem(item.copy(starred = !item.starred))
                                        },
                                        reorderEnabled = reordering && !isBulkMode,
                                        dragModifier = dragMod,
                                        onMoveUp = onMoveUp,
                                        onMoveDown = onMoveDown
                                    )
                                } else {
                                    ListItemCard(
                                        item = item,
                                        notebook = notebook,
                                        isSelected = selectedItemIds.contains(item.id),
                                        isBulkMode = isBulkMode,
                                        onClick = {
                                            if (isBulkMode) {
                                                viewModel.toggleItemSelection(item.id)
                                            } else {
                                                // Navigate to detailed editor
                                                onNavigateToAddEdit(item.id)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleItemSelection(item.id)
                                        },
                                        onToggleComplete = {
                                            viewModel.toggleItemComplete(item)
                                        },
                                        onToggleStar = {
                                            viewModel.updateListItem(item.copy(starred = !item.starred))
                                        },
                                        reorderEnabled = reordering && !isBulkMode,
                                        dragModifier = dragMod,
                                        onMoveUp = onMoveUp,
                                        onMoveDown = onMoveDown
                                    )
                                }
                                }
                        }

                    }
                }
            } // Close standard layout conditional block

            // --- WATER INTAKE TRACKER TAB ---
            if (isWorkout && selectedWorkoutTab == 1) {
                val todayWater by viewModel.todayWaterIntake.collectAsState()
                val waterAmount = todayWater?.amountMl ?: 0
                val goal = 2500
                val percent = (waterAmount.toFloat() / goal.toFloat()).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stringResource(R.string.ux_daily_goal), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.ux_water_progress, java.text.NumberFormat.getIntegerInstance().format(waterAmount), java.text.NumberFormat.getIntegerInstance().format(goal)), fontWeight = FontWeight.Black, fontSize = 36.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)

                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(percent)
                                        .background(MaterialTheme.colorScheme.primary)
                                )

                            }
                            Text(
                                java.text.NumberFormat.getPercentInstance().format(percent),
                                modifier = Modifier.padding(top = 8.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(stringResource(R.string.quick_add_water), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(250 to "bardak_250", 500 to "sise_500", 750 to "surahi_750").forEach { (ml, tag) ->
                            Button(
                                onClick = { viewModel.addWater(ml) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.heightIn(min = 48.dp).testTag(tag)
                            ) {
                                Icon(Icons.Default.LocalCafe, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+$ml ml")
                            }
                        }
                    }

                    TextButton(
                        onClick = { resetWaterConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.reset))
                    }
                }
            }

            // --- WORKOUT PERFORMANCE ANALYTICS & HISTORIC LOGS TAB ---
            if (isWorkout && selectedWorkoutTab == 2) {
                val logs by viewModel.workoutLogs.collectAsState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.workout_analytics_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))

                            var selectedMetric by varRemember { mutableStateOf("Sets") }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Sets" to stringResource(R.string.metric_sets),
                                    "Weekly" to stringResource(R.string.metric_weekly_tonnage),
                                    "Monthly" to stringResource(R.string.metric_monthly_tonnage)
                                ).forEach { (key, label) ->
                                    val isSelected = selectedMetric == key
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else Color.Transparent
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedMetric = key }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height

                                    if (logs.isEmpty()) {
                                        drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), 2f)
                                    } else {
                                        when (selectedMetric) {
                                            "Sets" -> {
                                                val maxSets = logs.maxOf { it.totalSetsCompleted }.coerceAtLeast(1)
                                                val points = logs.takeLast(7)
                                                val stepX = w / (points.size.coerceAtLeast(2) - 1).coerceAtLeast(1)

                                                points.forEachIndexed { index, log ->
                                                    val x = index * stepX
                                                    val y = h - (log.totalSetsCompleted.toFloat() / maxSets.toFloat() * (h - 30f))

                                                    // Draw Bar
                                                    drawRect(
                                                        color = Color(0xFF40C4FF).copy(alpha = 0.6f),
                                                        topLeft = Offset(x - 12f, y),
                                                        size = Size(24f, h - y)
                                                    )
                                                    // Draw glowing top cap
                                                    drawRect(
                                                        color = Color(0xFF00E5FF),
                                                        topLeft = Offset(x - 12f, y),
                                                        size = Size(24f, 4f)
                                                    )
                                                }
                                            }
                                            "Weekly" -> {
                                                val cal = java.util.Calendar.getInstance()
                                                val weeklyTonnage = logs.groupBy { log ->
                                                    cal.timeInMillis = log.startTime
                                                    val year = cal.get(java.util.Calendar.YEAR)
                                                    val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
                                                    "$year-W$week"
                                                }.mapValues { entry ->
                                                    entry.value.sumOf { it.totalWeightLiftedKg }
                                                }.toList().sortedBy { it.first }.takeLast(5)

                                                if (weeklyTonnage.isNotEmpty()) {
                                                    val maxWeight = weeklyTonnage.maxOf { it.second }.toFloat().coerceAtLeast(1f)
                                                    val stepX = w / (weeklyTonnage.size.coerceAtLeast(2) - 1).coerceAtLeast(1)

                                                    weeklyTonnage.forEachIndexed { index, pair ->
                                                        val x = index * stepX
                                                        val y = h - (pair.second.toFloat() / maxWeight * (h - 30f))

                                                        // Draw Violet Bar
                                                        drawRect(
                                                            color = Color(0xFF9575CD).copy(alpha = 0.6f),
                                                            topLeft = Offset(x - 15f, y),
                                                            size = Size(30f, h - y)
                                                        )
                                                        drawRect(
                                                            color = Color(0xFFB39DDB),
                                                            topLeft = Offset(x - 15f, y),
                                                            size = Size(30f, 4f)
                                                        )
                                                    }
                                                } else {
                                                    drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), 2f)
                                                }
                                            }
                                            "Monthly" -> {
                                                val cal = java.util.Calendar.getInstance()
                                                val monthlyTonnage = logs.groupBy { log ->
                                                    cal.timeInMillis = log.startTime
                                                    val year = cal.get(java.util.Calendar.YEAR)
                                                    val month = cal.get(java.util.Calendar.MONTH) + 1
                                                    "$year-%02d".format(month)
                                                }.mapValues { entry ->
                                                    entry.value.sumOf { it.totalWeightLiftedKg }
                                                }.toList().sortedBy { it.first }.takeLast(5)

                                                if (monthlyTonnage.isNotEmpty()) {
                                                    val maxWeight = monthlyTonnage.maxOf { it.second }.toFloat().coerceAtLeast(1f)
                                                    val stepX = w / (monthlyTonnage.size.coerceAtLeast(2) - 1).coerceAtLeast(1)

                                                    monthlyTonnage.forEachIndexed { index, pair ->
                                                        val x = index * stepX
                                                        val y = h - (pair.second.toFloat() / maxWeight * (h - 30f))

                                                        // Draw Sunset Orange Bar
                                                        drawRect(
                                                            color = Color(0xFFFF8A65).copy(alpha = 0.6f),
                                                            topLeft = Offset(x - 18f, y),
                                                            size = Size(36f, h - y)
                                                        )
                                                        drawRect(
                                                            color = Color(0xFFFFAB91),
                                                            topLeft = Offset(x - 18f, y),
                                                            size = Size(36f, 4f)
                                                        )
                                                    }
                                                } else {
                                                    drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), 2f)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Labels and Metrics text row
                            if (logs.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    when (selectedMetric) {
                                        "Sets" -> {
                                            logs.takeLast(7).forEach { log ->
                                                Text(
                                                    text = "${log.totalSetsCompleted} S",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF40C4FF)
                                                )
                                            }
                                        }
                                        "Weekly" -> {
                                            val cal = java.util.Calendar.getInstance()
                                            val weeklyTonnage = logs.groupBy { log ->
                                                cal.timeInMillis = log.startTime
                                                val year = cal.get(java.util.Calendar.YEAR)
                                                val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
                                                "$year-W$week"
                                            }.mapValues { entry ->
                                                entry.value.sumOf { it.totalWeightLiftedKg }
                                            }.toList().sortedBy { it.first }.takeLast(5)

                                            weeklyTonnage.forEach { pair ->
                                                val label = pair.first.substringAfter("-")
                                                Text(
                                                    text = "$label (${pair.second.toInt()}k)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFB39DDB)
                                                )
                                            }
                                        }
                                        "Monthly" -> {
                                            val cal = java.util.Calendar.getInstance()
                                            val monthlyTonnage = logs.groupBy { log ->
                                                cal.timeInMillis = log.startTime
                                                val year = cal.get(java.util.Calendar.YEAR)
                                                val month = cal.get(java.util.Calendar.MONTH) + 1
                                                String.format(java.util.Locale.US, "%02d", month)
                                            }.mapValues { entry ->
                                                entry.value.sumOf { it.totalWeightLiftedKg }
                                            }.toList().sortedBy { it.first }.takeLast(5)

                                            monthlyTonnage.forEach { pair ->
                                                Text(
                                                    text = "Ay ${pair.first} (${pair.second.toInt()}k)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFFAB91)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = when (selectedMetric) {
                                    "Sets" -> stringResource(R.string.metric_sets_desc)
                                    "Weekly" -> stringResource(R.string.metric_weekly_desc)
                                    "Monthly" -> stringResource(R.string.metric_monthly_desc)
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(stringResource(R.string.workout_history_reports), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.no_workout_reports), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(logs) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(log.workoutName, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = { viewModel.deleteWorkoutLog(log.id) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(log.startTime))
                                            Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${log.totalDurationSeconds} sn • ${log.totalSetsCompleted} Set • ${log.totalWeightLiftedKg} kg", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full Screen Distraction-Free Workout Player Overlay
        if (showWorkoutExecution) {
            WorkoutPlayerScreen(
                viewModel = viewModel,
                notebookId = notebook?.id ?: 0L,
                workoutName = notebook?.name ?: "Antrenman",
                exercises = displayItems,
                onDismiss = { showWorkoutExecution = false }
            )
        }

        // List/Workout Reminder Dialog
        val currentNotebook = notebook
        if (showReminderDialog && currentNotebook != null) {
            ListReminderDialog(
                notebook = currentNotebook,
                viewModel = viewModel,
                onDismiss = { showReminderDialog = false }
            )
        }
    }

    // Quick Add Mini Widget Dialog
    if (resetWaterConfirmation) AlertDialog(
        onDismissRequest = { resetWaterConfirmation = false },
        text = { Text(stringResource(R.string.ux_water_reset_message)) },
        confirmButton = { TextButton(onClick = { viewModel.resetWater(); resetWaterConfirmation = false }) { Text(stringResource(R.string.reset)) } },
        dismissButton = { TextButton(onClick = { resetWaterConfirmation = false }) { Text(stringResource(R.string.cancel)) } }
    )
    if (showQuickAddDialog && notebook != null) {
        QuickEntrySheet(viewModel, notebook!!,
            onDismiss = { showQuickAddDialog = false },
            onDetailed = { showQuickAddDialog = false; onNavigateToAddEdit(0L) })
    }

    // Bulk Move Dialog selection
    if (showBulkMoveDialog) {
        AlertDialog(
            onDismissRequest = { showBulkMoveDialog = false },
            title = { Text(stringResource(R.string.move_items_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.select_target_notebook))
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(notebooks) { n ->
                            if (n.id != notebook?.id) {
                                Card(
                                    onClick = {
                                        viewModel.bulkMove(n.id)
                                        showBulkMoveDialog = false
                                        Toast.makeText(context, context.getString(R.string.items_moved_successfully), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(n.emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                                        Text(n.name, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBulkMoveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (resetConfirmation) AlertDialog(
        onDismissRequest = { resetConfirmation = false }, text = { Text(stringResource(R.string.ux_reset_confirmation)) },
        confirmButton = { TextButton(onClick = { viewModel.resetAllExercisesInNotebook(); viewModel.resetWorkoutStepIndex(notebook?.id ?: 0L); resetConfirmation = false }) { Text(stringResource(R.string.reset)) } },
        dismissButton = { TextButton(onClick = { resetConfirmation = false }) { Text(stringResource(R.string.cancel)) } }
    )

    // Statistics & Analytics Dialog
    if (showStatsDialog) {
        NotebookStatisticsDialog(
            notebook = notebook,
            items = items,
            onDismiss = { showStatsDialog = false }
        )
    }

    // Notebook Settings Dialog (Specialized for Workout Planner or Standard List)
    if (showNotebookSettingsDialog && notebook != null) {
        val n = notebook!!
        val isWorkout = NotebookKind.parse(n.type) == NotebookKind.WORKOUT
        if (isWorkout) {
            WorkoutPlannerSettingsDialog(
                notebook = n,
                viewModel = viewModel,
                onDismiss = { showNotebookSettingsDialog = false },
                onEditWorkout = {
                    showNotebookSettingsDialog = false
                    showEditNotebookDialog = true
                },
                onOpenReminder = {
                    showNotebookSettingsDialog = false
                    reminderPermission { showReminderDialog = true }
                },
                onOpenStats = {
                    showNotebookSettingsDialog = false
                    showStatsDialog = true
                }
            )
        } else {
            StandardListSettingsDialog(
                notebook = n,
                viewModel = viewModel,
                onDismiss = { showNotebookSettingsDialog = false },
                onEditNotebook = {
                    showNotebookSettingsDialog = false
                    showEditNotebookDialog = true
                },
                onOpenReminder = {
                    showNotebookSettingsDialog = false
                    reminderPermission { showReminderDialog = true }
                },
                onOpenPriorityCustomization = {
                    showNotebookSettingsDialog = false
                    showPriorityCustomizationDialog = true
                },
                onOpenStats = {
                    showNotebookSettingsDialog = false
                    showStatsDialog = true
                }
            )
        }
    }

    // Priority Customization Dialog
    if (showPriorityCustomizationDialog && notebook != null) {
        val currentNb = notebook!!
        PriorityCustomizationDialog(
            initialOptions = currentNb.getPriorityConfig(),
            onDismiss = { showPriorityCustomizationDialog = false },
            onSave = { updatedList ->
                val priorityCfgJson = NotebookEntity.serializePriorityConfig(updatedList)
                viewModel.updateNotebook(currentNb.copy(priorityConfigJson = priorityCfgJson))
                showPriorityCustomizationDialog = false
                Toast.makeText(context, context.getString(R.string.priorities_updated), Toast.LENGTH_SHORT).show()
            }
        )
    }
}

fun getPriorityColor(priorityLevel: Int, notebook: NotebookEntity?): Color {
    if (priorityLevel <= 0) return Color.Transparent
    val config = notebook?.getPriorityConfig() ?: NotebookEntity.DEFAULT_PRIORITIES
    val option = config.firstOrNull { it.level == priorityLevel }
    return if (option != null && option.colorHex.isNotBlank() && option.colorHex != "#00000000") {
        try {
            Color(android.graphics.Color.parseColor(option.colorHex))
        } catch (e: Exception) {
            Color.Transparent
        }
    } else {
        when (priorityLevel) {
            3 -> Color(0xFFFF5252)
            2 -> Color(0xFFFFAB00)
            1 -> Color(0xFF00E676)
            else -> Color.Transparent
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ListItemCard(
    item: ListItemEntity,
    notebook: NotebookEntity? = null,
    isSelected: Boolean,
    isBulkMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onToggleStar: () -> Unit,
    reorderEnabled: Boolean = false,
    dragModifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val priorityColor = getPriorityColor(item.priority, notebook)
    val pOption = notebook?.getPriorityConfig()?.firstOrNull { it.level == item.priority }
    val pName = pOption?.getLocalizedName() ?: ""
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.98f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                item.priority > 0 && priorityColor != Color.Transparent -> priorityColor.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = when {
            isSelected -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            item.priority > 0 && priorityColor != Color.Transparent -> androidx.compose.foundation.BorderStroke(1.5.dp, priorityColor.copy(alpha = 0.7f))
            else -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority left bar indicator
                if (item.priority > 0 && priorityColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(priorityColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                // If reorder is enabled, display drag handle and quick arrow buttons on the far left
                if (reorderEnabled) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        IconButton(
                            onClick = { onMoveUp?.invoke() },
                            enabled = onMoveUp != null,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.ux_move_up),
                                modifier = Modifier.size(20.dp),
                                tint = if (onMoveUp != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = stringResource(R.string.ux_drag),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            modifier = dragModifier
                                .size(24.dp)
                                .padding(vertical = 2.dp)
                        )

                        IconButton(
                            onClick = { onMoveDown?.invoke() },
                            enabled = onMoveDown != null,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.ux_move_down),
                                modifier = Modifier.size(20.dp),
                                tint = if (onMoveDown != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                }

                Checkbox(
                    checked = item.completed,
                    onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggleComplete() },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("complete-item-${item.id}")
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Description or Notes quick previews
                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Metadata Row: Tags, DueDate, Recurrence Badge, Priority Tag
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Priority Badge Tag
                        if (item.priority > 0 && priorityColor != Color.Transparent) {
                            Surface(
                                color = priorityColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, priorityColor.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(priorityColor)
                                    )
                                    Text(
                                        text = pName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        // Due date indicator
                        if (item.dueDate != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Event, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.dueDate)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Recurrence indicator
                        if (item.isRecurring && !item.recurrenceRule.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Repeat, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(item.recurrenceRule, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Category chip
                        if (item.category.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(item.category, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                // Favorite Gold Star
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleStar()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (item.starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(if (item.starred) R.string.ux_unfavorite else R.string.ux_favorite),
                        tint = if (item.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutExerciseCard(
    item: ListItemEntity,
    notebook: NotebookEntity? = null,
    isSelected: Boolean,
    isBulkMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onToggleStar: () -> Unit,
    reorderEnabled: Boolean = false,
    dragModifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val cfg = remember(item.description) { parseExerciseConfig(item.description) }
    val priorityColor = getPriorityColor(item.priority, notebook)
    val pOption = notebook?.getPriorityConfig()?.firstOrNull { it.level == item.priority }
    val pName = pOption?.getLocalizedName() ?: ""
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.98f)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                item.priority > 0 && priorityColor != Color.Transparent -> priorityColor.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = when {
            isSelected -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            item.priority > 0 && priorityColor != Color.Transparent -> androidx.compose.foundation.BorderStroke(1.5.dp, priorityColor.copy(alpha = 0.7f))
            else -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority left accent indicator
                if (item.priority > 0 && priorityColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(priorityColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
            // Drag handles
            if (reorderEnabled) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    IconButton(
                        onClick = { onMoveUp?.invoke() },
                        enabled = onMoveUp != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.ux_move_up),
                            modifier = Modifier.size(20.dp),
                            tint = if (onMoveUp != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.ux_drag),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = dragModifier
                            .size(24.dp)
                            .padding(vertical = 2.dp)
                    )

                    IconButton(
                        onClick = { onMoveDown?.invoke() },
                        enabled = onMoveDown != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.ux_move_down),
                            modifier = Modifier.size(20.dp),
                            tint = if (onMoveDown != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Checklist Toggler
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleComplete()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = stringResource(if (item.completed) R.string.make_active else R.string.complete),
                    tint = if (item.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Main Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Exercise Title
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Superset Badge
                    if (cfg.supersetGroupId.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⚡ SÜPERSET",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Priority Badge
                    if (item.priority > 0 && priorityColor != Color.Transparent) {
                        Surface(
                            color = priorityColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, priorityColor.copy(alpha = 0.6f)),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(priorityColor)
                                )
                                Text(
                                    text = pName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Muscle target tags
                if (cfg.targetMuscles.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        cfg.targetMuscles.take(4).forEach { muscle ->
                            val muscleBg = when (muscle.lowercase()) {
                                "göğüs", "chest" -> Color(0xFFFCE4EC)
                                "sırt", "back" -> Color(0xFFE3F2FD)
                                "omuz", "shoulders" -> Color(0xFFFFF3E0)
                                "ön kol", "arka kol", "biceps", "triceps", "kollar" -> Color(0xFFF3E5F5)
                                "bacak", "legs" -> Color(0xFFE8F5E9)
                                "karın", "abs" -> Color(0xFFE0F2F1)
                                else -> Color(0xFFECEFF1)
                            }
                            val muscleColor = when (muscle.lowercase()) {
                                "göğüs", "chest" -> Color(0xFFC2185B)
                                "sırt", "back" -> Color(0xFF1976D2)
                                "omuz", "shoulders" -> Color(0xFFF57C00)
                                "ön kol", "arka kol", "biceps", "triceps", "kollar" -> Color(0xFF7B1FA2)
                                "bacak", "legs" -> Color(0xFF388E3C)
                                "karın", "abs" -> Color(0xFF00796B)
                                else -> Color(0xFF455A64)
                            }
                            Box(
                                modifier = Modifier
                                    .background(muscleBg, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = localizedMuscle(muscle),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = xyz.younglion.flexilist.ui.theme.lightAccent(muscleColor)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Exercise parameters wrapped in FlowRow for responsive fit on any screen size
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Sets pill
                    ParameterPill(label = stringResource(R.string.ux_sets_short, cfg.setsCount), color = MaterialTheme.colorScheme.primary)

                    // Reps/Time pill
                    val goalText = if (cfg.type == "Reps") stringResource(R.string.ux_reps_short, cfg.targetReps) else stringResource(R.string.ux_seconds_short, cfg.targetTimeSeconds)
                    ParameterPill(label = goalText, color = MaterialTheme.colorScheme.secondary)

                    // Weight pill
                    if (cfg.weightKg > 0) {
                        ParameterPill(label = "${java.text.NumberFormat.getNumberInstance().format(cfg.weightKg)} kg", color = MaterialTheme.colorScheme.tertiary)
                    }

                    // Rest pill
                    ParameterPill(label = stringResource(R.string.ux_rest_short, cfg.restTimeSeconds), color = MaterialTheme.colorScheme.outline)
                }
            }

            // Star toggler on the far right
            IconButton(onClick = onToggleStar, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (item.starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(if (item.starred) R.string.ux_unfavorite else R.string.ux_favorite),
                    tint = if (item.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
}

@Composable
fun ParameterPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatMetricChip(label: String, value: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookStatisticsDialog(
    notebook: NotebookEntity?,
    items: List<ListItemEntity>,
    onDismiss: () -> Unit
) {
    val totalCount = items.size
    val completedCount = items.count { it.completed }
    val activeCount = totalCount - completedCount
    val completionRate = if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()) else 0f
    val completionPercent = (completionRate * 100).toInt()
    val starredCount = items.count { it.starred }

    val priorityConfig = notebook?.getPriorityConfig() ?: NotebookEntity.DEFAULT_PRIORITIES
    val priorityCounts = remember(items) {
        val map = mutableMapOf<Int, Int>()
        items.forEach { item ->
            map[item.priority] = (map[item.priority] ?: 0) + 1
        }
        map
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 500.dp)
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.notebook_analysis),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Completion Rate Gauge / Ring Chart
                    if (notebook?.type != "Workout Planner") {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "🎯 " + stringResource(R.string.completion_rate),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(130.dp)
                                ) {
                                    val primaryColor = MaterialTheme.colorScheme.primary
                                    val trackColor = MaterialTheme.colorScheme.surfaceVariant

                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeWidth = 16.dp.toPx()
                                        drawArc(
                                            color = trackColor,
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = DrawStroke(width = strokeWidth, cap = StrokeCap.Round)
                                        )
                                        if (completionRate > 0f) {
                                            drawArc(
                                                color = primaryColor,
                                                startAngle = -90f,
                                                sweepAngle = 360f * completionRate,
                                                useCenter = false,
                                                style = DrawStroke(width = strokeWidth, cap = StrokeCap.Round)
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "%$completionPercent",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "$completedCount / $totalCount",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatMetricChip(label = stringResource(R.string.stat_completed), value = "$completedCount", color = MaterialTheme.colorScheme.primary)
                                    StatMetricChip(label = stringResource(R.string.stat_active), value = "$activeCount", color = MaterialTheme.colorScheme.secondary)
                                    StatMetricChip(label = stringResource(R.string.stat_starred), value = "$starredCount", color = Color(0xFFFFB300))
                                }
                            }
                        }
                    }

                    // 2. Color & Priority Distribution Chart
                    if (notebook?.type != "Workout Planner") {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "🎨 " + stringResource(R.string.priority_color_distribution),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                if (totalCount == 0) {
                                    Text(
                                        text = stringResource(R.string.no_data_yet),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    // Stacked multi-color progress bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(20.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            var currentFraction = 0f
                                            priorityConfig.forEach { opt ->
                                                val count = priorityCounts[opt.level] ?: 0
                                                if (count > 0) {
                                                    val fraction = count.toFloat() / totalCount.toFloat()
                                                    val hex = opt.colorHex
                                                    val color = if (opt.level == 0 || hex.isBlank()) {
                                                        surfaceVariantColor
                                                    } else {
                                                        try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                                                    }
                                                    drawRect(
                                                        color = color,
                                                        topLeft = androidx.compose.ui.geometry.Offset(size.width * currentFraction, 0f),
                                                        size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height)
                                                    )
                                                    currentFraction += fraction
                                                }
                                            }
                                        }
                                    }

                                    // Legend list
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        priorityConfig.forEach { opt ->
                                            val count = priorityCounts[opt.level] ?: 0
                                            val pct = if (totalCount > 0) ((count.toFloat() / totalCount) * 100).toInt() else 0
                                            val hex = opt.colorHex
                                            val color = if (opt.level == 0 || hex.isBlank()) {
                                                MaterialTheme.colorScheme.outline
                                            } else {
                                                try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .clip(CircleShape)
                                                            .background(color)
                                                    )
                                                    Text(
                                                        text = if (opt.level == 0) "Derecesiz" else "${opt.name} (Derece ${opt.level})",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }

                                                Text(
                                                    text = "$count öğe (%$pct)",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = color
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Kapat")
                }
            }
        }
    }
}

/** Translate only the display label, never the stored weekday key. */
@Composable
fun localizedDay(key: String): String {
    val label = when (key) {
        "Pzt" -> R.string.day_mon_short
        "Sal" -> R.string.day_tue_short
        "Çar" -> R.string.day_wed_short
        "Per" -> R.string.day_thu_short
        "Cum" -> R.string.day_fri_short
        "Cmt" -> R.string.day_sat_short
        "Paz" -> R.string.day_sun_short
        else -> null
    }
    return label?.let { stringResource(it) } ?: key
}

/** Display aliases only; the serialized exercise configuration keeps its original keys. */
@Composable
private fun localizedMuscle(key: String): String {
    val label = when (key.lowercase(java.util.Locale.ROOT)) {
        "göğüs", "chest" -> R.string.muscle_chest
        "sırt", "back" -> R.string.muscle_back
        "bacak", "legs" -> R.string.muscle_legs
        "omuz", "shoulders" -> R.string.muscle_shoulders
        "kollar", "arms" -> R.string.muscle_arms
        "karın", "abs", "core" -> R.string.muscle_core
        else -> null
    }
    return label?.let { stringResource(it) } ?: key
}
