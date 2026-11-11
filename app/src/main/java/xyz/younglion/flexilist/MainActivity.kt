package xyz.younglion.flexilist

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import xyz.younglion.flexilist.data.backup.BackupManager
import xyz.younglion.flexilist.data.db.AppDatabase
import xyz.younglion.flexilist.data.repository.FlexiRepository
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.FlexiViewModelFactory
import xyz.younglion.flexilist.ui.theme.MyApplicationTheme
import xyz.younglion.flexilist.ui.workspace.WorkspaceApp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var mainViewModel: FlexiViewModel? = null
    private var appliedLanguage = "en"

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("flexilist_prefs", Context.MODE_PRIVATE)
        appliedLanguage = prefs.getString("app_language", "en")?.takeIf { it in setOf("en", "tr", "ru", "de", "es", "fr") } ?: "en"
        val locale = Locale.forLanguageTag(appliedLanguage)
        Locale.setDefault(locale)
        val configuration = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = FlexiRepository(AppDatabase.getDatabase(applicationContext))
        val factory = FlexiViewModelFactory(application, repository, BackupManager(repository))
        val viewModel = ViewModelProvider(this, factory)[FlexiViewModel::class.java]
        mainViewModel = viewModel
        if (savedInstanceState == null) handleIncomingIntent(intent)
        enableEdgeToEdge()
        setContent {
            val mode by viewModel.themeMode.collectAsState()
            val accent by viewModel.selectedAccentIndex.collectAsState()
            val language by viewModel.appLanguage.collectAsState()
            LaunchedEffect(language) { if (language != appliedLanguage) recreate() }
            val dark = mode.isDark(isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            MyApplicationTheme(darkTheme = dark, accentIndex = accent) { WorkspaceApp(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        if (incoming?.action == Intent.ACTION_VIEW || incoming?.action == Intent.ACTION_SEND) mainViewModel?.workspace?.prepareIntent(requireNotNull(incoming))
    }
}
