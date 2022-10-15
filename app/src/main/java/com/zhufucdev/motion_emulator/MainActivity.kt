package com.zhufucdev.motion_emulator

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.highcapable.yukihookapi.YukiHookAPI
import com.zhufucdev.motion_emulator.data.Records
import com.zhufucdev.motion_emulator.databinding.ActivityMainBinding
import com.zhufucdev.motion_emulator.hook.SensorHandler

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateStatus()
        registerListeners()
        Records.readAll(this)
        Log.d("Serialization", "Loaded ${Records.list().size} pieces.")
        SensorHandler.init(this)
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
    }
}