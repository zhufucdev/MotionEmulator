package com.zhufucdev.motion_emulator.hook

import android.app.PendingIntent
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import android.telephony.CellInfo
import android.telephony.CellLocation
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.type.android.LooperClass
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringType
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.hooking
import java.util.concurrent.Executor
import java.util.function.Consumer

object LocationHooker : YukiBaseHooker() {
    private val listeners = arrayListOf<(Point) -> Unit>()
    override fun onHook() {
        onAppLifecycle {
            attachBaseContext { baseContext, _ ->
                Scheduler.init(baseContext)
                loggerD(msg = "scheduler initialized")
            }
        }

        invalidateOthers()
        hookGPS()
    }

    fun raise(point: Point) {
        listeners.forEach {
            it.invoke(point)
            loggerD(tag = "Location Hooker", "received $it")
        }
    }

    private fun addListener(l: (Point) -> Unit) {
        listeners.add(l)
        loggerD(tag = "Location Hooker", "listener $l registered")
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
                    name = "listen"
                    param(classOf<PhoneStateListener>(), IntType)
                    returnType = UnitType
                }
                replaceAny {}
            }
        }
    }

    private fun hookGPS() {
        classOf<LocationManager>().hook {
            injectMember {
                method {
                    name = "getLastKnownLocation"
                    param(StringType, "android.location.LastLocationRequest".toClass())
                    returnType = classOf<Location>()
                }
                replaceAny {
                    loggerD("Location Hooker", "getLastKnownLocation = ${Scheduler.location}")
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
                method {
                    name = "requestLocationUpdates"
                    param(StringType, classOf<LocationRequest>(), classOf<Executor>(), classOf<LocationListener>())
                    returnType = UnitType
                }
                replaceAny {
                    val listener = args(3).cast<LocationListener>()
                    val provider = args(0).string()
                    addListener {
                        val location = it.android(provider)
                        listener?.onLocationChanged(location)
                    }
                    listener?.onLocationChanged(Scheduler.location.android(provider))
                }
            }

            injectMember {
                method {
                    name = "requestLocationUpdates"
                    param(StringType, classOf<LocationRequest>(), classOf<PendingIntent>())
                    returnType = UnitType
                }
                replaceAny {}
            }

            injectMember {
                method {
                    name = "requestSingleUpdate"
                    param(StringType, classOf<LocationListener>(), LooperClass)
                    returnType = UnitType
                }
                replaceAny {
                    val listener = args(1).cast<LocationListener>()
                    val provider = args(0).string()
                    addListener {
                        listener?.onLocationChanged(it.android(provider))
                    }
                    listener?.onLocationChanged(Scheduler.location.android(provider))
                }
            }

            injectMember {
                method {
                    name = "requestSingleUpdate"
                    param(classOf<Criteria>(), classOf<LocationListener>(), LooperClass)
                    returnType = UnitType
                }
                replaceAny {
                    val listener = args(1).cast<LocationListener>()
                    addListener {
                        listener?.onLocationChanged(it.android())
                    }
                    listener?.onLocationChanged(Scheduler.location.android())
                }
            }

            injectMember {
                method {
                    name = "requestSingleUpdate"
                    param(StringType, classOf<PendingIntent>())
                }
                replaceAny {}
            }

            injectMember {
                method {
                    name = "requestSingleUpdate"
                    param(classOf<Criteria>(), classOf<PendingIntent>())
                }
                replaceAny {}
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
}