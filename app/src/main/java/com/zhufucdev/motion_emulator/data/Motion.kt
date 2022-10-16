package com.zhufucdev.motion_emulator.data

import android.content.Context
import com.zhufucdev.motion_emulator.dateString
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
data class Moment(val elapsed: Float, val data: MutableMap<Int, FloatArray>)

/**
 * Motion record, composed with series of [Moment]s.
 * @param time Time when it was recorded in millis.
 * @param moments The series.
 */
@Serializable
data class Motion(val time: Long, val moments: List<Moment>)

@OptIn(ExperimentalSerializationApi::class)
object Motions {
    private val records = arrayListOf<Motion>()
    private lateinit var rootDir: File

    fun readAll(context: Context) {
        rootDir = context.getDir("motion", Context.MODE_PRIVATE)
        rootDir.listFiles()?.forEach { file ->
            if (file.extension != "json") {
                return@forEach
            }
            file.inputStream().use {
                val record = Json.decodeFromStream<Motion>(it)
                records.add(record)
            }
        }
    }

    fun store(record: Motion) {
        val name = dateString(record.time) + ".json"
        File(rootDir, name).outputStream().use {
            Json.encodeToStream(record, it)
        }
    }

    fun list() = records.toList()
}