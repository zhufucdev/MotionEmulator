package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhufucdev.me.stub.Data
import com.zhufucdev.motion_emulator.BuildConfig
import com.zhufucdev.motion_emulator.data.Cells
import com.zhufucdev.motion_emulator.data.Emulations
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.extension.AppUpdater
import com.zhufucdev.motion_emulator.extension.defaultKtorClient
import com.zhufucdev.motion_emulator.extension.setUpStatusBar
import com.zhufucdev.motion_emulator.plugin.Plugins
import com.zhufucdev.motion_emulator.ui.model.AppViewModel
import com.zhufucdev.motion_emulator.ui.model.EmulationsViewModel
import com.zhufucdev.motion_emulator.ui.model.ManagerViewModel
import com.zhufucdev.motion_emulator.ui.model.PluginViewModel
import com.zhufucdev.motion_emulator.ui.model.toPluginItem
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import com.zhufucdev.sdk.findAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpStatusBar()

        setContent {
            MotionEmulatorTheme {
                val updater = remember {
                    AppUpdater(this)
                }
                LaunchedEffect(Unit) {
                    updater.check()
                }

                AppHome(calculateWindowSizeClass(this))
            }
        }
    }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AppViewModel(
                updater = AppUpdater(this@MainActivity)
            )
        }

        initializer {
            Emulations.require(this@MainActivity)
            EmulationsViewModel(
                configs = Emulations.list()
            )
        }

        initializer {
            Plugins.init(this@MainActivity)
            val enabled = Plugins.enabled
            val all = Plugins.available
            val plugins = enabled.map { it.toPluginItem(true) } + (all - enabled.toSet()).map {
                it.toPluginItem(false)
            }
            PluginViewModel(
                plugins = plugins,
                downloadable = flow {
                    val queries =
                        defaultKtorClient.findAsset(BuildConfig.server_uri, "me", "plugin")
                    emit(
                        queries.map {
                            it.packageId?.let { plugins.firstOrNull { p -> p.id == it } }
                                ?: it.toPluginItem()
                        }
                    )
                }
            )
        }

        initializer {
            val stores = listOf(Traces, Motions, Cells)
            val data = mutableStateListOf<Data>()
            ManagerViewModel(
                data = data,
                dataLoader = flow {
                    emit(false)
                    if (data.isEmpty()) {
                        withContext(Dispatchers.IO) {
                            data.addAll(
                                stores.flatMap {
                                    it.require(this@MainActivity)
                                    it.list()
                                }.sortedBy { it.id }
                            )
                        }
                    }
                    emit(true)
                },
                stores = stores,
                context = this@MainActivity
            )
        }
    }
}