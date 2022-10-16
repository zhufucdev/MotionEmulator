package com.zhufucdev.motion_emulator.data

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

/**
 * A location on earth.
 */
@Serializable
data class Point(val latitude: Double, val longitude: Double)

/**
 * Composed of series of [Point]s.
 * @param name to call the trace
 * @param points to describe the trace's shape and direction
 */
@Serializable
data class Trace(val name: String, val points: List<Point>)

@OptIn(ExperimentalSerializationApi::class)
object Traces {
    private val records = arrayListOf<Trace>()
    private lateinit var rootDir: File

    fun readAll(context: Context) {
        rootDir = context.getDir("record", Context.MODE_PRIVATE)
        rootDir.listFiles()?.forEach { file ->
            if (file.extension != "json") {
                return@forEach
            }
            file.inputStream().use {
                val record = Json.decodeFromStream<Trace>(it)
                records.add(record)
            }
        }
    }

    fun store(record: Trace) {
        File(rootDir, "${record.name}.json").outputStream().use {
            Json.encodeToStream(record, it)
        }
    }

    fun list() = records.toList()
}
