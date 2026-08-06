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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import xyz.younglion.flexilist.ui.dialogs.ListReminderDialog
import xyz.younglion.flexilist.ui.dialogs.StandardListSettingsDialog
import xyz.younglion.flexilist.ui.dialogs.WorkoutPlannerSettingsDialog
import xyz.younglion.flexilist.ui.notebooks.NotebookDialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemsScreen(
    viewModel: FlexiViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val notebook by viewModel.selectedNotebook.collectAsState()
    val items by viewModel.items.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()

    val isWorkout = notebook?.type == "Workout Planner" || notebook?.type == "WorkoutPlanner"
    val workoutConfig = remember(notebook?.description) {
        NotebookEntity.parseWorkoutScheduleConfig(notebook?.description)
    }

    val todayDayOfWeekAbbreviation = remember {
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
    val todayDayOfMonthStr = remember {
        java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH).toString()
    }
    var selectedDayFilter by remember { mutableStateOf(todayDayOfWeekAbbreviation) }
    var selectedMonthlyDayFilter by remember { mutableStateOf(todayDayOfMonthStr) }
    
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

    val displayItems = remember(localItems, selectedDayFilter, selectedMonthlyDayFilter, workoutConfig.frequency) {
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

    var showNotebookSettingsDialog by varRemember { mutableStateOf(false) }
    var showEditNotebookDialog by remember { mutableStateOf(false) }
    var showQuickAddDialog by varRemember { mutableStateOf(false) }
    var showBulkMoveDialog by varRemember { mutableStateOf(false) }
    var showStatsDialog by varRemember { mutableStateOf(false) }
    var showWorkoutExecution by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showPriorityCustomizationDialog by remember { mutableStateOf(false) }
    var selectedWorkoutTab by remember { mutableStateOf(0) }

    val isBulkMode = selectedItemIds.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(notebook?.emoji ?: "📝", fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text(
                                text = notebook?.name ?: "Liste Öğeleri",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${items.filter { !it.completed }.size} Aktif, ${items.filter { it.completed }.size} Tamamlandı",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri Dön")
                    }
                },
                actions = {
                    if (isBulkMode) {
                        IconButton(onClick = { viewModel.clearSelections() }) {
                            Icon(Icons.Default.Close, contentDescription = "Seçimleri Temizle", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        notebook?.let { nb ->
                            IconButton(onClick = { showReminderDialog = true }) {
                                val reminderTime = viewModel.getNotebookReminderTime(nb.id)
                                Icon(
                                    imageVector = if (reminderTime != null) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                                    contentDescription = "Hatırlatıcı Saati",
                                    tint = if (reminderTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (!isWorkout) {
                            IconButton(onClick = { showStatsDialog = true }) {
                                Icon(Icons.Default.PieChart, contentDescription = "İstatistikler & Analiz", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { showNotebookSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Defter Ayarları")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!isBulkMode && !showWorkoutExecution) {
                val isWorkout = notebook?.type == "Workout Planner" || notebook?.type == "WorkoutPlanner"
                FloatingActionButton(
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
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ekle")
                }
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
                                Icon(Icons.Default.CheckCircle, "Tamamla", tint = MaterialTheme.colorScheme.primary)
                            }
                            Text("Tamamla", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { viewModel.bulkComplete(false) }) {
                                Icon(Icons.Default.RadioButtonUnchecked, "Aktif Yap")
                            }
                            Text("Aktif Et", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { showBulkMoveDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Taşı")
                            }
                            Text("Taşı", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { viewModel.bulkDelete() }) {
                                Icon(Icons.Default.Delete, "Sil", tint = MaterialTheme.colorScheme.error)
                            }
                            Text("Sil", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
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
            val isWorkout = notebook?.type == "Workout Planner" || notebook?.type == "WorkoutPlanner"

            if (isWorkout) {
                TabRow(
                    selectedTabIndex = selectedWorkoutTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedWorkoutTab == 0,
                        onClick = { selectedWorkoutTab = 0 },
                        text = { Text("Egzersizler", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedWorkoutTab == 1,
                        onClick = { selectedWorkoutTab = 1 },
                        text = { Text("Su Takibi", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.LocalCafe, null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedWorkoutTab == 2,
                        onClick = { selectedWorkoutTab = 2 },
                        text = { Text("Analiz & Rapor", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Assessment, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            if (!isWorkout || selectedWorkoutTab == 0) {
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
                                                text = "Günlük Antrenman Takibi",
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
                                                text = "$daysSinceStart. Gün",
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
                                            text = "Tamamlanan: $completedWorkoutDays Gün",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "%$progressPercent Başarı",
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
                            val todayDayNum = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
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
                                                text = if (isToday) "$day (Bugün)" else "$day. Gün",
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
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")) { dayName ->
                                    val isSelected = selectedDayFilter == dayName
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedDayFilter = dayName },
                                        label = {
                                            Text(
                                                text = if (dayName == todayDayOfWeekAbbreviation) "$dayName (Bugün)" else dayName,
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
                        "Daily" -> "Günlük Antrenman Programı"
                        "Monthly" -> "$selectedMonthlyDayFilter. Gün Programı"
                        else -> "$selectedDayFilter Günlük Programı"
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
                                            isAllCompleted -> "Bugünkü antrenman tamamlandı! ✓"
                                            !isToday -> if (workoutConfig.frequency == "Monthly") "Seçilen gün bugün değil ($todayDayOfMonthStr. gün)" else "Seçilen gün bugün değil ($todayDayOfWeekAbbreviation)"
                                            isResuming -> "Antrenman devam ediyor (${savedStepIndex + 1}. adım)"
                                            else -> "${displayItems.size} egzersiz hazır"
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
                                        viewModel.resetAllExercisesInNotebook()
                                        viewModel.resetWorkoutStepIndex(notebook?.id ?: 0L)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(0.35f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sıfırla", fontSize = 12.sp, maxLines = 1)
                                }

                                val buttonEnabled = isToday && !isAllCompleted
                                val buttonText = when {
                                    isAllCompleted -> "Tamamlandı ✓"
                                    !isToday -> "Günü Değil"
                                    isResuming -> "Devam Et"
                                    else -> "Antrenmanı Başlat"
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

            // Fast Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("item_search_input"),
                placeholder = { Text("Mevcut listede arayın...") },
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

            // Summary Dashboard (Completion progress and total count)
            if (items.isNotEmpty() && !isWorkout) {
                val totalCount = items.size
                val completedCount = items.count { it.completed }
                val activeCount = totalCount - completedCount
                val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
                val progressPercent = (progress * 100).toInt()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("summary_dashboard"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.DonutLarge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "TAMAMLANMA ORANI",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "%$progressPercent",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Custom Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = progress)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                    Text(
                                        text = "$completedCount Tamamlanan",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(MaterialTheme.colorScheme.outline, CircleShape)
                                    )
                                    Text(
                                        text = "$activeCount Aktif",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "Toplam $totalCount Öğe",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Horizontal Filter Chips and Tag Rows
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Starred filter chip
                item {
                    FilterChip(
                        selected = filterStarred,
                        onClick = { viewModel.filterStarred.value = !filterStarred },
                        label = { Text("★ Yıldızlı") }
                    )
                }

                // Completed/Active filter chip
                item {
                    FilterChip(
                        selected = filterCompleted == false,
                        onClick = {
                            viewModel.filterCompleted.value = if (filterCompleted == false) null else false
                        },
                        label = { Text("Etkinler") }
                    )
                }
                item {
                    FilterChip(
                        selected = filterCompleted == true,
                        onClick = {
                            viewModel.filterCompleted.value = if (filterCompleted == true) null else true
                        },
                        label = { Text("Tamamlananlar") }
                    )
                }

                // Priorities filters
                if (!isWorkout) {
                    val pConfigs = notebook?.getPriorityConfig() ?: NotebookEntity.DEFAULT_PRIORITIES
                    items(pConfigs.filter { it.level > 0 && it.enabled }) { opt ->
                        val pColor = getPriorityColor(opt.level, notebook)
                        FilterChip(
                            selected = selectedPriorities.contains(opt.level),
                            onClick = {
                                val current = selectedPriorities
                                viewModel.selectedPriorities.value = if (current.contains(opt.level)) current - opt.level else current + opt.level
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(pColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(opt.name)
                                }
                            }
                        )
                    }
                }

                // Sort Dropdown Selector
                item {
                    var showSortMenu by varRemember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = sortType != "Manuel",
                            onClick = { showSortMenu = true },
                            label = { Text("⇅ Sırala: $sortType") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                        )
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            listOf("Manuel", "A-Z", "Z-A", "Öncelik", "Tarih", "Yıldızlı").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        viewModel.sortType.value = type
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Distinct dynamic tags filters
                items(availableTags) { tag ->
                    val isSelected = selectedTags.contains(tag)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val current = selectedTags
                            viewModel.selectedTags.value = if (isSelected) current - tag else current + tag
                        },
                        label = { Text("#$tag") }
                    )
                }
            }

            // Items List
            val showEmptyToday = isWorkout && selectedWorkoutTab == 0 && displayItems.isEmpty() && localItems.isNotEmpty()

            if (localItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Burada gösterilecek öğe yok", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Arama kriterlerinizi sıfırlayabilir veya sağ alttaki '+' butonu ile yeni bir liste ögesi oluşturabilirsiniz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else if (showEmptyToday) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("${selectedDayFilter} Günü İçin Plan Yok", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Bu gün için planlanmış egzersiz bulunmamaktadır. Detaylar ekranından egzersizlerinize bu günü atayabilir veya farklı bir günü filtreleyebilirsiniz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
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
                                reorderEnabled = !isBulkMode,
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
                                reorderEnabled = !isBulkMode,
                                dragModifier = dragMod,
                                onMoveUp = onMoveUp,
                                onMoveDown = onMoveDown
                            )
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
                            Text("GÜNLÜK HEDEF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("$waterAmount / $goal ml", fontWeight = FontWeight.Black, fontSize = 36.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            
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
                                Text(
                                    "${(percent * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    color = if (percent > 0.4f) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }

                    Text("Hızlı Su Ekle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(250 to "bardak_250", 500 to "sise_500", 750 to "surahi_750").forEach { (ml, tag) ->
                            Button(
                                onClick = { viewModel.addWater(ml) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag(tag)
                            ) {
                                Icon(Icons.Default.LocalCafe, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+$ml ml")
                            }
                        }
                    }

                    TextButton(
                        onClick = { viewModel.resetWater() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sıfırla")
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
                            Text("Antrenman Analitiği & Gelişim Grafiği", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))

                            var selectedMetric by varRemember { mutableStateOf("Sets") }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Sets" to "Set Sayıları",
                                    "Weekly" to "Haftalık Tonaj",
                                    "Monthly" to "Aylık Tonaj"
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
                                    "Sets" -> "Son 7 antrenmanda tamamlanan toplam set sayıları"
                                    "Weekly" -> "Haftalık kaldırılan toplam tonaj (kg cinsinden)"
                                    "Monthly" -> "Aylık kaldırılan toplam tonaj (kg cinsinden)"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text("Antrenman Geçmişi Raporları", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Kayıtlı antrenman raporu bulunamadı.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (showQuickAddDialog) {
        var quickTitle by varRemember { mutableStateOf("") }
        var quickPriority by varRemember { mutableStateOf(0) }
        
        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text("Hızlı Görev Ekle", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = quickTitle,
                        onValueChange = { quickTitle = it },
                        label = { Text("Görev Başlığı *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Column {
                        Text("Öncelik Seviyesi", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        val pOpts = notebook?.getPriorityConfig() ?: NotebookEntity.DEFAULT_PRIORITIES
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            pOpts.filter { it.enabled }.forEach { opt ->
                                val pColor = getPriorityColor(opt.level, notebook)
                                FilterChip(
                                    selected = quickPriority == opt.level,
                                    onClick = { quickPriority = opt.level },
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
                                            Text(opt.name)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            showQuickAddDialog = false
                            // Open full Add detail screen with empty draft
                            onNavigateToAddEdit(0L)
                        }
                    ) {
                        Text("Detaylı Ekle")
                    }
                    Button(
                        onClick = {
                            if (quickTitle.isNotBlank()) {
                                viewModel.createListItem(
                                    title = quickTitle,
                                    priority = quickPriority
                                )
                                showQuickAddDialog = false
                            }
                        },
                        enabled = quickTitle.isNotBlank()
                    ) {
                        Text("Ekle")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // Bulk Move Dialog selection
    if (showBulkMoveDialog) {
        AlertDialog(
            onDismissRequest = { showBulkMoveDialog = false },
            title = { Text("Ögeleri Taşı", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seçilen ögelerin taşınacağı defteri seçin:")
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(notebooks) { n ->
                            if (n.id != notebook?.id) {
                                Card(
                                    onClick = {
                                        viewModel.bulkMove(n.id)
                                        showBulkMoveDialog = false
                                        Toast.makeText(context, "Ögeler başarıyla taşındı!", Toast.LENGTH_SHORT).show()
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
                    Text("İptal")
                }
            }
        )
    }

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
        val isWorkout = n.type == "Workout Planner" || n.type == "WorkoutPlanner"
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
                    showReminderDialog = true
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
                    showReminderDialog = true
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

    // Edit Notebook Dialog
    if (showEditNotebookDialog && notebook != null) {
        val currentNb = notebook!!
        NotebookDialog(
            notebook = currentNb,
            onDismiss = { showEditNotebookDialog = false },
            onSave = { name, desc, emoji, color, type, extraCfg ->
                var finalDesc = desc
                if (type == "Workout Planner") {
                    val cfgStr = extraCfg.ifBlank {
                        val cfg = currentNb.getWorkoutScheduleConfig()
                        "frequency=${cfg.frequency}|workoutDays=${cfg.workoutDurationDays}|restDays=${cfg.restDurationDays}|startDate=${cfg.startDate}|weekly=${cfg.weeklyDays.joinToString(",")}|monthly=${cfg.monthlyDays.joinToString(",")}"
                    }
                    finalDesc = "[WORKOUT_CFG]$cfgStr"
                }
                viewModel.updateNotebook(currentNb.copy(
                    name = name,
                    description = finalDesc,
                    emoji = emoji,
                    color = color,
                    type = type,
                    updatedAt = System.currentTimeMillis()
                ))
                showEditNotebookDialog = false
                Toast.makeText(context, "Defter güncellendi!", Toast.LENGTH_SHORT).show()
            }
        )
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
                Toast.makeText(context, "Öncelik seviyeleri güncellendi!", Toast.LENGTH_SHORT).show()
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

@OptIn(ExperimentalFoundationApi::class)
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
    val pName = pOption?.name ?: if (item.priority > 0) "Derece ${item.priority}" else ""
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
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Yukarı Taşı",
                                modifier = Modifier.size(20.dp),
                                tint = if (onMoveUp != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Sürükle",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            modifier = dragModifier
                                .size(24.dp)
                                .padding(vertical = 2.dp)
                        )

                        IconButton(
                            onClick = { onMoveDown?.invoke() },
                            enabled = onMoveDown != null,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Aşağı Taşı",
                                modifier = Modifier.size(20.dp),
                                tint = if (onMoveDown != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Checkbox status toggler
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleComplete()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Tamamla",
                        tint = if (item.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = priorityColor
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
                                    fontSize = 10.sp,
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
                                Text(item.recurrenceRule, fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Category chip
                        if (item.category.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(item.category, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
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
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (item.starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favori",
                        tint = if (item.starred) Color(0xFFFFD600) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
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
    val pName = pOption?.name ?: if (item.priority > 0) "Derece ${item.priority}" else ""
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
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Yukarı Taşı",
                            modifier = Modifier.size(20.dp),
                            tint = if (onMoveUp != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Sürükle",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = dragModifier
                            .size(24.dp)
                            .padding(vertical = 2.dp)
                    )

                    IconButton(
                        onClick = { onMoveDown?.invoke() },
                        enabled = onMoveDown != null,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Aşağı Taşı",
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
                    contentDescription = "Tamamla",
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
                        color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
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
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = priorityColor
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
                                    text = muscle.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = muscleColor
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
                    ParameterPill(label = "${cfg.setsCount} Set", color = MaterialTheme.colorScheme.primary)
                    
                    // Reps/Time pill
                    val goalText = if (cfg.type == "Reps") "${cfg.targetReps} Tekrar" else "${cfg.targetTimeSeconds} sn"
                    ParameterPill(label = goalText, color = MaterialTheme.colorScheme.secondary)

                    // Weight pill
                    if (cfg.weightKg > 0) {
                        ParameterPill(label = "${cfg.weightKg} kg", color = MaterialTheme.colorScheme.tertiary)
                    }

                    // Rest pill
                    ParameterPill(label = "⏱️ ${cfg.restTimeSeconds}s Mola", color = MaterialTheme.colorScheme.outline)
                }
            }

            // Star toggler on the far right
            IconButton(onClick = onToggleStar, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (item.starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Yıldızla",
                    tint = if (item.starred) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
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
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
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
                .fillMaxWidth()
                .padding(vertical = 12.dp),
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
                            text = "Defter Analizi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
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
                                    text = "🎯 Tamamlama Oranı",
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
                                    StatMetricChip(label = "Tamamlanan", value = "$completedCount", color = MaterialTheme.colorScheme.primary)
                                    StatMetricChip(label = "Aktif", value = "$activeCount", color = MaterialTheme.colorScheme.secondary)
                                    StatMetricChip(label = "Yıldızlı", value = "$starredCount", color = Color(0xFFFFB300))
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
                                    text = "🎨 Öncelik & Renk Dağılımı",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                if (totalCount == 0) {
                                    Text(
                                        text = "Henüz veri yok.",
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
