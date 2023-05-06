package com.zhufucdev.motion_emulator.mock_location_plugin

import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.content.getSystemService
import com.zhufucdev.motion_emulator.mock_location_plugin.ui.TestStatus
import com.zhufucdev.motion_emulator.mock_location_plugin.ui.TestViewModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

object Availability {
    private var connectionCallback: ((Boolean) -> Unit)? = null

    suspend fun test(context: Context, viewModel: TestViewModel) {
        viewModel.carrying.postValue(true)
        viewModel.serviceAvailable.postValue(TestStatus.ONGOING)
        val locationManager = context.getSystemService<LocationManager>()
        if (locationManager == null) {
            viewModel.serviceAvailable.postValue(TestStatus.FAILED)
            viewModel.carrying.postValue(false)
            return
        }

        viewModel.serviceAvailable.postValue(TestStatus.PASSED)
        viewModel.developerSet.postValue(TestStatus.ONGOING)
        viewModel.providerConnected.postValue(TestStatus.ONGOING)

        try {
            val (powerUsage, accuracy) = if (Build.VERSION.SDK_INT >= 30) 1 to 2 else 0 to 5
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                powerUsage,
                accuracy
            )
            viewModel.developerSet.postValue(TestStatus.PASSED)
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) {
            viewModel.developerSet.postValue(TestStatus.FAILED)
        }

        connectionCallback = {
            viewModel.providerConnected.postValue(if (it) TestStatus.PASSED else TestStatus.FAILED)
            viewModel.carrying.postValue(false)
        }

        delay(15.seconds)
        if (viewModel.carrying.value != false) {
            // timeout
            viewModel.providerConnected.postValue(TestStatus.UNKNOWN)
            viewModel.carrying.postValue(false)
            connectionCallback = null
        }
    }

    fun notifyConnected(available: Boolean) {
        connectionCallback?.invoke(available)
    }
}
