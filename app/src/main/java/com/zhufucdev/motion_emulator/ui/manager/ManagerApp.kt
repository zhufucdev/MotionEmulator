@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import kotlin.random.Random

@Composable
fun ManagerApp(navigateUp: () -> Unit, dataProvider: Map<Screen<*>, ScreenParameter<*>>) {
    val navController = rememberNavController()
    val screens by remember { derivedStateOf { Screen.list } }
    Scaffold(
        topBar = { AppBar(onBackPressed = { if (!navController.navigateUp()) navigateUp() }) },
        bottomBar = { AppNavigationBar(navController) },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.padding(it)) {
            NavHost(navController = navController, startDestination = screens.first().name) {
                screens.forEach { screen ->
                    composable(screen.name) {
                        val parameter = dataProvider[screen]!!
                        (screen as Screen<Any>).screen(parameter as ScreenParameter<Any>)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBar(onBackPressed: () -> Unit) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.title_manager)) },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.action_navigate_up)
                )
            }
        }
    )
}

@Composable
private fun AppNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val screens by remember { derivedStateOf { Screen.list } }
    NavigationBar {
        screens.forEach { route ->
            NavigationBarItem(
                selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == route.name } == true,
                onClick = {
                    navController.navigate(route.name) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(route.iconId),
                        contentDescription = stringResource(route.titleId)
                    )
                },
                label = { Text(text = stringResource(route.titleId)) }
            )
        }
    }
}

sealed class Screen<T>(
    val name: String,
    val titleId: Int,
    val iconId: Int,
    val screen: @Composable (ScreenParameter<T>) -> Unit
) {
    object MotionScreen : Screen<Motion>(
        "motion",
        R.string.title_motion_data,
        R.drawable.ic_baseline_smartphone_24,
        { MotionScreen(it) }
    )

    object CellScreen : Screen<CellTimeline>(
        "cell",
        R.string.title_cells_data,
        R.drawable.ic_baseline_cell_tower_24,
        { CellScreen(it) }
    )

    object TraceScreen : Screen<Trace>(
        "trace",
        R.string.title_trace,
        R.drawable.ic_baseline_map_24,
        { TraceScreen(it) }
    )

    companion object {
        val list get() = listOf(MotionScreen, CellScreen, TraceScreen)
    }
}

@Composable
fun <T : Referable> DataList(data: List<T>, create: @Composable (T) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
    ) {
        data.forEach {
            item(key = it.id) {
                create(it)
            }
        }
    }
}

@Composable
@Preview
fun ActivityPreview() {
    MotionEmulatorTheme {
        ManagerApp(
            navigateUp = {},
            dataProvider = mapOf(
                Screen.MotionScreen to randomMotionData()
            )
        )
    }
}

fun randomMotionData(): ScreenParameter<Motion> =
    ScreenParameter(
        data = buildList {
            repeat(10) {
                add(
                    Motion(
                        NanoIdUtils.randomNanoId(),
                        System.currentTimeMillis() - Random.nextLong(10000),
                        emptyList(),
                        emptyList()
                    )
                )
            }
        },
        handler = {

        }
    )

data class ScreenParameter<T>(val data: List<T>, val handler: (T) -> Unit)
