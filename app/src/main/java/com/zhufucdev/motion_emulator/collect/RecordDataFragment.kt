package com.zhufucdev.motion_emulator.collect

import android.hardware.Sensor
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.setMargins
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.getAttrColor

/**
 * To display current motion data.
 */
class RecordDataFragment : Fragment() {
    private lateinit var motion: MotionCallback
    private lateinit var telephony: TelephonyRecordCallback
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var types: ArrayList<Int>
    private var useTelephony = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        types = arguments?.getIntegerArrayList("types") ?: defaultSensors
        useTelephony = arguments?.getBoolean("telephony") ?: false
        motion = MotionRecorder.start(types)
        if (useTelephony)
            telephony = TelephonyRecorder.start()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_record_data, container, false)
        val records = root.findViewById<LinearLayoutCompat>(R.id.record_container)
        fab = root.findViewById(R.id.btn_stop)
        types.forEach {
            records.addView(generateChart(it))
        }
        if (useTelephony)
            records.addView(telephonyChart())
        fab.setOnClickListener {
            val motions = motion.summarize()
            val cellTimeline = telephony.summarize()
            Motions.store(motions)
            Cells.store(cellTimeline)
            requireActivity().finish()
        }
        return root
    }

    private fun generateChart(type: Int): View {
        val data = LineData()
        val chart = LineChart(requireContext())
        chart.stylize()
        chart.description.text = getString(sensorNames[type]!!)

        motion.onUpdate(type) { moment ->
            val values = moment.data[type]!!
            if (data.dataSets.isEmpty()) {
                values.forEachIndexed { index, v ->
                    data.addDataSet(
                        LineDataSet(
                            arrayListOf(Entry(moment.elapsed, v)),
                            sensorValueLabels[index]
                        )
                    )
                }
            } else {
                values.forEachIndexed { index, v ->
                    data.addEntry(
                        Entry(moment.elapsed, v),
                        index
                    )
                }
                while (data.dataSets.first().entryCount > maxDisplayWidth) {
                    data.dataSets.forEach {
                        it.removeFirst()
                    }
                }
            }
            requireActivity().runOnUiThread {
                data.notifyDataChanged()
                chart.data = data
                chart.invalidate()
            }
        }
        chart.data = data
        chart.invalidate()
        chart.layout()
        return chart
    }

    private fun telephonyChart(): View {
        
        telephony.onUpdate {

        }
    }

    private fun LineChart.stylize() {
        val onSurface = getAttrColor(com.google.android.material.R.attr.colorOnSurface, requireContext())
        setBorderColor(onSurface)
        description.textColor = onSurface
        legend.textColor = onSurface
        listOf(axisLeft, xAxis).forEach {
            it.textColor = onSurface
            it.axisLineColor = onSurface
        }
        axisRight.isEnabled = false
    }

    private fun LineChart.layout() {
        val matchParent = LinearLayoutCompat.LayoutParams.MATCH_PARENT
        layoutParams =
            LinearLayoutCompat.LayoutParams(matchParent, resources.getDimensionPixelSize(R.dimen.chart_height))
        updateLayoutParams<LinearLayoutCompat.LayoutParams> {
            setMargins(resources.getDimensionPixelSize(R.dimen.card_margin))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        motion.summarize()
    }
}

const val maxDisplayWidth = 50

val defaultSensors
    get() = ArrayList(sensorNames.keys.toList())

val sensorNames = mapOf(
    Sensor.TYPE_ACCELEROMETER to R.string.name_sensor_acc,
    Sensor.TYPE_GYROSCOPE to R.string.name_sensor_gyroscope,
    Sensor.TYPE_GYROSCOPE_UNCALIBRATED to R.string.name_sensor_gyroscope_uncal,
    Sensor.TYPE_STEP_COUNTER to R.string.name_sensor_step_counter,
    Sensor.TYPE_STEP_DETECTOR to R.string.name_sensor_step_detec,
    Sensor.TYPE_MAGNETIC_FIELD to R.string.name_sensor_magnetic,
    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED to R.string.name_sensor_magnetic_uncal,
    Sensor.TYPE_LINEAR_ACCELERATION to R.string.name_sensor_linear_acc,
    Sensor.TYPE_LIGHT to R.string.name_sensor_light
).let {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        it.plus(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED to R.string.name_sensor_acc_uncal)
    } else {
        it
    }
}

val sensorValueLabels = listOf("x", "y", "z", "v", "h", "w", "i", "j")