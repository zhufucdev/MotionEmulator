package com.zhufucdev.motion_emulator.ui.collect

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.setMargins
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.zhufucdev.stub.CellMoment
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
            Motions.store(motions)
            if (useTelephony) {
                val cellTimeline = telephony.summarize()
                Cells.store(cellTimeline)
            }
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
                    if (index < sensorValueLabels.size) {
                        data.addDataSet(
                            LineDataSet(
                                arrayListOf(Entry(moment.elapsed, v)),
                                sensorValueLabels[index]
                            )
                        )
                    } else {
                        data.addDataSet(
                            LineDataSet(
                                arrayListOf(Entry(moment.elapsed, v)),
                                "U"
                            )
                        )
                    }
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

    @SuppressLint("NewApi")
    private fun telephonyChart(): View {
        fun generateData(moment: CellMoment) = BarData(
            BarDataSet(
                moment.cell.mapIndexed { i, c ->
                    BarEntry(i.toFloat(), c.cellSignalStrength.dbm.toFloat())
                },
                "BarDataSet"
            )
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            LinearLayoutCompat(requireContext()).apply {
                orientation = LinearLayoutCompat.VERTICAL
                val title = TextView(requireContext()).apply {
                    text = getString(R.string.title_telephony_recording, "-1")
                }
                addView(title)
                addView(
                    BarChart(requireContext()).apply {
                        telephony.onUpdate {
                            data = generateData(it)
                            invalidate()
                            title.text = getString(R.string.title_telephony_recording, it.elapsed.toString())
                        }
                        stylize()
                        legend.isEnabled = false
                        description.text = getString(R.string.name_cell_signal)
                        setFitBars(true)
                        layout()
                    }
                )
            }
        } else {
            TextView(requireContext()).apply {
                text = getString(R.string.title_telephony_recording, "-1")
                var containsLocation = false
                telephony.onUpdate {
                    text = buildString {
                        appendLine(getString(R.string.title_telephony_recording, it.elapsed.toString()))
                        appendLine(getString(R.string.text_telephony_recording_neighboring, it.neighboring.size))
                        if (it.location != null || containsLocation) {
                            appendLine(getString(R.string.text_telephony_recording_location))
                            containsLocation = true
                        }
                    }
                }
            }
        }
    }

    private fun BarLineChartBase<*>.stylize() {
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

    private fun View.layout() {
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
