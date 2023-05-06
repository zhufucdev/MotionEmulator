package com.zhufucdev.motion_emulator.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.amap.api.maps.AMap
import com.amap.api.maps.MapView
import com.google.android.gms.maps.SupportMapFragment
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.data.CoordinateSystem
import com.zhufucdev.data.Point
import com.zhufucdev.data.Trace
import com.zhufucdev.motion_emulator.ui.DrawResult
import com.zhufucdev.motion_emulator.ui.DrawToolCallback
import com.zhufucdev.motion_emulator.ui.GpsToolCallback
import kotlin.coroutines.suspendCoroutine

class UnifiedMapFragment : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributes: AttributeSet) : super(context, attributes) {
        context.theme.obtainStyledAttributes(
            attributes, R.styleable.UnifiedMapFragment, 0, 0
        ).apply {
            init(this)
        }
    }

    constructor(context: Context, attributes: AttributeSet, defStyleAttr: Int)
            : super(context, attributes, defStyleAttr) {
        context.theme.obtainStyledAttributes(
            attributes, R.styleable.UnifiedMapFragment,
            defStyleAttr, 0
        ).apply {
            init(this)
        }
    }

    private fun init(args: TypedArray) {
        provider = Provider.values()[args.getInteger(R.styleable.UnifiedMapFragment_provider, 0)]
    }

    private val container = FrameLayout(context)

    init {
        addView(container)
    }

    enum class Provider {
        AMAP, GCP_MAPS
    }

    var provider: Provider = Provider.AMAP
        set(value) {
            if (field == value && controller != null) return
            removeAllViews()
            initializeAs(value)
            field = value
        }

    var controller: MapController? = null
        private set

    suspend fun requireController(): MapController = suspendCoroutine { res ->
        val current = controller
        if (current != null) res.resumeWith(Result.success(current))
        onReady.add { res.resumeWith(Result.success(it)) }
    }

    private val onReady = mutableSetOf<(MapController) -> Unit>()

    private fun initializeAs(provider: Provider) {
        val maps = when (provider) {
            Provider.AMAP ->
                AMapFragment().apply {
                    getMapAsync {
                        val ctrl = AMapController(it, requireContext())
                        controller = ctrl
                        notifyReady(ctrl)
                    }
                }

            Provider.GCP_MAPS ->
                SupportMapFragment.newInstance().apply {
                    getMapAsync {
                        val ctrl = GoogleMapsController(requireContext(), it)
                        controller = ctrl
                        notifyReady(ctrl)
                    }
                }
        }

        val context = this.context
        if (context is FragmentActivity) {
            context.supportFragmentManager.beginTransaction()
                .replace(id, maps)
                .setReorderingAllowed(false)
                .commit()
        } else {
            throw IllegalStateException("Can't initialize unless in a Fragment Activity context.")
        }
    }

    private fun notifyReady(controller: MapController) {
        onReady.forEach { it.invoke(controller) }
        onReady.clear()
    }
}

class AMapFragment : Fragment() {
    private lateinit var map: MapView
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        map = MapView(container?.context)
        map.onCreate(savedInstanceState)
        getter?.invoke(map.map)
        return map
    }

    override fun onDestroy() {
        super.onDestroy()
        map.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        map.onSaveInstanceState(outState)
    }

    private var getter: ((AMap) -> Unit)? = null
    fun getMapAsync(getter: (AMap) -> Unit) {
        this.getter = getter
    }
}

abstract class MapController(protected val context: Context) {
    abstract fun moveCamera(location: Point, focus: Boolean = false, animate: Boolean = false)
    abstract fun boundCamera(bounds: TraceBounds, animate: Boolean = false)
    abstract fun project(x: Int, y: Int): Point
    abstract suspend fun getAddress(point: Point): String?
    abstract fun cameraCenter(): Point

    abstract fun usePen(): MapScrawl

    @SuppressLint("ClickableViewAccessibility")
    fun useDraw(screen: View): DrawToolCallback {
        val pen = usePen()

        val callback = object : DrawToolCallback {
            override fun clear() {
                pen.clear()
            }

            override fun undo() {
                pen.undo()
            }

            override suspend fun complete(): DrawResult {
                screen.isVisible = false
                screen.setOnTouchListener(null)
                val points = pen.points

                if (points.isEmpty()) {
                    return DrawResult()
                }

                val address = try {
                    getAddress(cameraCenter())
                } catch (e: Exception) {
                    null
                }
                val name = address
                    ?.let { context.getString(R.string.text_near, it) }
                    ?: context.effectiveTimeFormat().dateString()
                val result = DrawResult(name, points, points.first().coordinateSystem)
                completeListener?.invoke(result)
                return result
            }

            private var completeListener: ((DrawResult) -> Unit)? = null
            override fun onCompleted(l: (DrawResult) -> Unit) {
                completeListener = l
            }
        }

        screen.isVisible = true
        screen.setOnTouchListener { _, event ->
            if (event.pointerCount > 1) {
                return@setOnTouchListener false
            }
            if (event.action == MotionEvent.ACTION_DOWN) {
                pen.markBegin()
            }
            val point = project(event.x.toInt(), event.y.toInt())
            pen.addPoint(point)
            true
        }

        return callback
    }

    @SuppressLint("MissingPermission")
    suspend fun useGps(): GpsToolCallback = suspendCoroutine { c ->
        val pen = usePen()
        pen.markBegin()
        val locationManager = context.getSystemService(LocationManager::class.java)
        val provider = LocationManager.GPS_PROVIDER
        if (!locationManager.isProviderEnabled(provider)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !locationManager.hasProvider(provider)) {
                c.resumeWith(Result.failure(RuntimeException("No $provider")))
                return@suspendCoroutine
            }
        }

        var paused = false
        var started = false
        var callback: GpsToolCallback? = null
        val listener = LocationListener { location ->
            val point = location.toPoint()
            if (!paused)
                pen.addPoint(point)
            if (!started) {
                moveCamera(point, focus = true, animate = true)
                started = true
                c.resumeWith(Result.success(callback!!))
            }
            updateLocationIndicator(location)
        }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 0, mapCaptureAccuracy, listener
        )

        callback = object : GpsToolCallback {
            private var completeListener: ((DrawResult) -> Unit)? = null
            override val isPaused: Boolean
                get() = paused

            override fun pause() {
                paused = true
            }

            override fun unpause() {
                pen.markBegin()
                paused = false
            }

            override fun undo() {
                pen.undo()
            }

            override suspend fun complete(): DrawResult {
                val points = pen.points
                val result =
                    if (points.isEmpty()) DrawResult()
                    else {
                        val address = getAddress(points.first())
                        val name = address
                            ?.let { context.getString(R.string.text_near, it) }
                            ?: context.effectiveTimeFormat().dateString()
                        DrawResult(
                            poiName = name,
                            trace = points,
                            coordinateSystem = CoordinateSystem.WGS84
                        )
                    }
                completeListener?.invoke(result)

                locationManager.removeUpdates(listener)
                return result
            }

            override fun onCompleted(l: (DrawResult) -> Unit) {
                completeListener = l
            }
        }
    }

    abstract fun drawTrace(trace: Trace): MapTraceCallback
    abstract fun updateLocationIndicator(location: Location)
    abstract var displayStyle: MapStyle
    abstract var displayType: MapDisplayType
}

interface MapTraceCallback {
    fun remove()
}

const val mapCaptureAccuracy = 0.5F // in meters

enum class MapStyle {
    NORMAL, NIGHT, SATELLITE
}

enum class MapDisplayType {
    STILL, INTERACTIVE
}

interface MapScrawl {
    val points: List<Point>
    fun addPoint(point: Point)
    fun markBegin()
    fun undo()
    fun clear()
}