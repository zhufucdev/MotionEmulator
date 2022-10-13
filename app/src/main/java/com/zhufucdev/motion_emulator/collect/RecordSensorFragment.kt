package com.zhufucdev.motion_emulator.collect

import android.hardware.Sensor
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.databinding.FragmentRecordSensorBinding

/**
 * To choose which sensors to record.
 */
class RecordSensorFragment : Fragment() {

    private lateinit var binding: FragmentRecordSensorBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecordSensorBinding.inflate(layoutInflater, container, false)
        binding.btnContinue.setOnClickListener {
            findNavController()
                .navigate(
                    R.id.action_recordSensorFragment_to_recordDataFragment,
                    bundleOf("types" to getSensorTypes())
                )
        }
        return binding.root
    }

    private fun getSensorTypes(): ArrayList<Int> = ArrayList(mapOf(
            binding.switchAcc to Sensor.TYPE_ACCELEROMETER,
            binding.switchStepDetec to Sensor.TYPE_STEP_DETECTOR,
            binding.switchStepCounter to Sensor.TYPE_STEP_COUNTER,
            binding.switchGyroscope to Sensor.TYPE_GYROSCOPE,
            binding.switchLight to Sensor.TYPE_LIGHT
        ).mapNotNull { e ->
            e.value.takeIf { e.key.isChecked }
        })
}