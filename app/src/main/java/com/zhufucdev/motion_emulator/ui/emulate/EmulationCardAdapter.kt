package com.zhufucdev.motion_emulator.ui.emulate

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.zhufucdev.motion_emulator.data.Emulation
import com.zhufucdev.motion_emulator.hook_frontend.Scheduler
import com.zhufucdev.motion_emulator.ui.map.UnifiedMapFragment
import kotlinx.coroutines.runBlocking

class EmulationCardAdapter(fragment: Fragment, private val emulation: Emulation, val map: UnifiedMapFragment) :
    FragmentStateAdapter(fragment) {
    private val emulations = arrayListOf<String>()

    init {
        Scheduler.onEmulationStateChanged { id, start ->
            if (start && !emulations.contains(id)) {
                emulations.add(id)
                notifyItemInserted(emulations.size)
            }
        }
    }

    override fun getItemCount(): Int = emulations.size + 1

    override fun createFragment(position: Int): Fragment {
        return if (position > 0) {
            val fragment = EmulationAppFragment()
            fragment.arguments = bundleOf("target_id" to emulations[position - 1])
            fragment.emulation = emulation
            runBlocking {
                fragment.map = map.requireController()
            }
            fragment
        } else {
            val fragment = EmulationControlFragment()
            fragment.emulation = emulation
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