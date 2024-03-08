package com.zhufucdev.motion_emulator.data

import android.content.Context
import com.zhufucdev.me.stub.Data
import com.zhufucdev.me.stub.Metadata
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.KClass

/**
 * Abstraction of method set to store and read
 * simulation data (or any [Data])
 */
abstract class DataStore<T : Data> {
    private val data = sortedMapOf<String, DataLoader<T>>()
    private lateinit var rootDir: File

    /**
     * Files would be saved as [typeName]_[Data.id].json
     */
    abstract val typeName: String
    abstract val clazz: KClass<T>
    protected abstract val dataSerializer: KSerializer<T>

    private val DataLoader<T>.storeName get() = "${typeName}_${id}.json"

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
            val existingIds = mutableSetOf<String>()
            files.forEach {
                val file = File(rootDir, it)
                if (!it.endsWith("json") || !it.startsWith(typeName))
                    return@forEach
                val id = file.nameWithoutExtension.removePrefix("${typeName}_")
                val metaFile = File(rootDir, "meta_${id}.json")
                if (!metaFile.exists()) {
                    return@forEach
                }
                existingIds.add(id)
                if (data.containsKey(id)) {
                    return@forEach
                }
                data[id] = LazyData(id, clazz, file, metaFile, dataSerializer)
            }
            val removed = data.keys.filter { it !in existingIds }
            removed.forEach {
                data.remove(it)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun put(record: DataLoader<T>, overwrite: Boolean = false): DataLoader<T>? {
        if (data.containsKey(record.id) && !overwrite) return null

        if (record is WorkingData<T>) {
            val file = File(rootDir, record.storeName)
            file.outputStream().use {
                Json.encodeToStream(dataSerializer, record.value, it)
            }
        }
        data[record.id] = record
        return record
    }

    fun import(source: InputStream, overwrite: Boolean = false): DataLoader<T>? {
        val text = source.bufferedReader().use { it.readText() }
        val element = Json.parseToJsonElement(text).jsonObject
        if (element.containsKey("value") && element.containsKey("metadata")) {
            return put(
                WorkingData(
                    Json.decodeFromJsonElement(dataSerializer, element["value"]!!),
                    Json.decodeFromJsonElement(element["metadata"]!!)
                ),
                overwrite
            )
        } else {
            throw IllegalArgumentException("source does not contain value and metadata")
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun export(record: DataLoader<T>, dest: OutputStream) {
        Json.encodeToStream(
            buildJsonObject {
                put("value", Json.encodeToJsonElement(dataSerializer, record.value))
                put("metadata", Json.encodeToJsonElement(record.metadata))
            },
            dest
        )
    }

    fun delete(record: DataLoader<T>, context: Context) {
        context.deleteFile(record.storeName)
        data.remove(record.id)
    }

    fun list() = data.values.toList()

    operator fun get(id: String) = data[id]

    override fun equals(other: Any?): Boolean =
        other is DataStore<*> && other::class == this::class && other.clazz == this.clazz

    override fun hashCode(): Int {
        var result = rootDir.hashCode()
        result = 31 * result + typeName.hashCode()
        result = 31 * result + clazz.hashCode()
        return result
    }
}

sealed interface DataLoader<T : Data> {
    val value: T
    val metadata: Metadata
    val id: String
    val clazz: KClass<out T>
    fun copy(metadata: Metadata): DataLoader<T>
}

data class WorkingData<T : Data>(
    override val value: T,
    override val metadata: Metadata
) : DataLoader<T> {
    override val id: String
        get() = value.id
    override val clazz
        get() = value::class

    override fun copy(metadata: Metadata): DataLoader<T> = WorkingData(value, metadata)
}

data class LazyValueData<T : Data>(
    override val metadata: Metadata,
    override val clazz: KClass<out T>,
    private val file: File,
    private val serializer: KSerializer<T>
) : DataLoader<T> {
    override val id: String
        get() = value.id

    @OptIn(ExperimentalSerializationApi::class)
    override val value by lazy {
        file.inputStream().use { s ->
            Json.decodeFromStream(serializer, s)
        }
    }

    override fun copy(metadata: Metadata): DataLoader<T> =
        LazyValueData(metadata, clazz, file, serializer)
}

@OptIn(ExperimentalSerializationApi::class)
data class LazyData<T : Data>(
    override val id: String,
    override val clazz: KClass<out T>,
    private val file: File,
    private val metaFile: File,
    private val serializer: KSerializer<T>
) : DataLoader<T> {
    override val value by lazy {
        file.inputStream().use { s ->
            Json.decodeFromStream(serializer, s)
        }
    }

    override val metadata: Metadata by lazy {
        metaFile.inputStream().use { s ->
            Json.decodeFromStream(serializer<Metadata>(), s)
        }
    }

    override fun copy(metadata: Metadata) = LazyValueData(metadata, clazz, file, serializer)
}
