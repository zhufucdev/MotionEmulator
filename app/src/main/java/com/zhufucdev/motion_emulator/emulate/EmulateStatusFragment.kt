package com.zhufucdev.motion_emulator.emulate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.NavigateArrow
import com.amap.api.maps.model.NavigateArrowOptions
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.databinding.FragmentEmulateStatusBinding
import com.zhufucdev.motion_emulator.hook_frontend.*
import kotlin.math.roundToInt

class EmulateStatusFragment : Fragment() {
    private lateinit var binding: FragmentEmulateStatusBinding
    private val listeners = mutableSetOf<ListenCallback>()
    private lateinit var emulation: Emulation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        skipAmapFuckingLicense(requireContext())
        registerChannel()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEmulateStatusBinding.inflate(layoutInflater, container, false)
        binding.mapMotionPreview.onCreate(savedInstanceState)
        return binding.root
    }

    override fun onStart() {
        super.onStart()

        initializeMap()
        initializeMonitors()
    }

    private fun initializeMap() {
        val map = binding.mapMotionPreview.map
        map.uiSettings.isZoomControlsEnabled = false
        map.unifyTheme(resources)

        val naviArrow = NavigateArrowOptions()
        var lastNav: NavigateArrow? = null
        addIntermediateListener {
            lastNav?.remove()
            naviArrow.add(it.location.toLatLng())
            requireActivity().runOnUiThread {
                lastNav = map.addNavigateArrow(naviArrow)
            }
        }

        addEmulationStateListener {
            lastNav?.remove()
        }

        arguments?.let {
            val lat = it.getDouble("cam_center_lat")
            val lng = it.getDouble("cam_center_lng")
            val zoom = it.getFloat("cam_zoom")
            if (zoom > 0) {
                map.animateCamera(
                    CameraUpdateFactory
                        .newLatLngZoom(LatLng(lat, lng), zoom)
                )
            }
        }
    }


    private fun initializeMonitors() {
        val expander = binding.layoutBottomSheet.stackMonitors
        val title = binding.layoutBottomSheet.titleEmulationStatus
        val caption = binding.layoutBottomSheet.textEmulationStatus
        val btnAction = binding.layoutBottomSheet.btnDetermine
        val progressBar = binding.layoutBottomSheet.progressEmulation
        val velocity = expander.textStatusVelocity
        val time = expander.textStatusTime
        val length = expander.textStatusLength
        val context = requireContext()
        val span = progressBar.max

        fun notifyTime(remaining: Double) {
            val t = remaining.toFixed(2)
            time.text =
                context.getString(
                    R.string.status_remaining,
                    "$t ${context.getString(R.string.suffix_second)}"
                )
        }

        fun notifyStopped() {
            title.text = getString(R.string.title_emulation_stopped)
            expander.root.isVisible = false
            caption.isVisible = false
            btnAction.setOnClickListener {
                Scheduler.emulation = emulation
            }
            btnAction.setText(R.string.action_restart)
            btnAction.isVisible = true
            progressBar.isVisible = false
        }

        fun notifyStarted(info: EmulationInfo) {
            emulation = Scheduler.emulation!!

            title.setText(R.string.title_emulation_ongoing)
            velocity.text =
                context.getString(
                    R.string.status_velocity,
                    "${Scheduler.emulation!!.velocity} ${context.getString(R.string.suffix_velocity)}"
                )
            length.text =
                context.getString(
                    R.string.status_total,
                    "${info.length.toFixed(2)}${context.getString(R.string.suffix_meter)}"
                )
            notifyTime(info.duration)
            expander.root.isVisible = true
            caption.isVisible = false

            btnAction.setOnClickListener {
                Scheduler.emulation = null
            }
            btnAction.setText(R.string.action_determine)
            btnAction.isVisible = true
            progressBar.isIndeterminate = false
            progressBar.isVisible = true
        }

        fun notifyPending() {
            emulation = Scheduler.emulation!!
            title.setText(R.string.title_emulation_pending)
            caption.setText(R.string.text_emulation_pending)
            expander.root.isVisible = false
            btnAction.isVisible = false
            progressBar.isIndeterminate = true
            progressBar.isVisible = true
        }

        addIntermediateListener {
            activity?.runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    progressBar.setProgress((span * it.progress).roundToInt(), true)
                } else {
                    progressBar.progress = (span * it.progress).roundToInt()
                }
                Scheduler.info?.duration?.let { t -> t - it.elapsed }?.let {
                    notifyTime(it)
                }
            }
        }

        fun stateListener(running: Boolean) {
            val info = Scheduler.info
            if (running) {
                if (info != null) {
                    notifyStarted(info)
                } else {
                    notifyPending()
                }
            } else {
                notifyStopped()
            }
        }

        stateListener(Scheduler.emulation != null)
        addEmulationStateListener { running ->
            activity?.runOnUiThread {
                stateListener(running)
            }
        }
    }

    private fun addIntermediateListener(l: (Intermediate) -> Unit) {
        listeners.add(Scheduler.addIntermediateListener(l))
    }

    private fun addEmulationStateListener(l: (Boolean) -> Unit) {
        listeners.add(Scheduler.onEmulationStateChanged(l))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapMotionPreview.onSaveInstanceState(outState)
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
        binding.mapMotionPreview.onDestroy()
        Scheduler.emulation = null
        removeMonitorWorker()
    }

    override fun onResume() {
        super.onResume()
        binding.mapMotionPreview.onResume()
        removeMonitorWorker()
    }

    override fun onPause() {
        super.onPause()
        binding.mapMotionPreview.onPause()
    }

    override fun onStop() {
        super.onStop()

        listeners.forEach { it.cancel() }
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
