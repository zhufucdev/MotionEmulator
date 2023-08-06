package com.zhufucdev.cgsport_plugin

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.type.java.DoubleType
import com.highcapable.yukihookapi.hook.type.java.ListClass

class AlsHooker(private val scheduler: Scheduler) : YukiBaseHooker() {
    override fun onHook() {
        "net.crigh.cgsport.mysport_.AMAPLocationService_".hook {
            injectMember {
                method {
                    param(appClassLoader.amapLatLngClass, ListClass)
                }

                beforeHook {
                    args[0] =
                        appClassLoader.amapLatLngClass
                            .getConstructor(DoubleType, DoubleType)
                            .newInstance(scheduler.currentPoint.latitude, scheduler.currentPoint.longitude)
                }
            }

            // total length in km
            injectMember {
                method {
                    param(DoubleType)
                    modifiers { isPublic }
                }

                beforeHook {
                    args[0] = 10.0
                }
            }
        }
    }
}