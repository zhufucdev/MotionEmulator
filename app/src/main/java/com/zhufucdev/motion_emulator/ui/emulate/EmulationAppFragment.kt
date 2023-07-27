package com.zhufucdev.motion_emulator.ui.emulate

import android.content.pm.PackageManager
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
import com.zhufucdev.motion_emulator.databinding.FragmentEmulationAppBinding
import com.zhufucdev.motion_emulator.provider.Scheduler
import com.zhufucdev.motion_emulator.ui.map.MapController
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.AgentState
import com.zhufucdev.stub.android
import com.zhufucdev.stub.toFixed
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
class EmulationAppFragment : EmulationMonitoringFragment() {
    private lateinit var id: String
    private lateinit var binding: FragmentEmulationAppBinding
    private lateinit var packageManager: PackageManager
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
                Scheduler.instance[id]?.duration?.let { t -> t - info.elapsed }?.let {
                    notifyTime(it)
                }
                map?.updateLocationIndicator(info.location.android())
            }
        }

        notifyState(AgentState.PENDING)
        addEmulationStateListener { id, state ->
            if (id != this.id) return@addEmulationStateListener
            requireActivity().runOnUiThread {
                notifyState(state)
            }
        }
    }

    private fun notifyState(state: AgentState) {
        val info = Scheduler.instance[id]
        when (state) {
            AgentState.NOT_JOINED -> notifyOffline()
            AgentState.PENDING -> notifyPending()
            AgentState.RUNNING -> notifyStarted(info!!)
            AgentState.CANCELED -> notifyStopped(R.string.title_emulation_canceled)
            AgentState.PAUSED -> TODO()
            AgentState.COMPLETED -> notifyStopped(R.string.title_emulation_completed)
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
        progressBar.setProgress((span * progress).roundToInt(), true)
    }

    private fun notifyOffline() {
        binding.titleEmulationStatus.setText(R.string.title_agent_offline)
        binding.textEmulationStatus.setText(R.string.text_emulation_pending)
        binding.textEmulationStatus.isVisible = true
        binding.stackMonitors.root.isVisible = false
        binding.stackAppReceived.root.isVisible = false
        binding.btnDetermine.isVisible = false
        binding.progressEmulation.isVisible = true
    }

    private fun notifyStopped(title: Int) {
        binding.titleEmulationStatus.text = getString(title)
        binding.textEmulationStatus.isVisible = true
        binding.stackMonitors.root.isVisible = false
        binding.stackAppReceived.root.isVisible = false
        binding.btnDetermine.setOnClickListener {
            Scheduler.startAgent(id)
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
            packageManager
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
            Scheduler.cancelAgent(id)
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
        notifyState(Scheduler.currentEmulationState(id))
    }
}