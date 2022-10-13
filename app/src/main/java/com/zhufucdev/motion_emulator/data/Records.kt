package com.zhufucdev.motion_emulator.data

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.text.DateFormat
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
object Records {
    private val records = arrayListOf<Motion>()

    fun readAll(context: Context) {
        context.fileList().forEach { file ->
            if (!file.endsWith(".json")) {
                return@forEach
            }
            context.openFileInput(file).use {
                val record = Json.decodeFromStream<Motion>(it)
                records.add(record)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun store(record: Motion, context: Context) {
        val name = DateFormat.getDateInstance().format(Date(record.time)) + ".json"
        context.openFileOutput(name, Context.MODE_PRIVATE).use {
            Json.encodeToStream(record, it)
        }
    }

    fun list() = records.toList()
}