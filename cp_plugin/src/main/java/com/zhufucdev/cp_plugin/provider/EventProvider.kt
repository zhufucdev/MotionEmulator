package com.zhufucdev.cp_plugin.provider

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.zhufucdev.cp_plugin.EMULATION_START
import com.zhufucdev.cp_plugin.EMULATION_STOP
import com.zhufucdev.cp_plugin.PROVIDER_AUTHORITY
import com.zhufucdev.me.stub.CoordinateSystem
import com.zhufucdev.me.stub.Emulation
import com.zhufucdev.me.stub.EmulationInfo
import com.zhufucdev.me.stub.Intermediate
import com.zhufucdev.me.stub.Point
import com.zhufucdev.me.stub.encodeToString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Optional


class EventProvider : ContentProvider() {
    private lateinit var matcher: UriMatcher
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("EventProvider", "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("EventProvider", "Service disconnected")
        }
    }

    override fun onCreate(): Boolean {
        matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(PROVIDER_AUTHORITY, "next", REQUEST_NEXT)
            addURI(PROVIDER_AUTHORITY, "state", REQUEST_STATE)
            addURI(PROVIDER_AUTHORITY, "progress", REQUEST_PROGRESS)
        }

        context!!.bindService(
            Intent(context, EmulationBridgeService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        return true
    }

    /**
     * Xposed request
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (matcher.match(uri)) {
            REQUEST_NEXT -> runBlocking {
                val id = selection
                    ?: error("No id provided. Is the app running an old version of hook?")
                ControllerScheduler.queueRequest(EmulationRequest(id)).cursor()
            }

            else -> null
        }
    }

    override fun getType(uri: Uri): String? {
        throw NotImplementedError()
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw NotImplementedError()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw NotImplementedError()
    }

    /**
     * Xposed callback
     */
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int {
        if (values == null || selection == null) return -1

        when (matcher.match(uri)) {
            REQUEST_STATE -> {
                val running = values.getAsBoolean("running")
                if (!running) {
                    runBlocking {
                        ControllerScheduler.queueRequest(StopRequest(selection))
                    }
                } else {
                    val duration = values.getAsDouble("duration")
                    val length = values.getAsDouble("length")
                    val owner = values.getAsString("owner")
                    runBlocking {
                        ControllerScheduler.queueRequest(
                            SendStartedRequest(
                                selection, EmulationInfo(duration, length, owner)
                            )
                        )
                    }
                }
                return 0
            }

            REQUEST_PROGRESS -> {
                val progress = values.getAsFloat("progress")
                val coordSys = CoordinateSystem.entries[values.getAsInteger("coord_sys")]
                val position =
                    Point(values.getAsDouble("pos_la"), values.getAsDouble("pos_lg"), coordSys)
                val elapsed = values.getAsDouble("elapsed")
                runBlocking {
                    ControllerScheduler.queueRequest(
                        SendProgressRequest(
                            selection, Intermediate(position, elapsed, progress)
                        )
                    )
                }
                return 0
            }
        }
        return -1
    }

    companion object {
        private const val REQUEST_NEXT = 0x10
        private const val REQUEST_STATE = 0x11
        private const val REQUEST_PROGRESS = 0x12
    }
}

fun Optional<Emulation>.cursor(): Cursor {
    if (isPresent) {
        val cursor = MatrixCursor(
            arrayOf(
                "command", "trace", "motion", "cells", "velocity", "repeat", "satellites"
            )
        )
        val e = get()
        val traceData = Json.encodeToString(e.trace)
        val motionData = e.motion.encodeToString()
        val cellsData = e.cells.encodeToString()
        cursor.addRow(
            arrayOf(
                EMULATION_START,
                traceData,
                motionData,
                cellsData,
                e.velocity,
                e.repeat,
                e.satelliteCount
            )
        )
        return cursor
    } else {
        val cursor = MatrixCursor(arrayOf("command"))
        cursor.addRow(arrayOf(EMULATION_STOP))
        return cursor
    }
}