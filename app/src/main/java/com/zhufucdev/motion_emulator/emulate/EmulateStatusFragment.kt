package com.zhufucdev.motion_emulator.emulate

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
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
        emulation = Scheduler.emulation!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEmulateStatusBinding.inflate(layoutInflater, container, false)
        binding.mapMotionPreview.onCreate(savedInstanceState)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeMap()
        initializeMonitors()
    }

    private fun initializeMap() {
        val map = binding.mapMotionPreview.map
        map.uiSettings.isZoomControlsEnabled = false
        map.unifyTheme(resources)

        val naviArrow = NavigateArrowOptions()
        var lastNav: NavigateArrow? = null
        addEmulationListener {
            lastNav?.remove()
            naviArrow.add(it.location.toLatLng())
            requireActivity().runOnUiThread {
                lastNav = map.addNavigateArrow(naviArrow)
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
            title.setText(R.string.title_emulation_pending)
            caption.setText(R.string.text_emulation_pending)
            expander.root.isVisible = false
            btnAction.isVisible = false
            progressBar.isIndeterminate = true
        }

        addEmulationListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                progressBar.setProgress((span * it.progress).roundToInt(), true)
            } else {
                progressBar.progress = (span * it.progress).roundToInt()
            }
            notifyTime(Scheduler.info!!.duration - it.elapsed)
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

        stateListener(true)
        Scheduler.onEmulationStateChanged { running ->
            requireActivity().runOnUiThread {
                stateListener(running)
            }
        }
    }

    private fun addEmulationListener(l: (Intermediate) -> Unit) {
        listeners.add(Scheduler.addIntermediateListener(l))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapMotionPreview.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapMotionPreview.onDestroy()
        listeners.forEach { it.cancel() }
    }

    override fun onResume() {
        super.onResume()
        binding.mapMotionPreview.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapMotionPreview.onPause()
    }
}