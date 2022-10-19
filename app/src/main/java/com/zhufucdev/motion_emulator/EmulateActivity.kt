package com.zhufucdev.motion_emulator

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LiveData
import androidx.work.*
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.zhufucdev.motion_emulator.data.Motion
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Trace
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.databinding.ActivityEmulateBinding
import com.zhufucdev.motion_emulator.hook_frontend.Emulation
import com.zhufucdev.motion_emulator.hook_frontend.EmulationWorker
import com.zhufucdev.motion_emulator.hook_frontend.Scheduler
import java.util.*

class EmulateActivity : AppCompatActivity(R.layout.activity_emulate) {
    private lateinit var binding: ActivityEmulateBinding
    private var motion: Motion? = null
    private var trace: Trace? = null
    private var repeatCount: Int? = 1
    private var velocity: Double = 3.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        skipAmapFuckingLicense(this)
        binding = ActivityEmulateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mapMotionPreview.onCreate(savedInstanceState)

        Traces.require(this)
        Motions.require(this)

        initMotionDropdown()
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
            disableEmulationMonitor()
        } else {
            val repeat = repeatCount
            if (repeat == null || repeat <= 0 || velocity.isNaN() || velocity <= 0 || trace == null) {
                return
            }
            Scheduler.emulation = Emulation(trace!!, motion!!, velocity)
            initEmulationMonitor()
        }
    }

    private fun initMotionDropdown() {
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val motions = Motions.list()
        motions.forEach {
            adapter.add(dateString(it.time))
        }
        binding.dropdownMotion.setOnItemClickListener { _, _, position, _ ->
            motion = motions[position]
        }
        binding.dropdownMotion.setAdapter(adapter)
    }

    private fun initializeOthers() {
        fun notifyFab() {
            if (binding.inputVelocity.error.isNotEmpty() || binding.inputRepeatCount.error.isNotEmpty())
                binding.btnStartEmulation.hide()
            else
                binding.btnStartEmulation.show()
        }

        binding.inputVelocity.doAfterTextChanged {
            velocity = if (it.isNullOrEmpty()) {
                Double.NaN
            } else {
                it.toString().toDouble()
            }
            binding.inputVelocity.error =
                if (velocity.isNaN()) getString(R.string.text_field_must_not_empty)
                else if (velocity <= 0) getString(R.string.text_field_must_not_neg_or_zero)
                else null
            notifyFab()
        }
        binding.inputRepeatCount.doAfterTextChanged {
            repeatCount = if (it.isNullOrEmpty()) {
                null
            } else {
                it.toString().toInt()
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
    }

    private fun initializeMap() {
        val amap = binding.mapMotionPreview.map

        amap.uiSettings.isZoomControlsEnabled = false
        amap.mapType = if (isDarkModeEnabled(resources)) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL

        val traces = hashMapOf<Polyline, Trace>()
        val selectedColors = hashMapOf<Polyline, Int>()

        Traces.list().forEachIndexed { index, trace ->
            val polyline = trace.drawOnMap(amap, index)
            traces[polyline] = trace
            selectedColors[polyline] = polyline.color
        }

        amap.setOnPolylineClickListener {
            val selected = traces[it]!!
            trace = selected
            Snackbar
                .make(binding.root, getString(R.string.text_trace_selected, selected.name), Snackbar.LENGTH_LONG)
                .setAnchorView(binding.btnStartEmulation)
                .show()
            it.color = getColor(R.color.teal_700)
            traces.entries.forEach { (e, _) ->
                if (it.id != e.id) {
                    e.color = selectedColors[e]!!
                }
            }
        }

        if (traces.isNotEmpty()) {
            val camera = CameraUpdateFactory.newLatLngZoom(traces.keys.first().points.first(), 10F)
            amap.moveCamera(camera)
        }
    }

    private val inputWrappers
        get() = listOf(binding.wrapperDropdown, binding.wrapperVelocity, binding.wrapperRepeatCount)

    private fun initEmulationMonitor() {
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

    private fun Trace.drawOnMap(amap: AMap, index: Int): Polyline {
        val polyline = PolylineOptions()
        polyline.color(getColor(lineColor(index)))
        polyline.zIndex(index.toFloat())

        points.forEach {
            polyline.add(LatLng(it.latitude, it.longitude))
        }
        return amap.addPolyline(polyline)
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

    companion object {
        const val workName = "emulation"
    }
}