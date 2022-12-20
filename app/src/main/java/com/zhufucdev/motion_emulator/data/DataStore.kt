package com.zhufucdev.motion_emulator.data

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

/**
 * Abstraction of method set to store and read
 * simulation data
 */
@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSerializationApi::class)
abstract class DataStore<T : Referable> {
    private val data = mutableMapOf<String, T>()
    private lateinit var rootDir: File

    protected abstract val typeName: String
    protected abstract val dataSerializer: KSerializer<T>

    private val Referable.storeName: String get() = "${typeName}_${id}.json"

    /**
     * Make sure it works
     *
     * Should be called before any I/O operation
     */
    fun require(context: Context) {
        rootDir = context.filesDir

        val files = rootDir.list()
        if (files == null) {
            data.clear()
        } else {
            val existingIds = mutableListOf<String>()
            files.forEach {
                val file = File(rootDir, it)
                if (!it.endsWith("json") || !it.startsWith(typeName))
                    return@forEach
                val id = file.nameWithoutExtension.removePrefix("${typeName}_")
                existingIds.add(id)
                if (data.containsKey(id)) {
                    return@forEach
                }

                file.inputStream().use { s ->
                    val record = Json.decodeFromStream(dataSerializer, s)
                    data[record.id] = record
                }
            }
            data.keys.forEach {
                if (it !in existingIds) {
                    data.remove(it)
                }
            }
        }
    }

    fun store(record: T) {
        if (data.containsKey(record.id)) return

        File(rootDir, record.storeName).outputStream().use {
            Json.encodeToStream(dataSerializer, record, it)
        }
        data[record.id] = record
    }

    fun delete(record: T, context: Context) {
        context.deleteFile(record.storeName)
        data.remove(record.id)
    }

    fun list() = data.values.toList()

    operator fun get(id: String) = data[id]
}