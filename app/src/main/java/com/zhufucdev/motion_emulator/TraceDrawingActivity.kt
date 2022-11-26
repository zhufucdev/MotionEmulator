package com.zhufucdev.motion_emulator

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.graphics.Point
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
import android.view.MotionEvent
import android.widget.CursorAdapter
import android.widget.SearchView
import android.widget.SimpleCursorAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.poisearch.PoiResultV2
import com.amap.api.services.poisearch.PoiSearchV2
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.google.android.material.snackbar.Snackbar
import com.zhufucdev.motion_emulator.data.Trace
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.databinding.ActivityTraceDrawingBinding
import kotlinx.coroutines.launch

class TraceDrawingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTraceDrawingBinding
    private lateinit var amap: AMap
    private lateinit var locationManager: LocationManager

    private val traces = arrayListOf<Trace>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        skipAmapFuckingLicense(this)

        binding = ActivityTraceDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarToolbar)

        binding.mapCanvas.onCreate(savedInstanceState)
        amap = binding.mapCanvas.map
        amap.stylize()

        locationManager = getSystemService(LocationManager::class.java)
        Traces.require(this)

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
        var lastResults: PoiResultV2? = null
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                lastQuery = newText
                handler.postDelayed({
                    if (lastQuery != newText) {
                        return@postDelayed
                    }
                    // carry out search only if the user holds on for 0.5s
                    val query = PoiSearchV2.Query(newText, null)
                    query.pageSize = 20
                    val search = PoiSearchV2(this@TraceDrawingActivity, query)
                    search.setOnPoiSearchListener(object : PoiSearchV2.OnPoiSearchListener {
                        override fun onPoiSearched(p0: PoiResultV2?, p1: Int) {
                            if (p0 == null) {
                                return
                            }
                            lastResults = p0
                            val cursor = MatrixCursor(arrayOf(BaseColumns._ID, "name", "city"))
                            p0.pois.forEachIndexed { index, item ->
                                // Something like XX Park \n Luan, Anhui
                                cursor.addRow(
                                    arrayOf<Any>(
                                        index,
                                        item.title,
                                        getString(R.string.name_location, item.provinceName, item.cityName)
                                    )
                                )
                            }
                            adapter.changeCursor(cursor)
                        }

                        override fun onPoiItemSearched(p0: PoiItemV2?, p1: Int) {}
                    })

                    search.searchPOIAsyn()
                }, 500)
                return false
            }
        })
        searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return false
            }

            override fun onSuggestionClick(position: Int): Boolean {
                val location = lastResults?.pois?.get(position)?.latLonPoint ?: return false

                searchView.isIconified = true
                searchView.onActionViewCollapsed()
                moveCamera(location)
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
                moveCamera(it)
                return
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            locationManager.getCurrentLocation(provider, null, application.mainExecutor) {
                if (it != null) {
                    moveCamera(it)
                } else {
                    Log.w("Trace", "failed to obtain location")
                }
            }
        } else {
            // register a one-time listener
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    moveCamera(location)
                    locationManager.removeUpdates(this)
                }
            }
            locationManager.requestLocationUpdates(provider, 50000, 0F, listener)
        }
    }

    private fun moveCamera(location: Location) {
        val camera = CameraUpdateFactory
            .newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                10F
            )
        amap.animateCamera(camera)
    }

    private fun moveCamera(location: LatLonPoint) {
        val camera = CameraUpdateFactory
            .newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                50F
            )
        amap.animateCamera(camera)
    }

    private fun AMap.stylize() {
        isMyLocationEnabled = false
        uiSettings.isZoomControlsEnabled = false

        if (isDarkModeEnabled(resources)) {
            amap.mapType = AMap.MAP_TYPE_NIGHT
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        amap.mapType = when (item.itemId) {
            R.id.app_bar_type_common -> AMap.MAP_TYPE_NORMAL
            R.id.app_bar_type_satellite -> AMap.MAP_TYPE_SATELLITE
            R.id.app_bar_type_night -> AMap.MAP_TYPE_NIGHT
            else -> return super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }

    private var currentToolType: Tool = Tool.MOVE
    private var currentTool: ToolCallback = MoveToolCallback
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
                    currentTool.complete()
                    if (traces.isNotEmpty())  {
                        traces.forEach { t ->
                            Traces.store(t)
                        }
                        Snackbar.make(
                            binding.root,
                            getString(R.string.text_trace_saved, traces.size),
                            Snackbar.LENGTH_LONG
                        )
                            .setAnchorView(binding.toolSlots)
                            .show()
                    }
                    false
                }

                else -> false
            }

            if (working) {
                lastTool.complete()
            }

            working
        }
    }

    private fun useMove(): ToolCallback {
        binding.mapCanvas.isFocusable = true
        binding.touchReceiver.apply {
            isVisible = false
            setOnTouchListener(null)
        }
        return MoveToolCallback
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun useDraw(): ToolCallback {
        binding.mapCanvas.isFocusable = false
        binding.touchReceiver.isVisible = true


        var lastPos = LatLng(0.0, 0.0)
        val points = PolylineOptions()
        val lastPoints = arrayListOf<ArrayList<LatLng>>() // for undoing
        var lastPolyline: Polyline? = null
        points.color(getColor(if (isDarkModeEnabled(resources)) R.color.purple_200 else R.color.purple_500))

        val callback = object : DrawToolCallback {
            private fun project(x: Int, y: Int): LatLng = amap.projection.fromScreenLocation(Point(x, y))

            override fun addPoint(x: Int, y: Int) {
                val al = project(x, y)
                if (distance(lastPos, al) >= drawPrecision) {
                    points.add(al)
                    lastPoints.lastOrNull()?.add(al)
                    lastPolyline?.remove()
                    lastPolyline = amap.addPolyline(points)
                }
                lastPos = al
            }

            override fun markBegin(x: Int, y: Int) {
                lastPoints.add(arrayListOf())
                addPoint(x, y)
            }

            override fun markEnd(x: Int, y: Int) {}

            override fun undo() {
                lastPoints.removeLastOrNull()?.let { points.points.removeAll(it) } ?: return
                lastPolyline?.remove()
                lastPolyline = amap.addPolyline(points)
                lastPos = points.points.lastOrNull() ?: LatLng(0.0, 0.0)
            }

            override fun complete() {
                if (points.points.isEmpty()) {
                    return
                }

                val target = amap.cameraPosition.target
                lifecycleScope.launch {
                    val p = points.points
                    val address = getAddress(target)
                    val offset = offsetPatch(binding.mapCanvas, p)
                    val name = address
                        ?.let { getString(R.string.text_near, it) }
                        ?: dateString()
                    traces.add(
                        Trace(
                            NanoIdUtils.randomNanoId(),
                            name,
                            p.map { com.zhufucdev.motion_emulator.data.Point(it.latitude, it.longitude) },
                            offset
                        )
                    )
                    runOnUiThread {
                        Snackbar.make(binding.root, getString(R.string.text_trace_name, name), Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.toolSlots)
                            .show()
                    }
                }
            }
        }

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

        return callback
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0 && grantResults.first() == PackageManager.PERMISSION_GRANTED) {
            involveLocation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapCanvas.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        binding.mapCanvas.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapCanvas.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapCanvas.onSaveInstanceState(outState)
    }
}

enum class Tool {
    MOVE, DRAW
}

private fun distance(p1: LatLng, p2: LatLng): Float =
    AMapUtils.calculateLineDistance(p1, p2)

const val drawPrecision = 0.5F // in meters

interface ToolCallback {
    fun undo()
    fun complete()
}

interface DrawToolCallback : ToolCallback {
    fun addPoint(x: Int, y: Int)
    fun markBegin(x: Int, y: Int)
    fun markEnd(x: Int, y: Int)
}

object MoveToolCallback : ToolCallback {
    override fun undo() {}

    override fun complete() {}
}