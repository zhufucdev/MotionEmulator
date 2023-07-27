/***
 * I copied it from
 * https://gist.github.com/Koitharu/0adbbc73df774929434de26a5159a2f8#file-pausingdispatchqueue-kt
 */
package com.zhufucdev.stub_plugin.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class PausingDispatchQueue : AbstractCoroutineContextElement(Key), PausingHandle {

    private val paused = AtomicBoolean(false)
    private val queue = ArrayDeque<Resumer>()

    override val isPaused: Boolean
        get() = paused.get()

    override fun pause() {
        paused.set(true)
    }

    override fun resume() {
        if (paused.compareAndSet(true, false)) {
            dispatchNext()
        }
    }

    fun queue(context: CoroutineContext, block: Runnable, dispatcher: CoroutineDispatcher) {
        queue.addLast(Resumer(dispatcher, context, block))
    }

    private fun dispatchNext() {
        val resumer = queue.removeFirstOrNull() ?: return
        resumer.dispatch()
    }

    private inner class Resumer(
        private val dispatcher: CoroutineDispatcher,
        private val context: CoroutineContext,
        private val block: Runnable,
    ) : Runnable {

        override fun run() {
            block.run()
            if (!paused.get()) {
                dispatchNext()
            }
        }

        fun dispatch() {
            dispatcher.dispatch(context, this)
        }
    }

    companion object Key : CoroutineContext.Key<PausingDispatchQueue>
}