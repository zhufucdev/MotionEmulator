package com.zhufucdev.motion_emulator.ui.map

import android.content.Context
import android.location.Location
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.LocationSource
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Trace

class AMapController(private val map: AMap, context: Context) : MapController(context) {
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

    override fun usePen(): MapScrawl = object : MapScrawl {
        var lastPos = LatLng(0.0, 0.0)
        val polyline = PolylineOptions()
        val backStack = arrayListOf<ArrayList<LatLng>>() // for undoing
        var lastPolyline: Polyline? = null

        init {
            polyline.color(lineColor)
        }

        override val points: List<Point> by lazy(lastPos) { polyline.points.map { it.toPoint() } }

        override fun addPoint(point: Point) {
            val al = point.toAmapLatLng()
            if (distance(lastPos, al) >= mapCaptureAccuracy) {
                polyline.add(al)
                backStack.lastOrNull()?.add(al)
                lastPolyline?.remove()
                lastPolyline = map.addPolyline(polyline)
            }
            lastPos = al
        }

        override fun markBegin() {
            backStack.add(arrayListOf())
        }

        override fun undo() {
            backStack.removeLastOrNull()?.let { polyline.points.removeAll(it) } ?: return
            lastPolyline?.remove()
            lastPolyline = map.addPolyline(polyline)
            lastPos = polyline.points.lastOrNull() ?: LatLng(0.0, 0.0)
        }

        override fun clear() {
            backStack.removeLastOrNull()?.let {
                it.forEach { p ->
                    if (polyline.points.contains(p))
                        polyline.points.remove(p)
                    else
                        polyline.add(p)
                }
            } ?: return
            lastPolyline?.remove()
            lastPolyline = map.addPolyline(polyline)
            lastPos = LatLng(0.0, 0.0)
        }
    }

    override fun project(x: Int, y: Int): Point =
        map.projection.fromScreenLocation(android.graphics.Point(x, y)).toPoint()

    override suspend fun getAddress(point: Point): String? {
        return getAddressWithAmap(point.ensureAmapCoordinate().toAmapLatLng())
    }

    override fun cameraCenter(): Point = map.cameraPosition.target.toPoint()

    override fun drawTrace(trace: Trace): MapTraceCallback {
        val options = PolylineOptions()
        options.addAll(trace.points.map { it.toAmapLatLng() }.plus(trace.points[0].toAmapLatLng())) // closed shape
        options.color(lineColor)
        val line = map.addPolyline(options)

        return object : MapTraceCallback {
            override fun remove() {
                line.remove()
            }
        }
    }

    private val locationListener: (Location) -> Unit by lazy {
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
            callback?.onLocationChanged(it)
        }
    }

    override fun updateLocationIndicator(location: Location) {
        locationListener.invoke(location)
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
