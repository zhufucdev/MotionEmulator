package com.zhufucdev.motion_emulator.mock_location_plugin

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.runBlocking

class EmulationService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        runBlocking {
            MockLocationProvider.wait()
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }
}