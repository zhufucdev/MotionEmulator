package com.zhufucdev.motion_emulator.ui.emulate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.databinding.FragmentEmulateStatusBinding
import com.zhufucdev.motion_emulator.extension.lazySharedPreferences
import com.zhufucdev.motion_emulator.extension.skipAmapFuckingLicense
import com.zhufucdev.motion_emulator.provider.EmulationMonitorWorker
import com.zhufucdev.motion_emulator.provider.Scheduler
import com.zhufucdev.motion_emulator.ui.map.MapTraceCallback
import com.zhufucdev.motion_emulator.ui.map.TraceBounds
import com.zhufucdev.motion_emulator.ui.map.UnifiedMapFragment
import kotlinx.coroutines.launch

class EmulateStatusFragment : Fragment() {
    private lateinit var binding: FragmentEmulateStatusBinding
    private val preferences by lazySharedPreferences()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Scheduler.init(requireContext())
        skipAmapFuckingLicense(requireContext())
        registerChannel()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEmulateStatusBinding.inflate(layoutInflater, container, false)
        binding.mapMotionPreview.provider =
            UnifiedMapFragment.Provider.valueOf(preferences.getString("map_provider", "gcp_maps")!!.uppercase())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            initializeMonitors()
        }
    }

    private var previousTrace: MapTraceCallback? = null
    private suspend fun initializeMap() {
        val controller = binding.mapMotionPreview.requireController()

        arguments?.let {
            val traceId = it.getString("target_trace") ?: return@let
            val trace = Traces[traceId] ?: return@let
            previousTrace?.remove()
            previousTrace = controller.drawTrace(trace)
            controller.boundCamera(TraceBounds(trace))
        }
    }

    private fun initializeMonitors() {
        val viewPager = binding.viewpagerStatus
        viewPager.adapter = EmulationCardAdapter(this, binding.mapMotionPreview)
    }

    private fun addMonitorWorker() {
        val workRequest =
            OneTimeWorkRequestBuilder<EmulationMonitorWorker>()
                .build()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork(
                WORK_NAME_MONITOR,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }

    private fun removeMonitorWorker() {
        WorkManager.getInstance(requireContext())
            .cancelUniqueWork(WORK_NAME_MONITOR)
    }

    override fun onDestroy() {
        super.onDestroy()
        Scheduler.stop(requireContext())
        removeMonitorWorker()
    }

    override fun onResume() {
        super.onResume()
        removeMonitorWorker()
        lifecycleScope.launch {
            initializeMap()
        }
    }

    override fun onStop() {
        super.onStop()
        addMonitorWorker()
    }

    private fun registerChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                0
            )
        }
        with(requireContext().applicationContext) {
            val name = getString(R.string.title_channel_emulation)
            val description = getString(R.string.text_channel_emulation)
            getSystemService(android.app.NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        EmulationMonitorWorker.CHANNEL_ID,
                        name,
                        NotificationManager.IMPORTANCE_LOW
                    )
                        .apply {
                            setDescription(description)
                        }
                )
        }
    }
}

const val WORK_NAME_MONITOR = "emulationMonitor"
