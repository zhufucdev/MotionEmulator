package com.zhufucdev.motion_emulator.ui.map

import android.content.Context
import android.content.res.TypedArray
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.amap.api.maps.AMap
import com.amap.api.maps.MapView
import com.google.android.gms.maps.SupportMapFragment
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Trace
import com.zhufucdev.motion_emulator.ui.DrawToolCallback

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

    fun requireController(): MapController {
        return controller ?: throw NullPointerException()
    }

    private var onReady: ((MapController) -> Unit)? = null
    fun setReadyListener(l: (MapController) -> Unit) {
        onReady = l
    }

    private fun initializeAs(provider: Provider) {
        val maps = when (provider) {
            Provider.AMAP ->
                AMapFragment().apply {
                    getMapAsync {
                        val ctrl = AMapController(it, requireContext())
                        controller = ctrl
                        onReady?.invoke(ctrl)
                    }
                }

            Provider.GCP_MAPS ->
                SupportMapFragment.newInstance().apply {
                    getMapAsync {
                        val ctrl = GoogleMapsController(requireContext(), it)
                        controller = ctrl
                        onReady?.invoke(ctrl)
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
}

class AMapFragment : Fragment() {
    private lateinit var map: MapView
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        map = MapView(container?.context)
        getter?.invoke(map.map)
        map.onCreate(savedInstanceState)
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

interface MapController {
    fun moveCamera(location: Point, focus: Boolean = false, animate: Boolean = false)
    fun boundCamera(bounds: TraceBounds, animate: Boolean = false)
    fun useDraw(): DrawToolCallback
    fun drawTrace(trace: Trace): MapTraceCallback
    fun updateLocationIndicator(point: Point)
    var displayStyle: MapStyle
    var displayType: MapDisplayType
}

interface MapTraceCallback {
    fun remove()
}

const val drawPrecision = 0.5F // in meters

enum class MapStyle {
    NORMAL, NIGHT, SATELLITE
}

enum class MapDisplayType {
    STILL, INTERACTIVE
}