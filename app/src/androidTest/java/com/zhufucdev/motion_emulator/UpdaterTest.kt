package com.zhufucdev.motion_emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhufucdev.motion_emulator.extension.Updater
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
        val updater = Updater(InstrumentationRegistry.getInstrumentation().context)
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            val update = updater.check()
            Assert.assertNotNull(update)
        }
    }
}