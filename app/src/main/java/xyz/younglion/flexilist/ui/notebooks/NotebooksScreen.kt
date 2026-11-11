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
