package com.zhufucdev.motion_emulator.ui.emulate

import androidx.fragment.app.Fragment
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.motion_emulator.provider.ListenCallback
import com.zhufucdev.motion_emulator.provider.Scheduler
import com.zhufucdev.stub.AgentState

/**
 * A [Fragment] capable of receiving emulation changes
 */
abstract class EmulationMonitoringFragment : Fragment() {
    private val listeners = mutableSetOf<ListenCallback>()


    protected fun addIntermediateListener(l: (String, Intermediate) -> Unit) {
        listeners.add(Scheduler.addIntermediateListener(l))
    }

    protected fun addEmulationStateListener(l: (String, AgentState) -> Unit) {
        listeners.add(Scheduler.onAgentStateChanged(l))
    }

    override fun onPause() {
        super.onPause()
        listeners.forEach {
            try {
                it.pause()
            } catch (e: IllegalStateException) {
                // do nothing
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listeners.forEach {
            try {
                it.resume()
            } catch (e: IllegalStateException) {
                // do nothing
            }
        }
    }
}