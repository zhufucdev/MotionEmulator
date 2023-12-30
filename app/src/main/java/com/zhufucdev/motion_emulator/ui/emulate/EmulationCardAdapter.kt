package com.zhufucdev.motion_emulator.ui.emulate

import android.os.Handler
import android.os.Looper
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.zhufucdev.motion_emulator.provider.Scheduler
import com.zhufucdev.motion_emulator.ui.map.UnifiedMapFragment
import com.zhufucdev.me.stub.AgentState
import kotlinx.coroutines.runBlocking

class EmulationCardAdapter(fragment: Fragment, val map: UnifiedMapFragment) :
    FragmentStateAdapter(fragment) {
    private val emulations = arrayListOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    init {
        Scheduler.onAgentStateChanged { id, state ->
            handler.post {
                if (state == AgentState.RUNNING && !emulations.contains(id)) {
                    emulations.add(id)
                    notifyItemInserted(emulations.size)
                }
            }
        }
    }

    override fun getItemCount(): Int = emulations.size + 1

    override fun createFragment(position: Int): Fragment {
        return if (position > 0) {
            val fragment = EmulationAppFragment()
            fragment.arguments = bundleOf("target_id" to emulations[position - 1])
            runBlocking {
                fragment.map = map.requireController()
            }
            fragment
        } else {
            val fragment = EmulationControlFragment()
            fragment
        }
    }

    override fun getItemId(position: Int): Long {
        return if (position > 0) {
            emulations[position - 1].hashCode().toLong()
        } else {
            0
        }
    }
}