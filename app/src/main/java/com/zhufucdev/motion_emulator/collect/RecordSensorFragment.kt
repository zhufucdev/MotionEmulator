package com.zhufucdev.motion_emulator.collect

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
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
    private lateinit var sm: SensorManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecordSensorBinding.inflate(layoutInflater, container, false)
        sm = requireContext().getSystemService(SensorManager::class.java)
        binding.btnContinue.setOnClickListener {
            findNavController()
                .navigate(
                    R.id.action_recordSensorFragment_to_recordDataFragment,
                    bundleOf(
                        "types" to getSensorTypes(),
                        "telephony" to binding.switchTelephony.isChecked
                    )
                )
        }
        return binding.root
    }

    private fun getSensorTypes(): ArrayList<Int> = ArrayList(mapOf(
        Sensor.TYPE_ACCELEROMETER to binding.switchAcc,
        Sensor.TYPE_STEP_DETECTOR to binding.switchStepDetec,
        Sensor.TYPE_STEP_COUNTER to binding.switchStepCounter,
        Sensor.TYPE_GYROSCOPE to binding.switchGyroscope,
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED to binding.switchGyroscope,
        Sensor.TYPE_MAGNETIC_FIELD to binding.switchMagnetic,
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED to binding.switchMagnetic,
        Sensor.TYPE_LINEAR_ACCELERATION to binding.switchLinearAcc,
        Sensor.TYPE_LIGHT to binding.switchLight
    ).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            it.plus(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED to binding.switchAcc)
        } else {
            it
        }
    }.mapNotNull { e ->
        e.key.takeIf { e.value.isChecked && sm.getDefaultSensor(e.key) != null }
    })
}