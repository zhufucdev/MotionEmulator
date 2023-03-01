package com.zhufucdev.motion_emulator.ui.map

import android.content.Context
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.LocationSource
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.data.CoordinateSystem
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Trace
import com.zhufucdev.motion_emulator.hook.android
import com.zhufucdev.motion_emulator.ui.DrawResult
import com.zhufucdev.motion_emulator.ui.DrawToolCallback

class AMapController(private val map: AMap, val context: Context) : MapController {
    init {
        map.apply {
            isMyLocationEnabled = false
            uiSettings.isZoomControlsEnabled = false
            isMyLocationEnabled = false

            if (isDarkModeEnabled(context.resources)) {
                mapType = AMap.MAP_TYPE_NIGHT
                displayStyle = MapStyle.NIGHT
            } else {
                displayStyle = MapStyle.NORMAL
            }
        }
    }

    private val lineColor get() = getAttrColor(com.google.android.material.R.attr.colorTertiary, context)

    override fun moveCamera(location: Point, focus: Boolean, animate: Boolean) {
        val camera = CameraUpdateFactory.newLatLngZoom(
            location.ensureAmapCoordinate().toAmapLatLng(),
            if (focus) 40F else 10F
        )
        if (animate) map.animateCamera(camera)
        else map.moveCamera(camera)
    }

    override fun boundCamera(bounds: TraceBounds, animate: Boolean) {
        val update = CameraUpdateFactory.newLatLngBounds(bounds.amap(), 400)
        if (animate) map.animateCamera(update)
        else map.moveCamera(update)
    }


    override fun useDraw(): DrawToolCallback {
        var lastPos = LatLng(0.0, 0.0)
        val points = PolylineOptions()
        val lastPoints = arrayListOf<ArrayList<LatLng>>() // for undoing
        var lastPolyline: Polyline? = null
        points.color(lineColor)

        val callback = object : DrawToolCallback {
            private fun project(x: Int, y: Int): LatLng =
                map.projection.fromScreenLocation(android.graphics.Point(x, y))

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
                val address = getAddressWithAmap(target)
                val name = address
                    ?.let { context.getString(R.string.text_near, it) }
                    ?: context.effectiveTimeFormat().dateString()
                val result = DrawResult(name, p.map { it.toPoint() }, CoordinateSystem.GCJ02)
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
        val options = PolylineOptions()
        options.addAll(trace.points.map { it.toAmapLatLng() })
        options.color(lineColor)
        val line = map.addPolyline(options)

        return object : MapTraceCallback {
            override fun remove() {
                line.remove()
            }
        }
    }

    private val locationListener: (Point) -> Unit by lazy {
        var callback: LocationSource.OnLocationChangedListener? = null
        map.setLocationSource(object : LocationSource {
            override fun activate(p0: LocationSource.OnLocationChangedListener?) {
                callback = p0
            }

            override fun deactivate() {
                callback = null
            }
        })
        map.isMyLocationEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false

        return@lazy {
            callback?.onLocationChanged(it.android())
        }
    }

    override fun updateLocationIndicator(point: Point) {
        locationListener.invoke(point)
    }

    private fun distance(p1: LatLng, p2: LatLng): Float =
        AMapUtils.calculateLineDistance(p1, p2)

    override var displayStyle: MapStyle
        set(value) {
            field = value
            map.mapType = when (value) {
                MapStyle.NORMAL -> AMap.MAP_TYPE_NORMAL
                MapStyle.NIGHT -> AMap.MAP_TYPE_NIGHT
                MapStyle.SATELLITE -> AMap.MAP_TYPE_SATELLITE
            }
        }

    override var displayType: MapDisplayType = MapDisplayType.INTERACTIVE
        set(value) {
            field = value
            when (value) {
                MapDisplayType.STILL -> {
                    map.uiSettings.apply {
                        isZoomGesturesEnabled = false
                        isScrollGesturesEnabled = false
                        isRotateGesturesEnabled = false
                    }
                }

                MapDisplayType.INTERACTIVE -> {
                    map.uiSettings.apply {
                        isZoomGesturesEnabled = true
                        isScrollGesturesEnabled = true
                        isRotateGesturesEnabled = true
                    }
                }
            }
        }
}
