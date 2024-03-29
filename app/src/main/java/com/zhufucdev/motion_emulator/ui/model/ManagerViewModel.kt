package com.zhufucdev.motion_emulator.ui.model

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import com.zhufucdev.me.stub.Data
import com.zhufucdev.me.stub.Metadata
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.extension.FILE_PROVIDER_AUTHORITY
import com.zhufucdev.motion_emulator.extension.dateString
import com.zhufucdev.motion_emulator.extension.effectiveTimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream

@SuppressLint("StaticFieldLeak")
class ManagerViewModel(
    val data: MutableList<DataLoader<*>> = mutableListOf(),
    val dataLoader: Flow<Boolean> = emptyFlow(),
    private val context: Context,
    val stores: List<DataStore<*>>
) : ViewModel() {
    private val storeByType by lazy { stores.associateBy { it.typeName } }
    val storeByClass by lazy { stores.associateBy { it.clazz } }

    suspend fun <T : Data> remove(item: DataLoader<T>) {
        withContext(Dispatchers.IO) {
            val store =
                storeByClass[item.clazz] ?: error("unsupported type ${item::class.simpleName}")
            @Suppress("UNCHECKED_CAST")
            (store as DataStore<T>).delete(item, context)
        }
    }

    suspend fun <T : Data> save(item: DataLoader<T>) {
        withContext(Dispatchers.IO) {
            val store =
                storeByClass[item.clazz] ?: error("unsupported type ${item::class.simpleName}")
            @Suppress("UNCHECKED_CAST")
            (store as DataStore<T>).put(item, overwrite = true)
        }
    }

    suspend fun <T : Data> save(item: T, metadata: Metadata) {
        save(WorkingData(item, metadata))
    }

    fun <T : Data> update(newValue: DataLoader<T>) {
        val index = data.indexOfFirst { it.id == newValue.id }
        data[index] = newValue
    }

    suspend fun writeInto(stream: OutputStream, items: Map<String, List<DataLoader<*>>>) {
        val bufOut = BufferedOutputStream(stream)
        val gzOut = GzipCompressorOutputStream(bufOut)
        val tarOut = TarArchiveOutputStream(gzOut)

        items.forEach { (type, data) ->
            fun <T : Data> export(loader: DataLoader<T>, store: DataStore<*>, dest: OutputStream) {
                (store as DataStore<T>).export(loader, dest)
            }
            data.forEach { datum ->
                val tmpFile = File.createTempFile(type, null, context.cacheDir)
                val store = storeByType[type]!!
                tmpFile.outputStream().use { stream ->
                    export(datum, store, stream)
                }
                val entry = TarArchiveEntry(tmpFile, "${type}_${datum.id}.json")
                tarOut.putArchiveEntry(entry)
                tmpFile.inputStream().use {
                    it.copyTo(tarOut)
                }
                tarOut.closeArchiveEntry()
            }
        }

        tarOut.finish()
        gzOut.close()
        withContext(Dispatchers.IO) {
            bufOut.close()
        }
    }

    suspend fun getExportedUri(items: Map<String, List<DataLoader<*>>>): Uri {
        val sharedDir = exportedDir()
        if (!sharedDir.exists()) sharedDir.mkdir()
        val file = File(
            sharedDir,
            "${
                context.getString(
                    R.string.title_exported,
                    context.effectiveTimeFormat().dateString()
                )
            }.tar.gz"
        )

        val fileOut = file.outputStream()
        writeInto(fileOut, items)
        withContext(Dispatchers.IO) {
            fileOut.close()
        }
        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    }

    fun exportedDir() = File(context.filesDir, "exported")

    suspend fun import(uri: Uri): Int {
        val fileIn = context.contentResolver.openInputStream(uri) ?: return 0
        val gzIn = GzipCompressorInputStream(fileIn)
        val tarIn = TarArchiveInputStream(gzIn)

        var entry = tarIn.nextTarEntry
        var count = 0
        while (entry != null) {
            count++
            val name = entry.name
            val separator = name.indexOf('_')
            if (separator < 0) continue
            val type = name.substring(0, separator)
            val store = storeByType[type] ?: error("unknown type $type")

            withContext(Dispatchers.IO) {
                store.import(tarIn, overwrite = true)?.let { record ->
                    data.apply {
                        val oldIndex = indexOfFirst { it.id == record.id }
                        if (oldIndex < 0) {
                            // insert
                            add(record)
                        } else {
                            // update
                            set(oldIndex, record)
                        }
                    }
                }
            }

            entry = tarIn.nextTarEntry
        }

        tarIn.close()
        gzIn.close()
        withContext(Dispatchers.IO) {
            fileIn.close()
        }

        return count
    }
}
