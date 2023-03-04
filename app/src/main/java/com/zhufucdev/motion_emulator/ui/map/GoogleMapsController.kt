package com.zhufucdev.motion_emulator.ui.map

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.LocationSource
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.SphericalUtil
import com.google.maps.android.ktx.addPolyline
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.data.CoordinateSystem
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Trace
import com.zhufucdev.motion_emulator.hook.android
import com.zhufucdev.motion_emulator.ui.DrawResult
import com.zhufucdev.motion_emulator.ui.DrawToolCallback

@SuppressLint("MissingPermission")
class GoogleMapsController(private val context: Context, private val map: GoogleMap) : MapController {
    init {
        context.apply {
            displayStyle = if (isDarkModeEnabled(resources)) {
                MapStyle.NIGHT
            } else {
                MapStyle.NORMAL
            }
        }
        map.isMyLocationEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
    }

    override fun moveCamera(location: Point, focus: Boolean, animate: Boolean) {
        val update = CameraUpdateFactory.newLatLngZoom(
            location.ensureGoogleCoordinate().toGoogleLatLng(), if (focus) 40F else 10F
        )
        if (animate) map.animateCamera(update)
        else map.moveCamera(update)
    }

    override fun boundCamera(bounds: TraceBounds, animate: Boolean) {
        val update = CameraUpdateFactory.newLatLngBounds(bounds.google(), 40)
        if (animate) map.animateCamera(update)
        else map.moveCamera(update)
    }

    private fun project(x: Int, y: Int): LatLng =
        map.projection.fromScreenLocation(android.graphics.Point(x, y))

    private val lineColor get() = getAttrColor(com.google.android.material.R.attr.colorTertiary, context)

    override fun useDraw(): DrawToolCallback {
        var lastPos = LatLng(0.0, 0.0)
        val points = PolylineOptions()
        val lastPoints = arrayListOf<ArrayList<LatLng>>() // for undoing
        var lastPolyline: Polyline? = null
        points.color(lineColor)


        val callback = object : DrawToolCallback {

            override fun addPoint(x: Int, y: Int) {
                val al = project(x, y)
                if (distance(lastPos, al) >= drawPrecision) {
                    points.add(al)
                    lastPoints.lastOrNull()?.add(al)
                    lastPolyline?.remove()
                    lastPolyline = map.addPolyline(points)
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
                lastPolyline = map.addPolyline(points)
                lastPos = points.points.lastOrNull() ?: LatLng(0.0, 0.0)
            }

            override suspend fun complete(): DrawResult {
                if (points.points.isEmpty()) {
                    return DrawResult()
                }

                val target = map.cameraPosition.target
                val p = points.points
                val address = getAddressWithGoogle(target, context)
                val name = address
                    ?.let { context.getString(R.string.text_near, it) }
                    ?: context.effectiveTimeFormat().dateString()
                val result = DrawResult(name, p.map { it.toPoint() }, CoordinateSystem.WGS84)
                completeListener?.invoke(result)
                return result
            }

            private var completeListener: ((DrawResult) -> Unit)? = null
            override fun onCompleted(l: (DrawResult) -> Unit) {
                completeListener = l
            }
        }

        return callback
    }

    override fun drawTrace(trace: Trace): MapTraceCallback {
        val line = map.addPolyline {
            color(lineColor)
            addAll(trace.points.map { it.toGoogleLatLng() }.plus(trace.points[0].toGoogleLatLng()))
        }
        return object : MapTraceCallback {
            override fun remove() {
                line.remove()
            }
        }
    }

    private val locationIndicator: (Point) -> Unit by lazy {
        var listener: LocationSource.OnLocationChangedListener? = null
        map.setLocationSource(
            object : LocationSource {
                override fun activate(p0: LocationSource.OnLocationChangedListener) {
                    listener = p0
                }

                override fun deactivate() {
                    listener = null
                }
            }
        )
        map.isMyLocationEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
        return@lazy {
            listener?.onLocationChanged(it.android())
        }
    }

    override fun updateLocationIndicator(point: Point) {
        locationIndicator.invoke(point.ensureGoogleCoordinate())
    }

    fun distance(a: LatLng, b: LatLng) = SphericalUtil.computeDistanceBetween(a, b)

    override var displayStyle: MapStyle = MapStyle.NORMAL
        set(value) {
            field = value
            when (value) {
                MapStyle.NORMAL -> {
                    map.setMapStyle(null)
                    map.mapType = GoogleMap.MAP_TYPE_NORMAL
                }
                MapStyle.NIGHT -> {
                    map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.mapstyle_night))
                    map.mapType = GoogleMap.MAP_TYPE_NORMAL
                }
                MapStyle.SATELLITE -> {
                    map.setMapStyle(null)
                    map.mapType = GoogleMap.MAP_TYPE_SATELLITE
                }
            }
        }
    override var displayType: MapDisplayType = MapDisplayType.INTERACTIVE
        set(value) {
            field = value
            when (value) {
                MapDisplayType.STILL -> map.uiSettings.apply {
                    isScrollGesturesEnabled = false
                    isZoomGesturesEnabled = false
                    isRotateGesturesEnabled = false
                }
                MapDisplayType.INTERACTIVE -> map.uiSettings.apply {
                    isScrollGesturesEnabled = true
                    isZoomGesturesEnabled = true
                    isRotateGesturesEnabled = true
                }
            }
        }
}
