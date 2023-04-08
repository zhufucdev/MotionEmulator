package com.zhufucdev.motion_emulator.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
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
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Trace

@SuppressLint("MissingPermission")
class GoogleMapsController(context: Context, private val map: GoogleMap) : MapController(context) {
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

    override fun project(x: Int, y: Int): Point =
        map.projection.fromScreenLocation(android.graphics.Point(x, y)).toPoint()

    override suspend fun getAddress(point: Point): String? {
        return getAddressWithGoogle(point.ensureGoogleCoordinate().toGoogleLatLng(), context)
    }

    override fun cameraCenter(): Point = map.cameraPosition.target.toPoint()

    private val lineColor get() = getAttrColor(com.google.android.material.R.attr.colorTertiary, context)

    override fun usePen() = object : MapScrawl {
        val polyline = PolylineOptions()
        var lastPos = LatLng(0.0, 0.0)
        val backStack = arrayListOf<ArrayList<LatLng>>() // for undoing
        var lastPolyline: Polyline? = null

        override val points: List<Point> by lazy(backStack) { polyline.points.map { it.toPoint() } }

        init {
            polyline.color(lineColor)
        }

        override fun addPoint(point: Point) {
            val al = point.ensureGoogleCoordinate().toGoogleLatLng()
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

    override fun drawTrace(trace: Trace): MapTraceCallback {
        val line = map.addPolyline {
            color(lineColor)
            addAll(trace.points.map { it.ensureGoogleCoordinate().toGoogleLatLng() }
                .plus(trace.points[0].ensureGoogleCoordinate().toGoogleLatLng()))
        }
        return object : MapTraceCallback {
            override fun remove() {
                line.remove()
            }
        }
    }

    private val locationIndicator: (Location) -> Unit by lazy {
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
            listener?.onLocationChanged(it)
        }
    }

    override fun updateLocationIndicator(location: Location) {
        locationIndicator.invoke(location)
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
