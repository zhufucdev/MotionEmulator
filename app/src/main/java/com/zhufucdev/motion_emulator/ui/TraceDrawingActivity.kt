package com.zhufucdev.motion_emulator.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.BaseColumns
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.CursorAdapter
import android.widget.SearchView
import android.widget.SimpleCursorAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.poisearch.PoiResultV2
import com.amap.api.services.poisearch.PoiSearchV2
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.google.android.material.snackbar.Snackbar
import com.highcapable.yukihookapi.hook.log.loggerW
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.data.CoordinateSystem
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Trace
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.databinding.ActivityTraceDrawingBinding
import com.zhufucdev.motion_emulator.ui.map.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

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
        binding.mapUnified.provider = getProvider("map")


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
                binding.mapUnified.requireController().moveCamera(location.toPoint(), focus = true, animate = true)
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

        val provider = LocationManager.NETWORK_PROVIDER
        locationManager.getLastKnownLocation(provider)
            ?.let {
                notifyLocated(it.toPoint())
                return
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            locationManager.getCurrentLocation(provider, null, application.mainExecutor) {
                if (it != null) {
                    notifyLocated(it.toPoint())
                } else {
                    loggerW("Trace", "failed to obtain location")
                }
            }
        } else {
            // register a one-time listener
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    notifyLocated(location.toPoint())
                    locationManager.removeUpdates(this)
                }
            }
            locationManager.requestLocationUpdates(provider, 50000, 0F, listener)
        }
    }

    fun notifyLocated(point: Point) {
        binding.mapUnified.requireController().apply {
            updateLocationIndicator(point)
            moveCamera(point, focus = true, animate = false)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        binding.mapUnified.requireController().displayStyle = when (item.itemId) {
            R.id.app_bar_type_common -> MapStyle.NORMAL
            R.id.app_bar_type_satellite -> MapStyle.SATELLITE
            R.id.app_bar_type_night -> MapStyle.NIGHT
            else -> return super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }

    private var currentToolType: Tool = Tool.MOVE
    private var currentTool: ToolCallback<*> = MoveToolCallback
    private fun initializeToolSlots() {
        val undoItem = binding.toolSlots.menu.findItem(R.id.slot_undo)
        binding.toolSlots.setOnItemSelectedListener {
            val lastTool = currentTool
            val working = when (it.itemId) {
                R.id.slot_hand -> {
                    currentToolType = Tool.MOVE
                    currentTool = useMove()
                    undoItem.isEnabled = false
                    true
                }

                R.id.slot_draw -> {
                    currentToolType = Tool.DRAW
                    currentTool = useDraw()
                    undoItem.isEnabled = true
                    true
                }

                R.id.slot_undo -> {
                    currentTool.undo()
                    false
                }

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
                        }
                    }
                    false
                }

                else -> false
            }

            if (working) {
                lifecycleScope.launch {
                    lastTool.complete()
                }
            }

            working
        }
    }

    private fun useMove(): MoveToolCallback {
        binding.mapUnified.isFocusable = true
        binding.touchReceiver.apply {
            isVisible = false
            setOnTouchListener(null)
        }
        return MoveToolCallback
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun useDraw(): DrawToolCallback {
        binding.mapUnified.isFocusable = false
        binding.touchReceiver.isVisible = true

        val callback = binding.mapUnified.requireController().useDraw()
        binding.touchReceiver.setOnTouchListener { _, event ->
            if (event.pointerCount > 1) {
                return@setOnTouchListener false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> callback.markBegin(event.x.toInt(), event.y.toInt())
                MotionEvent.ACTION_UP -> callback.markEnd(event.x.toInt(), event.y.toInt())
                else -> callback.addPoint(event.x.toInt(), event.y.toInt())
            }
            true
        }
        callback.onCompleted {
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
}

enum class Tool {
    MOVE, DRAW
}

interface ToolCallback<T : ToolCallbackResult> {
    fun undo()
    suspend fun complete(): T
    fun onCompleted(l: (T) -> Unit)
}

interface ToolCallbackResult

interface DrawToolCallback : ToolCallback<DrawResult> {
    fun addPoint(x: Int, y: Int)
    fun markBegin(x: Int, y: Int)
    fun markEnd(x: Int, y: Int)
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