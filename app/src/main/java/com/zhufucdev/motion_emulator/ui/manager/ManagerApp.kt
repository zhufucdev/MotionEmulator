@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.ui.Swipeable
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon

@Composable
fun ManagerApp(navigateUp: () -> Unit, dataProvider: List<ManagerViewModel<*>>) {
    val navController = rememberNavController()
    val snackbarState = remember { SnackbarHostState() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { AppBar(onBackPressed = { if (!navController.navigateUp()) navigateUp() }) },
        bottomBar = { AppNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarState) }
    ) {
        Box(Modifier.padding(it)) {
            NavHost(
                navController = navController,
                startDestination = dataProvider.first().screen.name
            ) {
                dataProvider.forEach { para ->
                    composable(para.screen.name) {
                        para.Compose(
                            ManagerViewModel.RuntimeArguments(
                                snackbarState,
                                navController,
                                LocalContext.current
                            )
                        )
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
        screens.forEach { route: Screen<*> ->
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

@Composable
@Preview
fun ActivityPreview() {
    MotionEmulatorTheme {
        ManagerApp(
            navigateUp = {},
            dataProvider = listOf(
                randomizedMotionData()
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : Referable> DataList(
    viewModel: ManagerViewModel<T>,
    content: @Composable (T) -> Unit
) {
    if (viewModel.data.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                modifier = Modifier.size(180.dp),
                painter = painterResource(R.drawable.ic_thinking_face_72),
                contentDescription = "empty",
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(paddingCommon),
        verticalArrangement = Arrangement.spacedBy(paddingCommon)
    ) {
        viewModel.data.forEach { item ->
            item(key = item.id) {
                var heightAnimator by remember { mutableStateOf(Animatable(0F)) }
                var removed by remember { mutableStateOf(false) }

                LaunchedEffect(removed) {
                    if (!removed) return@LaunchedEffect

                    heightAnimator.animateTo(0F)
                    viewModel.onRemove(item)
                }

                Swipeable(
                    foreground = {
                        content(item)
                    },
                    fillColor = MaterialTheme.colorScheme.errorContainer,
                    backgroundEnd = {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_delete_24),
                            contentDescription = "delete",
                        )
                    },
                    endActivated = { removed = true },
                    container = { content ->
                        Card(
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItemPlacement()
                                .heightIn(
                                    max =
                                    if (!removed) Dp.Infinity
                                    else with(LocalDensity.current) { heightAnimator.value.toDp() }
                                )
                                .onGloballyPositioned {
                                    if (!removed) heightAnimator =
                                        Animatable(it.size.height.toFloat())
                                },
                            onClick = { viewModel.onClick(item) }
                        ) {
                            content()
                        }
                    },
                    fractionWidth = 50.dp
                )
            }
        }
    }
}
