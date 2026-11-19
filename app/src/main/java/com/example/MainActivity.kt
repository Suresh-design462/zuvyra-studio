package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.EditorViewModel
import com.example.ui.StudioViewModel
import com.example.ui.components.NewProjectDialog
import com.example.ui.editor.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MediaScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.ZuvyraTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object ProjectsRoute

@Serializable
object EditorRoute

@Serializable
object MediaRoute

@Serializable
object SettingsRoute

private data class StudioNavItem(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZuvyraAppRoot()
        }
    }
}

@Composable
fun ZuvyraAppRoot() {
    val studioVm: StudioViewModel = viewModel()
    val editorVm: EditorViewModel = viewModel()

    val settings by studioVm.settings.collectAsStateWithLifecycle()
    val projects by studioVm.projects.collectAsStateWithLifecycle()
    val mediaAssets by studioVm.mediaAssets.collectAsStateWithLifecycle()
    val exportHistory by studioVm.exportHistory.collectAsStateWithLifecycle()
    val recoveryCandidate by studioVm.recoveryCandidate.collectAsStateWithLifecycle()
    val importState by studioVm.importState.collectAsStateWithLifecycle()
    val selectedMediaIds by studioVm.selectedMediaIds.collectAsStateWithLifecycle()
    val storageBreakdown by studioVm.storageBreakdown.collectAsStateWithLifecycle()
    val studioStatus by studioVm.statusMessage.collectAsStateWithLifecycle()

    val activeProject by editorVm.project.collectAsStateWithLifecycle()
    val editorToast by editorVm.toastMessage.collectAsStateWithLifecycle()

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showNewProjectModal by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    LaunchedEffect(studioStatus) {
        studioStatus?.let {
            snackbarHostState.showSnackbar(it)
            studioVm.clearStatusMessage()
        }
    }

    LaunchedEffect(editorToast) {
        editorToast?.let {
            snackbarHostState.showSnackbar(it)
            editorVm.clearToast()
        }
    }

    val navItems = remember {
        listOf(
            StudioNavItem(HomeRoute, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            StudioNavItem(ProjectsRoute, R.string.nav_projects, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_projects"),
            StudioNavItem(EditorRoute, R.string.nav_editor, Icons.Filled.MovieCreation, Icons.Outlined.MovieCreation, "nav_editor"),
            StudioNavItem(MediaRoute, R.string.nav_media, Icons.Filled.PermMedia, Icons.Outlined.PermMedia, "nav_media"),
            StudioNavItem(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    fun navigateToRoute(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openProjectInEditor(projectId: String, preferRecovery: Boolean = false) {
        editorVm.openProject(projectId, preferRecovery)
        if (preferRecovery) studioVm.clearRecoveryBanner()
        navigateToRoute(EditorRoute)
    }

    ZuvyraTheme(
        themeMode = settings.themeMode,
        accentOption = settings.accentColor
    ) {
        if (showSplash) {
            SplashScreen(onFinished = { showSplash = false })
            return@ZuvyraTheme
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val inActiveEditor = currentDestination?.hasRoute<EditorRoute>() == true && activeProject != null

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 680.dp

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useRail && !inActiveEditor) {
                        NavigationBar {
                            navItems.forEach { item ->
                                val selected = when (item.route) {
                                    is HomeRoute -> currentDestination?.hasRoute<HomeRoute>() == true
                                    is ProjectsRoute -> currentDestination?.hasRoute<ProjectsRoute>() == true
                                    is EditorRoute -> currentDestination?.hasRoute<EditorRoute>() == true
                                    is MediaRoute -> currentDestination?.hasRoute<MediaRoute>() == true
                                    is SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToRoute(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(item.labelRes)) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail && !inActiveEditor) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            navItems.forEach { item ->
                                val selected = when (item.route) {
                                    is HomeRoute -> currentDestination?.hasRoute<HomeRoute>() == true
                                    is ProjectsRoute -> currentDestination?.hasRoute<ProjectsRoute>() == true
                                    is EditorRoute -> currentDestination?.hasRoute<EditorRoute>() == true
                                    is MediaRoute -> currentDestination?.hasRoute<MediaRoute>() == true
                                    is SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
                                    else -> false
                                }
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToRoute(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(item.labelRes)) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = HomeRoute
                        ) {
                            composable<HomeRoute> {
                                HomeScreen(
                                    projects = projects,
                                    recoveryCandidate = recoveryCandidate,
                                    onNewProjectClick = { showNewProjectModal = true },
                                    onOpenProject = { id, rec -> openProjectInEditor(id, rec) },
                                    onDismissRecovery = { studioVm.dismissRecovery(it) },
                                    onNavigateToMedia = { navigateToRoute(MediaRoute) },
                                    onNavigateToProjects = { navigateToRoute(ProjectsRoute) },
                                    onNavigateToSettings = { navigateToRoute(SettingsRoute) },
                                    onRenameProject = { id, n -> studioVm.renameProject(id, n) },
                                    onDuplicateProject = { studioVm.duplicateProject(it) },
                                    onDeleteProject = { studioVm.deleteProject(it) },
                                    onSharePackage = { studioVm.shareProjectPackage(it) }
                                )
                            }

                            composable<ProjectsRoute> {
                                ProjectsScreen(
                                    projects = projects,
                                    exportHistory = exportHistory,
                                    onNewProject = { showNewProjectModal = true },
                                    onOpenProject = { id, rec -> openProjectInEditor(id, rec) },
                                    onRenameProject = { id, n -> studioVm.renameProject(id, n) },
                                    onDuplicateProject = { studioVm.duplicateProject(it) },
                                    onDeleteProject = { studioVm.deleteProject(it) },
                                    onSharePackage = { studioVm.shareProjectPackage(it) }
                                )
                            }

                            composable<EditorRoute> {
                                EditorScreen(
                                    editorVm = editorVm,
                                    recentProjects = projects,
                                    onCreateNewProjectClick = { showNewProjectModal = true },
                                    onOpenExistingProject = { id -> openProjectInEditor(id, false) },
                                    onNavigateBack = {
                                        editorVm.saveNow()
                                        navigateToRoute(HomeRoute)
                                    }
                                )
                            }

                            composable<MediaRoute> {
                                MediaScreen(
                                    mediaAssets = mediaAssets,
                                    selectedIds = selectedMediaIds,
                                    importState = importState,
                                    hasActiveProject = activeProject != null,
                                    onImportUris = { uris, folder -> studioVm.importMediaUris(uris, folder) },
                                    onImportFolderTree = { tree -> studioVm.importFromFolderTree(tree) },
                                    onCancelImport = { studioVm.cancelImport() },
                                    onClearImportReport = { studioVm.clearImportReport() },
                                    onToggleSelect = { studioVm.toggleMediaSelection(it) },
                                    onSelectAll = { studioVm.selectAllMedia(it) },
                                    onDeselectAll = { studioVm.deselectAllMedia() },
                                    onDeleteSelected = { studioVm.deleteSelectedMedia() },
                                    onAddAssetsToProject = { assets ->
                                        scope.launch {
                                            if (editorVm.project.value == null) {
                                                val created = studioVm.createProject(
                                                    name = assets.firstOrNull()?.displayName?.substringBeforeLast('.') ?: "ZUVYRA Edit",
                                                    aspectRatio = settings.defaultAspectRatio,
                                                    resolution = settings.defaultResolution,
                                                    fps = settings.defaultFps
                                                )
                                                editorVm.setLoadedProject(created)
                                            }
                                            assets.forEach { editorVm.addMediaAssetToTimeline(it) }
                                            studioVm.deselectAllMedia()
                                            navigateToRoute(EditorRoute)
                                        }
                                    }
                                )
                            }

                            composable<SettingsRoute> {
                                SettingsScreen(
                                    settings = settings,
                                    storage = storageBreakdown,
                                    capabilities = studioVm.deviceCapabilities,
                                    onRefreshStorage = { studioVm.refreshStorageBreakdown() },
                                    onClearTempFiles = { studioVm.clearTemporaryFiles() },
                                    onThemeModeChange = { studioVm.setThemeMode(it) },
                                    onAccentChange = { studioVm.setAccentColor(it) },
                                    onDensityChange = { studioVm.setDensity(it) },
                                    onDefaultResolutionChange = { studioVm.setDefaultResolution(it) },
                                    onDefaultFpsChange = { studioVm.setDefaultFps(it) },
                                    onDefaultAspectChange = { studioVm.setDefaultAspectRatio(it) },
                                    onSnappingChange = { studioVm.setTimelineSnapping(it) },
                                    onPreviewQualityChange = { studioVm.setPreviewQuality(it) },
                                    onAutoSaveChange = { studioVm.setAutoSaveInterval(it) },
                                    onThumbCacheChange = { studioVm.setThumbnailCacheLimitMb(it) },
                                    onHardwareAccelChange = { studioVm.setHardwareAccel(it) },
                                    onBackgroundExportChange = { studioVm.setBackgroundExport(it) }
                                )
                            }
                        }
                    }
                }
            }

            if (showNewProjectModal) {
                NewProjectDialog(
                    defaultRatio = settings.defaultAspectRatio,
                    defaultResolution = settings.defaultResolution,
                    defaultFps = settings.defaultFps,
                    onDismiss = { showNewProjectModal = false },
                    onCreate = { name, ratio, res, fps ->
                        showNewProjectModal = false
                        scope.launch {
                            val created = studioVm.createProject(name, ratio, res, fps)
                            editorVm.setLoadedProject(created)
                            navigateToRoute(EditorRoute)
                        }
                    }
                )
            }
        }
    }
}
