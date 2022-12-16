package com.zhufucdev.motion_emulator.ui.emulate

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.databinding.FragmentConfigurationBinding
import com.zhufucdev.motion_emulator.hook.center
import com.zhufucdev.motion_emulator.hook_frontend.Emulation
import com.zhufucdev.motion_emulator.hook_frontend.EmulationRef
import com.zhufucdev.motion_emulator.hook_frontend.Scheduler
import kotlinx.serialization.serializer
import net.edwardday.serialization.preferences.Preferences

class ConfigurationFragment : Fragment(), MenuProvider {
    private lateinit var binding: FragmentConfigurationBinding
    private lateinit var btnRun: ExtendedFloatingActionButton
    private var btnDefault: MenuItem? = null
    private val settings by lazy {
        requireContext().getSharedPreferences(
            "settings",
            Context.MODE_PRIVATE
        )
    }
    private val defaultConfig by lazy {
        try {
            val pref = Preferences(settings)
            pref.decode(serializer<EmulationRef>(), "default_config")
        } catch (e: Exception) {
            null
        }
    }

    private var motion: Box<Motion> = EmptyBox()
    private var trace: Trace? = null
    private var cells: Box<CellTimeline> = EmptyBox()
    private var repeatCount: Int? = 1
    private var velocity: Double? = 3.0
    private var satelliteCount: Int? = 10

    private var drawnTrace: Pair<Polyline, Marker>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        skipAmapFuckingLicense(requireContext())
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
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        btnRun = binding.btnRunEmulation
    }

    override fun onStart() {
        super.onStart()

        initMotionDropdown()
        initCellsDropdown()
        initializeMap()
        initTracesDropdown()
        initializeOthers()
        notifyConfig()
    }

    private fun emulation(): Emulation? {
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
        ) {
            return null
        }
        return Emulation(trace, motion, cells, velocity, repeat, satellites)
    }

    private fun startEmulation() {
        Scheduler.emulation = emulation() ?: return
        val camera = binding.mapTracePreview.map.cameraPosition
        findNavController()
            .navigate(
                R.id.action_configurationFragment_to_emulateStatusFragment,
                bundleOf(
                    "cam_center_lat" to camera.target.latitude,
                    "cam_center_lng" to camera.target.longitude,
                    "cam_zoom" to camera.zoom
                )
            )
        btnRun.hide()
    }

    private fun AutoCompleteTextView.select(adapter: ArrayAdapter<*>, name: String) {
        setText(name)
        adapter.filter.filter(null)
    }

    private fun initMotionDropdown() {
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        val motions = Motions.list()
        motions.forEach {
            adapter.add(dateString(it.time))
        }
        adapter.addDefaults()
        binding.dropdownMotion.apply {
            setOnItemClickListener { _, _, position, _ ->
                motion = if (position < motions.size) {
                    motions[position].box()
                } else if (position == motions.size) {
                    EmptyBox()
                } else {
                    BlockBox()
                }
                notifyConfig()
            }
            setAdapter(adapter)

            defaultConfig?.motion?.let { id ->
                if (selectDefaults(adapter, id)) {
                    return
                }
                val m = motions.firstOrNull { it.id == id }
                if (m != null) {
                    select(adapter, dateString(m.time))
                    motion = m.box()
                }
            } ?: apply {
                selectDefaults(adapter, EMPTY_REF)
            }
        }
    }

    private fun initCellsDropdown() {
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        val timelines = Cells.list()
        timelines.forEach {
            adapter.add(dateString(it.time))
        }
        adapter.addDefaults()
        binding.dropdownCells.apply {
            setOnItemClickListener { _, _, position, _ ->
                cells = if (position < timelines.size) {
                    timelines[position].box()
                } else if (position == timelines.size) {
                    EmptyBox()
                } else {
                    BlockBox()
                }
                notifyConfig()
            }
            setAdapter(adapter)

            defaultConfig?.cells?.let { id ->
                if (selectDefaults(adapter, id)) {
                    return
                }
                val timeline = timelines.firstOrNull { it.id == id }
                if (timeline != null) {
                    select(adapter, dateString(timeline.time))
                    cells = timeline.box()
                }
            } ?: apply {
                selectDefaults(adapter, EMPTY_REF)
            }
        }
    }

    private fun selectTrace(trace: Trace) {
        this.trace = trace
        notifyConfig()

        drawnTrace?.apply {
            first.remove()
            second.remove()
        }
        val map = binding.mapTracePreview.map
        val drawn = trace.drawOnMap(map)
        drawn.second.position
        drawnTrace = drawn
        map.animateCamera(
            CameraUpdateFactory
                .newLatLngBounds(trace.points.bounds(), 400)
        )
    }

    private fun initTracesDropdown() {
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        val traces = Traces.list()
        traces.forEach {
            adapter.add(it.name)
        }
        binding.dropdownTrace.apply {
            setOnItemClickListener { _, _, position, _ ->
                selectTrace(traces[position])
            }
            setAdapter(adapter)

            defaultConfig?.trace?.let { id ->

                val trace = traces.firstOrNull { it.id == id }
                if (trace != null) {
                    select(adapter, trace.name)
                    selectTrace(trace)
                }
            }
        }
    }

    /**
     * - None
     * - Block
     */
    private fun ArrayAdapter<String>.addDefaults() {
        add(getString(R.string.name_none))
        add(getString(R.string.name_block))
    }

    private fun AutoCompleteTextView.selectDefaults(adapter: ArrayAdapter<*>, id: String): Boolean {
        when (id) {
            EMPTY_REF -> select(adapter, getString(R.string.name_none))
            BLOCK_REF -> select(adapter, getString(R.string.name_block))
            else -> return false
        }
        return true
    }

    private val notContinue
        get() = !binding.inputVelocity.error.isNullOrEmpty()
                || !binding.inputRepeatCount.error.isNullOrEmpty()
                || !binding.inputSatellite.error.isNullOrBlank()
                || trace == null

    private fun notifyConfig() {
        if (notContinue) {
            btnRun.hide()
            btnDefault?.isEnabled = false
        } else {
            btnRun.show()
            btnDefault?.isEnabled = true
        }
    }

    private fun initializeOthers() {
        defaultConfig?.apply {
            binding.inputVelocity.setText(velocity.toString())
            this@ConfigurationFragment.velocity = velocity
            binding.inputRepeatCount.setText(repeat.toString())
            repeatCount = repeat
            binding.inputSatellite.setText(satelliteCount.toString())
            this@ConfigurationFragment.satelliteCount = satelliteCount

            notifyConfig()
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
            notifyConfig()
        }

        fun setError(input: TextInputEditText, value: Int?, filterNegative: Boolean) {
            input.error =
                if (value == null) getString(R.string.text_field_must_not_empty)
                else if (filterNegative && value <= 0) getString(R.string.text_field_must_not_neg_or_zero)
                else null
            notifyConfig()
        }
        binding.inputRepeatCount.doAfterTextChanged {
            repeatCount = if (it.isNullOrEmpty()) {
                null
            } else {
                it.toString().toIntOrNull()
            }
            setError(binding.inputRepeatCount, repeatCount, true)
            notifyConfig()
        }
        binding.inputSatellite.doAfterTextChanged {
            satelliteCount = if (it.isNullOrEmpty()) {
                null
            } else {
                it.toString().toIntOrNull()
            }
            setError(binding.inputSatellite, satelliteCount, false)
            notifyConfig()
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
                length += AMapUtils.calculateLineDistance(
                    point.toLatLng(),
                    points[i - 1].toLatLng()
                )
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

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.config_actionbar, menu)
        btnDefault = menu.findItem(R.id.app_bar_default)
        btnDefault?.isEnabled = !notContinue
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.app_bar_default -> {
                val emulation = emulation()?.ref() ?: return false
                val pref = Preferences(settings)
                pref.encode(serializer(), "default_config", emulation)
                Snackbar.make(btnRun, R.string.text_saved_as_default, Snackbar.LENGTH_LONG).show()
                return true
            }
        }
        return false
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
        notifyConfig()
    }

    override fun onPause() {
        super.onPause()
        binding.mapTracePreview.onPause()
    }
}