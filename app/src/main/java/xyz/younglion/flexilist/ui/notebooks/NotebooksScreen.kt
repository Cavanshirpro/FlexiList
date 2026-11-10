package xyz.younglion.flexilist.ui.notebooks

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import xyz.younglion.flexilist.util.bounceClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import xyz.younglion.flexilist.data.backup.NotebookShareManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.data.model.WorkoutScheduleConfig
import androidx.compose.ui.res.stringResource
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.dialogs.ListReminderDialog
import xyz.younglion.flexilist.ui.dialogs.NotebookStatisticsDialog
import xyz.younglion.flexilist.ui.dialogs.PriorityCustomizationDialog
import xyz.younglion.flexilist.ui.dialogs.StandardListSettingsDialog
import xyz.younglion.flexilist.ui.dialogs.ThemeCustomizationDialog
import xyz.younglion.flexilist.ui.dialogs.WorkoutPlannerSettingsDialog
import androidx.compose.ui.window.Dialog
import xyz.younglion.flexilist.ui.theme.NotebookColors
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotebooksScreen(
    viewModel: FlexiViewModel,
    onNavigateToItems: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notebooks by viewModel.notebooks.collectAsState()
    val availableNotebookCategories by viewModel.availableNotebookCategories.collectAsState()
    val selectedNotebookCategory by viewModel.selectedNotebookCategory.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isOnline = xyz.younglion.flexilist.util.rememberIsOnline()
    
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var notebookToEditId by rememberSaveable { mutableStateOf<Long?>(null) }
    val notebookToEdit = remember(notebookToEditId, notebooks) {
        notebookToEditId?.let { id -> notebooks.find { it.id == id } }
    }
    var notebookForReminderId by rememberSaveable { mutableStateOf<Long?>(null) }
    val notebookForReminder = remember(notebookForReminderId, notebooks) {
        notebookForReminderId?.let { id -> notebooks.find { it.id == id } }
    }
    var searchNotebookQuery by rememberSaveable { mutableStateOf("") }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showCloudSyncDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageMenu by rememberSaveable { mutableStateOf(false) }
    var notebookForPriorityCustomizationId by rememberSaveable { mutableStateOf<Long?>(null) }
    val notebookForPriorityCustomization = remember(notebookForPriorityCustomizationId, notebooks) {
        notebookForPriorityCustomizationId?.let { id -> notebooks.find { it.id == id } }
    }
    var notebookForStatsId by rememberSaveable { mutableStateOf<Long?>(null) }
    val notebookForStats = remember(notebookForStatsId, notebooks) {
        notebookForStatsId?.let { id -> notebooks.find { it.id == id } }
    }

    // Activity result launchers for custom file picker backup export/import
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
            if (outputStream != null) {
                viewModel.exportBackupJson(
                    outputStream = outputStream,
                    onSuccess = { Toast.makeText(context, "Yedek başarıyla dışa aktarıldı!", Toast.LENGTH_SHORT).show() },
                    onError = { Toast.makeText(context, "Dışa aktarma başarısız oldu.", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream: InputStream? = context.contentResolver.openInputStream(it)
            if (inputStream != null) {
                viewModel.importBackupJson(
                    inputStream = inputStream,
                    onSuccess = { Toast.makeText(context, "Yedek başarıyla içe aktarıldı!", Toast.LENGTH_SHORT).show() },
                    onError = { Toast.makeText(context, "İçe aktarma başarısız oldu. Dosyayı kontrol edin.", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
            if (outputStream != null) {
                viewModel.exportBackupCsv(
                    outputStream = outputStream,
                    onSuccess = { Toast.makeText(context, "CSV başarıyla dışa aktarıldı!", Toast.LENGTH_SHORT).show() },
                    onError = { Toast.makeText(context, "CSV dışa aktarma başarısız oldu.", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FlexiList",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 26.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF4CAF50), CircleShape) // Green dot
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "OFFLINE SECURED",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Language Selector Dropdown Button
                    Box {
                        IconButton(
                            onClick = { showLanguageMenu = !showLanguageMenu },
                            modifier = Modifier.testTag("language_selector_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = stringResource(R.string.language)
                            )
                        }

                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lang_en)) },
                                onClick = {
                                    viewModel.setAppLanguage("en")
                                    showLanguageMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lang_tr)) },
                                onClick = {
                                    viewModel.setAppLanguage("tr")
                                    showLanguageMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lang_ru)) },
                                onClick = {
                                    viewModel.setAppLanguage("ru")
                                    showLanguageMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lang_de)) },
                                onClick = {
                                    viewModel.setAppLanguage("de")
                                    showLanguageMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lang_es)) },
                                onClick = {
                                    viewModel.setAppLanguage("es")
                                    showLanguageMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lang_fr)) },
                                onClick = {
                                    viewModel.setAppLanguage("fr")
                                    showLanguageMenu = false
                                }
                            )
                        }
                    }

                    // Theme Customization Palette Button
                    IconButton(
                        onClick = { showThemeDialog = true },
                        modifier = Modifier.testTag("theme_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = stringResource(R.string.theme_and_language)
                        )
                    }

                    // Theme Mode Toggler
                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = stringResource(R.string.appearance_mode)
                        )
                    }

                    // Import/Export Dropdown Trigger
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.settings))
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_backup_json)) },
                            leadingIcon = { Icon(Icons.Default.Upload, null) },
                            onClick = {
                                showMenu = false
                                importJsonLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_backup_json)) },
                            leadingIcon = { Icon(Icons.Default.Download, null) },
                            onClick = {
                                showMenu = false
                                exportJsonLauncher.launch("flexilist_backup_${System.currentTimeMillis()}.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cloud_backup_sync)) },
                            leadingIcon = { Icon(Icons.Default.Cloud, null) },
                            onClick = {
                                showMenu = false
                                showCloudSyncDialog = true
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_csv)) },
                            leadingIcon = { Icon(Icons.Default.TableChart, null) },
                            onClick = {
                                showMenu = false
                                exportCsvLauncher.launch("flexilist_export_${System.currentTimeMillis()}.csv")
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .testTag("add_notebook_button")
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_notebook), modifier = Modifier.size(28.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search notebooks field
            OutlinedTextField(
                value = searchNotebookQuery,
                onValueChange = { searchNotebookQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("notebook_search_input"),
                placeholder = { Text(stringResource(R.string.search_notebooks_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchNotebookQuery.isNotEmpty()) {
                        IconButton(onClick = { searchNotebookQuery = "" }) {
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

            // Performance / Stats bar under search (mapping 1:1 to the HTML stats bar)
            val outlineColor = MaterialTheme.colorScheme.outline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .drawBehind {
                        drawLine(
                            color = outlineColor,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = outlineColor,
                            start = androidx.compose.ui.geometry.Offset(0f, size.height),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column {
                    Text(stringResource(R.string.db_status), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                    if (isOnline) {
                        Text(stringResource(R.string.online), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF4CAF50))
                    } else {
                        Text(stringResource(R.string.offline), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFFFF9800))
                    }
                }
                Column {
                    Text(stringResource(R.string.list_notebooks), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                    Text("${notebooks.size} ${stringResource(R.string.active)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Column {
                    Text(stringResource(R.string.speed), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                    Text("0.02s", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF4CAF50))
                }
            }

            // Category Filter Chips for Notebooks
            val categoriesForFilter = remember(availableNotebookCategories) {
                listOf(
                    "Work" to "Work",
                    "Personal" to "Personal",
                    "Groceries" to "Groceries"
                ).map { it.first } + availableNotebookCategories.filter { it !in listOf("Work", "Personal", "Groceries") }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedNotebookCategory == null,
                        onClick = { viewModel.selectedNotebookCategory.value = null },
                        label = { Text(stringResource(R.string.all_categories), fontSize = 12.sp, fontWeight = if (selectedNotebookCategory == null) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = if (selectedNotebookCategory == null) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.testTag("filter_category_all")
                    )
                }

                items(categoriesForFilter) { cat ->
                    val isSelected = selectedNotebookCategory == cat
                    val chipEmoji = when (cat.lowercase()) {
                        "work", "iş" -> "💼"
                        "personal", "kişisel" -> "👤"
                        "groceries", "market", "alışveriş" -> "🛒"
                        "fitness", "spor" -> "🏋️"
                        "study", "ders" -> "📚"
                        else -> "🏷️"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.selectedNotebookCategory.value = if (isSelected) null else cat
                        },
                        label = {
                            Text(
                                text = "$chipEmoji $cat",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.testTag("filter_category_${cat.lowercase()}")
                    )
                }
            }

            val filteredNotebooks = notebooks.filter { nb ->
                val matchesQuery = searchNotebookQuery.isBlank() ||
                    nb.name.contains(searchNotebookQuery, ignoreCase = true) ||
                    nb.description.contains(searchNotebookQuery, ignoreCase = true) ||
                    nb.category.contains(searchNotebookQuery, ignoreCase = true)
                val matchesCategory = selectedNotebookCategory == null || nb.category.equals(selectedNotebookCategory, ignoreCase = true)
                matchesQuery && matchesCategory
            }

            if (filteredNotebooks.isEmpty()) {
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
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchNotebookQuery.isNotEmpty()) stringResource(R.string.no_notebooks_found) else stringResource(R.string.no_notebooks_yet),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchNotebookQuery.isNotEmpty()) stringResource(R.string.no_notebooks_found_sub) else stringResource(R.string.no_notebooks_yet_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.active_notebooks_header),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${filteredNotebooks.size} ${stringResource(R.string.total_suffix)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredNotebooks, key = { it.id }) { notebook ->
                        NotebookCard(
                            notebook = notebook,
                            viewModel = viewModel,
                            onClick = {
                                viewModel.selectNotebook(notebook.id)
                                onNavigateToItems(notebook.id)
                            },
                            onLongClick = {
                                notebookToEditId = notebook.id
                            },
                            onTogglePin = {
                                viewModel.updateNotebook(notebook.copy(pinned = !notebook.pinned))
                            },
                            onToggleFavorite = {
                                viewModel.updateNotebook(notebook.copy(favorite = !notebook.favorite))
                            },
                            onOpenReminder = {
                                notebookForReminderId = notebook.id
                            },
                            onShare = {
                                coroutineScope.launch {
                                    val itemsList = viewModel.getItemsForNotebook(notebook.id).first()
                                    NotebookShareManager.shareNotebook(context, notebook, itemsList)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Notebook Dialog
    if (showAddDialog) {
        NotebookDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, desc, emoji, color, type, priorityCfg, category ->
                viewModel.createNotebook(name, desc, emoji, color, type, priorityCfg, category)
                showAddDialog = false
            }
        )
    }

    // Cloud Sync Dialog
    if (showCloudSyncDialog) {
        CloudSyncDialog(
            viewModel = viewModel,
            onDismiss = { showCloudSyncDialog = false }
        )
    }

    // Edit/Action Sheet Notebook Dialog
    notebookToEdit?.let { notebook ->
        var showDetailsDialog by varRemember { mutableStateOf(false) }
        
        if (showDetailsDialog) {
            NotebookDialog(
                notebook = notebook,
                onDismiss = {
                    showDetailsDialog = false
                    notebookToEditId = null
                },
                onSave = { name, desc, emoji, color, type, priorityCfg, category ->
                    viewModel.updateNotebook(
                        notebook.copy(
                            name = name,
                            description = desc,
                            emoji = emoji,
                            color = color,
                            type = type,
                            category = category,
                            priorityConfigJson = priorityCfg,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    showDetailsDialog = false
                    notebookToEditId = null
                }
            )
        } else {
            val isWorkout = notebook.type == "Workout Planner" || notebook.type == "WorkoutPlanner"
            if (isWorkout) {
                WorkoutPlannerSettingsDialog(
                    notebook = notebook,
                    viewModel = viewModel,
                    onDismiss = { notebookToEditId = null },
                    onEditWorkout = { showDetailsDialog = true },
                    onOpenReminder = {
                        val targetId = notebook.id
                        notebookToEditId = null
                        notebookForReminderId = targetId
                    },
                    onOpenStats = {
                        val targetId = notebook.id
                        notebookToEditId = null
                        notebookForStatsId = targetId
                    }
                )
            } else {
                StandardListSettingsDialog(
                    notebook = notebook,
                    viewModel = viewModel,
                    onDismiss = { notebookToEditId = null },
                    onEditNotebook = { showDetailsDialog = true },
                    onOpenReminder = {
                        val targetId = notebook.id
                        notebookToEditId = null
                        notebookForReminderId = targetId
                    },
                    onOpenPriorityCustomization = {
                        val targetId = notebook.id
                        notebookToEditId = null
                        notebookForPriorityCustomizationId = targetId
                    },
                    onOpenStats = {
                        val targetId = notebook.id
                        notebookToEditId = null
                        notebookForStatsId = targetId
                    }
                )
            }
        }
    }

    // Theme Customization Dialog
    if (showThemeDialog) {
        ThemeCustomizationDialog(
            viewModel = viewModel,
            onDismiss = { showThemeDialog = false }
        )
    }

    // List/Workout Reminder Dialog
    notebookForReminder?.let { targetNotebook ->
        ListReminderDialog(
            notebook = targetNotebook,
            viewModel = viewModel,
            onDismiss = { notebookForReminderId = null }
        )
    }

    // Priority Customization Dialog
    notebookForPriorityCustomization?.let { targetNotebook ->
        PriorityCustomizationDialog(
            initialOptions = targetNotebook.getPriorityConfig(),
            onDismiss = { notebookForPriorityCustomizationId = null },
            onSave = { updatedList ->
                val priorityCfgJson = NotebookEntity.serializePriorityConfig(updatedList)
                viewModel.updateNotebook(targetNotebook.copy(priorityConfigJson = priorityCfgJson))
                notebookForPriorityCustomizationId = null
                Toast.makeText(context, "Öncelik seviyeleri güncellendi!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Notebook Statistics Dialog
    notebookForStats?.let { targetNotebook ->
        NotebookStatisticsDialog(
            notebook = targetNotebook,
            viewModel = viewModel,
            onDismiss = { notebookForStatsId = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotebookCard(
    notebook: NotebookEntity,
    viewModel: FlexiViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenReminder: () -> Unit,
    onShare: () -> Unit
) {
    val coverColor = NotebookColors.getOrElse(notebook.color) { NotebookColors[0] }
    val reminderTime = viewModel.getNotebookReminderTime(notebook.id)
    val cleanDescription = remember(notebook.description, notebook.type) { notebook.getCleanDescription() }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.98f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.5.dp, coverColor.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left Cover Spine Bar (Vibrant vertical accent)
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(coverColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Emoji Icon Box
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(coverColor.copy(alpha = 0.18f), CircleShape)
                        .border(1.dp, coverColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(notebook.emoji, fontSize = 26.sp)
                }

                // Middle Text Content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = notebook.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Type Badge
                        Box(
                            modifier = Modifier
                                .background(coverColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(0.5.dp, coverColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (notebook.type == "Workout") "Workout Planner" else notebook.type,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = coverColor
                            )
                        }

                        // Category Label Badge if assigned
                        if (notebook.category.isNotBlank()) {
                            val catEmoji = when (notebook.category.lowercase()) {
                                "work", "iş" -> "💼"
                                "personal", "kişisel" -> "👤"
                                "groceries", "market", "alışveriş" -> "🛒"
                                "fitness", "spor" -> "🏋️"
                                "study", "ders" -> "📚"
                                else -> "🏷️"
                            }
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                    .border(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$catEmoji ${notebook.category}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Text(
                        text = cleanDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val createdDateStr = remember(notebook.createdAt) {
                        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.forLanguageTag("tr")).format(java.util.Date(notebook.createdAt))
                    }

                    Text(
                        text = createdDateStr,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Right Quick Action Buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (notebook.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.favorite),
                                tint = if (notebook.favorite) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (notebook.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(R.string.pin),
                                tint = if (notebook.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenReminder,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (reminderTime != null) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                                contentDescription = stringResource(R.string.reminder),
                                tint = if (reminderTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Paylaş (.flexi)",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NotebookDialog(
    notebook: NotebookEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, String, String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(notebook?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(notebook?.description ?: "") }
    var category by rememberSaveable { mutableStateOf(notebook?.category ?: "") }
    var emoji by rememberSaveable { mutableStateOf(notebook?.emoji ?: "📝") }
    var selectedColorIndex by rememberSaveable { mutableStateOf(notebook?.color ?: 0) }
    var selectedType by rememberSaveable { mutableStateOf(notebook?.type ?: "Normal") }

    val initialPriorityOptions = notebook?.getPriorityConfig() ?: NotebookEntity.DEFAULT_PRIORITIES
    var priorityOptions by remember { mutableStateOf(initialPriorityOptions) }
    var showPriorityCustomizationDialog by rememberSaveable { mutableStateOf(false) }

    val initialCfg = if (notebook?.description?.startsWith("[WORKOUT_CFG]") == true) {
        val clean = notebook.description.removePrefix("[WORKOUT_CFG]")
        val parts = clean.split("|").associate {
            val s = it.split("=")
            if (s.size >= 2) s[0] to s[1] else s[0] to ""
        }
        val freq = parts["frequency"] ?: "Weekly"
        val wd = parts["workoutDays"]?.toIntOrNull() ?: 3
        val rd = parts["restDays"]?.toIntOrNull() ?: 2
        val start = parts["startDate"]?.toLongOrNull() ?: System.currentTimeMillis()
        val weekly = parts["weekly"]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: listOf(1, 3, 5)
        val monthly = parts["monthly"]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: listOf(1, 15)
        WorkoutScheduleConfig(freq, wd, rd, start, weekly, monthly)
    } else {
        WorkoutScheduleConfig()
    }

    var frequency by rememberSaveable { mutableStateOf(initialCfg.frequency) }
    var consecutiveWorkoutDays by rememberSaveable { mutableStateOf(initialCfg.workoutDurationDays) }
    var consecutiveRestDays by rememberSaveable { mutableStateOf(initialCfg.restDurationDays) }
    var weeklyDaysList by rememberSaveable { mutableStateOf(ArrayList(initialCfg.weeklyDays)) }
    val weeklyDays = weeklyDaysList.toSet()
    var monthlyDaysList by rememberSaveable { mutableStateOf(ArrayList(initialCfg.monthlyDays)) }
    val monthlyDays = monthlyDaysList.toSet()

    val notebookTypes = listOf("Normal", "ToDo", "Checklist", "Habit Tracker", "Workout Planner", "Reading List", "Collection")
    val defaultEmojis = listOf("📝", "📚", "🎬", "🏋", "🛒", "💼", "🏠", "🎮", "💊", "✈", "🍔", "🎨", "💰", "💡")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 500.dp)
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                .padding(vertical = 8.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (notebook == null) stringResource(R.string.create_notebook) else stringResource(R.string.edit_notebook),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.notebook_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.notebook_description)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Selection & Input (e.g., Work, Personal, Groceries)
                Column {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text(stringResource(R.string.notebook_category_label)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notebook_category_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val quickCategories = listOf("Work", "Personal", "Groceries", "Fitness", "Study")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        quickCategories.forEach { cat ->
                            val isChosen = category.equals(cat, ignoreCase = true)
                            val catEmoji = when (cat.lowercase()) {
                                "work" -> "💼"
                                "personal" -> "👤"
                                "groceries" -> "🛒"
                                "fitness" -> "🏋️"
                                "study" -> "📚"
                                else -> "🏷️"
                            }
                            FilterChip(
                                selected = isChosen,
                                onClick = {
                                    category = if (isChosen) "" else cat
                                },
                                label = { Text("$catEmoji $cat", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Notebook Type Selection
                Column {
                    Text(stringResource(R.string.notebook_type), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        notebookTypes.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                // Priority Customization Button (Only for Non-Workout notebooks)
                if (selectedType != "Workout Planner") {
                    OutlinedButton(
                        onClick = { showPriorityCustomizationDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${stringResource(R.string.customize_priorities)} (${priorityOptions.filter { it.level > 0 }.size})", fontSize = 11.sp)
                    }

                    if (showPriorityCustomizationDialog) {
                        PriorityCustomizationDialog(
                            initialOptions = priorityOptions,
                            onDismiss = { showPriorityCustomizationDialog = false },
                            onSave = { updatedList ->
                                priorityOptions = updatedList
                                showPriorityCustomizationDialog = false
                            }
                        )
                    }
                }

                if (selectedType == "Workout Planner") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.workout_frequency_setting), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Daily" to stringResource(R.string.recurrence_daily), "Weekly" to stringResource(R.string.recurrence_weekly), "Monthly" to stringResource(R.string.recurrence_monthly)).forEach { (key, label) ->
                                    FilterChip(
                                        selected = frequency == key,
                                        onClick = { frequency = key },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            when (frequency) {
                                "Daily" -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = consecutiveWorkoutDays.toString(),
                                            onValueChange = { consecutiveWorkoutDays = it.toIntOrNull() ?: 1 },
                                            label = { Text(stringResource(R.string.workout_day)) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = consecutiveRestDays.toString(),
                                            onValueChange = { consecutiveRestDays = it.toIntOrNull() ?: 0 },
                                            label = { Text(stringResource(R.string.rest_day)) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                "Weekly" -> {
                                    Text(stringResource(R.string.select_days_multiple), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val daysMap = listOf(1 to "Pzt", 2 to "Sal", 3 to "Çar", 4 to "Per", 5 to "Cum", 6 to "Cmt", 7 to "Paz")
                                        daysMap.forEach { (num, name) ->
                                            val isSelected = weeklyDays.contains(num)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        weeklyDaysList = if (isSelected) ArrayList(weeklyDaysList - num) else ArrayList(weeklyDaysList + num)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = name,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                "Monthly" -> {
                                    Text(stringResource(R.string.select_month_days_multiple), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
                                    FlowRow(
                                        maxItemsInEachRow = 7,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        (1..31).forEach { day ->
                                            val isSelected = monthlyDays.contains(day)
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                                    )
                                                    .clickable {
                                                        monthlyDaysList = if (isSelected) ArrayList(monthlyDaysList - day) else ArrayList(monthlyDaysList + day)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    day.toString(),
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Color Cover Picker
                Column {
                    Text(stringResource(R.string.notebook_cover_color), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NotebookColors.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColorIndex = index },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColorIndex == index) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Quick Emoji Picker
                Column {
                    Text(stringResource(R.string.cover_icon_emoji), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        maxItemsInEachRow = 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        defaultEmojis.forEach { itemEmoji ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (emoji == itemEmoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { emoji = itemEmoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(itemEmoji, fontSize = 18.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val finalDesc = if (selectedType == "Workout Planner") {
                                    "[WORKOUT_CFG]frequency=$frequency|workoutDays=$consecutiveWorkoutDays|restDays=$consecutiveRestDays|startDate=${System.currentTimeMillis()}|weekly=${weeklyDays.joinToString(",")}|monthly=${monthlyDays.joinToString(",")}"
                                } else {
                                    description
                                }
                                val priorityCfgJson = NotebookEntity.serializePriorityConfig(priorityOptions)
                                onSave(name, finalDesc, emoji, selectedColorIndex, selectedType, priorityCfgJson, category.trim())
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

// Compact helper to avoid inline Kotlin compiler warnings
@Composable
inline fun <T> varRemember(crossinline calculation: @DisallowComposableCalls () -> T): T = remember(calculation)

@Composable
fun CloudSyncDialog(
    viewModel: FlexiViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var syncCode by rememberSaveable { mutableStateOf("") }
    var isUploading by rememberSaveable { mutableStateOf(false) }
    var isDownloading by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 500.dp)
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                .padding(vertical = 8.dp)
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Cloud Icon
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.cloud_backup_sync),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "JSONBlob Cloud Vault (SSL REST Store)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sync Code Text Field
                OutlinedTextField(
                    value = syncCode,
                    onValueChange = { syncCode = it.trim() },
                    label = { Text(stringResource(R.string.sync_code)) },
                    placeholder = { Text(stringResource(R.string.sync_code_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Row {
                            if (syncCode.isNotBlank()) {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("FlexiList Sync Code", syncCode)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.cloud_sync_copied), Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_code), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = {
                                val chars = ('a'..'f') + ('0'..'9')
                                syncCode = (1..12).map { chars.random() }.joinToString("")
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.generate_code), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Informative explanation box
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cloud_info_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.cloud_info_desc),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                if (isUploading || isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val enterCodeWarn = stringResource(R.string.cloud_enter_code_warning)
                        val downloadingMsg = stringResource(R.string.cloud_downloading)
                        val importedSuccessMsg = stringResource(R.string.cloud_imported_success)
                        val syncSuccessMsg = stringResource(R.string.cloud_sync_success)
                        val uploadingMsg = stringResource(R.string.cloud_uploading)
                        val uploadedSuccessMsg = stringResource(R.string.cloud_uploaded_success)
                        val uploadedToastMsg = stringResource(R.string.cloud_uploaded_toast)

                        OutlinedButton(
                            onClick = {
                                if (syncCode.isBlank()) {
                                    statusMessage = enterCodeWarn
                                    return@OutlinedButton
                                }
                                isDownloading = true
                                statusMessage = downloadingMsg
                                viewModel.downloadBackupFromCloud(
                                    syncCode = syncCode,
                                    onSuccess = {
                                        isDownloading = false
                                        statusMessage = importedSuccessMsg
                                        Toast.makeText(context, syncSuccessMsg, Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    onError = { error ->
                                        isDownloading = false
                                        statusMessage = error
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.restore), fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                isUploading = true
                                statusMessage = uploadingMsg
                                viewModel.uploadBackupToCloud(
                                    syncCode = syncCode,
                                    onSuccess = { newCode ->
                                        isUploading = false
                                        syncCode = newCode
                                        statusMessage = String.format(uploadedSuccessMsg, newCode)
                                        Toast.makeText(context, uploadedToastMsg, Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { error ->
                                        isUploading = false
                                        statusMessage = error
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.backup_to_cloud), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityCustomizationDialog(
    initialOptions: List<NotebookEntity.PriorityOption>,
    onDismiss: () -> Unit,
    onSave: (List<NotebookEntity.PriorityOption>) -> Unit
) {
    var options by varRemember { mutableStateOf(initialOptions) }

    val presetColors = listOf(
        "#81C784" to "Yeşil",
        "#FFB74D" to "Turuncu",
        "#E57373" to "Kırmızı",
        "#64B5F6" to "Mavi",
        "#BA68C8" to "Mor",
        "#4DD0E1" to "Turkuaz",
        "#F06292" to "Pembe",
        "#FFD54F" to "Sarı",
        "#A1887F" to "Kahve"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(4.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(18.dp)
                    .fillMaxSize()
            ) {
                // Title Header
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
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Öncelik Seviyeleri",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Text(
                    text = stringResource(R.string.customize_priorities_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                HorizontalDivider()

                // List of options
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    options.forEachIndexed { index, opt ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (opt.level == 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "0: ${opt.name} (Derecesiz / Renksiz)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    // Header with Level Badge, Color preview, and Delete
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val parsedColor = try {
                                                Color(android.graphics.Color.parseColor(opt.colorHex))
                                            } catch (e: Exception) {
                                                MaterialTheme.colorScheme.primary
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(parsedColor)
                                            )
                                            Text(
                                                text = "Derece ${opt.level}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                options = options.filterIndexed { i, _ -> i != index }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Sil",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Name Field
                                    OutlinedTextField(
                                        value = opt.name,
                                        onValueChange = { newName ->
                                            options = options.toMutableList().also { list ->
                                                list[index] = opt.copy(name = newName)
                                            }
                                        },
                                        label = { Text("Derece İsmi") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Color Selector Chips
                                    Text(
                                        text = "Renk Seçimi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        presetColors.forEach { (hex, _) ->
                                            val pColor = try {
                                                Color(android.graphics.Color.parseColor(hex))
                                            } catch (e: Exception) {
                                                Color.Gray
                                            }
                                            val isSelected = opt.colorHex.equals(hex, ignoreCase = true)
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(pColor)
                                                    .border(
                                                        width = if (isSelected) 2.dp else 0.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        options = options.toMutableList().also { list ->
                                                            list[index] = opt.copy(colorHex = hex)
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Button to Add New Priority Level
                    OutlinedButton(
                        onClick = {
                            val maxLvl = options.maxOfOrNull { it.level } ?: 0
                            val nextLvl = maxLvl + 1
                            val defaultColor = presetColors.getOrNull((nextLvl - 1) % presetColors.size)?.first ?: "#64B5F6"
                            options = options + NotebookEntity.PriorityOption(
                                level = nextLvl,
                                name = "Derece $nextLvl",
                                colorHex = defaultColor,
                                enabled = true
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Yeni Öncelik Derecesi Ekle")
                    }
                }

                HorizontalDivider()

                // Footer Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            options = NotebookEntity.DEFAULT_PRIORITIES
                        }
                    ) {
                        Text("Sıfırla", color = MaterialTheme.colorScheme.outline)
                    }

                    Button(
                        onClick = {
                            onSave(options)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bitti")
                    }
                }
            }
        }
    }
}
