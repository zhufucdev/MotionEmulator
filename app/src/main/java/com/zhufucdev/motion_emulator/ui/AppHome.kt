package com.zhufucdev.motion_emulator.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Telephonies
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.ui.component.TooltipHost
import com.zhufucdev.motion_emulator.ui.composition.DefaultFloatingActionButtonManipulator
import com.zhufucdev.motion_emulator.ui.composition.LocalNavControllerProvider
import com.zhufucdev.motion_emulator.ui.composition.LocalNestedScrollConnectionProvider
import com.zhufucdev.motion_emulator.ui.composition.LocalSnackbarProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHome(windowSize: WindowSizeClass) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbars = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalSnackbarProvider provides snackbars,
        LocalNestedScrollConnectionProvider provides scrollBehavior.nestedScrollConnection,
        LocalNavControllerProvider provides navController
    ) {
        when (windowSize.widthSizeClass) {
            WindowWidthSizeClass.Compact -> {
                Scaffold(
                    topBar = { TopBar(scrollBehavior) },
                    floatingActionButton = {
                        DefaultFloatingActionButtonManipulator.CurrentFloatingActionButton()
                    },
                    snackbarHost = { SnackbarHost(snackbars) },
                    bottomBar = {
                        NavigationBar {
                            NavigationDestinations.entries.forEach { dest ->
                                val selected =
                                    backStackEntry?.destination?.let { dest.selected(it) } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            navController.navigate(dest.name)
                                        }
                                    },
                                    icon = dest.icon,
                                    label = dest.label
                                )
                            }
                        }
                    }
                ) {
                    NavContent(it)
                }
            }

            WindowWidthSizeClass.Medium -> {
                Row {
                    NavigationRail {
                        NavigationDestinations.entries.forEach { dest ->
                            val selected =
                                backStackEntry?.destination?.let { dest.selected(it) } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(dest.name)
                                    }
                                },
                                icon = dest.icon,
                                label = dest.label
                            )
                        }
                    }
                    Scaffold(
                        topBar = { TopBar(scrollBehavior) },
                        floatingActionButton = { DefaultFloatingActionButtonManipulator.CurrentFloatingActionButton() },
                        snackbarHost = { SnackbarHost(snackbars) }
                    ) {
                        NavContent(it)
                    }
                }
            }

            WindowWidthSizeClass.Expanded -> {
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet {
                            NavigationDestinations.entries.forEach { dest ->
                                val selected =
                                    backStackEntry?.destination?.let { dest.selected(it) } == true
                                NavigationDrawerItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            navController.navigate(dest.name)
                                        }
                                    },
                                    icon = dest.icon,
                                    label = dest.label
                                )
                            }
                        }
                    }
                ) {
                    Scaffold(
                        topBar = { TopBar(scrollBehavior) },
                        floatingActionButton = { DefaultFloatingActionButtonManipulator.CurrentFloatingActionButton() },
                        snackbarHost = { SnackbarHost(snackbars) }
                    ) {
                        NavContent(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavContent(paddingValues: PaddingValues) {
    val provider = LocalViewModelStoreOwner.current!!
    NavHost(
        navController = LocalNavControllerProvider.current!!,
        startDestination = NavigationDestinations.Emulate.name
    ) {
        composable(NavigationDestinations.Plugins.route) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides provider
            ) {
                PluginsApp(paddingValues)
            }
        }
        composable(NavigationDestinations.Emulate.route) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides provider
            ) {
                EmulateHome(paddingValues)
            }
        }
        navigation(
            startDestination = "home",
            route = NavigationDestinations.Data.route
        ) {
            composable("home") {
                CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                    ManagerApp(paddingValues)
                }
            }
            composable(
                route = "cell/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                val targetId = it.arguments?.getString("id") ?: error("invalid argument")
                val target = Telephonies[targetId] ?: error("unknown datum")
                CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                    CellEditor(target, paddingValues)
                }
            }
            composable(
                route = "motion/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                val targetId = it.arguments?.getString("id") ?: error("invalid argument")
                val target = Motions[targetId] ?: error("unknown datum")
                CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                    MotionEditor(target, paddingValues)
                }
            }
            composable(
                route = "trace/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                val targetId = it.arguments?.getString("id") ?: error("invalid argument")
                val target = Traces[targetId] ?: error("unknown datum")
                CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                    TraceEditor(target, paddingValues)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(scrollBehavior: TopAppBarScrollBehavior) {
    TooltipHost {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontFamily = FontFamily.Serif
                )
            },
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            actions = {
                IconButton(
                    onClick = {

                    },
                    content = {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                    },
                    modifier = Modifier.tooltip {
                        Text(text = stringResource(id = R.string.title_activity_settings))
                    }
                )
            }
        )
    }
}

enum class NavigationDestinations(
    val label: @Composable () -> Unit,
    val icon: @Composable () -> Unit,
    val route: String
) {
    Plugins(
        label = { Text(text = stringResource(id = R.string.title_activity_plugin)) },
        icon = {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null
            )
        },
        route = "plugins"
    ),
    Emulate(
        label = { Text(text = stringResource(id = R.string.title_emulate)) },
        icon = {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = null
            )
        },
        route = "emulate"
    ),
    Data(
        label = { Text(text = stringResource(id = R.string.title_data)) },
        icon = {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null
            )
        },
        route = "data"
    )
}

fun NavigationDestinations.selected(currentDest: NavDestination): Boolean =
    isCurrent(currentDest) || currentDest.hierarchy.any { isCurrent(it) }

fun NavigationDestinations.isCurrent(currentDest: NavDestination) =
    currentDest.route == name.lowercase()
