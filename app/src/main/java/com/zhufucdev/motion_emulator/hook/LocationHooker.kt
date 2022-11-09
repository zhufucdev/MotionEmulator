package com.zhufucdev.motion_emulator.hook

import android.location.*
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CancellationSignal
import android.telephony.*
import androidx.annotation.RequiresApi
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationClientOption.AMapLocationMode
import com.amap.api.location.AMapLocationListener
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.type.java.*
import com.zhufucdev.motion_emulator.data.Point
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.random.Random
import kotlin.reflect.jvm.isAccessible

object LocationHooker : YukiBaseHooker() {
    private const val TAG = "Location Hook"

    private val listeners = arrayListOf<(Point) -> Unit>()
    override fun onHook() {
        invalidateOthers()
        hookGPS()
        hookAMap()
        hookLocation()
    }

    fun raise(point: Point) {
        listeners.forEach {
            it.invoke(point)
        }
    }

    private fun redirectListener(l: (Point) -> Unit) {
        listeners.add(l)
    }

    /**
     * Make network and cell providers invalid
     */
    private fun invalidateOthers() {
        classOf<TelephonyManager>().hook {
            injectMember {
                method {
                    name = "getCellLocation"
                    emptyParam()
                    returnType(classOf<CellLocation>())
                }
                replaceTo(null)
            }

            injectMember {
                method {
                    name = "getAllCellInfo"
                    emptyParam()
                    returnType(classOf<List<CellInfo>>())
                }
                replaceTo(null)
            }

            injectMember {
                method {
                    name = "getNeighboringCellInfo"
                    emptyParam()
                    returnType = classOf<List<NeighboringCellInfo>>()
                }
                replaceTo(null)
            }

            injectMember {
                method {
                    name = "listen"
                    param(classOf<PhoneStateListener>(), IntType)
                    returnType = UnitType
                }
                replaceAny {}
            }

            injectMember {
                method {
                    name = "registerTelephonyCallback"
                    param(classOf<Executor>(), classOf<TelephonyCallback>())
                    returnType = UnitType
                }
                replaceAny {}
            }

            injectMember {
                method {
                    name = "getPhoneCount"
                    emptyParam()
                    returnType = IntType
                }
                replaceTo(1)
            }

            injectMember {
                method {
                    name = "getActiveModemCount"
                    emptyParam()
                    returnType = IntType
                }
                replaceTo(1)
            }
        }

        classOf<PhoneStateListener>().hook {
            injectMember {
                method {
                    name = "onCellLocationChanged"
                    param(classOf<CellLocation>())
                    returnType = UnitType
                }
                replaceAny {}
            }
        }

        classOf<WifiManager>().hook {
            injectMember {
                method {
                    name = "getScanResults"
                    emptyParam()
                    returnType = classOf<List<ScanResult>>()
                }
                replaceTo(null)
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
                    returnType = StringType
                }
                replaceTo("null")
            }

            injectMember {
                method {
                    name = "getBSSID"
                    emptyParam()
                    returnType = StringType
                }
                replaceTo("00-00-00-00-00-00-00-00")
            }

            injectMember {
                method {
                    name = "getMacAddress"
                    emptyParam()
                    returnType = StringType
                }
                replaceTo("00-00-00-00-00-00-00-00")
            }
        }
    }

    private fun hookGPS() {
        val classOfLM = classOf<LocationManager>()
        classOfLM.hook {
            injectMember {
                method {
                    name = "getLastKnownLocation"
                    param(StringType, "android.location.LastLocationRequest".toClass())
                    returnType = classOf<Location>()
                }
                replaceAny {
                    Scheduler.location.android(args(0).string())
                }
            }

            injectMember {
                method {
                    name = "getLastLocation"
                    emptyParam()
                    returnType = classOf<Location>()
                }
                replaceAny {
                    Scheduler.location.android()
                }
            }

            injectMember {
                members(*classOfLM.methods.filter { it.name == "requestLocationUpdates" }.toTypedArray())
                replaceAny {
                    val listener = args.firstOrNull { it is LocationListener } as LocationListener?
                    val provider = args.firstOrNull { it is String } as String? ?: LocationManager.GPS_PROVIDER
                    redirectListener {
                        val location = it.android(provider)
                        listener?.onLocationChanged(location)
                    }
                    listener?.onLocationChanged(Scheduler.location.android(provider))
                }
            }

            injectMember {
                members(*classOfLM.methods.filter { it.name == "requestSingleUpdate" }.toTypedArray())
                replaceAny {
                    val listener = args.firstOrNull { it is LocationListener } as LocationListener?
                    val provider = args.firstOrNull { it is String } as String? ?: LocationManager.GPS_PROVIDER
                    redirectListener {
                        listener?.onLocationChanged(it.android(provider))
                    }
                    listener?.onLocationChanged(Scheduler.location.android(provider))
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
                    val info = args(0).cast<GpsStatus>() ?: result as GpsStatus
                    val method7 =
                        GpsStatus::class.members.firstOrNull { it.name == "setStatus" && it.parameters.size == 8 }

                    if (method7 != null) {
                        method7.isAccessible = true

                        val svCount = 5
                        val prns = intArrayOf(1, 2, 3, 4, 5)
                        val snrs = floatArrayOf(0f, 0f, 0f, 0f, 0f)
                        val elevations = floatArrayOf(0f, 0f, 0f, 0f, 0f)
                        val azimuths = floatArrayOf(0f, 0f, 0f, 0f, 0f)
                        val ephemerisMask = 0x1f
                        val almanacMask = 0x1f

                        //5 satellites are fixed
                        val usedInFixMask = 0x1f

                        method7.call(
                            info,
                            svCount,
                            prns,
                            snrs,
                            elevations,
                            azimuths,
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
                    val listener = args(0).cast<GpsStatus.Listener>()
                    listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_STARTED)
                    listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_FIRST_FIX)
                    true
                }
            }

            injectMember {
                method {
                    name = "addNmeaListener"
                    param(classOf<GpsStatus.NmeaListener>())
                    returnType = BooleanType
                }

                replaceTo(false)
            }

            injectMember {
                method {
                    name = "addNmeaListener"
                    param(classOf<Executor>(), classOf<OnNmeaMessageListener>())
                    returnType = BooleanType
                }

                replaceTo(false)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                injectMember {
                    method {
                        name = "registerGnssStatusCallback"
                        param(classOf<Executor>(), classOf<GnssStatus.Callback>())
                        returnType = BooleanType
                    }
                    replaceAny {
                        val callback = args(1).cast<GnssStatus.Callback>()
                        callback?.onStarted()
                        callback?.onFirstFix(1000 + Random.nextInt(-500, 500))

                        callback?.onSatelliteStatusChanged(fakeGnssStatus ?: return@replaceAny true)
                        true
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                injectMember {
                    method {
                        name = "getCurrentLocation"
                        param(
                            StringType,
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
                                Scheduler.location.android(args(0).string())
                            )
                    }
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
    private fun hookAMap() {
        classOf<AMapLocationClient>().hook {
            injectMember {
                method {
                    name = "setLocationListener"
                    param(classOf<AMapLocationListener>())
                }

                replaceAny {
                    val listener = args(0).cast<AMapLocationListener>()
                    redirectListener {
                        listener?.onLocationChanged(it.amap())
                        loggerI(TAG, "AMap location received")
                    }
                    loggerI(TAG, "AMap location registered")
                }
            }

            injectMember {
                method {
                    name = "startLocation"
                    emptyParam()
                }

                beforeHook {
                    loggerI(TAG, "AMap location started")
                }
            }
        }

        classOf<AMapLocationClientOption>().hook {
            injectMember {
                method {
                    name = "setLocationMode"
                    param(classOf<AMapLocationMode>())
                }

                beforeHook {
                    args[0] = AMapLocationMode.Device_Sensors
                    loggerI(TAG, "Modified amap location mode")
                }
            }
        }
    }

    private fun hookLocation() {
        fun YukiMemberHookCreator.common() {
            injectMember {
                method {
                    name = "getLatitude"
                    emptyParam()
                    returnType = DoubleType
                }

                afterHook {
                    result = Scheduler.location.latitude
                }
            }

            injectMember {
                method {
                    name = "getLongitude"
                    emptyParam()
                    returnType = DoubleType
                }

                afterHook {
                    result = Scheduler.location.longitude
                }
            }
        }

        classOf<Location>().hook {
            common()
        }

        classOf<AMapLocation>().hook {
            common()
        }
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    val fakeGnssStatus: GnssStatus?
        get() {
            val sv = 5
            val svid = intArrayOf(1, 2, 3, 4, 5)
            val cn = floatArrayOf(0f, 0f, 0f, 0f)

            val constructor = GnssStatus::class.constructors.firstOrNull { it.parameters.size == 7 }
            if (constructor == null) {
                loggerE(TAG, "GnssStatus constructor not available")
                return null
            }

            return constructor.call(sv, svid, cn, cn, cn, cn, cn)
        }
}
