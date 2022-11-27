package com.zhufucdev.motion_emulator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.zhufucdev.motion_emulator.data.Cells
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.databinding.ActivityEmulateBinding

class EmulateActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEmulateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmulateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navController = findNavController(R.id.nav_host_fragment_activity_emulate)
        initializeToolbar(binding.appBarToolbar, navController)

        Traces.require(this)
        Motions.require(this)
        Cells.require(this)
    }
}