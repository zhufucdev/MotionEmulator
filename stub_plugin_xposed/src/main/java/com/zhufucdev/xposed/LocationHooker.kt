@file:Suppress("DEPRECATION")

package com.zhufucdev.xposed

import android.app.Activity
import android.content.ContentResolver
import android.location.GnssStatus
import android.location.GpsSatellite
import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.location.OnNmeaMessageListener
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.log.loggerW
import com.highcapable.yukihookapi.hook.type.android.ActivityClass
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.DoubleType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.zhufucdev.stub.Point
import com.zhufucdev.stub.android
import com.zhufucdev.stub.estimateSpeed
import com.zhufucdev.stub.offsetFixed
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.concurrent.timer
import kotlin.random.Random
import kotlin.reflect.jvm.isAccessible

class LocationHooker(private val scheduler: XposedScheduler) : YukiBaseHooker() {
    companion object {
        private const val TAG = "LocationHook"
    }

    private var lastLocation = scheduler.location to System.currentTimeMillis()
    private var estimatedSpeed = 0F

    private val listeners = mutableMapOf<Any, (Point) -> Unit>()
    override fun onHook() {
        if (scheduler.hookingMethod.directHook) {
            invalidateOthers()
            hookGPS()
            val appClassLoaderSucceeded = hookAMap(appClassLoader)
            if (!appClassLoaderSucceeded) {
                ActivityClass.hook {
                    injectMember {
                        method {
                            name = "onCreate"
                            param(BundleClass)
                        }

                        beforeHook {
                            hookAMap((instance as Activity).classLoader, log = true)
                        }
                    }
                }
            }
            hookLocation()
        } else if (scheduler.hookingMethod.testProviderTrick) {
            testProviderTrick()
        }
    }

    fun raise(point: Point) {
        listeners.forEach { (_, p) ->
            estimatedSpeed =
                runCatching {
                    estimateSpeed(
                        point to System.currentTimeMillis(),
                        lastLocation
                    ).toFloat()
                }.getOrDefault(0f)
            p.invoke(point)
            lastLocation = point to System.currentTimeMillis()
        }
    }

    private fun redirectListener(original: Any, l: (Point) -> Unit) {
        listeners[original] = l
    }

    private fun cancelRedirection(listener: Any) {
        listeners.remove(listener)
    }

    /**
     * Make network and cell providers invalid
     */
    private fun invalidateOthers() {
        loggerI(TAG, "-- block other location methods --")

        classOf<WifiManager>().hook {
            injectMember {
                method {
                    name = "getScanResults"
                    emptyParam()
                    returnType = classOf<List<ScanResult>>()
                }
                afterHook {
                    result = emptyList<ScanResult>()
                }
            }

            injectMember {
                method {
                    name = "getWifiState"
                    emptyParam()
                    returnType = IntType
                }
                replaceTo(WifiManager.WIFI_STATE_ENABLED)
            }

            injectMember {
                method {
                    name = "isWifiEnabled"
                    emptyParam()
                    returnType = BooleanType
                }
                replaceTo(true)
            }

            injectMember {
                method {
                    name = "getConnectionInfo"
                    emptyParam()
                    returnType = classOf<WifiInfo>()
                }
                replaceTo(null)
            }
        }

        classOf<NetworkInfo>().hook {
            injectMember {
                method {
                    name = "isConnectedOrConnecting"
                    emptyParam()
                    returnType = BooleanType
                }
                replaceTo(true)
            }

            injectMember {
                method {
                    name = "isConnected"
                    emptyParam()
                    returnType = BooleanType
                }
                replaceTo(true)
            }

            injectMember {
                method {
                    name = "isAvailable"
                    emptyParam()
                    returnType = BooleanType
                }
                replaceTo(true)
            }
        }

        classOf<WifiInfo>().hook {
            injectMember {
                method {
                    name = "getSSID"
                    emptyParam()
                    returnType = StringClass
                }
                replaceTo("null")
            }

            injectMember {
                method {
                    name = "getBSSID"
                    emptyParam()
                    returnType = StringClass
                }
                replaceTo("00-00-00-00-00-00-00-00")
            }

            injectMember {
                method {
                    name = "getMacAddress"
                    emptyParam()
                    returnType = StringClass
                }
                replaceTo("00-00-00-00-00-00-00-00")
            }
        }
    }

    private fun hookGPS() {
        loggerI(TAG, "-- hook GPS --")

        val classOfLM = classOf<LocationManager>()
        classOfLM.hook {
            injectMember {
                method {
                    name = "getLastKnownLocation"
                    param(StringClass, "android.location.LastLocationRequest".toClass())
                    returnType = classOf<Location>()
                }
                replaceAny {
                    scheduler.location.android(args(0).string(), estimatedSpeed)
                }
            }

            injectMember {
                method {
                    name = "getLastLocation"
                    emptyParam()
                    returnType = classOf<Location>()
                }
                replaceAny {
                    scheduler.location.android(speed = estimatedSpeed)
                }
            }

            injectMember {
                members(*classOfLM.methods.filter { it.name == "requestLocationUpdates" || it.name == "requestSingleUpdate" }
                    .toTypedArray().also {
                        if (it.isEmpty()) {
                            loggerW(TAG, "active update block failed: no such method")
                        } else {
                            loggerD(
                                TAG,
                                "active update block finished with ${it.size} methods hooked"
                            )
                        }
                    })
                replaceAny {
                    val listener = args.firstOrNull { it is LocationListener } as LocationListener?
                        ?: return@replaceAny callOriginal()
                    val provider =
                        args.firstOrNull { it is String } as String? ?: LocationManager.GPS_PROVIDER
                    val handler = Handler(Looper.getMainLooper())
                    redirectListener(listener) {
                        val location = it.android(provider, estimatedSpeed)
                        handler.post {
                            listener.onLocationChanged(location)
                        }
                    }
                    listener.onLocationChanged(scheduler.location.android(provider, estimatedSpeed))
                }
            }

            injectMember {
                method {
                    name = "removeUpdates"
                    param(classOf<LocationListener>())
                }
                replaceAny {
                    val listener = args(0)
                    if (listeners.contains(listener)) {
                        cancelRedirection(listener)
                    } else {
                        callOriginal()
                    }
                }
            }

            // make the app believe gps works
            injectMember {
                method {
                    name = "getGpsStatus"
                    param(classOf<GpsStatus>())
                    returnType = classOf<GpsStatus>()
                }
                afterHook {
                    if (scheduler.satellites <= 0)
                        return@afterHook

                    val info = args(0).cast<GpsStatus>() ?: result as GpsStatus
                    val method7 =
                        GpsStatus::class.members.firstOrNull { it.name == "setStatus" && it.parameters.size == 8 }

                    if (method7 != null) {
                        method7.isAccessible = true

                        val prns = IntArray(scheduler.satellites) { it }
                        val ones = FloatArray(scheduler.satellites) { 1f }
                        val zeros = FloatArray(scheduler.satellites) { 0f }
                        val ephemerisMask = 0x1f
                        val almanacMask = 0x1f

                        //5 Scheduler.satellites are fixed
                        val usedInFixMask = 0x1f

                        method7.call(
                            info,
                            scheduler.satellites,
                            prns,
                            ones,
                            zeros,
                            zeros,
                            ephemerisMask,
                            almanacMask,
                            usedInFixMask
                        )
                    } else {
                        val method =
                            GpsStatus::class.members.firstOrNull { it.name == "setStatus" && it.parameters.size == 3 }
                        if (method == null) {
                            loggerE(TAG, "method GpsStatus::setStatus is not provided")
                            return@afterHook
                        }
                        method.isAccessible = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val fake = fakeGnssStatus
                            method.call(info, fake, 1000 + Random.nextInt(-500, 500))
                        } else {
                            loggerE(TAG, "Gnss api is not available")
                        }
                    }
                    result = info
                }
            }

            injectMember {
                method {
                    name = "addGpsStatusListener"
                    param(classOf<GpsStatus.Listener>())
                    returnType = BooleanType
                }

                replaceAny {
                    if (scheduler.satellites <= 0) {
                        return@replaceAny callOriginal()
                    }
                    val listener = args(0).cast<GpsStatus.Listener>()
                    listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_STARTED)
                    listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_FIRST_FIX)
                    timer(name = "satellite heartbeat", period = 1000) {
                        listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_SATELLITE_STATUS)
                    }
                    true
                }
            }

            injectMember {
                method {
                    name = "addNmeaListener"
                    param(classOf<GpsStatus.NmeaListener>())
                    returnType = BooleanType
                }

                replaceAny {
                    if (scheduler.satellites <= 0) {
                        return@replaceAny callOriginal()
                    }
                    false
                }
            }

            injectMember {
                method {
                    name = "addNmeaListener"
                    param(classOf<Executor>(), classOf<OnNmeaMessageListener>())
                    returnType = BooleanType
                }

                replaceAny {
                    if (scheduler.satellites <= 0) {
                        return@replaceAny callOriginal()
                    }
                    false
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                injectMember {
                    method {
                        name = "registerGnssStatusCallback"
                        param(classOf<Executor>(), classOf<GnssStatus.Callback>())
                        returnType = BooleanType
                    }
                    replaceAny {
                        if (scheduler.satellites <= 0) {
                            return@replaceAny callOriginal()
                        }

                        val callback = args(1).cast<GnssStatus.Callback>()
                        callback?.onStarted()
                        callback?.onFirstFix(1000 + Random.nextInt(-500, 500))
                        timer(name = "satellite heartbeat", period = 1000) {
                            callback?.onSatelliteStatusChanged(fakeGnssStatus ?: return@timer)
                        }
                        true
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                injectMember {
                    method {
                        name = "getCurrentLocation"
                        param(
                            StringClass,
                            classOf<LocationRequest>(),
                            classOf<CancellationSignal>(),
                            classOf<Executor>(),
                            classOf<Consumer<Location>>()
                        )
                        returnType = UnitType
                    }
                    replaceAny {
                        args(4).cast<Consumer<Location>>()
                            ?.accept(
                                scheduler.location.android(args(0).string(), estimatedSpeed)
                            )
                    }
                }
            }
        }

        classOf<GpsStatus>().hook {
            injectMember {
                method {
                    name = "getSatellites"
                    emptyParam()
                    returnType = classOf<Iterable<GpsSatellite>>()
                }

                afterHook {
                    if (scheduler.satellites <= 0)
                        return@afterHook

                    result = fakeSatellites.also {
                        loggerD(TAG, "${it.count()} satellites are fixed")
                    }
                }
            }

            injectMember {
                method {
                    name = "getMaxSatellites"
                    emptyParam()
                    returnType = IntType
                }

                replaceAny {
                    if (scheduler.satellites <= 0) callOriginal()
                    else scheduler.satellites
                }
            }
        }

        classOf<GnssStatus>().hook {
            injectMember {
                method {
                    name = "usedInFix"
                    param(IntType)
                    returnType = BooleanType
                }

                replaceAny {
                    if (scheduler.satellites <= 0) callOriginal()
                    else true
                }
            }
        }
    }

    /**
     * AMap uses network location, which
     * is a troublemaker for this project
     *
     * Specially designed for it
     */
    private fun hookAMap(classLoader: ClassLoader, log: Boolean = false): Boolean {
        loggerI(TAG, "-- hook Amap --")
        var succeeded = true

        try {
            classLoader.loadAMapLocation().hook {
                locationHook()
            }

            classLoader.loadAMapLocation().hook {
                injectMember {
                    method {
                        name = "getSatellites"
                        emptyParam()
                    }
                    replaceAny {
                        scheduler.satellites
                    }
                }

                injectMember {
                    method {
                        name = "getAccuracy"
                        emptyParam()
                    }
                    replaceTo(5F)
                }
            }
        } catch (e: ClassNotFoundException) {
            succeeded = false
            if (log) {
                loggerE(TAG, "Failed to hook AMap location", e)
            }
        }

        try {
            val listenerOf = mutableMapOf<Any, Any>()
            val stateOf = mutableMapOf<Any, Boolean>()

            classLoader.loadAMapLocationClient().hook {
                injectMember {
                    method {
                        name = "setLocationListener"
                        param(classLoader.loadAMapListener())
                    }
                    replaceAny {
                        val listener = args[0]
                            ?: return@replaceAny callOriginal()

                        listenerOf[instance] = listener
                        loggerD(TAG, "AMap location registered")
                    }
                }

                injectMember {
                    method {
                        name = "startLocation"
                        emptyParam()
                    }

                    replaceUnit {
                        loggerI(TAG, "AMap location started")
                        stateOf[instance] = true

                        val listener = listenerOf[instance] ?: return@replaceUnit
                        val listenerClass = classLoader.loadAMapListener()
                        val locationClass = classLoader.loadAMapLocation()
                        val method = listenerClass.getMethod(
                            "onLocationChanged",
                            locationClass
                        )
                        val handler = Handler(Looper.getMainLooper())

                        redirectListener(listener) {
                            val android = it.android(speed = estimatedSpeed)
                            // create an AMapLocation instance via reflection
                            val amap =
                                locationClass.getConstructor(classOf<Location>())
                                    .newInstance(android)
                            // Location type 1 is GPS located
                            locationClass.getMethod("setLocationType", IntType)
                                .invoke(amap, 1)
                            handler.post {
                                method.invoke(listener, amap)
                            }
                            loggerD(TAG, "AMap location redirected")
                        }
                    }
                }

                injectMember {
                    method {
                        name = "stopLocation"
                        emptyParam()
                    }

                    replaceUnit {
                        stateOf[instance] = false
                        loggerI(TAG, "AMap location stopped")

                        val listener = listenerOf[instance] ?: return@replaceUnit
                        cancelRedirection(listener)
                    }
                }

                injectMember {
                    method {
                        name = "isStarted"
                        emptyParam()
                        returnType(BooleanType)
                    }

                    replaceAny {
                        stateOf[instance] == true
                    }
                }
            }
        } catch (e: ClassNotFoundException) {
            succeeded = false
            if (log) {
                loggerE(TAG, "Failed to hook AMap Location Client", e)
            }
        }

        return succeeded
    }

    private fun YukiMemberHookCreator.locationHook() {
        injectMember {
            method {
                name = "getLatitude"
                emptyParam()
                returnType = DoubleType
            }

            afterHook {
                result = scheduler.location.offsetFixed().latitude
            }
        }

        injectMember {
            method {
                name = "getLongitude"
                emptyParam()
                returnType = DoubleType
            }

            afterHook {
                result = scheduler.location.offsetFixed().longitude
            }
        }
    }

    private fun hookLocation() {
        loggerI(TAG, "-- hook location --")

        classOf<Location>().hook {
            locationHook()
        }
    }

    private fun testProviderTrick() {
        loggerI(TAG, "-- make test provider undetectable --")

        classOf<Location>().hook {
            injectMember {
                method {
                    name = "isMock"
                    emptyParam()
                }
                replaceToFalse()
            }

            injectMember {
                method {
                    name = "isFromMockProvider"
                    emptyParam()
                }
                replaceToFalse()
            }
        }

        classOf<Settings.Secure>().hook {
            injectMember {
                method {
                    name = "getString"
                    param(classOf<ContentResolver>(), StringClass)
                    modifiers { isStatic }
                }
                afterHook {
                    val item = args(1).string()
                    if (item == Settings.Secure.ALLOW_MOCK_LOCATION) {
                        loggerI(TAG, "Spoof mock location developer options")
                        result = "0"
                    }
                }
            }
        }

        classOf<LocationManager>().hook {
            injectMember {
                method {
                    name = "removeTestProvider"
                    param(StringClass)
                }
                replaceUnit {
                    loggerI(TAG, "Block test provider removal")
                }
            }
        }
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    val fakeGnssStatus: GnssStatus?
        get() {
            val svid = IntArray(scheduler.satellites) { it }
            val zeros = FloatArray(scheduler.satellites) { 0f }
            val ones = FloatArray(scheduler.satellites) { 1f }

            val constructor = GnssStatus::class.constructors.firstOrNull { it.parameters.size >= 6 }
            if (constructor == null) {
                loggerE(TAG, "GnssStatus constructor not available")
                return null
            }

            val constructorArgs = Array(constructor.parameters.size) { index ->
                when (index) {
                    0 -> scheduler.satellites
                    1 -> svid
                    2 -> ones
                    else -> zeros
                }
            }
            return constructor.call(*constructorArgs)
        }

    private val fakeSatellites: Iterable<GpsSatellite> =
        buildList {
            val clz = classOf<GpsSatellite>()
            for (i in 1..scheduler.satellites) {
                val instance =
                    clz.constructor {
                        param(IntType)
                    }
                        .get()
                        .newInstance<GpsSatellite>(i) ?: return@buildList
                listOf("mValid", "mHasEphemeris", "mHasAlmanac", "mUsedInFix").forEach {
                    clz.field { name = it }.get(instance).setTrue()
                }
                listOf("mSnr", "mElevation", "mAzimuth").forEach {
                    clz.field { name = it }.get(instance).set(1F)
                }
                add(instance)
            }
        }
}