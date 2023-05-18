package com.zhufucdev.motion_emulator

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine


class SuspendedActivityResultLauncher(activity: ComponentActivity) {
    private val callbacks = mutableListOf<(ActivityResult) -> Unit>()
    private val launcher: ActivityResultLauncher<Intent>

    init {
        launcher =
            activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                callbacks.forEach {
                    try {
                        it(result)
                    } catch (e: Exception) {
                        Log.w("ActivityResultLauncher", "error while handling a callback")
                        e.printStackTrace()
                    }
                }
                callbacks.clear()
            }
    }

    suspend fun launch(intent: Intent) = suspendCancellableCoroutine {
        launcher.launch(intent)
        callbacks.add { result ->
            it.resumeWith(Result.success(result))
        }
    }
}