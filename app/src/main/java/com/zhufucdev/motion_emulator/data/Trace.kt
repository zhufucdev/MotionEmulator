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
data class Point(val latitude: Double, val longitude: Double) {
    companion object {
        val zero get() = Point(0.0, 0.0)
    }
}

/**
 * Composed of series of [Point]s.
 * @param name to call the trace
 * @param points to describe the trace's shape and direction
 */
@Serializable
data class Trace(val id: String, val name: String, val points: List<Point>, val offset: Point = Point.zero)

@OptIn(ExperimentalSerializationApi::class)
object Traces {
    private val records = arrayListOf<Trace>()
    private lateinit var rootDir: File

    /**
     * Make sure it works
     *
     * Should be called before any read operation
     */
    fun require(context: Context) {
        rootDir = context.getDir("record", Context.MODE_PRIVATE)
        val list = rootDir.listFiles() ?: emptyArray<File>()
        if (list.map { it.nameWithoutExtension }.toSet() != records.map { it.id }.toSet()) {
            records.clear()
            list.forEach { file ->
                if (file.extension != "json") {
                    return@forEach
                }
                file.inputStream().use {
                    val record = Json.decodeFromStream<Trace>(it)
                    records.add(record)
                }
            }
        }
    }

    fun store(record: Trace) {
        if (records.contains(record)) return
        File(rootDir, "${record.id}.json").outputStream().use {
            Json.encodeToStream(record, it)
        }
        records.add(record)
    }

    fun list() = records.toList()
    operator fun get(id: String) = list().firstOrNull { it.id == id }
}
