package com.zhufucdev.motion_emulator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.BaseColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.CursorAdapter
import android.widget.SearchView
import android.widget.SimpleCursorAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.stub.CoordinateSystem
import com.zhufucdev.stub.Point
import com.zhufucdev.stub.Trace
import com.zhufucdev.stub.toPoint
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.databinding.ActivityTraceDrawingBinding
import com.zhufucdev.motion_emulator.ui.map.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TraceDrawingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTraceDrawingBinding
    private lateinit var locationManager: LocationManager
    private val preferences by lazySharedPreferences()

    private val traces = arrayListOf<Trace>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        skipAmapFuckingLicense(this)

        binding = ActivityTraceDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeToolbar(binding.appBarToolbar)

        locationManager = getSystemService(LocationManager::class.java)
        Traces.require(this)
        binding.mapUnified.provider = getProvider("map_provider")


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0)
        } else {
            involveLocation()
        }

        initializeToolSlots()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.trace_drawing_actionbar, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val search = menu.findItem(R.id.app_bar_search).actionView as SearchView
        initializeSearch(search)
        return true
    }

    private fun getProvider(key: String): UnifiedMapFragment.Provider {
        return UnifiedMapFragment.Provider.valueOf(preferences.getString(key, "gcp_maps")!!.uppercase())
    }

    private val poiEngine: PoiSearchEngine by lazy {
        when (getProvider("poi_provider")) {
            UnifiedMapFragment.Provider.AMAP -> AMapPoiEngine(this)
            UnifiedMapFragment.Provider.GCP_MAPS -> GooglePoiEngine(this)
        }
    }

    private fun initializeSearch(searchView: SearchView) {
        val adapter = SimpleCursorAdapter(
            this,
            android.R.layout.simple_list_item_2,
            null,
            arrayOf("name", "city"),
            intArrayOf(android.R.id.text1, android.R.id.text2),
            CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
        )
        searchView.suggestionsAdapter = adapter

        val handler = Handler(mainLooper)
        var lastQuery: String?
        var lastResults: List<Poi>? = null
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                lastQuery = newText
                handler.postDelayed({
                    if (lastQuery != newText || newText.isEmpty()) {
                        return@postDelayed
                    }
                    // carry out search only if the user holds on for 1s
                    lifecycleScope.launch {
                        val results = poiEngine.search(newText, 20)
                        lastResults = results

                        val cursor = MatrixCursor(arrayOf(BaseColumns._ID, "name", "city"))
                        results.forEachIndexed { index, item ->
                            // Something like XX Park \n Luan, Anhui
                            cursor.addRow(
                                arrayOf<Any>(
                                    index,
                                    item.name,
                                    if (item.province.isNotEmpty())
                                        getString(
                                            R.string.name_location,
                                            item.province,
                                            item.city
                                        )
                                    else if (item.city.isNotEmpty())
                                        item.city
                                    else ""
                                )
                            )
                        }
                        adapter.changeCursor(cursor)
                    }
                }, 1000)
                return false
            }
        })

        searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return false
            }

            override fun onSuggestionClick(position: Int): Boolean {
                val location = lastResults?.get(position)?.location ?: return false

                searchView.isIconified = true
                searchView.onActionViewCollapsed()
                lifecycleScope.launch {
                    binding.mapUnified.requireController().moveCamera(location.toPoint(), focus = true, animate = true)
                }
                return true
            }
        })
    }

    private fun involveLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val provider = locationManager.getBestProvider(
            Criteria().apply {
                accuracy = Criteria.ACCURACY_COARSE
                isSpeedRequired = false
            }, true
        ) ?: return
        locationManager.getLastKnownLocation(provider)
            ?.let {
                lifecycleScope.launch {
                    notifyLocated(it)
                }
                return
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            locationManager.getCurrentLocation(provider, null, application.mainExecutor) {
                if (it != null) {
                    lifecycleScope.launch {
                        notifyLocated(it)
                    }
                } else {
                    Log.w("Trace", "failed to obtain location")
                }
            }
        } else {
            // register a one-time listener
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lifecycleScope.launch {
                        notifyLocated(location)
                    }
                    locationManager.removeUpdates(this)
                }
            }
            locationManager.requestLocationUpdates(provider, 50000, 0F, listener)
        }
    }

    suspend fun notifyLocated(location: Location) {
        binding.mapUnified.requireController().apply {
            updateLocationIndicator(location)
            moveCamera(location.toPoint(), focus = true, animate = false)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        binding.mapUnified.controller?.displayStyle = when (item.itemId) {
            R.id.app_bar_type_common -> MapStyle.NORMAL
            R.id.app_bar_type_satellite -> MapStyle.SATELLITE
            R.id.app_bar_type_night -> MapStyle.NIGHT
            else -> return false
        }
        return binding.mapUnified.controller != null
    }

    private var currentTool: ToolCallback<*> = MoveToolCallback
    private fun initializeToolSlots() {
        val menu = binding.toolSlots.menu

        fun reset() {
            // idk why. there's a bug from Google
            val last = menu.getItem(menu.size() - 1)
            last.isVisible = false
            last.isVisible = true
        }

        binding.toolSlots.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.slot_hand -> {
                    currentTool = useMove()
                    true
                }

                R.id.slot_add -> {
                    binding.toolSlots.menu.clear()
                    menuInflater.inflate(R.menu.trace_drawing_tool_selection, binding.toolSlots.menu)
                    reset()
                    binding.toolSlots.selectedItemId = R.id.tool_draw
                    false
                }

                R.id.tool_opt -> {
                    when (binding.toolSlots.selectedItemId) {
                        R.id.tool_draw -> {
                            menu.clear()
                            menuInflater.inflate(R.menu.trace_drawing_tool_draw, menu)
                            reset()
                            binding.toolSlots.selectedItemId = R.id.tool_draw

                            runBlocking {
                                currentTool = useDraw()
                            }
                        }

                        R.id.tool_gps -> {
                            menu.clear()
                            menuInflater.inflate(R.menu.trace_drawing_tool_gps, menu)
                            reset()
                            binding.toolSlots.selectedItemId = R.id.tool_gps

                            lifecycleScope.launch {
                                try {
                                    currentTool = useGps()
                                } catch (e: RuntimeException) {
                                    MaterialAlertDialogBuilder(this@TraceDrawingActivity)
                                        .setTitle(R.string.title_no_gps_provider)
                                        .setMessage(R.string.text_no_gps_provider)
                                        .setNegativeButton(R.string.action_cancel, null)
                                        .show()
                                }
                            }
                        }

                        else -> throw NotImplementedError(
                            "Unknown tool: " +
                                    "${menu.findItem(binding.toolSlots.selectedItemId).title}"
                        )
                    }
                    false
                }

                R.id.tool_done -> {
                    lifecycleScope.launch {
                        currentTool.complete()
                    }

                    menu.clear()
                    menuInflater.inflate(R.menu.trace_drawing_tool_slots, menu)
                    binding.toolSlots.selectedItemId = R.id.slot_hand
                    reset()

                    false
                }

                R.id.tool_draw -> true
                R.id.tool_gps -> true

                R.id.slot_save -> {
                    lifecycleScope.launch {
                        currentTool.complete()

                        if (traces.isNotEmpty()) {
                            traces.forEach { t ->
                                Traces.store(t)
                            }
                            runOnUiThread {
                                Snackbar.make(
                                    binding.root,
                                    getString(R.string.text_trace_saved, traces.size),
                                    Snackbar.LENGTH_LONG
                                )
                                    .setAnchorView(binding.toolSlots)
                                    .show()
                            }
                            traces.clear()
                        }
                    }
                    false
                }

                R.id.tool_clear -> {
                    val current = currentTool
                    if (current is DrawToolCallback) {
                        current.clear()
                    }
                    false
                }

                R.id.tool_undo -> {
                    currentTool.undo()
                    false
                }

                R.id.tool_pause -> {
                    val current = currentTool
                    if (current is GpsToolCallback) {
                        if (current.isPaused) {
                            current.unpause()
                            it.setIcon(R.drawable.ic_baseline_pause_24)
                            it.setTitle(R.string.action_pause)
                        } else {
                            current.pause()
                            it.setIcon(R.drawable.ic_baseline_fiber_manual_record_24)
                            it.setTitle(R.string.action_unpause)
                        }
                    }
                    false
                }

                else -> false
            }
        }
    }

    private fun useMove(): MoveToolCallback {
        return MoveToolCallback
    }

    private fun ToolCallback<DrawResult>.addCompleteListener() {
        onCompleted {
            traces.add(
                Trace(
                    NanoIdUtils.randomNanoId(),
                    it.poiName,
                    it.trace,
                    coordinateSystem = it.coordinateSystem
                )
            )
            runOnUiThread {
                Snackbar.make(
                    binding.root,
                    getString(R.string.text_trace_name, it.poiName),
                    Snackbar.LENGTH_LONG
                )
                    .setAnchorView(binding.toolSlots)
                    .show()
            }
        }
    }

    private suspend fun useDraw(): DrawToolCallback {
        binding.mapUnified.isFocusable = false

        val callback = binding.mapUnified.requireController().useDraw(binding.touchReceiver)
        callback.addCompleteListener()
        return callback
    }

    private suspend fun useGps(): GpsToolCallback {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        }

        val pendingMsg =
            Snackbar.make(binding.root, R.string.text_gps_pending, Snackbar.LENGTH_INDEFINITE)
                .setAnchorView(binding.toolSlots)
        pendingMsg.show()
        val callback = binding.mapUnified.requireController().useGps()
        pendingMsg.dismiss()
        callback.addCompleteListener()
        return callback
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0 && grantResults.first() == PackageManager.PERMISSION_GRANTED) {
            involveLocation()
        }
    }

    override fun finish() {
        if (traces.isNotEmpty() || currentTool is DrawToolCallback || currentTool is GpsToolCallback) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_unsaved_trace)
                .setMessage(R.string.text_unsaved_trace)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    super.finish()
                }
                .show()
            return
        }
        super.finish()
    }
}

interface ToolCallback<T : ToolCallbackResult> {
    fun undo()
    suspend fun complete(): T
    fun onCompleted(l: (T) -> Unit)
}

interface ToolCallbackResult

interface DrawToolCallback : ToolCallback<DrawResult> {
    fun clear()
}

interface GpsToolCallback : ToolCallback<DrawResult> {
    val isPaused: Boolean
    fun pause()
    fun unpause()
}

data class DrawResult(
    val poiName: String = "",
    val trace: List<Point> = emptyList(),
    val coordinateSystem: CoordinateSystem = CoordinateSystem.WGS84
) : ToolCallbackResult

object MoveToolCallback : ToolCallback<MoveResult> {
    override fun undo() {}

    override suspend fun complete(): MoveResult = MoveResult

    override fun onCompleted(l: (MoveResult) -> Unit) {}
}

object MoveResult : ToolCallbackResult