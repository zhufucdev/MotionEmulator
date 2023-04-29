package com.zhufucdev.motion_emulator.ui.emulate

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.AppMeta
import com.zhufucdev.motion_emulator.data.Emulation
import com.zhufucdev.motion_emulator.data.EmulationInfo
import com.zhufucdev.motion_emulator.databinding.FragmentEmulationAppBinding
import com.zhufucdev.motion_emulator.hook.android
import com.zhufucdev.motion_emulator.hook_frontend.*
import com.zhufucdev.motion_emulator.toFixed
import com.zhufucdev.motion_emulator.ui.map.MapController
import kotlin.math.roundToInt

class EmulationAppFragment : EmulationMonitoringFragment() {
    private lateinit var id: String
    private lateinit var binding: FragmentEmulationAppBinding
    private lateinit var packageManager: PackageManager
    var emulation: Emulation? = null
    var map: MapController? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        packageManager = requireContext().packageManager
        binding = FragmentEmulationAppBinding.inflate(layoutInflater, container, false)
        arguments?.let {
            id = it.getString("target_id")!!
        }
        val margined = FrameLayout(requireContext())
        margined.addView(binding.root)
        val margin =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12F, requireContext().resources.displayMetrics)
        margined.setPadding(margin.roundToInt())
        return margined
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addIntermediateListener { id, info ->
            if (id != this.id) return@addIntermediateListener

            requireActivity().runOnUiThread {
                notifyProgress(info.progress)
                Scheduler.info[id]?.duration?.let { t -> t - info.elapsed }?.let {
                    notifyTime(it)
                }
                map?.updateLocationIndicator(info.location.android())
            }
        }

        notifyState(Scheduler.emulation != null)
        addEmulationStateListener { id, running ->
            if (id != this.id) return@addEmulationStateListener
            requireActivity().runOnUiThread {
                notifyState(running)
            }
        }
    }

    private fun notifyState(running: Boolean) {
        val info = Scheduler.info[id]
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

    private fun notifyTime(remaining: Double) {
        val t = remaining.toFixed(2)
        val context = requireContext()
        binding.stackMonitors.textStatusTime.text =
            context.getString(
                R.string.status_remaining,
                "$t ${context.getString(R.string.suffix_second)}"
            )
    }

    private fun notifyProgress(progress: Float) {
        val progressBar = binding.progressEmulation
        val span = progressBar.max
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            progressBar.setProgress((span * progress).roundToInt(), true)
        } else {
            progressBar.progress = (span * progress).roundToInt()
        }
    }

    private fun notifyStopped() {
        binding.titleEmulationStatus.text = getString(R.string.title_emulation_stopped)
        binding.textEmulationStatus.isVisible = true
        binding.stackMonitors.root.isVisible = false
        binding.stackAppReceived.root.isVisible = false
        binding.btnDetermine.setOnClickListener {
            Scheduler.emulation = emulation
            notifyPending()
        }
        binding.btnDetermine.setText(R.string.action_restart)
        binding.btnDetermine.isVisible = true
        binding.progressEmulation.isVisible = false
    }

    private fun notifyStarted(info: EmulationInfo) {
        val app = binding.stackAppReceived
        val context = requireContext()

        binding.titleEmulationStatus.text =
            context.getString(R.string.title_named_emulation_ongoing, id.substring(0..4))
        val appInfo = AppMeta.of(
            packageManager.getApplicationInfo(info.owner, PackageManager.GET_META_DATA),
            packageManager,
            true
        )
        app.textAppPicked.text = getString(R.string.text_app_received, appInfo.name)
        app.iconView.setImageDrawable(appInfo.icon)
        binding.stackMonitors.textStatusVelocity.text =
            context.getString(
                R.string.status_velocity,
                "${Scheduler.emulation!!.velocity} ${context.getString(R.string.suffix_velocity)}"
            )
        binding.stackMonitors.textStatusLength.text =
            context.getString(
                R.string.status_total,
                "${info.length.toFixed(2)}${context.getString(R.string.suffix_meter)}"
            )
        notifyTime(info.duration)
        binding.stackMonitors.root.isVisible = true
        binding.textEmulationStatus.isVisible = false

        binding.btnDetermine.setOnClickListener {
            Scheduler.setInfo(id, null)
        }
        binding.btnDetermine.setText(R.string.action_determine)
        binding.btnDetermine.isVisible = true
        binding.progressEmulation.apply {
            isVisible = true
            isIndeterminate = false
        }

        // recover from missed intermediate
        Scheduler.intermediate[id]?.let {
            notifyProgress(it.progress)
            map?.updateLocationIndicator(it.location.android())
            map?.moveCamera(it.location, focus = true, animate = true)
        }
    }

    private fun notifyPending() {
        binding.titleEmulationStatus.setText(R.string.title_emulation_pending)
        binding.textEmulationStatus.setText(R.string.text_emulation_app_pending)
        binding.textEmulationStatus.isVisible = true
        binding.stackMonitors.root.isVisible = false
        binding.stackAppReceived.root.isVisible = false
        binding.btnDetermine.isVisible = false
        binding.progressEmulation.isIndeterminate = true
        binding.progressEmulation.isVisible = true
    }

    override fun onResume() {
        super.onResume()
        notifyState(Scheduler.emulation != null)
    }
}