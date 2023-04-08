package com.zhufucdev.motion_emulator.hook_frontend

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.hardware.SensorManager
import android.net.Uri
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.hook.EMULATION_START
import com.zhufucdev.motion_emulator.hook.EMULATION_STOP
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


const val AUTHORITY = "com.zhufucdev.motion_emulator.event_provider"

class EventProvider : ContentProvider() {
    private lateinit var matcher: UriMatcher
    private lateinit var sensorManager: SensorManager

    override fun onCreate(): Boolean {
        matcher = UriMatcher(UriMatcher.NO_MATCH)
        matcher.addURI(AUTHORITY, "next", REQUEST_NEXT)
        matcher.addURI(AUTHORITY, "state", REQUEST_STATE)
        matcher.addURI(AUTHORITY, "current", REQUEST_CURRENT)
        sensorManager = context?.getSystemService(SensorManager::class.java) ?: error("context unavailable")
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
        fun present(e: Emulation?) = if (e == null) {
            val cursor = MatrixCursor(arrayOf("command"))
            cursor.addRow(arrayOf(EMULATION_STOP))
            cursor
        } else {
            val cursor = MatrixCursor(arrayOf("trace", "motion", "cells", "velocity", "repeat", "satellites"))
            cursor.addRow(arrayOf(EMULATION_START, 0, 0, 0, 0, 0))
            val traceData = Json.encodeToString(e.trace)
            val motionData = e.motion.encodeToString()
            val cellsData = e.cells.encodeToString()
            cursor.addRow(
                arrayOf(
                    traceData,
                    motionData,
                    cellsData,
                    e.velocity,
                    e.repeat,
                    e.satelliteCount
                )
            )
            cursor
        }

        return when (matcher.match(uri)) {
            REQUEST_NEXT -> runBlocking {
                val id = selectionArgs?.get(0)
                    ?: error("No id provided. Is the app running an old version of hook?")
                present(Scheduler.queue(id))
            }
            REQUEST_CURRENT -> present(Scheduler.emulation)
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
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        if (matcher.match(uri) == REQUEST_STATE && values != null) {
            when (selection) {
                "state" -> {
                    val running = values.getAsBoolean("state")
                    val id = values.getAsString("id")
                    if (!running) {
                        Scheduler.setInfo(id, null)
                    } else {
                        val duration = values.getAsDouble("duration")
                        val length = values.getAsDouble("length")
                        val owner = values.getAsString("owner")
                        Scheduler.setInfo(id, EmulationInfo(duration, length, owner))
                    }
                    return 0
                }

                "progress" -> {
                    val progress = values.getAsFloat("progress")
                    val coordSys = CoordinateSystem.values()[values.getAsInteger("coord_sys")]
                    val position = Point(values.getAsDouble("pos_la"), values.getAsDouble("pos_lg"), coordSys)
                    val elapsed = values.getAsDouble("elapsed")
                    val id = values.getAsString("id")
                    Scheduler.setIntermediate(id, Intermediate(position, elapsed, progress))
                    return 0
                }
            }
        }
        return -1
    }

    companion object {
        private const val REQUEST_NEXT = 0x10
        private const val REQUEST_STATE = 0x11
        private const val REQUEST_CURRENT = 0x12
    }
}