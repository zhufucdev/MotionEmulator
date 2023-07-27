package com.zhufucdev.stub_plugin.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalStdlibApi::class)
fun CoroutineScope.launchPausing(block: suspend CoroutineScope.() -> Unit): PausingJob {
    val base = coroutineContext[CoroutineDispatcher]
        ?.let { if (it is PausingDispatcher) it.baseDispatcher else it }
        ?: Dispatchers.Default
    val queue = PausingDispatchQueue()
    val dispatcher = PausingDispatcher(queue, base)

    val job = launch(context = coroutineContext + dispatcher + queue, block = block)
    return PausingJob(job, queue)
}