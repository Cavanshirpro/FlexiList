package xyz.younglion.flexilist.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import xyz.younglion.flexilist.R
import xyz.younglion.flexilist.ui.FlexiViewModel
import xyz.younglion.flexilist.ui.detail.ItemDetailScreen
import xyz.younglion.flexilist.ui.items.ItemsScreen

private fun workspaceIcon(workspace: WorkspaceId) = when (workspace) {
    WorkspaceId.LISTS -> Icons.Default.Checklist
    WorkspaceId.HABITS -> Icons.Default.Repeat
    WorkspaceId.WORKOUTS -> Icons.Default.FitnessCenter
    WorkspaceId.LIBRARY -> Icons.AutoMirrored.Filled.MenuBook
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceApp(viewModel: FlexiViewModel) {
    val controller = viewModel.workspace
    var activeName by rememberSaveable { mutableStateOf(controller.lastWorkspace().name) }
    val active = WorkspaceId.valueOf(activeName)
    var fullScreen by remember { mutableStateOf(false) }
    var requestedNotebook by rememberSaveable { mutableStateOf<Long?>(null) }
    val holder = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val deleted by controller.deletion.collectAsState()
    val collection by controller.notebooks.collectAsState()
    LaunchedEffect(collection) {
        (collection as? NotebookCollectionState.Ready)?.let { ready ->
            controller.migrateWorkspace(ready.notebooks)?.let { activeName = it.name }
        }
    }
    val accessibility = LocalAccessibilityManager.current
    val deletedMessage = stringResource(R.string.ux_deleted)
    val undoLabel = stringResource(R.string.ux_undo)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(controller) { controller.notices.collect { snackbar.showSnackbar(context.getString(it)) } }
    LaunchedEffect(deleted?.token) {
        val batch = deleted ?: return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(5000, containsIcons = false, containsText = true, containsControls = true) ?: 5000
        val result = withTimeoutOrNull(timeout) { snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Indefinite) }
        if (result == SnackbarResult.ActionPerformed) controller.undoDelete(batch.token)
        else controller.expireDeletion(batch.token)
    }
    fun select(workspace: WorkspaceId) {
        activeName = workspace.name
        fullScreen = false
        controller.rememberWorkspace(workspace)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 600.dp
        val fontScale = LocalDensity.current.fontScale
        val expandedLayout = maxWidth >= 840.dp && fontScale <= 1.5f
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (!fullScreen) 88.dp else 0.dp)) },
            bottomBar = {
                if (!rail && !fullScreen) {
                    if (fontScale > 1.3f) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.navigationBarsPadding()) {
                            WorkspaceId.entries.chunked(2).forEach { group ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
                                    group.forEach { workspace ->
                                        NavigationBarItem(selected = active == workspace, onClick = { select(workspace) },
                                            icon = { Icon(workspaceIcon(workspace), null) },
                                            label = { Text(stringResource(workspace.title), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                            modifier = Modifier.testTag("workspace-${workspace.name}"))
                                    }
                                }
                            }
                        }
                    } else NavigationBar {
                        WorkspaceId.entries.forEach { workspace ->
                            NavigationBarItem(selected = active == workspace, onClick = { select(workspace) },
                                icon = { Icon(workspaceIcon(workspace), null) }, label = { Text(stringResource(workspace.title)) },
                                modifier = Modifier.testTag("workspace-${workspace.name}"))
                        }
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (rail && !fullScreen) NavigationRail(modifier = Modifier.width(if (fontScale > 1.3f) 140.dp else 96.dp)) {
                    Spacer(Modifier.weight(1f))
                    WorkspaceId.entries.forEach { workspace ->
                        NavigationRailItem(selected = active == workspace, onClick = { select(workspace) },
                            icon = { Icon(workspaceIcon(workspace), null) }, label = { Text(stringResource(workspace.title)) },
                            modifier = Modifier.testTag("workspace-${workspace.name}"))
                    }
                    Spacer(Modifier.weight(1f))
                }
                holder.SaveableStateProvider(active.name) {
                    WorkspaceGraph(
                        active, viewModel, requestedNotebook, expanded = expandedLayout,
                        onRequestConsumed = { requestedNotebook = null },
                        onFocus = { fullScreen = it },
                        onTransfer = { workspace, id ->
                            controller.rememberNotebook(workspace, id)
                            requestedNotebook = id
                            select(workspace)
                        }
                    )
                }
            }
        }
    }
    ImportReview(controller)
}

@Composable
private fun WorkspaceGraph(
    workspace: WorkspaceId,
    viewModel: FlexiViewModel,
    request: Long?,
    expanded: Boolean,
    onRequestConsumed: () -> Unit,
    onFocus: (Boolean) -> Unit,
    onTransfer: (WorkspaceId, Long) -> Unit
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val controller = viewModel.workspace
    var restored by rememberSaveable { mutableStateOf(false) }
    var childFocus by remember { mutableStateOf(false) }
    val transfer = rememberUpdatedState(onTransfer)
    val collectionState = controller.notebooks.collectAsState()
    LaunchedEffect(route) { childFocus = false }
    LaunchedEffect(route, childFocus) { onFocus(childFocus || route == "settings" || route.startsWith("editor/") || route.startsWith("notebook-edit/")) }
    fun open(id: Long) {
        controller.rememberNotebook(workspace, id)
        viewModel.selectNotebook(id)
        nav.navigate("notebook/$id") { popUpTo("home"); launchSingleTop = true }
    }
    fun home() {
        controller.rememberNotebook(workspace, null)
        nav.popBackStack("home", false)
    }
    LaunchedEffect(collectionState.value) {
        val ready = collectionState.value as? NotebookCollectionState.Ready ?: return@LaunchedEffect
        if (!restored) {
            restored = true
            val id = controller.lastNotebook(workspace)
            if (id != null && ready.notebooks.any { it.id == id && NotebookKind.parse(it.type).workspace == workspace }) open(id)
            else if (id != null) controller.rememberNotebook(workspace, null)
        }
    }
    LaunchedEffect(request) { request?.let { open(it); onRequestConsumed() } }
    BackHandler(enabled = route != "home" && route.isNotEmpty()) {
        if (route.startsWith("notebook/")) home() else nav.popBackStack()
    }
    @Composable fun overview() {
        WorkspaceHome(workspace, viewModel, ::open,
            onCreate = { nav.navigate("notebook-edit/0") },
            onEdit = { nav.navigate("notebook-edit/$it") },
            onSettings = { nav.navigate("settings") })
    }
    NavHost(navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize(), enterTransition = { androidx.compose.animation.EnterTransition.None }, exitTransition = { androidx.compose.animation.ExitTransition.None }) {
        composable("home") { overview() }
        composable("settings") { WorkspaceSettings(viewModel, onBack = { nav.popBackStack() }) }
        composable("notebook-edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { target ->
            val id = target.arguments?.getLong("id") ?: 0
            val editing = (collectionState.value as? NotebookCollectionState.Ready)?.notebooks?.firstOrNull { it.id == id }
            if (id != 0L && collectionState.value is NotebookCollectionState.Loading) LoadingNotebook()
            else if (id != 0L && collectionState.value is NotebookCollectionState.Error) MessageState(R.string.ux_load_failed, R.string.ux_retry, controller::retryNotebooks)
            else if (id != 0L && editing == null) MessageState(R.string.ux_missing_notebook, R.string.back, { nav.popBackStack() })
            else NotebookForm(controller, editing, workspace, onDismiss = { nav.popBackStack() }, onSaved = { saved ->
                val destination = NotebookKind.parse(saved.type).workspace
                if (destination == workspace) open(saved.id)
                else { home(); transfer.value(destination, saved.id) }
            })
        }
        composable("notebook/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { target ->
            val id = requireNotNull(target.arguments).getLong("id")
            val state by remember(id) { controller.notebook(id) }.collectAsState(initial = NotebookUiState.Loading)
            val selected by viewModel.selectedNotebook.collectAsState()
            val items by viewModel.items.collectAsState()
            LaunchedEffect(id) { viewModel.selectNotebook(id) }
            Box(Modifier.fillMaxSize()) {
                val twoPane = expanded
                Row(Modifier.fillMaxSize()) {
                    if (twoPane) Box(Modifier.width(300.dp).fillMaxHeight()) { overview() }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (val current = state) {
                            NotebookUiState.Missing -> MessageState(R.string.ux_missing_notebook, R.string.back, ::home)
                            NotebookUiState.Error -> MessageState(R.string.ux_load_failed, R.string.ux_retry, { nav.navigate("notebook/$id") { popUpTo("home"); launchSingleTop = true } })
                            is NotebookUiState.Ready -> {
                                LaunchedEffect(current.notebook.type) {
                                    val destination = NotebookKind.parse(current.notebook.type).workspace
                                    if (destination != workspace) { home(); transfer.value(destination, id) }
                                }
                                if (selected?.id != id || items.any { it.notebookId != id }) LoadingNotebook()
                                else ItemsScreen(viewModel, onNavigateBack = ::home,
                                    onNavigateToAddEdit = { nav.navigate("editor/$id/$it") }, onFocusChanged = { childFocus = it })
                            }
                            NotebookUiState.Loading -> LoadingNotebook()
                        }
                    }
                }
            }
        }
        composable("editor/{notebookId}/{itemId}", arguments = listOf(navArgument("notebookId") { type = NavType.LongType }, navArgument("itemId") { type = NavType.LongType })) { target ->
            val notebookId = requireNotNull(target.arguments).getLong("notebookId")
            val itemId = requireNotNull(target.arguments).getLong("itemId")
            val state by remember(notebookId) { controller.notebook(notebookId) }.collectAsState(initial = NotebookUiState.Loading)
            val selected by viewModel.selectedNotebook.collectAsState()
            LaunchedEffect(notebookId) { viewModel.selectNotebook(notebookId) }
            when (state) {
                is NotebookUiState.Ready -> if (selected?.id == notebookId) ItemDetailScreen(itemId, viewModel, onNavigateBack = { nav.popBackStack() }, notebookId = notebookId) else LoadingNotebook()
                NotebookUiState.Missing -> MessageState(R.string.ux_missing_notebook, R.string.back, ::home)
                NotebookUiState.Error -> MessageState(R.string.ux_load_failed, R.string.back, { nav.popBackStack() })
                NotebookUiState.Loading -> LoadingNotebook()
            }
        }
    }
}

@Composable
private fun LoadingNotebook() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
