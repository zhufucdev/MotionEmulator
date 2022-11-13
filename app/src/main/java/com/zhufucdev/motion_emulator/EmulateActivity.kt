package com.zhufucdev.motion_emulator

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.databinding.ActivityEmulateBinding
import com.zhufucdev.motion_emulator.hook.center
import com.zhufucdev.motion_emulator.hook_frontend.Emulation
import com.zhufucdev.motion_emulator.hook_frontend.Scheduler

class EmulateActivity : AppCompatActivity(R.layout.activity_emulate) {
    private lateinit var binding: ActivityEmulateBinding
    private var motion: Motion? = null
    private var trace: Trace? = null
    private var cells: CellTimeline? = null
    private var repeatCount: Int? = 1
    private var velocity: Double? = 3.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        skipAmapFuckingLicense(this)
        binding = ActivityEmulateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mapMotionPreview.onCreate(savedInstanceState)

        Traces.require(this)
        Motions.require(this)
        Cells.require(this)

        initMotionDropdown()
        initCellsDropdown()
        initializeMap()
        initializeOthers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapMotionPreview.onSaveInstanceState(outState)
    }

    private fun toggleEmulation() {
        if (Scheduler.emulation != null) {
            Scheduler.emulation = null
        } else {
            val repeat = this.repeatCount
            val motion = this.motion
            val trace = this.trace
            val cells = this.cells
            val velocity = this.velocity
            if (repeat == null
                || repeat <= 0
                || velocity == null
                || velocity <= 0
                || trace == null
                || cells == null
                || motion == null) {
                return
            }
            Scheduler.emulation = Emulation(trace, motion, cells, velocity, repeat)
        }
    }

    private fun initMotionDropdown() {
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val motions = Motions.list()
        motions.forEach {
            adapter.add(dateString(it.time))
        }
        binding.dropdownMotion.apply {
            setOnItemClickListener { _, _, position, _ ->
                motion = motions[position]
            }
            setAdapter(adapter)
        }
    }

    private fun initCellsDropdown() {
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val timelines = Cells.list()
        timelines.forEach {
            adapter.add(dateString(it.time))
        }
        binding.dropdownCells.apply {
            setOnItemClickListener { _, _, position, _ ->
                cells = timelines[position]
            }
            setAdapter(adapter)
        }
    }

    private fun initializeOthers() {
        fun notifyFab() {
            if (!binding.inputVelocity.error.isNullOrEmpty() && !binding.inputRepeatCount.error.isNullOrEmpty())
                binding.btnStartEmulation.hide()
            else
                binding.btnStartEmulation.show()
        }

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
        binding.inputRepeatCount.doAfterTextChanged {
            repeatCount = if (it.isNullOrEmpty()) {
                null
            } else {
                it.toString().toIntOrNull()
            }
            binding.inputRepeatCount.error =
                if (repeatCount == null) getString(R.string.text_field_must_not_empty)
                else if (repeatCount!! <= 0) getString(R.string.text_field_must_not_neg_or_zero)
                else null
            notifyFab()
        }

        binding.btnStartEmulation.setOnClickListener {
            toggleEmulation()
        }
        Scheduler.onEmulationStateChanged { running ->
            runOnUiThread {
                if (running) {
                    disableEmulationMonitor()
                } else {
                    enableEmulationMonitor()
                }
            }
        }
    }

    private fun initializeMap() {
        val amap = binding.mapMotionPreview.map

        amap.uiSettings.isZoomControlsEnabled = false
        amap.mapType = if (isDarkModeEnabled(resources)) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
        amap.isMyLocationEnabled = false

        val traceByLine = hashMapOf<Polyline, Trace>()
        val traceByMarker = hashMapOf<Marker, Trace>()
        val selectedColors = hashMapOf<Polyline, Int>()

        Traces.list().forEachIndexed { index, trace ->
            val result = trace.drawOnMap(amap, index)
            traceByLine[result.first] = trace
            traceByMarker[result.second] = trace
            selectedColors[result.first] = result.first.color
        }

        fun select(trace: Trace) {
            this.trace = trace
            val polyline = traceByLine.entries.first { it.value == trace }.key
            Snackbar
                .make(binding.root, getString(R.string.text_trace_selected, trace.name), Snackbar.LENGTH_LONG)
                .setAnchorView(binding.btnStartEmulation)
                .show()
            polyline.color = getColor(R.color.teal_700)
            traceByLine.entries.forEach { (e, _) ->
                if (polyline.id != e.id) {
                    e.color = selectedColors[e]!!
                }
            }
        }

        amap.setOnPolylineClickListener {
            val selected = traceByLine[it]!!
            select(selected)
        }

        amap.setOnMarkerClickListener {
            val selected = traceByMarker[it]!!
            select(selected)
            true
        }

        if (traceByLine.isNotEmpty()) {
            val camera = CameraUpdateFactory.newLatLngZoom(traceByLine.keys.first().points.first(), 10F)
            amap.moveCamera(camera)
        }

        val naviArrow = NavigateArrowOptions()
        var lastNav: NavigateArrow? = null
        Scheduler.addIntermediateListener {
            lastNav?.remove()
            naviArrow.add(it.location.toLatLng())
            runOnUiThread {
                lastNav = amap.addNavigateArrow(naviArrow)
            }
        }
    }

    private val inputWrappers
        get() = listOf(binding.wrapperDropdown, binding.wrapperVelocity, binding.wrapperRepeatCount)

    private fun enableEmulationMonitor() {
        binding.btnStartEmulation.hide(object : ExtendedFloatingActionButton.OnChangedCallback() {
            override fun onHidden(fab: ExtendedFloatingActionButton) {
                fab.setIconResource(R.drawable.ic_baseline_stop_24)
                fab.setText(R.string.action_stop)
                fab.show()
            }
        })
        inputWrappers.forEach { it.isEnabled = false }
    }

    private fun disableEmulationMonitor() {
        binding.btnStartEmulation.hide(object : ExtendedFloatingActionButton.OnChangedCallback() {
            override fun onHidden(fab: ExtendedFloatingActionButton) {
                fab.setIconResource(R.drawable.ic_baseline_auto_fix_high_24)
                fab.setText(R.string.action_start_emulation)
                fab.show()
            }
        })
        inputWrappers.forEach { it.isEnabled = true }
    }

    private fun Trace.drawOnMap(amap: AMap, index: Int): Pair<Polyline, Marker> {
        val polyline = PolylineOptions()
        polyline.color(getColor(lineColor(index)))
        polyline.zIndex(index.toFloat())

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

    private fun lineColor(index: Int) = when (index % 6) {
        0 -> R.color.purple_500
        1 -> R.color.orange_500
        2 -> R.color.yellow_500
        3 -> R.color.green_500
        4 -> R.color.aqua_500
        else -> R.color.pink_500
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapMotionPreview.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        binding.mapMotionPreview.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapMotionPreview.onPause()
    }
}