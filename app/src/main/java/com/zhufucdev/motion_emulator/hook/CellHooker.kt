@file:Suppress("DEPRECATION")

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

    var toggle = Toggle.PRESENT

    override fun onHook() {
        classOf<TelephonyManager>().hook {
            injectMember {
                method {
                    name = "getCellLocation"
                    emptyParam()
                    returnType(classOf<CellLocation>())
                }
                afterHook {
                    if (!hooking || toggle == Toggle.NONE)
                        return@afterHook
                    result = if (toggle == Toggle.BLOCK)
                        null
                    else {
                        Scheduler.cells.cellLocation()
                    }
                }
            }

            injectMember {
                method {
                    name = "getAllCellInfo"
                    emptyParam()
                    returnType(classOf<List<CellInfo>>())
                }
                afterHook {
                    if (!hooking || toggle == Toggle.NONE)
                        return@afterHook
                    result =
                        if (toggle == Toggle.BLOCK)
                            null
                        else
                            Scheduler.cells.cell.takeIf { it.isNotEmpty() }
                }
            }

            injectMember {
                method {
                    name = "getNeighboringCellInfo"
                    emptyParam()
                    returnType = classOf<List<NeighboringCellInfo>>()
                }
                afterHook {
                    if (!hooking || toggle == Toggle.NONE)
                        return@afterHook
                    result =
                        if (toggle == Toggle.BLOCK)
                            null
                        else
                            Scheduler.cells.neighboringInfo().takeIf { it.isNotEmpty() }
                }
            }

            injectMember {
                method {
                    name = "listen"
                    param(classOf<PhoneStateListener>(), IntType)
                    returnType = UnitType
                }
                replaceUnit {
                    if (!hooking || toggle == Toggle.NONE) {
                        callOriginal()
                        return@replaceUnit
                    }

                    if (toggle == Toggle.BLOCK)
                        return@replaceUnit

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
                replaceUnit {
                    if (!hooking || toggle == Toggle.NONE) {
                        callOriginal()
                        return@replaceUnit
                    }

                    if (toggle == Toggle.BLOCK)
                        return@replaceUnit

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
                replaceAny {
                    if (!hooking || toggle == Toggle.NONE) {
                        callOriginal()
                    } else if (toggle == Toggle.BLOCK) {
                        0
                    } else {
                        1
                    }
                }
            }

            injectMember {
                method {
                    name = "getActiveModemCount"
                    emptyParam()
                    returnType = IntType
                }
                replaceAny {
                    if (!hooking || toggle == Toggle.NONE) {
                        callOriginal()
                    } else if (toggle == Toggle.BLOCK) {
                        0
                    } else {
                        1
                    }
                }
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