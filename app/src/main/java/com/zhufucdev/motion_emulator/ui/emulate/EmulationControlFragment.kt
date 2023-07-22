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
import com.zhufucdev.stub.Emulation
import com.zhufucdev.motion_emulator.provider.Scheduler
import kotlin.math.roundToInt

class EmulationControlFragment : EmulationMonitoringFragment() {
    private lateinit var binding: FragmentEmulationControlBinding
    var emulation: Emulation? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentEmulationControlBinding.inflate(inflater, container, false)
        val margined = FrameLayout(requireContext())
        margined.addView(binding.root)
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12F, requireContext().resources.displayMetrics)
        margined.setPadding(margin.roundToInt())
        return margined
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notifyState(Scheduler.emulation != null)
        addEmulationStateListener { _, started ->
            notifyState(started)
        }
    }

    private fun notifyState(started: Boolean) {
        val running = Scheduler.info.isEmpty()
        requireActivity().runOnUiThread {
            if (started && !running) {
                notifyRunning()
            } else if (!started) {
                notifyStopped()
            } else {
                notifyPending()
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
            Scheduler.emulation = null
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
            Scheduler.emulation = null
            notifyStopped()
        }
    }

    private fun notifyStopped() {
        val emu = emulation
        binding.progressEmulation.isVisible = false
        binding.titleEmulationStatus.setText(R.string.title_emulation_stopped)
        binding.textEmulationStatus.text = null

        if (emu != null) {
            binding.btnDetermine.setText(R.string.action_restart)
            binding.btnDetermine.isVisible = true
            binding.btnDetermine.setOnClickListener {
                Scheduler.emulation = emu
                notifyPending()
            }
        } else {
            binding.btnDetermine.isVisible = false
        }
    }

    override fun onResume() {
        super.onResume()
        notifyState(Scheduler.emulation != null)
    }
}