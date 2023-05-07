package com.zhufucdev.mock_location_plugin

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.*

class EmulationService : JobService() {
    private val scope = CoroutineScope(Dispatchers.Default)
    private lateinit var waitJob: Job

    override fun onStartJob(params: JobParameters?): Boolean {
        waitJob = scope.launch {
            MockLocationProvider.wait()
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        waitJob.cancel()
        scope.cancel()
        return true
    }
}