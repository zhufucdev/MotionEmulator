package com.zhufucdev.motion_emulator.ui.emulate

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.databinding.FragmentEmulationControlBinding
import com.zhufucdev.motion_emulator.provider.Scheduler
import com.zhufucdev.me.stub.AgentState
import kotlin.math.roundToInt

class EmulationControlFragment : EmulationMonitoringFragment() {
    private lateinit var binding: FragmentEmulationControlBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEmulationControlBinding.inflate(inflater, container, false)
        val margined = FrameLayout(requireContext())
        margined.addView(binding.root)
        val margin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            12F,
            requireContext().resources.displayMetrics
        )
        margined.setPadding(margin.roundToInt())
        return margined
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notifyState(Scheduler.controllerState)
        addEmulationStateListener { _, _ ->
            notifyState(Scheduler.controllerState)
        }
    }

    private fun notifyState(state: AgentState) {
        requireActivity().runOnUiThread {
            when (state) {
                AgentState.PENDING -> notifyPending()
                AgentState.CANCELED -> notifyStopped()
                AgentState.RUNNING -> notifyRunning()
                AgentState.PAUSED -> notifyOffline()
                else -> notifyStopped()
            }
        }
    }

    private fun notifyRunning() {
        binding.progressEmulation.isVisible = false
        binding.btnDetermine.isVisible = true
        binding.btnDetermine.setText(R.string.action_determine)
        binding.titleEmulationStatus.setText(R.string.title_emulation_ongoing)
        binding.textEmulationStatus.setText(R.string.text_swipe_to_see_more)

        binding.btnDetermine.setOnClickListener {
            Scheduler.cancelAll()
            notifyStopped()
        }
    }

    private fun notifyPending() {
        binding.progressEmulation.isVisible = true
        binding.btnDetermine.isVisible = true
        binding.btnDetermine.setText(R.string.action_determine)
        binding.titleEmulationStatus.setText(R.string.title_emulation_pending)
        binding.textEmulationStatus.setText(R.string.text_emulation_pending)

        binding.btnDetermine.setOnClickListener {
            Scheduler.cancelAll()
            notifyStopped()
        }
    }

    private fun notifyOffline() {
        binding.progressEmulation.isVisible = false
        binding.titleEmulationStatus.setText(R.string.title_controller_offline)
        binding.textEmulationStatus.setText(R.string.text_controller_offline)
        binding.btnDetermine.isVisible = false
    }

    private fun notifyStopped() {
        binding.progressEmulation.isVisible = false
        binding.titleEmulationStatus.setText(R.string.title_emulation_canceled)
        binding.textEmulationStatus.text = null

        binding.btnDetermine.setText(R.string.action_restart)
        binding.btnDetermine.isVisible = true
        binding.btnDetermine.setOnClickListener {
            Scheduler.startAll()
            notifyPending()
        }
    }

    override fun onResume() {
        super.onResume()
        notifyState(Scheduler.controllerState)
    }
}