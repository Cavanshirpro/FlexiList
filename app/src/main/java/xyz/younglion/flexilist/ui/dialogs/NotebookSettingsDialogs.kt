package xyz.younglion.flexilist.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.theme.NotebookColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutPlannerSettingsDialog(
    notebook: NotebookEntity,
    viewModel: FlexiViewModel,
    onDismiss: () -> Unit,
    onEditWorkout: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenStats: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val workoutConfig = remember(notebook.description) { notebook.getWorkoutScheduleConfig() }
    val reminderTime = remember(notebook.id) { viewModel.getNotebookReminderTime(notebook.id) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 500.dp)
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                .padding(vertical = 8.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(notebook.emoji.ifBlank { "🏋️" }, fontSize = 22.sp)
                                }
                            }
                            Column {
                                Text(
                                    text = notebook.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.workout_planner_assistant),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Workout Schedule Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.workout_schedule_frequency),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = when (workoutConfig.frequency) {
                                        "Daily" -> stringResource(R.string.cycle_daily)
                                        "Monthly" -> stringResource(R.string.cycle_monthly)
                                        else -> stringResource(R.string.cycle_weekly)
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }

                        // Detailed Schedule Description
                        when (workoutConfig.frequency) {
                            "Daily" -> {
                                Text(
                                    text = stringResource(R.string.cycle_period_desc, workoutConfig.workoutDurationDays, workoutConfig.restDurationDays),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            "Monthly" -> {
                                Text(
                                    text = stringResource(R.string.selected_month_days, workoutConfig.monthlyDays.sorted().joinToString(", ")),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            else -> {
                                val daysNameMap = mapOf(1 to "Pzt", 2 to "Sal", 3 to "Çar", 4 to "Per", 5 to "Cum", 6 to "Cmt", 7 to "Paz")
                                val activeDaysStr = workoutConfig.weeklyDays.sorted().mapNotNull { daysNameMap[it] }.joinToString(", ")
                                Text(
                                    text = stringResource(R.string.active_days, activeDaysStr.ifEmpty { stringResource(R.string.not_selected) }),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Reminder info if active
                        if (reminderTime != null) {
                            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", reminderTime.first, reminderTime.second)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Text(
                                    text = stringResource(R.string.reminder_time_label, formattedTime),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.workout_operations),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )

                // Display Options Toggles Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.show_completed_exercises), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Switch(
                                checked = notebook.showCompleted,
                                onCheckedChange = { viewModel.updateNotebook(notebook.copy(showCompleted = it)) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.move_completed_to_bottom), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Switch(
                                checked = notebook.moveCompletedToBottom,
                                onCheckedChange = { viewModel.updateNotebook(notebook.copy(moveCompletedToBottom = it)) }
                            )
                        }
                    }
                }

                // Quick Action Buttons Matrix
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Reminder Button
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenReminder()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("workout_settings_reminder_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Alarm, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (reminderTime != null) stringResource(R.string.edit_workout_reminder) else stringResource(R.string.set_workout_reminder),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Edit Workout Program Button
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onEditWorkout()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("workout_settings_edit_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.EditCalendar, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_program_days), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Workout Statistics / Analysis Button
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenStats()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("workout_settings_stats_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PieChart, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.exercise_stats_reports), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Reset Workout Cycle/Days Button
                    OutlinedButton(
                        onClick = {
                            val newConfig = workoutConfig.copy(startDate = System.currentTimeMillis())
                            val newDesc = "[WORKOUT_CFG]frequency=${newConfig.frequency}|workoutDays=${newConfig.workoutDurationDays}|restDays=${newConfig.restDurationDays}|startDate=${newConfig.startDate}|weekly=${newConfig.weeklyDays.joinToString(",")}|monthly=${newConfig.monthlyDays.joinToString(",")}"
                            viewModel.updateNotebook(notebook.copy(description = newDesc, updatedAt = System.currentTimeMillis()))
                            Toast.makeText(context, context.getString(R.string.workout_start_date_reset), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.restart_cycle_today), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Quick Toggle Pin / Favorite
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = notebook.pinned,
                            onClick = {
                                viewModel.updateNotebook(notebook.copy(pinned = !notebook.pinned))
                            },
                            label = { Text(if (notebook.pinned) stringResource(R.string.pinned) else stringResource(R.string.pin), fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = notebook.favorite,
                            onClick = {
                                viewModel.updateNotebook(notebook.copy(favorite = !notebook.favorite))
                            },
                            label = { Text(if (notebook.favorite) stringResource(R.string.favorite_star) else stringResource(R.string.make_favorite), fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                // Delete Section
                if (!showDeleteConfirm) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.delete_workout_program), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.confirm_delete_workout_program),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = {
                                        viewModel.deleteNotebook(notebook)
                                        Toast.makeText(context, context.getString(R.string.workout_schedule_deleted), Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.yes_delete), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StandardListSettingsDialog(
    notebook: NotebookEntity,
    viewModel: FlexiViewModel,
    onDismiss: () -> Unit,
    onEditNotebook: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenPriorityCustomization: () -> Unit,
    onOpenStats: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val coverColor = NotebookColors.getOrElse(notebook.color) { NotebookColors[0] }
    val reminderTime = remember(notebook.id) { viewModel.getNotebookReminderTime(notebook.id) }
    val createdDateStr = remember(notebook.createdAt) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sdf.format(Date(notebook.createdAt))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 500.dp)
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                .padding(vertical = 8.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(notebook.emoji.ifBlank { "📝" }, fontSize = 22.sp)
                            }
                            Column {
                                Text(
                                    text = notebook.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = notebook.type,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "• ${stringResource(R.string.created_date, createdDateStr)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                }

                HorizontalDivider()

                // List Description Card
                if (notebook.description.isNotBlank()) {
                    Text(
                        text = notebook.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Active Reminder Summary Card if set
                if (reminderTime != null) {
                    val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", reminderTime.first, reminderTime.second)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = stringResource(R.string.reminder_time_label, formattedTime),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.notebook_options),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )

                // List Display Options Switches Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.show_completed_items), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(stringResource(R.string.show_completed_items_sub), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = notebook.showCompleted,
                                onCheckedChange = { viewModel.updateNotebook(notebook.copy(showCompleted = it)) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.move_completed_to_bottom), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(stringResource(R.string.move_completed_bottom_sub), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = notebook.moveCompletedToBottom,
                                onCheckedChange = { viewModel.updateNotebook(notebook.copy(moveCompletedToBottom = it)) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.auto_sort), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(stringResource(R.string.auto_sort_sub), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = notebook.autoSort,
                                onCheckedChange = { viewModel.updateNotebook(notebook.copy(autoSort = it)) }
                            )
                        }
                    }
                }

                // Actions Column
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Reminder Setup
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenReminder()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("list_settings_reminder_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (reminderTime != null) stringResource(R.string.change_reminder_time) else stringResource(R.string.set_reminder), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Edit Details
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onEditNotebook()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("list_settings_edit_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_name_cover), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Priority Customization
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenPriorityCustomization()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("list_settings_priority_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.customize_priorities_btn), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Statistics & Analytics
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenStats()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("list_settings_stats_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PieChart, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.notebook_stats), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Pin & Favorite Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = notebook.pinned,
                            onClick = {
                                viewModel.updateNotebook(notebook.copy(pinned = !notebook.pinned))
                            },
                            label = { Text(if (notebook.pinned) stringResource(R.string.pinned) else stringResource(R.string.pin), fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = notebook.favorite,
                            onClick = {
                                viewModel.updateNotebook(notebook.copy(favorite = !notebook.favorite))
                            },
                            label = { Text(if (notebook.favorite) stringResource(R.string.favorite_star) else stringResource(R.string.make_favorite), fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                // Delete Section
                if (!showDeleteConfirm) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.delete_notebook_all), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.confirm_delete_notebook),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                                 Button(
                                    onClick = {
                                        viewModel.deleteNotebook(notebook)
                                        Toast.makeText(context, context.getString(R.string.notebook_deleted), Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.yes_delete), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PriorityCustomizationDialog(
    initialOptions: List<NotebookEntity.PriorityOption>,
    onDismiss: () -> Unit,
    onSave: (List<NotebookEntity.PriorityOption>) -> Unit
) {
    var options by remember { mutableStateOf(initialOptions) }
    var editingLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var editName by rememberSaveable { mutableStateOf("") }
    var editColorHex by rememberSaveable { mutableStateOf("") }

    val defaultPalette = listOf(
        "#81C784", "#66BB6A", "#4CAF50",
        "#FFB74D", "#FFA726", "#FF9800",
        "#E57373", "#EF5350", "#F44336",
        "#64B5F6", "#42A5F5", "#2196F3",
        "#BA68C8", "#AB47BC", "#9C27B0"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 500.dp)
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                .padding(vertical = 8.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.priority_levels_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider()

                options.forEach { option ->
                    val isEditing = editingLevel == option.level
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val chipColor = if (option.level == 0 || option.colorHex.isBlank() || option.colorHex == "#00000000") {
                                        Color.Gray
                                    } else {
                                        try { Color(android.graphics.Color.parseColor(option.colorHex)) } catch (e: Exception) { Color.Gray }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(chipColor)
                                    )
                                    val optionLocalizedName = option.getLocalizedName()
                                    val levelFormat = stringResource(R.string.priority_level_format, option.level)
                                    val unrankedText = stringResource(R.string.priority_unranked)
                                    Text(
                                        text = if (option.level == 0) "$unrankedText ($optionLocalizedName)" else "$optionLocalizedName ($levelFormat)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                if (option.level > 0) {
                                    IconButton(
                                        onClick = {
                                            if (isEditing) {
                                                editingLevel = null
                                            } else {
                                                editingLevel = option.level
                                                editName = option.name
                                                editColorHex = option.colorHex
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(if (isEditing) Icons.Default.Check else Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (isEditing) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text(stringResource(R.string.degree_name)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("Renk Seçimi:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    defaultPalette.forEach { hex ->
                                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .clickable { editColorHex = hex },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (editColorHex.equals(hex, ignoreCase = true)) {
                                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            options = options.map { opt ->
                                                if (opt.level == option.level) {
                                                    opt.copy(name = editName.ifBlank { opt.name }, colorHex = editColorHex.ifBlank { opt.colorHex })
                                                } else opt
                                            }
                                            editingLevel = null
                                        }
                                    ) {
                                        Text("Kaydet", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(options)
                        }
                    ) {
                        Text("Tamam", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun NotebookStatisticsDialog(
    notebook: NotebookEntity,
    viewModel: FlexiViewModel,
    onDismiss: () -> Unit
) {
    val allItems by viewModel.items.collectAsState()
    val notebookItems = remember(allItems, notebook.id) {
        allItems.filter { it.notebookId == notebook.id }
    }

    xyz.younglion.flexilist.ui.items.NotebookStatisticsDialog(
        notebook = notebook,
        items = notebookItems,
        onDismiss = onDismiss
    )
}
