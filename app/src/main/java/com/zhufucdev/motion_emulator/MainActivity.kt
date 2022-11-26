package com.zhufucdev.motion_emulator

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.highcapable.yukihookapi.YukiHookAPI
import com.zhufucdev.motion_emulator.data.Cells
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarToolbar)
        updateStatus()
        registerListeners()

        Traces.require(this)
        Motions.require(this)
        Cells.require(this)
    }

    private fun updateStatus() {
        if (YukiHookAPI.Status.isModuleActive) {
            binding.statusTitle.setText(R.string.title_status_activated)
            binding.statusSubtitle.setText(R.string.text_status_activated)
            binding.statusCard.setCardBackgroundColor(resources.getColor(R.color.success, null))
            binding.statusIcon.setImageResource(R.drawable.ic_baseline_done_all_24)
        } else {
            binding.statusTitle.setText(R.string.title_status_inactivated)
            binding.statusSubtitle.setText(R.string.text_status_inactivated)
            binding.statusCard.setCardBackgroundColor(resources.getColor(R.color.error, null))
            binding.statusIcon.setImageResource(R.drawable.ic_baseline_error_outline_24)
        }
    }

    private fun registerListeners() {
        binding.statusCard.setOnClickListener {
            startActivity(
                Intent(this, AppStrategyActivity::class.java)
            )
        }
        binding.recordCard.setOnClickListener {
            startActivity(
                Intent(this, RecordActivity::class.java)
            )
        }
        binding.traceCard.setOnClickListener {
            startActivity(
                Intent(this, TraceDrawingActivity::class.java)
            )
        }
        binding.emulateCard.setOnClickListener {
            startActivity(
                Intent(this, EmulateActivity::class.java)
            )
        }
    }

    override fun onStart() {
        super.onStart()
        updateStatus()
    }
}