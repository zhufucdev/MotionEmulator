package com.zhufucdev.motion_emulator.data

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

/**
 * Basic motion record unit
 *
 * @param data SensorType to its value
 * @param elapsed Time from start (in sec.)
 */
@Serializable
data class MotionMoment(val elapsed: Float, val data: MutableMap<Int, FloatArray>)

/**
 * Motion record, composed with series of [MotionMoment]s.
 * @param time Time when it was recorded in millis.
 * @param moments The series.
 * @param sensorsInvolved types of sensor that's possibly present in the [moments].
 * Notice that not every moment includes all the sensors.
 */
@Serializable
data class Motion(val id: String, val time: Long, val moments: List<MotionMoment>, val sensorsInvolved: List<Int>)

@OptIn(ExperimentalSerializationApi::class)
object Motions {
    private val records = arrayListOf<Motion>()
    private lateinit var rootDir: File

    /**
     * Make sure it works
     *
     * Should be called before any read operation
     */
    fun require(context: Context) {
        rootDir = context.getDir("motion", Context.MODE_PRIVATE)
        val list = rootDir.listFiles() ?: emptyArray<File>()
        if (list.map { it.nameWithoutExtension }.toSet() != records.map { it.id }.toSet()) {
            records.clear()
            list.forEach { file ->
                if (file.extension != "json") {
                    return@forEach
                }
                file.inputStream().use {
                    val record = Json.decodeFromStream<Motion>(it)
                    records.add(record)
                }
            }
        }
    }

    fun store(record: Motion) {
        if (records.contains(record)) return
        val name = record.id + ".json"
        File(rootDir, name).outputStream().use {
            Json.encodeToStream(record, it)
        }
        records.add(record)
    }

    fun list() = records.toList()
    operator fun get(id: String) = records.firstOrNull { it.id == id }
}