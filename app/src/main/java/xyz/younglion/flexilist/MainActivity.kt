package xyz.younglion.flexilist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import xyz.younglion.flexilist.data.backup.BackupManager
import xyz.younglion.flexilist.data.db.AppDatabase
import xyz.younglion.flexilist.data.repository.FlexiRepository
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.FlexiViewModelFactory
import xyz.younglion.flexilist.ui.detail.ItemDetailScreen
import xyz.younglion.flexilist.ui.items.ItemsScreen
import xyz.younglion.flexilist.ui.notebooks.NotebooksScreen
import xyz.younglion.flexilist.ui.theme.MyApplicationTheme

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import android.content.res.Configuration
import xyz.younglion.flexilist.data.backup.NotebookShareManager

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidedValue

class MainActivity : ComponentActivity() {
    private var mainViewModel: FlexiViewModel? = null

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("flexilist_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "en") ?: "en"
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup SQLite Database, Repository and Backup manager completely offline
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = FlexiRepository(database)
        val backupManager = BackupManager(repository)
        
        // Instantiate our primary business ViewModel
        val factory = FlexiViewModelFactory(application, repository, backupManager)
        val viewModel = ViewModelProvider(this, factory)[FlexiViewModel::class.java]
        mainViewModel = viewModel

        if (savedInstanceState == null) handleIncomingIntent(intent)

        enableEdgeToEdge()
        
        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val accentIndex by viewModel.selectedAccentIndex.collectAsState()
            val appLanguage by viewModel.appLanguage.collectAsState()

            LaunchedEffect(appLanguage) {
                val currentLang = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    resources.configuration.locales.get(0)?.language ?: "en"
                } else {
                    @Suppress("DEPRECATION")
                    resources.configuration.locale?.language ?: "en"
                }
                if (currentLang != appLanguage) {
                    recreate()
                }
            }

            val baseContext = LocalContext.current
            val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
            val localizedContext = remember(appLanguage) {
                val locale = Locale.forLanguageTag(appLanguage)
                Locale.setDefault(locale)
                val config = Configuration(baseContext.resources.configuration)
                config.setLocale(locale)
                @Suppress("DEPRECATION")
                baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
                @Suppress("DEPRECATION")
                applicationContext.resources.updateConfiguration(config, applicationContext.resources.displayMetrics)
                baseContext.createConfigurationContext(config)
            }

            key(appLanguage) {
                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalConfiguration provides localizedContext.resources.configuration,
                    LocalActivityResultRegistryOwner provides this@MainActivity
                ) {
                    MyApplicationTheme(
                        darkTheme = isDarkMode,
                        accentIndex = accentIndex
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            val navController = rememberNavController()

                            NavHost(
                                navController = navController,
                                startDestination = "notebooks"
                            ) {
                        // 1. Notebooks Screen Destination
                        composable("notebooks") {
                            NotebooksScreen(
                                viewModel = viewModel,
                                onNavigateToItems = { _ ->
                                    navController.navigate("items")
                                }
                            )
                        }

                        // 2. Notebook List Items Destination
                        composable("items") {
                            ItemsScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToAddEdit = { itemId ->
                                    navController.navigate("add_edit/$itemId")
                                }
                            )
                        }

                        // 3. Add / Edit ListItem details Destination
                        composable(
                            route = "add_edit/{itemId}",
                            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
                            ItemDetailScreen(
                                itemId = itemId,
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        val vm = mainViewModel ?: return
        if (incomingIntent == null) return
        val action = incomingIntent.action
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) {
            NotebookShareManager.importFlexiNotebookFromIntent(
                context = this,
                intent = incomingIntent,
                viewModel = vm,
                onSuccess = { name, count ->
                    Toast.makeText(this, "✅ '$name' defteri ve $count öğe başarıyla içe aktarıldı!", Toast.LENGTH_LONG).show()
                },
                onError = { err ->
                    Toast.makeText(this, "⚠️ Defter içe aktarılamadı: $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}
