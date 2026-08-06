package com.example

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
import com.example.data.backup.BackupManager
import com.example.data.db.AppDatabase
import com.example.data.repository.FlexiRepository
import com.example.ui.FlexiViewModel
import com.example.ui.FlexiViewModelFactory
import com.example.ui.detail.ItemDetailScreen
import com.example.ui.items.ItemsScreen
import com.example.ui.notebooks.NotebooksScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup SQLite Database, Repository and Backup manager completely offline
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = FlexiRepository(
            database.notebookDao(),
            database.listItemDao(),
            database.workoutLogDao(),
            database.waterIntakeDao()
        )
        val backupManager = BackupManager(repository)
        
        // Instantiate our primary business ViewModel
        val factory = FlexiViewModelFactory(application, repository, backupManager)
        val viewModel = ViewModelProvider(this, factory)[FlexiViewModel::class.java]

        enableEdgeToEdge()
        
        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val accentIndex by viewModel.selectedAccentIndex.collectAsState()

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
