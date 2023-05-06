package com.zhufucdev.motion_emulator.ui.emulate

import androidx.fragment.app.Fragment
import com.zhufucdev.data.Intermediate
import com.zhufucdev.motion_emulator.provider.ListenCallback
import com.zhufucdev.motion_emulator.provider.Scheduler

/**
 * A [Fragment] capable of receiving emulation changes
 */
abstract class EmulationMonitoringFragment : Fragment() {
    private val listeners = mutableSetOf<ListenCallback>()


    protected fun addIntermediateListener(l: (String, Intermediate) -> Unit) {
        listeners.add(Scheduler.addIntermediateListener(l))
    }

    protected fun addEmulationStateListener(l: (String, Boolean) -> Unit) {
        listeners.add(Scheduler.onEmulationStateChanged(l))
    }

    override fun onPause() {
        super.onPause()
        listeners.forEach {
            try {
                it.cancel()
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