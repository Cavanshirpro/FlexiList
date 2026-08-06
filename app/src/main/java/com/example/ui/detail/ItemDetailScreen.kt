package com.example.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.data.model.NotebookEntity

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
import com.example.data.model.ListItemEntity
import com.example.ui.FlexiViewModel
import com.example.ui.notebooks.varRemember
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemDetailScreen(
    itemId: Long,
    viewModel: FlexiViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Retrieve existing item or start from a new draft (with explicit type annotation to avoid Any? inference)
    val existingItemFlow: kotlinx.coroutines.flow.Flow<ListItemEntity?> = remember(itemId) {
        if (itemId > 0L) viewModel.getItemById(itemId) else kotlinx.coroutines.flow.flowOf(null)
    }
    val existingItem by existingItemFlow.collectAsState(initial = null)

    var itemTitle by varRemember { mutableStateOf("") }
    var itemDescription by varRemember { mutableStateOf("") }
    var priority by varRemember { mutableStateOf(0) }
    var tagsInput by varRemember { mutableStateOf("") } // comma-separated
    var itemCategory by varRemember { mutableStateOf("") }
    var starred by varRemember { mutableStateOf(false) }

    // Date/Time triggers
    var dueDate by varRemember { mutableStateOf<Long?>(null) }
    var reminderTime by varRemember { mutableStateOf<Long?>(null) }
    var isRecurring by varRemember { mutableStateOf(false) }
    var recurrenceRule by varRemember { mutableStateOf<String?>(null) }

    // Structured subtasks and custom fields
    var subtasks by varRemember { mutableStateOf<List<ListItemEntity.Subtask>>(emptyList()) }
    var customFields by varRemember { mutableStateOf<List<ListItemEntity.CustomField>>(emptyList()) }

    // Workout specific states
    val isWorkoutNotebook = viewModel.selectedNotebook.collectAsState().value?.type.let { it == "Workout Planner" || it == "WorkoutPlanner" }
    var exerciseType by varRemember { mutableStateOf("Reps") }
    var setsCount by varRemember { mutableStateOf(3) }
    var targetReps by varRemember { mutableStateOf(10) }
    var targetTimeSeconds by varRemember { mutableStateOf(30) }
    var restTimeSeconds by varRemember { mutableStateOf(60) }
    var weightKg by varRemember { mutableStateOf(0.0) }
    var targetMuscles by varRemember { mutableStateOf(emptySet<String>()) }
    var supersetGroupId by varRemember { mutableStateOf("") }
    var workoutDays by varRemember { mutableStateOf(emptySet<String>()) }

    // Once existing item loads, initialize the states
    LaunchedEffect(existingItem) {
        existingItem?.let { item ->
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

            if (isWorkoutNotebook && item.description.startsWith("[EXERCISE_CFG]")) {
                val cfg = parseExerciseConfig(item.description)
                exerciseType = cfg.type
                setsCount = cfg.setsCount
                targetReps = cfg.targetReps
                targetTimeSeconds = cfg.targetTimeSeconds
                restTimeSeconds = cfg.restTimeSeconds
                weightKg = cfg.weightKg
                targetMuscles = cfg.targetMuscles.toSet()
                supersetGroupId = cfg.supersetGroupId
                workoutDays = cfg.workoutDays.toSet()
            }
        }
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
                title = { Text(if (itemId > 0L) "Görevi Düzenle" else "Yeni Görev Detayı", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vazgeç")
                    }
                },
                actions = {
                    // Star toggler
                    IconButton(onClick = { starred = !starred }) {
                        Icon(
                            imageVector = if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Yıldızla",
                            tint = if (starred) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Save Button
                    IconButton(
                        onClick = {
                            if (itemTitle.isBlank()) {
                                Toast.makeText(context, "Lütfen bir başlık girin!", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val finalDescription = if (isWorkoutNotebook) {
                                "[EXERCISE_CFG]type=$exerciseType|sets=$setsCount|reps=$targetReps|time=$targetTimeSeconds|rest=$restTimeSeconds|weight=$weightKg|muscles=${targetMuscles.joinToString(",")}|group=$supersetGroupId|days=${workoutDays.joinToString(",")}"
                            } else {
                                itemDescription
                            }
                            if (itemId > 0L) {
                                // Update existing
                                existingItem?.let {
                                    viewModel.updateListItem(
                                        it.copy(
                                            title = itemTitle,
                                            description = finalDescription,
                                            priority = priority,
                                            tags = tagsInput,
                                            category = itemCategory,
                                            starred = starred,
                                            dueDate = dueDate,
                                            reminderTime = reminderTime,
                                            isRecurring = isRecurring,
                                            recurrenceRule = recurrenceRule,
                                            subtasksJson = ListItemEntity.serializeSubtasks(subtasks),
                                            customFieldsJson = ListItemEntity.serializeCustomFields(customFields),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            } else {
                                // Create new
                                viewModel.createListItem(
                                    title = itemTitle,
                                    description = finalDescription,
                                    priority = priority,
                                    tags = tagsInput,
                                    category = itemCategory,
                                    starred = starred,
                                    dueDate = dueDate,
                                    reminderTime = reminderTime,
                                    isRecurring = isRecurring,
                                    recurrenceRule = recurrenceRule,
                                    subtasks = subtasks,
                                    customFields = customFields
                                )
                            }
                            Toast.makeText(context, "Başarıyla kaydedildi!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("save_item_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Kaydet", tint = MaterialTheme.colorScheme.primary)
                    }
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isWorkoutNotebook) {
                // Title Inputs for workout
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = itemTitle,
                            onValueChange = { itemTitle = it },
                            placeholder = { Text("Egzersiz Adı (Örn: Bench Press) *", fontSize = 18.sp) },
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
                            Text("Egzersiz Detayları", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                            // Exercise type
                            Column {
                                Text("Çalışma Tipi", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Reps" to "Tekrarlı", "Timer" to "Süreli (Saniye)").forEach { (key, label) ->
                                        FilterChip(
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
                                OutlinedTextField(
                                    value = setsCount.toString(),
                                    onValueChange = { setsCount = it.toIntOrNull() ?: 1 },
                                    label = { Text("Set Sayısı") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )

                                if (exerciseType == "Reps") {
                                    OutlinedTextField(
                                        value = targetReps.toString(),
                                        onValueChange = { targetReps = it.toIntOrNull() ?: 1 },
                                        label = { Text("Tekrar Hedefi") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = targetTimeSeconds.toString(),
                                        onValueChange = { targetTimeSeconds = it.toIntOrNull() ?: 1 },
                                        label = { Text("Süre (Saniye)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = weightKg.toString(),
                                    onValueChange = { weightKg = it.toDoubleOrNull() ?: 0.0 },
                                    label = { Text("Ağırlık (kg)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = restTimeSeconds.toString(),
                                    onValueChange = { restTimeSeconds = it.toIntOrNull() ?: 1 },
                                    label = { Text("Dinlenme (Saniye)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Superset Group ID (Hibrit Çalışma için)
                            OutlinedTextField(
                                value = supersetGroupId,
                                onValueChange = { supersetGroupId = it },
                                label = { Text("Hibrit Grup ID (Superset için örn. A, B. Boşsa normal)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Target muscles
                            Column {
                                Text("Hedef Kas Grupları", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val musclesList = listOf("Göğüs", "Sırt", "Bacak", "Omuz", "Kollar", "Karın")
                                    Column {
                                        val row1 = musclesList.take(3)
                                        val row2 = musclesList.drop(3)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            row1.forEach { muscle ->
                                                val isSel = targetMuscles.contains(muscle)
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        targetMuscles = if (isSel) targetMuscles - muscle else targetMuscles + muscle
                                                    },
                                                    label = { Text(muscle, fontSize = 10.sp) }
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            row2.forEach { muscle ->
                                                val isSel = targetMuscles.contains(muscle)
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        targetMuscles = if (isSel) targetMuscles - muscle else targetMuscles + muscle
                                                    },
                                                    label = { Text(muscle, fontSize = 10.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Workout Days Selection
                            Column {
                                Text("Antrenman Günleri (Seçilirse filtrelerde görünür)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val daysMap = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
                                    daysMap.forEach { dayName ->
                                        val isSel = workoutDays.contains(dayName)
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
                                                    workoutDays = if (isSel) workoutDays - dayName else workoutDays + dayName
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayName,
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
                        OutlinedTextField(
                            value = itemTitle,
                            onValueChange = { itemTitle = it },
                            placeholder = { Text("Görev/Liste Ögesi Başlığı *", fontSize = 18.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("item_title_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )

                        OutlinedTextField(
                            value = itemDescription,
                            onValueChange = { itemDescription = it },
                            placeholder = { Text("Detaylı notlar veya açıklama ekleyin...") },
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

            // Priority Selection
            item {
                Column {
                    Text("Öncelik Derecesi", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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

                            FilterChip(
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
                                        Text(opt.name)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Category & Tags Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = itemCategory,
                        onValueChange = { itemCategory = it },
                        label = { Text("Kategori") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { tagsInput = it },
                        label = { Text("Etiketler (virgülle ayırın)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
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
                        Text("Zamanlama & Hatırlatıcı", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        
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
                                    text = if (dueDate == null) "Bitiş Tarihi" else SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(dueDate!!)),
                                    fontSize = 12.sp
                                )
                            }

                            // Reminder alarm trigger
                            Button(
                                onClick = { reminderDatePickerDialog.show() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (reminderTime == null) "Hatırlatıcı Kur" else SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(reminderTime!!)),
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
                                    Text("Zamanları Sıfırla", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
                        Text("Tekrarlayan Görev Aktif", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    if (isRecurring) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("DAILY" to "Günlük", "WEEKDAYS" to "Haftaiçi", "WEEKLY" to "Haftalık", "MONTHLY" to "Aylık").forEach { (rule, label) ->
                                FilterChip(
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
                        Text("Alt Görevler (${subtasks.filter { it.completed }.size}/${subtasks.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)

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
                            OutlinedTextField(
                                value = newSubtaskTitle,
                                onValueChange = { newSubtaskTitle = it },
                                placeholder = { Text("Alt görev başlığı...") },
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
                                    .size(40.dp)
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
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
                        Text("Özel Dinamik Alanlar", fontWeight = FontWeight.Bold, fontSize = 14.sp)

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
                                            Switch(
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
                                            OutlinedTextField(
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
                            OutlinedTextField(
                                value = newFieldName,
                                onValueChange = { newFieldName = it },
                                placeholder = { Text("Alan İsmi...") },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f)
                            )

                            // Type dropdown selection chip
                            Box(modifier = Modifier.weight(1f)) {
                                FilterChip(
                                    selected = true,
                                    onClick = { isTypeExpanded = !isTypeExpanded },
                                    label = { Text(newFieldType) },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                                )
                                DropdownMenu(expanded = isTypeExpanded, onDismissRequest = { isTypeExpanded = false }) {
                                    listOf("Text", "Number", "Boolean", "Date", "Dropdown", "Rating", "URL").forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text(t) },
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
                                    .size(40.dp)
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
                            }
                        }
                    }
                }
            }
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
            val s = it.split("=")
            if (s.size >= 2) s[0] to s[1] else s[0] to ""
        }
        val type = parts["type"] ?: "Reps"
        val sets = parts["sets"]?.toIntOrNull() ?: 3
        val reps = parts["reps"]?.toIntOrNull() ?: 10
        val time = parts["time"]?.toIntOrNull() ?: 30
        val rest = parts["rest"]?.toIntOrNull() ?: 60
        val weight = parts["weight"]?.toDoubleOrNull() ?: 0.0
        val muscles = parts["muscles"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val group = parts["group"] ?: ""
        val days = parts["days"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        ExerciseConfig(type, sets, reps, time, rest, weight, muscles, group, days)
    } catch (e: Exception) {
        ExerciseConfig()
    }
}

