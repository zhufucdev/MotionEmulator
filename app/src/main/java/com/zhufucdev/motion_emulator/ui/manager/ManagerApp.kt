@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zhufucdev.stub.Data
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.ui.TooltipHost
import com.zhufucdev.motion_emulator.ui.TooltipScope
import com.zhufucdev.motion_emulator.ui.component.BottomSheetModal
import com.zhufucdev.motion_emulator.ui.component.BottomSheetModalState
import com.zhufucdev.motion_emulator.ui.component.Swipeable
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon

@Composable
fun ManagerApp(navigateUp: () -> Unit) {
    val appbarBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val navController = rememberNavController()
    val snackbarState = remember { SnackbarHostState() }
    val bottomSheetState = remember { BottomSheetModalState() }
    val context = LocalContext.current
    val screenProviders = LocalScreenProviders.current

    val runtimeParameters = remember {
        ManagerViewModel.RuntimeArguments(
            snackbarState,
            navController,
            context,
            bottomSheetState
        )
    }

    BottomSheetModal(state = bottomSheetState) {
        TooltipHost {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(appbarBehavior.nestedScrollConnection),
                topBar = {
                    AppBar(
                        onBackPressed = { if (!navController.navigateUp()) navigateUp() },
                        scrollBehavior = appbarBehavior
                    )
                },
                bottomBar = { AppNavigationBar(navController, screenProviders) },
                snackbarHost = { SnackbarHost(snackbarState) }
            ) {
                Box(Modifier.padding(it)) {
                    NavHost(
                        navController = navController,
                        startDestination = screenProviders.value.first().screen.name
                    ) {
                        screenProviders.value.forEach { para ->
                            with(para) {
                                compose(runtimeParameters)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TooltipScope.AppBar(onBackPressed: () -> Unit, scrollBehavior: TopAppBarScrollBehavior) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.title_manager)) },
        navigationIcon = {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier.tooltip { Text(text = stringResource(R.string.action_navigate_up)) }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.action_navigate_up)
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun AppNavigationBar(navController: NavController, provider: ScreenProviders) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    NavigationBar {
        provider.value.forEach { provider ->
            val route = provider.screen
            NavigationBarItem(
                selected = navBackStackEntry?.destination?.hierarchy?.any { it.route?.startsWith(route.name) == true } == true,
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
        CompositionLocalProvider(LocalScreenProviders provides ScreenProviders(listOf(randomizedMotionData()))) {
            ManagerApp(navigateUp = {})
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : Data> DataList(
    viewModel: EditorViewModel<T>,
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

    val state = rememberLazyListState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(paddingCommon),
        verticalArrangement = Arrangement.spacedBy(paddingCommon),
        state = state
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
                            onClick = {
                                viewModel.onClick(item)
                            }
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
