package xyz.younglion.flexilist.ui.workspace

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import xyz.younglion.flexilist.R

/** Request only after an explicit reminder action; denial never blocks ordinary notebook use. */
@Composable
fun rememberReminderPermissionAction(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var explain by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted) action?.invoke() else explain = true
    }
    if (explain) AlertDialog(
        onDismissRequest = { explain = false }, text = { Text(stringResource(R.string.ux_permission_needed)) },
        confirmButton = { TextButton(onClick = {
            explain = false
            val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            val settings = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) else details
            try { context.startActivity(settings) }
            catch (_: ActivityNotFoundException) { context.startActivity(details) }
        }) { Text(stringResource(R.string.ux_notification_settings)) } },
        dismissButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.cancel)) } }
    )
    return { action ->
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) action()
        else if (Build.VERSION.SDK_INT >= 33) { pending = action; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else explain = true
    }
}
