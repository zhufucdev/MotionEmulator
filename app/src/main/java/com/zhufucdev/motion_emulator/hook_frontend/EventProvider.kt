package com.zhufucdev.motion_emulator.hook_frontend

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.hardware.SensorManager
import android.net.Uri
import android.util.Log
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.hook.COMMAND_EMULATION_START
import com.zhufucdev.motion_emulator.hook.COMMAND_EMULATION_STOP
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
        if (matcher.match(uri) == REQUEST_NEXT) {
            val emulation = Scheduler.queue()
            return if (emulation == null) {
                val cursor = MatrixCursor(arrayOf("command"))
                cursor.addRow(arrayOf(COMMAND_EMULATION_STOP))
                cursor
            } else {
                val cursor = MatrixCursor(arrayOf("trace", "motion", "cells", "velocity", "repeat"))
                cursor.addRow(arrayOf(COMMAND_EMULATION_START, 0, 0))
                val traceData = Json.encodeToString(emulation.trace)
                val motionData = Json.encodeToString(emulation.motion)
                val cellsData = Json.encodeToString(emulation.cells)
                cursor.addRow(arrayOf(traceData, motionData, cellsData, emulation.velocity, emulation.repeat))
                cursor
            }
        }
        return null
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
                    if (!running) {
                        Scheduler.emulation = null
                    }
                    return 0
                }

                "progress" -> {
                    val progress = values.getAsFloat("progress")
                    val position = Point(values.getAsDouble("pos_la"), values.getAsDouble("pos_lg"))
                    Scheduler.intermediate = Intermediate(position, progress)
                    return 0
                }
            }
        }
        return -1
    }

    companion object {
        private const val REQUEST_NEXT = 0x10
        private const val REQUEST_STATE = 0x11
    }
}