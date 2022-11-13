package com.zhufucdev.motion_emulator.hook

import android.telephony.*
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.zhufucdev.motion_emulator.data.CellMoment
import java.util.concurrent.Executor

object CellHooker : YukiBaseHooker() {
    private val listeners = mutableSetOf<(CellMoment) -> Unit>()
    override fun onHook() {
        classOf<TelephonyManager>().hook {
            injectMember {
                method {
                    name = "getCellLocation"
                    emptyParam()
                    returnType(classOf<CellLocation>())
                }
                replaceAny {
                    Scheduler.cells.cellLocation()
                }
            }

            injectMember {
                method {
                    name = "getAllCellInfo"
                    emptyParam()
                    returnType(classOf<List<CellInfo>>())
                }
                replaceAny {
                    Scheduler.cells.cell
                }
            }

            injectMember {
                method {
                    name = "getNeighboringCellInfo"
                    emptyParam()
                    returnType = classOf<List<NeighboringCellInfo>>()
                }
                replaceAny {
                    Scheduler.cells.neighboringInfo()
                }
            }

            injectMember {
                method {
                    name = "listen"
                    param(classOf<PhoneStateListener>(), IntType)
                    returnType = UnitType
                }
                replaceAny {
                    val listener = args(0).cast<PhoneStateListener>()
                    val mode = args(1).int()
                    addListener {
                        listener?.treatWith(it, mode)
                    }
                }
            }

            injectMember {
                method {
                    name = "registerTelephonyCallback"
                    param(classOf<Executor>(), classOf<TelephonyCallback>())
                    returnType = UnitType
                }
                replaceAny {
                    val executor = args(0).cast<Executor>()
                    val callback = args(1).cast<TelephonyCallback>()
                    addListener {
                        executor?.execute {
                            callback?.treatWith(Scheduler.cells)
                        }
                    }
                }
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
    }

    private fun addListener(l: (CellMoment) -> Unit) {
        l(Scheduler.cells)
        listeners.add(l)
    }

    fun raise(moment: CellMoment) {
        listeners.forEach {
            it(moment)
        }
    }
}