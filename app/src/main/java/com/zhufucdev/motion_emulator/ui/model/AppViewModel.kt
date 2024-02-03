package com.zhufucdev.motion_emulator.ui.model

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.zhufucdev.update.AppUpdater

class AppViewModel(val updater: AppUpdater) : ViewModel() {
    lateinit var navController: NavHostController
    @OptIn(ExperimentalMaterial3Api::class)
    lateinit var scrollBehavior: TopAppBarScrollBehavior
}