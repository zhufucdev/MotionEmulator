package com.zhufucdev.motion_emulator.collect

import android.hardware.Sensor
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.annotation.AttrRes
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.withStyledAttributes
import androidx.core.view.setMargins
import androidx.core.view.updateLayoutParams
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton.OnChangedCallback
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Motion
import com.zhufucdev.motion_emulator.data.RecordCallback
import com.zhufucdev.motion_emulator.data.Recorder
import com.zhufucdev.motion_emulator.data.Records
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * To display current motion data.
 */
class RecordDataFragment : Fragment() {
    private lateinit var recorder: RecordCallback
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var types: ArrayList<Int>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        types = arguments?.getIntegerArrayList("types") ?: defaultSensors
        recorder = Recorder.start(types)
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
        fab.setOnClickListener {
            val summary = recorder.summarize()
            Records.store(summary, requireContext())
            requireActivity().finish()
        }
        return root
    }

    private fun generateChart(type: Int): View {
        val data = LineData()
        val chart = LineChart(requireContext())
        chart.stylize()
        chart.description.text = getString(sensorNames[type]!!)

        recorder.onUpdate(type) { moment ->
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

    private fun LineChart.stylize() {
        val onSurface = getAttrColor(com.google.android.material.R.attr.colorOnSurface)
        setBorderColor(onSurface)
        description.textColor = onSurface
        legend.textColor = onSurface
        listOf(axisLeft, xAxis).forEach {
            it.textColor = onSurface
            it.axisLineColor = onSurface
        }
        axisRight.isEnabled = false
    }

    private fun getAttrColor(@AttrRes id: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme
            .resolveAttribute(id, typedValue, true)
        return typedValue.data
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
        recorder.summarize()
    }
}

const val maxDisplayWidth = 50

val defaultSensors
    get() = ArrayList(sensorNames.keys.toList())

val sensorNames = mapOf(
    Sensor.TYPE_ACCELEROMETER to R.string.name_sensor_acc,
    Sensor.TYPE_GYROSCOPE to R.string.name_sensor_gyroscope,
    Sensor.TYPE_STEP_COUNTER to R.string.name_sensor_step_counter,
    Sensor.TYPE_STEP_DETECTOR to R.string.name_sensor_step_detec,
    Sensor.TYPE_LIGHT to R.string.name_sensor_light
)

val sensorValueLabels = listOf("x", "y", "z", "w")