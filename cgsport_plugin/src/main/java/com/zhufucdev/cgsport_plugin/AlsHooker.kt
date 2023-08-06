package com.zhufucdev.cgsport_plugin

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.type.java.DoubleType
import com.highcapable.yukihookapi.hook.type.java.ListClass

class AlsHooker(private val scheduler: Scheduler) : YukiBaseHooker() {
    private val amapClients = mutableMapOf<Any, AMapClient>()

    override fun onHook() {
        appClassLoader.amapLocationClientClass.hook {
            injectMember {
                method {
                    name = "setLocationListener"
                }

                replaceUnit {
                    // TODO
                }
            }

            injectMember {
                method {
                    name = "startLocation"
                    emptyParam()
                }

                replaceUnit {
                    // TODO
                }
            }
        }
    }
}

data class AMapClient(val listener: Scheduler.LocationListenerCallback, var started: Boolean)