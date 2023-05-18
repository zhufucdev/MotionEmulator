package com.zhufucdev.motion_emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdaterTest {
    @Test
    fun check() {
        val updater = InstrumentationRegistry.getInstrumentation().context.updater()
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            val update = updater.check()
            Assert.assertNotNull(update)
        }
    }
}