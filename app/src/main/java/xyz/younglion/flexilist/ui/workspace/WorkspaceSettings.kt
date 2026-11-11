package xyz.younglion.flexilist.ui.workspace

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.younglion.flexilist.BuildConfig
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.dialogs.ThemeCustomizationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSettings(viewModel: FlexiViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appearance by rememberSaveable { mutableStateOf(false) }
    var cloud by rememberSaveable { mutableStateOf(false) }
    fun notice(id: Int) { scope.launch { viewModel.workspace.notices.emit(id) } }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            val stream = requireNotNull(context.contentResolver.openOutputStream(uri))
            viewModel.exportBackupJson(stream, { notice(R.string.ux_export_success) }, { notice(R.string.ux_export_failed) })
        }.onFailure { notice(R.string.ux_export_failed) }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            val stream = requireNotNull(context.contentResolver.openOutputStream(uri))
            viewModel.exportBackupCsv(stream, { notice(R.string.ux_export_success) }, { notice(R.string.ux_export_failed) })
        }.onFailure { notice(R.string.ux_export_failed) }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel.workspace::prepareImport) }
    if (cloud) {
        CloudBackupSettings(viewModel, onBack = { cloud = false })
        return
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.theme_and_language), style = MaterialTheme.typography.titleMedium)
            SettingsButton(R.string.theme_and_language, Icons.Default.Palette) { appearance = true }
            HorizontalDivider()
            Text(stringResource(R.string.ux_backups), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ux_local_storage), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsButton(R.string.import_backup_json, Icons.Default.FileOpen) { importFile.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
            SettingsButton(R.string.export_backup_json, Icons.Default.SaveAlt) { exportJson.launch("FlexiList-backup.json") }
            SettingsButton(R.string.export_csv, Icons.Default.TableChart) { exportCsv.launch("FlexiList-export.csv") }
            SettingsButton(R.string.ux_cloud, Icons.Default.Cloud) { cloud = true }
            HorizontalDivider()
            Text(stringResource(R.string.ux_about), style = MaterialTheme.typography.titleMedium)
            Text("FlexiList ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            SettingsButton(R.string.ux_source_code, Icons.Default.Code) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Cavanshirpro/FlexiList"))) }
            SettingsButton(R.string.ux_license, Icons.Default.Description) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Cavanshirpro/FlexiList/blob/main/LICENSE"))) }
        }
    }
    if (appearance) ThemeCustomizationDialog(viewModel, onDismiss = { appearance = false })
}

@Composable
private fun SettingsButton(label: Int, image: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (label == R.string.theme_and_language) Modifier.testTag("appearance_settings") else Modifier), contentPadding = PaddingValues(16.dp)) {
        Icon(image, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(label), Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudBackupSettings(viewModel: FlexiViewModel, onBack: () -> Unit) {
    val savedCode by viewModel.workspace.cloudCode.collectAsState()
    val busy by viewModel.workspace.cloudBusy.collectAsState()
    var code by rememberSaveable { mutableStateOf(savedCode) }
    LaunchedEffect(savedCode) { if (savedCode.isNotBlank()) code = savedCode }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.ux_cloud)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.ux_cloud_notice), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text(stringResource(R.string.ux_backup_code)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            Button(onClick = { viewModel.workspace.uploadCloud(code) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (busy) R.string.ux_working else R.string.backup_to_cloud)) }
            OutlinedButton(onClick = { viewModel.workspace.prepareCloud(code) }, enabled = !busy && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ux_preview_cloud)) }
        }
    }
}

@Composable
fun ImportReview(controller: WorkspaceController) {
    val state by controller.importState.collectAsState()
    if (state == ImportUiState.Idle) return
    val preview = when (val current = state) {
        is ImportUiState.Ready -> current.preview
        is ImportUiState.Importing -> current.preview
        else -> null
    }
    val busy = state is ImportUiState.Preparing || state is ImportUiState.Importing
    AlertDialog(
        onDismissRequest = { if (!busy) controller.dismissImport() },
        title = { Text(stringResource(R.string.ux_import_preview)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) { CircularProgressIndicator(); Text(stringResource(R.string.ux_working)) }
                if (state is ImportUiState.Failed) Text(stringResource((state as ImportUiState.Failed).message), color = MaterialTheme.colorScheme.error)
                preview?.let { value ->
                    Text(pluralStringResource(R.plurals.ux_notebooks_count, value.names.size, value.names.size), style = MaterialTheme.typography.titleMedium)
                    value.names.take(8).forEach { Text(it) }
                    if (value.names.size > 8) Text(stringResource(R.string.ux_more_notebooks, value.names.size - 8))
                    Text(pluralStringResource(R.plurals.ux_items_count, value.itemCount, value.itemCount))
                    if (value.logCount > 0) Text(stringResource(R.string.ux_import_logs, value.logCount))
                    if (value.waterCount > 0) Text(stringResource(R.string.ux_import_water, value.waterCount))
                    Text(stringResource(R.string.ux_import_explanation), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            when (state) {
                is ImportUiState.Ready -> TextButton(onClick = controller::confirmImport, modifier = Modifier.testTag("confirm_import")) { Text(stringResource(R.string.ux_import_copies)) }
                is ImportUiState.Failed -> if (controller.canRetryImport) TextButton(onClick = controller::retryImport) { Text(stringResource(R.string.ux_retry)) }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = controller::dismissImport, enabled = !busy, modifier = Modifier.testTag("cancel_import")) { Text(stringResource(R.string.cancel)) } }
    )
}
