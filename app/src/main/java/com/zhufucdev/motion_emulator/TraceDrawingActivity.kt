package com.zhufucdev.motion_emulator

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Point
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.zhufucdev.motion_emulator.databinding.ActivityTraceDrawingBinding
import kotlin.math.pow
import kotlin.math.sqrt

class TraceDrawingActivity : AppCompatActivity(R.layout.activity_trace_drawing) {
    private lateinit var binding: ActivityTraceDrawingBinding
    private lateinit var amap: AMap
    private lateinit var locationManager: LocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)

        binding = ActivityTraceDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mapCanvas.onCreate(savedInstanceState)
        amap = binding.mapCanvas.map
        amap.stylize()

        locationManager = getSystemService(LocationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0)
        } else {
            involveLocation()
        }

        initializeToolSlots()
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

        val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) ?: return
        val camera = CameraUpdateFactory
            .newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                10F
            )
        amap.moveCamera(camera)
    }

    private fun AMap.stylize() {
        isMyLocationEnabled = false
        uiSettings.isZoomControlsEnabled = false
    }

    private var currentTool: Tool = Tool.MOVE

    private fun initializeToolSlots() {
        binding.toolSlots.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.slot_hand -> {
                    currentTool = Tool.MOVE
                    useMove()
                    true
                }

                R.id.slot_draw -> {
                    currentTool = Tool.DRAW
                    useDraw()
                    true
                }

                else -> false
            }
        }
    }

    private fun useMove() {
        binding.mapCanvas.isFocusable = true
        binding.touchReceiver.apply {
            isVisible = false
            setOnTouchListener(null)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun useDraw() {
        binding.mapCanvas.isFocusable = false
        binding.touchReceiver.isVisible = true

        var lastPos = LatLng(0.0, 0.0)
        val points = PolylineOptions()
        var lastPolyline: Polyline? = null
        points.color(getColor(R.color.purple_500))
        binding.touchReceiver.setOnTouchListener { _, event ->
            if (event.pointerCount > 1) {
                return@setOnTouchListener false
            }
            val al = amap.projection.fromScreenLocation(Point(event.x.toInt(), event.y.toInt()))
            if (distance(lastPos, al) >= drawPrecision) {
                points.add(al)
                lastPolyline?.remove()
                lastPolyline = amap.addPolyline(points)
            }
            lastPos = al
            true
        }
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

private fun distance(p1: LatLng, p2: LatLng): Double =
    sqrt((p1.longitude - p2.longitude).pow(2) + (p1.latitude - p2.latitude).pow(2))

const val drawPrecision = 1e-5