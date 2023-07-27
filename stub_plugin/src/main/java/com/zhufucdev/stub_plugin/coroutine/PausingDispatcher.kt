/***
 * I copied this from
 * https://gist.github.com/Koitharu/0adbbc73df774929434de26a5159a2f8#file-pausingdispatcher-kt
 */
package com.zhufucdev.stub_plugin.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

class PausingDispatcher(
    private val queue: PausingDispatchQueue,
    internal val baseDispatcher: CoroutineDispatcher,
): CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (queue.isPaused) {
            queue.queue(context, block, baseDispatcher)
        } else {
            baseDispatcher.dispatch(context, block)
        }
    }
}