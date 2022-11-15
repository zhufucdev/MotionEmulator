package com.zhufucdev.motion_emulator.emulate

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.databinding.FragmentConfigurationBinding
import com.zhufucdev.motion_emulator.hook.center
import com.zhufucdev.motion_emulator.hook_frontend.Emulation
import com.zhufucdev.motion_emulator.hook_frontend.Scheduler

class ConfigurationFragment : Fragment() {
    private lateinit var binding: FragmentConfigurationBinding
    private lateinit var btnRun: ExtendedFloatingActionButton

    private var motion: Motion? = null
    private var trace: Trace? = null
    private var cells: CellTimeline? = null
    private var repeatCount: Int? = 1
    private var velocity: Double? = 3.0
    private var satelliteCount: Int? = 10

    private var drawnTrace: Pair<Polyline, Marker>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        skipAmapFuckingLicense(requireContext())

        (requireActivity() as EmulateActivity).ready {
            btnRun = it
            initializeOthers()
            notifyFab()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // inflate and bind for this fragment
        binding = FragmentConfigurationBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapTracePreview.onCreate(savedInstanceState)

        initMotionDropdown()
        initCellsDropdown()
        initializeMap()
        initTracesDropdown()
    }

    private fun startEmulation() {
        val repeat = this.repeatCount
        val motion = this.motion
        val trace = this.trace
        val cells = this.cells
        val velocity = this.velocity
        val satellites = this.satelliteCount
        if (repeat == null
            || repeat <= 0
            || velocity == null
            || velocity <= 0
            || satellites == null
            || satellites < 0
            || trace == null
            || cells == null
            || motion == null
        ) {
            return
        }
        Scheduler.emulation = Emulation(trace, motion, cells, velocity, repeat, satellites)
        findNavController().navigate(R.id.action_configurationFragment_to_emulateStatusFragment)
        btnRun.hide()
    }

    private fun initMotionDropdown() {
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        val motions = Motions.list()
        motions.forEach {
            adapter.add(dateString(it.time))
        }
        binding.dropdownMotion.apply {
            setOnItemClickListener { _, _, position, _ ->
                motion = motions[position]
                notifyFab()
            }
            setAdapter(adapter)
        }
    }

    private fun initCellsDropdown() {
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        val timelines = Cells.list()
        timelines.forEach {
            adapter.add(dateString(it.time))
        }
        binding.dropdownCells.apply {
            setOnItemClickListener { _, _, position, _ ->
                cells = timelines[position]
                notifyFab()
            }
            setAdapter(adapter)
        }
    }

    private fun select(trace: Trace) {
        this.trace = trace
        notifyFab()

        drawnTrace?.apply {
            first.remove()
            second.remove()
        }
        val map = binding.mapTracePreview.map
        val drawn = trace.drawOnMap(map)
        drawn.second.position
        drawnTrace = drawn
    }

    private fun initTracesDropdown() {
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        val traces = Traces.list()
        traces.forEach {
            adapter.add(it.name)
        }
        binding.dropdownTrace.apply {
            setOnItemClickListener { _, _, position, _ ->
                select(traces[position])
            }
            setAdapter(adapter)
        }
    }

    private fun notifyFab() {
        if (!binding.inputVelocity.error.isNullOrEmpty()
            || !binding.inputRepeatCount.error.isNullOrEmpty()
            || !binding.inputSatellite.error.isNullOrBlank()
            || motion == null
            || trace == null
            || cells == null
        )
            btnRun.hide()
        else
            btnRun.show()
    }

    private fun initializeOthers() {
        binding.inputVelocity.doAfterTextChanged {
            velocity = if (it.isNullOrEmpty()) {
                null
            } else {
                it.toString().toDoubleOrNull() ?: Double.NaN
            }
            binding.inputVelocity.error =
                if (velocity == null) getString(R.string.text_field_must_not_empty)
                else if (velocity!! <= 0) getString(R.string.text_field_must_not_neg_or_zero)
                else null
            notifyFab()
        }

        fun setError(input: TextInputEditText, value: Int?, filterNegative: Boolean) {
            input.error =
                if (value == null) getString(R.string.text_field_must_not_empty)
                else if (filterNegative && value <= 0) getString(R.string.text_field_must_not_neg_or_zero)
                else null
            notifyFab()
        }
        binding.inputRepeatCount.doAfterTextChanged {
            repeatCount = if (it.isNullOrEmpty()) {
                null
            } else {
                it.toString().toIntOrNull()
            }
            setError(binding.inputRepeatCount, repeatCount, true)
            notifyFab()
        }
        binding.inputSatellite.doAfterTextChanged {
            satelliteCount = if (it.isNullOrEmpty()) {
                null
            } else {
                it.toString().toIntOrNull()
            }
            setError(binding.inputSatellite, satelliteCount, false)
            notifyFab()
        }

        btnRun.setOnClickListener {
            disable()
            startEmulation()
        }
    }

    private fun initializeMap() {
        val amap = binding.mapTracePreview.map

        amap.uiSettings.apply {
            isZoomControlsEnabled = false
            isZoomGesturesEnabled = false
            isScrollGesturesEnabled = false
            isRotateGesturesEnabled = false
        }
        amap.unifyTheme(resources)
        amap.isMyLocationEnabled = false
    }

    private val inputWrappers
        get() = listOf(
            binding.wrapperDropdown,
            binding.wrapperVelocity,
            binding.wrapperRepeatCount,
            binding.wrapperCellsDropdown,
            binding.wrapperSatellite
        )

    private fun disable() {
        btnRun.hide()
        inputWrappers.forEach { it.isEnabled = false }
    }

    private fun Trace.drawOnMap(amap: AMap): Pair<Polyline, Marker> {
        val polyline = PolylineOptions()
        polyline.color(requireContext().getColor(R.color.purple_500))

        var length = 0.0
        points.forEachIndexed { i, point ->
            polyline.add(point.toLatLng())
            if (i > 0) {
                length += AMapUtils.calculateLineDistance(point.toLatLng(), points[i - 1].toLatLng())
            }
        }
        val center = center(length)
        val marker = amap.addMarker(
            MarkerOptions()
                .position(center.toLatLng())
                .draggable(false)
        )
        val polylineInstance = amap.addPolyline(polyline)
        return polylineInstance to marker
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapTracePreview.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapTracePreview.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        binding.mapTracePreview.onResume()
        notifyFab()
    }

    override fun onPause() {
        super.onPause()
        binding.mapTracePreview.onPause()
    }
}