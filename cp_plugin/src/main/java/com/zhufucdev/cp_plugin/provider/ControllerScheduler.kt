package com.zhufucdev.cp_plugin.provider

import android.util.Log
import com.zhufucdev.me.stub.Emulation
import com.zhufucdev.me.stub.EmulationInfo
import com.zhufucdev.me.stub.Intermediate
import com.zhufucdev.me.plugin.WsServer
import com.zhufucdev.me.plugin.connect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Optional
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass
import kotlin.reflect.cast

typealias OptionalEmulation = Optional<Emulation>
typealias RequestQueueListener = (Request<*, *>) -> Unit

/**
 * A bridge between [EventProvider] and [EmulationBridgeService],
 * coroutine enabled
 */
object ControllerScheduler {
    private val idQueueListener = mutableMapOf<String, RequestQueueListener>()
    private val queueListeners = mutableListOf<RequestQueueListener>()
    private val queue = mutableListOf<Request<*, *>>()

    suspend fun <K, T> queueRequest(request: Request<K, T>): T =
        suspendCoroutine { c ->
            if (!idQueueListener.containsKey(request.id) && queueListeners.isEmpty()) {
                c.resumeWithException(RuntimeException("Request by ${request.id} is unresolvable."))
            }

            val callback: (T) -> Unit = {
                request.consumed = true
                c.resume(it)
                queue.remove(request)
            }
            request.resolve = callback
            queue.add(request)
            idQueueListener[request.id]?.invoke(request)

            if (!request.consumed) {
                queueListeners.forEach { it.invoke(request) }
                queueListeners.clear()
            }
        }

    suspend fun <T : Request<*, *>> awaitRequest(id: String, type: KClass<T>): T =
        suspendCoroutine { c ->
            val resolver: RequestQueueListener = {
                if (type.isInstance(it)) {
                    c.resume(type.cast(it))
                }
            }
            idQueueListener[id] = resolver
        }

    suspend fun awaitRequest(id: String): Request<*, *> =
        suspendCoroutine { c ->
            val resolver: RequestQueueListener = {
                c.resume(it)
            }
            idQueueListener[id] = resolver
        }

    suspend fun <T : Request<*, *>> awaitRequest(type: KClass<T>): T =
        suspendCoroutine { c ->
            queueListeners.add {
                if (type.isInstance(it)) {
                    c.resume(type.cast(it))
                }
            }
        }
}

suspend fun WsServer.bridge() = coroutineScope {
    Log.d("ControllerScheduler", "Bridge between WsServer and EventProvider has been constructed")
    fun connect(emu: EmulationRequest) = launch {
        connect(emu.id) {
            emu.resolve(emulation)
            while (true) {
                when (val next = ControllerScheduler.awaitRequest(emu.id)) {
                    is SendStartedRequest -> {
                        sendStarted(next.argument)
                        next.resolve(Unit)
                    }
                    is SendProgressRequest -> {
                        sendProgress(next.argument)
                        next.resolve(Unit)
                    }
                    is StopRequest -> {
                        next.resolve(Unit)
                        break
                    }
                }
            }
        }
    }

    while (true) {
        val emu = ControllerScheduler.awaitRequest(EmulationRequest::class)
        connect(emu)
    }
}

abstract class Request<K, T>(val id: String, val argument: K) {
    lateinit var resolve: (T) -> Unit
    var consumed = false
}

class EmulationRequest(id: String) : Request<Unit, OptionalEmulation>(id, Unit)
class SendStartedRequest(id: String, argument: EmulationInfo) :
    Request<EmulationInfo, Unit>(id, argument)

class SendProgressRequest(id: String, argument: Intermediate) :
    Request<Intermediate, Unit>(id, argument)

class StopRequest(id: String) : Request<Unit, Unit>(id, Unit)
