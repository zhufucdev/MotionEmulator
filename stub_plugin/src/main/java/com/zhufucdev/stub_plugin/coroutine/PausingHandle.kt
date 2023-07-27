package com.zhufucdev.stub_plugin.coroutine

/**
 * An entry point to control pausing coroutine execution
 */
interface PausingHandle {

    /**
     * Returns is coroutine paused or not
     */
    val isPaused: Boolean

    /**
     * Pause a coroutine with all nested coroutines.
     * Do nothing if already paused.
     * It is safe to call this from any thread
     */
    fun pause()

    /**
     * Resume a coroutine with all nested coroutines.
     * Do nothing if not paused.
     * It is safe to call this from any thread
     */
    fun resume()
}