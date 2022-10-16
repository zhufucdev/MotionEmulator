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
import com.highcapable.yukihookapi.hook.type.android.LooperClass
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringType
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.zhufucdev.motion_emulator.hooking
import java.util.concurrent.Executor
import java.util.function.Consumer

object LocationHooker : YukiBaseHooker() {
    override fun onHook() {
        invalidateOthers()
        hookGPS()
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
                replaceAny {
                    if (hooking) null
                    else callOriginal()
                }
            }

            injectMember {
                method {
                    name = "getAllCellInfo"
                    emptyParam()
                    returnType(classOf<List<CellInfo>>())
                }
                replaceAny {
                    if (hooking) null
                    else callOriginal()
                }
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
                    if (hooking) {
                        Fake.location.android(args(0).string())
                    } else {
                        callOriginal()
                    }
                }
            }

            injectMember {
                method {
                    name = "getLastLocation"
                    emptyParam()
                    returnType = classOf<Location>()
                }
                replaceAny {
                    if (hooking) {
                        Fake.location.android()
                    } else {
                        callOriginal()
                    }
                }
            }

            injectMember {
                method {
                    name = "requestLocationUpdates"
                    param(StringType, classOf<LocationRequest>(), classOf<Executor>(), classOf<LocationListener>())
                    returnType = UnitType
                }
                replaceAny {
                    if (hooking) {
                        val listener = args(3).cast<LocationListener>()
                        val provider = args(0).string()
                        Fake.addLocationListener {
                            val location = it.android(provider)
                            listener?.onLocationChanged(location)
                        }
                    } else {
                        callOriginal()
                    }
                }
            }

            injectMember {
                method {
                    name = "requestLocationUpdates"
                    param(StringType, classOf<LocationRequest>(), classOf<PendingIntent>())
                    returnType = UnitType
                }
                replaceAny {
                    if (!hooking) {
                        callOriginal()
                    }
                }
            }

            injectMember {
                method {
                    name = "requestSingleUpdate"
                    param(StringType, classOf<LocationListener>(), LooperClass)
                    returnType = UnitType
                }
                replaceAny {
                    if (hooking) {
                        val listener = args(1).cast<LocationListener>()
                        val provider = args(0).string()
                        Fake.addLocationListener {
                            listener?.onLocationChanged(it.android(provider))
                        }
                    }
                }
            }

            injectMember {
                method {
                    name = "requestSingleUpdate"
                    param(classOf<Criteria>(), classOf<LocationListener>(), LooperClass)
                    returnType = UnitType
                }
                replaceAny {
                    if (hooking) {
                        val listener = args(1).cast<LocationListener>()
                        Fake.addLocationListener {
                            listener?.onLocationChanged(it.android())
                        }
                    }
                }
            }

            injectMember {
                method {
                    name = "requestSingleUpdate"
                    param(StringType, classOf<PendingIntent>())
                }
                replaceAny {
                    if (!hooking) {
                        callOriginal()
                    }
                }
            }

            injectMember {
                method {
                    name = "requestSingleUpdate"
                    param(classOf<Criteria>(), classOf<PendingIntent>())
                }
                replaceAny {
                    if (!hooking) {
                        callOriginal()
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
                        if (hooking) {
                            args(4).cast<Consumer<Location>>()
                                ?.accept(
                                    Fake.location.android(args(0).string())
                                )
                        } else {
                            callOriginal()
                        }
                    }
                }
            }
        }
    }
}