package com.zhufucdev.motion_emulator.ui.map

import android.content.Context
import android.location.Location
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.*
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.stub.Point
import com.zhufucdev.stub.Trace
import kotlin.math.pow

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

        var zoom = map.cameraPosition.zoom
        map.addOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(cam: CameraPosition) {
                val locationIndicator = locationIndicator ?: return
                if (map.cameraPosition.zoom != zoom) {
                    zoom = cam.zoom
                    redrawLocationIndicator(locationIndicator.center)
                }
            }

            override fun onCameraChangeFinish(p0: CameraPosition?) {

            }
        })
    }

    private val lineColor get() = getAttrColor(com.google.android.material.R.attr.colorTertiary, context)
    private val indicatorColor get() = getAttrColor(com.google.android.material.R.attr.colorPrimary, context)
    private val indicatorStroke
        get() =
            if (map.mapType == AMap.MAP_TYPE_NORMAL)
                android.graphics.Color.rgb(55, 71, 79)
            else
                android.graphics.Color.rgb(250, 250, 250)

    override fun moveCamera(location: Point, focus: Boolean, animate: Boolean) {
        val camera = CameraUpdateFactory.newLatLngZoom(
            location.ensureAmapCoordinate(context).toAmapLatLng(),
            if (focus) 40F else 10F
        )
        if (animate) map.animateCamera(camera)
        else map.moveCamera(camera)
    }

    override fun boundCamera(bounds: TraceBounds, animate: Boolean) {
        val update = CameraUpdateFactory.newLatLngBounds(bounds.amap(context), 400)
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
            val al = point.ensureAmapCoordinate(context).toAmapLatLng()
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
            lastPos = polyline.points.lastOrNull() ?: LatLng(0.0, 0.0)
        }

        override fun clear() {
            val summary = arrayListOf<LatLng>()
            backStack.forEach {
                summary.addAll(it)
            }
            backStack.add(summary)
            lastPolyline?.remove()
            lastPolyline = null
            polyline.points.clear()
            lastPos = LatLng(0.0, 0.0)
        }
    }

    override fun project(x: Int, y: Int): Point =
        map.projection.fromScreenLocation(android.graphics.Point(x, y)).toPoint()

    override suspend fun getAddress(point: Point): String? {
        return getAddressWithAmap(point.ensureAmapCoordinate(context).toAmapLatLng())
    }

    override fun cameraCenter(): Point = map.cameraPosition.target.toPoint()

    override fun drawTrace(trace: Trace): MapTraceCallback {
        val options = PolylineOptions()
        options.addAll(trace.points.map { it.ensureAmapCoordinate(context).toAmapLatLng() }
            .plus(trace.points[0].ensureAmapCoordinate(context).toAmapLatLng())) // closed shape
        options.color(lineColor)
        val line = map.addPolyline(options)

        return object : MapTraceCallback {
            override fun remove() {
                line.remove()
            }
        }
    }

    private var locationIndicator: Circle? = null
    private var accuracyIndicator: Circle? = null
    override fun updateLocationIndicator(location: Location) {
        val point = location.toPoint().ensureAmapCoordinate(context).toAmapLatLng()
        accuracyIndicator?.remove()
        accuracyIndicator = map.addCircle(
            CircleOptions().apply {
                center(point)
                strokeColor(0)
                fillColor(android.graphics.Color.argb(100, 30, 136, 229))
                radius(location.accuracy * 1.0)
            }
        )
        redrawLocationIndicator(point)
    }

    private fun redrawLocationIndicator(point: LatLng) {
        locationIndicator?.remove()
        locationIndicator = map.addCircle(
            CircleOptions().apply {
                center(point)
                fillColor(indicatorColor)
                strokeColor(indicatorStroke)
                strokeWidth(5F)
                radius(1_048_576 / 2.0.pow(map.cameraPosition.zoom.toDouble()))
                zIndex(10F)
            }
        )
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
