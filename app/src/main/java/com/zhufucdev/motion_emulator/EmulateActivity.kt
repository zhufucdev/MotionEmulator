package com.zhufucdev.motion_emulator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
        setSupportActionBar(binding.appBarToolbar)

        Traces.require(this)
        Motions.require(this)
        Cells.require(this)
    }
}