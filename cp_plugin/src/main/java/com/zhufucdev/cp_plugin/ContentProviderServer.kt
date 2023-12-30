package com.zhufucdev.cp_plugin

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.content.contentValuesOf
import com.zhufucdev.me.stub.Box
import com.zhufucdev.me.stub.Emulation
import com.zhufucdev.me.stub.EmulationInfo
import com.zhufucdev.me.stub.Intermediate
import com.zhufucdev.me.plugin.ServerConnection
import com.zhufucdev.me.plugin.ServerScope
import kotlinx.serialization.json.Json
import java.util.Optional
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

const val EMULATION_START = 0x00
const val EMULATION_STOP = 0x01

data class ContentProviderServer(val authority: String, val resolver: ContentResolver)

@SuppressLint("Recycle")
suspend fun ContentProviderServer.connect(
    id: String,
    block: suspend ServerScope.() -> Unit
): ServerConnection {
    val emulation =
        runCatching {
            resolver
                .query(Uri.parse("content://$authority/next"), null, id, null, null)
                .use(::parseEmulation)
        }
    val context = coroutineContext
    if (emulation.isFailure) {
        Log.w("CpServer", "Failed to establish connection", emulation.exceptionOrNull())
        val scope = object : ServerScope {
            override val emulation: Optional<Emulation> = Optional.empty()

            override val coroutineContext: CoroutineContext = context

            override suspend fun sendStarted(info: EmulationInfo) {
                throw NotImplementedError()
            }

            override suspend fun sendProgress(intermediate: Intermediate) {
                throw NotImplementedError()
            }

            override suspend fun close() {
                throw NotImplementedError()
            }
        }
        block(scope)
        return object : ServerConnection {
            override val successful: Boolean = false

            override fun close() {
                // nothing
            }
        }
    } else {
        val scope = object : ServerScope {
            override val emulation: Optional<Emulation> = emulation.getOrThrow()

            override val coroutineContext: CoroutineContext = context

            override suspend fun sendStarted(info: EmulationInfo) {
                val uri = Uri.parse("content://$authority/state")
                resolver.update(uri, info.contentValues(), id, null)
            }

            override suspend fun sendProgress(intermediate: Intermediate) {
                val uri = Uri.parse("content://$authority/progress")
                resolver.update(uri, intermediate.contentValues(), id, null)
            }

            override suspend fun close() {
                // does nothing
            }
        }
        block(scope)
        resolver.sendStop(authority, id)
        return object : ServerConnection {
            override val successful: Boolean = false

            override fun close() {
                // nothing
            }
        }
    }
}

private fun parseEmulation(cursor: Cursor?): Optional<Emulation> {
    if (cursor == null) {
        throw NullPointerException("cursor")
    }
    cursor.moveToFirst()
    if (cursor.getInt(0) == EMULATION_STOP) {
        return Optional.empty()
    }
    val traceData = cursor.getString(1)
    val motionData = cursor.getString(2)
    val cellsData = cursor.getString(3)
    val velocity = cursor.getDouble(4)
    val repeat = cursor.getInt(5)
    val satellites = cursor.getInt(6)
    return Optional.of(
        Emulation(
            Json.decodeFromString(traceData),
            Box.decodeFromString(motionData),
            Box.decodeFromString(cellsData),
            velocity, repeat, satellites
        )
    )
}

private fun EmulationInfo.contentValues(): ContentValues {
    return contentValuesOf(
        "running" to true,
        "duration" to duration,
        "length" to length,
        "owner" to owner
    )
}

private fun Intermediate.contentValues(): ContentValues {
    return contentValuesOf(
        "progress" to progress,
        "coord_sys" to location.coordinateSystem.ordinal,
        "pos_la" to location.latitude,
        "pos_lg" to location.longitude,
        "elapsed" to elapsed
    )
}

private fun ContentResolver.sendStop(authority: String, id: String) {
    update(Uri.parse("content://$authority/state"), contentValuesOf("running" to false), id, null)
}