package com.zhufucdev.motion_emulator.data

import android.content.Context
import com.zhufucdev.me.stub.Data
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

/**
 * Abstraction of method set to store and read
 * simulation data
 */
@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSerializationApi::class)
abstract class DataStore<T : Data> {
    private val data = sortedMapOf<String, T>()
    private lateinit var rootDir: File

    abstract val typeName: String
    protected abstract val dataSerializer: KSerializer<T>

    private val Data.storeName: String get() = "${typeName}_${id}.json"

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
            val removed = data.keys.filter { it !in existingIds }
            removed.forEach {
                data.remove(it)
            }
        }
    }

    fun store(record: T, overwrite: Boolean = false) {
        if (data.containsKey(record.id) && !overwrite) return

        File(rootDir, record.storeName).outputStream().use {
            record.writeTo(it)
        }
        data[record.id] = record
    }

    fun parseAndStore(string: String, overwrite: Boolean = false): T {
        val record = Json.decodeFromString(dataSerializer, string)
        if (data.containsKey(record.id) && !overwrite) return record

        File(rootDir, record.storeName).writeText(string)
        data[record.id] = record
        return record
    }

    fun delete(record: T, context: Context) {
        context.deleteFile(record.storeName)
        data.remove(record.id)
    }

    fun list() = data.values.toList()

    operator fun get(id: String) = data[id]

    override fun equals(other: Any?): Boolean =
        other is DataStore<*> && other::class == this::class && other.typeName == this.typeName

    override fun hashCode(): Int {
        var result = data.hashCode()
        result = 31 * result + rootDir.hashCode()
        result = 31 * result + typeName.hashCode()
        result = 31 * result + dataSerializer.hashCode()
        return result
    }
}