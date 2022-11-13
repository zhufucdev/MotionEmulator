package com.zhufucdev.motion_emulator.data

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellLocation
import android.telephony.NeighboringCellInfo
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.highcapable.yukihookapi.hook.log.loggerW
import java.util.Timer
import java.util.concurrent.Executor
import kotlin.concurrent.timer
import kotlin.math.abs
import kotlin.reflect.full.memberFunctions

object TelephonyRecorder {
    private lateinit var manager: TelephonyManager
    fun init(context: Context) {
        manager = context.getSystemService(TelephonyManager::class.java)
        checkPermission = {
            context.checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                    && context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun start(): TelephonyRecordCallback {
        if (!checkPermission()) {
            return noop()
        }

        val start = System.currentTimeMillis()
        val timeline = arrayListOf<CellMoment>()

        var updateListener: ((CellMoment) -> Unit)? = null
        fun elapsed(): Float = (System.currentTimeMillis() - start) / 1000F
        val cancel: () -> Unit

        @Synchronized
        fun mergeIfPossible(moment: CellMoment): CellMoment {
            if (timeline.isNotEmpty() && abs(moment.elapsed - timeline.last().elapsed) <= VERTICAL_PERIOD) {
                val target = timeline.last()
                if (!target.isSameTypeOf(moment)) {
                    timeline.removeLast()
                    val merged = target.merge(moment)
                    timeline.add(merged)
                    return merged
                }
            }
            timeline.add(moment)
            return moment
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CellInfoListener, TelephonyCallback.CellLocationListener {
                override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                    val moment = CellMoment(elapsed(), cellInfo)
                    val merged = mergeIfPossible(moment)
                    updateListener?.invoke(merged)
                }

                override fun onCellLocationChanged(location: CellLocation) {
                    val moment = CellMoment(elapsed(), location = location)
                    val merged = mergeIfPossible(moment)
                    updateListener?.invoke(merged)
                }
            }

            manager.registerTelephonyCallback(mainExecutor, telephonyCallback)
            cancel = {
                manager.unregisterTelephonyCallback(telephonyCallback)
            }
        } else {
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
                    if (cellInfo != null) {
                        val moment = CellMoment(elapsed(), cellInfo)
                        mergeIfPossible(moment)
                        updateListener?.invoke(moment)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onCellLocationChanged(location: CellLocation?) {
                    if (location != null) {
                        val moment = CellMoment(elapsed(), location = location)
                        val merged = mergeIfPossible(moment)
                        updateListener?.invoke(merged)
                    }
                }
            }
            var timer: Timer? = null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val method =
                    TelephonyManager::class.memberFunctions.firstOrNull { it.name.startsWith("getNeighboringCellInfo") }
                if (method == null) {
                    loggerW(tag = "telephony recorder", "method to get neighboring cell info isn't available")
                } else {
                    timer = timer("neighboring daemon", period = 1500L) {
                        val infos = method.call(manager) as List<NeighboringCellInfo>? ?: return@timer
                        val moment = CellMoment(elapsed(), neighboring = infos)
                        val merged = mergeIfPossible(moment)
                        updateListener?.invoke(merged)
                    }
                }
            }

            manager.listen(
                listener,
                PhoneStateListener.LISTEN_CELL_INFO
                        or PhoneStateListener.LISTEN_CELL_LOCATION
            )
            cancel = {
                manager.listen(listener, PhoneStateListener.LISTEN_NONE)
                timer?.cancel()
            }
        }

        return object : TelephonyRecordCallback {
            override fun onUpdate(l: (CellMoment) -> Unit) {
                updateListener = l
            }

            override fun summarize(): CellTimeline {
                cancel()
                return CellTimeline(NanoIdUtils.randomNanoId(), start, timeline)
            }
        }
    }

    private val mainExecutor = object : Executor {
        private val handler = Handler(Looper.getMainLooper())
        override fun execute(command: Runnable?) {
            handler.post {
                command?.run()
            }
        }
    }

    private lateinit var checkPermission: () -> Boolean
    private fun noop() = object : TelephonyRecordCallback {
        override fun onUpdate(l: (CellMoment) -> Unit) {
        }

        override fun summarize(): CellTimeline {
            return CellTimeline(NanoIdUtils.randomNanoId(), 0, emptyList())
        }
    }
}


interface TelephonyRecordCallback {
    fun onUpdate(l: (CellMoment) -> Unit)
    fun summarize(): CellTimeline
}

fun CellMoment.isSameTypeOf(other: CellMoment): Boolean =
    cell.isEmpty() == other.cell.isEmpty()
            && neighboring.isEmpty() == other.neighboring.isEmpty()
            && (location == null) == (other.location == null)

fun CellMoment.merge(other: CellMoment): CellMoment {
    val rCell: List<CellInfo> = cell.takeIf { it.isNotEmpty() } ?: other.cell
    val rNeighboring: List<NeighboringCellInfo> =
        neighboring.takeIf { it.isNotEmpty() } ?: other.neighboring
    val rLocation: CellLocation? = location ?: other.location

    return CellMoment(elapsed, rCell, rNeighboring, rLocation)
}
