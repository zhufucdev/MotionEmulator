package com.zhufucdev.motion_emulator.ui.manager

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.component.Expandable
import com.zhufucdev.motion_emulator.data.Data
import com.zhufucdev.motion_emulator.dateString
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon
import com.zhufucdev.motion_emulator.ui.theme.paddingSmall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream

@Composable
fun OverviewScreen(viewModel: ManagerViewModel) {
    val state = viewModel.runtime.bottomModalState
    val coroutine = rememberCoroutineScope()
    val context = LocalContext.current
    var operation by remember { mutableStateOf(ExportType.File) }
    var lastArgs by remember { mutableStateOf(mapOf<Data, String>()) }

    val fileCreateHintLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val uri = it.data?.data
            if (it.resultCode == RESULT_OK && uri != null) {
                coroutine.launch {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        writeInto(out, context, lastArgs)
                    }
                }
            }
        }

    val providers = LocalScreenProviders.current
    val fileOpenHintLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val uri = it.data?.data
            if (it.resultCode == RESULT_OK && uri != null) {
                coroutine.launch {
                    val count = import(context, providers, uri)
                    viewModel.runtime.snackbarHost.showSnackbar(
                        message = context.getString(R.string.text_imported, count)
                    )
                }
            }
        }

    state.drawer {
        SheetContent {
            state.open = false
            when (operation) {
                ExportType.File -> {
                    lastArgs = it
                    fileCreateHintLauncher.launch(
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/gzip"
                            putExtra(
                                Intent.EXTRA_TITLE,
                                "${context.getString(R.string.title_exported, dateString())}.tar.gz"
                            )
                        }
                    )
                }

                ExportType.Share -> {
                    coroutine.launch {
                        val uri = getUri(context, it)
                        val share = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, uri)
                            type = "application/gzip"
                        }
                        context.startActivity(
                            Intent.createChooser(share, context.getString(R.string.title_send_to))
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exportedDir(context).deleteRecursively() // clear shared file
        }
    }

    fun import() {
        fileOpenHintLauncher.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/gzip"
            }
        )
    }

    LazyColumn {
        item {
            ListItem(
                modifier = Modifier.fillMaxWidth(),
                title = { Text(stringResource(R.string.title_export)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_database_export), contentDescription = null) },
                onClick = {
                    operation = ExportType.File
                    state.open = true
                }
            )
        }

        item {
            ListItem(
                modifier = Modifier.fillMaxWidth(),
                title = { Text(stringResource(R.string.title_import)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_database_import), contentDescription = null) },
                onClick = {
                    import()
                }
            )
        }

        item {
            ListItem(
                modifier = Modifier.fillMaxWidth(),
                title = { Text(stringResource(R.string.title_share)) },
                leadingIcon = { Icon(painterResource(R.drawable.baseline_share_24), contentDescription = null) },
                onClick = {
                    operation = ExportType.Share
                    state.open = true
                },
                divider = false
            )
        }
    }
}

private enum class ExportType {
    File, Share
}

@Composable
private fun SheetContent(onClick: (Map<Data, String>) -> Unit) {
    val providers = LocalScreenProviders.current.value
    val items = remember {
        mutableMapOf<Data, String>().apply {
            providers.forEach {
                if (it is EditorViewModel.StandardViewModel<*>) {
                    it.data.forEach { data ->
                        put(data, it.store.typeName)
                    }
                }
            }
        }
    }

    LazyColumn {
        item(key = "header") {
            Text(
                text = stringResource(R.string.title_select_to_export),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = paddingCommon * 2, top = paddingSmall, bottom = paddingCommon)
            )
        }

        providers.forEach { vm ->
            if (vm !is EditorViewModel.StandardViewModel<*>) return@forEach
            item(key = vm.screen.name) {
                var expanded by remember { mutableStateOf(false) }
                Expandable(
                    icon = {
                        Icon(painterResource(vm.screen.iconId), contentDescription = null)
                    },
                    header = {
                        Text(stringResource(vm.screen.titleId))
                    },
                    body = {
                        if (vm.data.isNotEmpty()) {
                            Column(
                                Modifier.fillMaxWidth()
                            ) {
                                vm.data.forEachIndexed { index, data ->
                                    var selected by remember { mutableStateOf(true) }
                                    SelectableItem(
                                        title = data.displayName,
                                        subtitle = data.id,
                                        selected = selected,
                                        onSelectedChanged = { s ->
                                            selected = s
                                            if (s) {
                                                items[data] = vm.store.typeName
                                            } else {
                                                items.remove(data)
                                            }
                                        },
                                        divider = index != vm.data.lastIndex
                                    )
                                }
                            }
                        } else {
                            Image(
                                painterResource(R.drawable.ic_thinking_face_72),
                                contentDescription = stringResource(R.string.text_empty_list),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    overview = {
                        val count =
                            items.count { it.value == vm.store.typeName }
                        Text(stringResource(R.string.text_selected_items, count))
                    },
                    expanded = expanded,
                    onToggle = { expanded = !expanded }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(paddingCommon),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        onClick(items)
                    },
                    content = {
                        Text(stringResource(R.string.action_continue))
                    }
                )
            }
        }
    }
}

@Composable
private fun LazyItemScope.SelectableItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    divider: Boolean = true,
    onSelectedChanged: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.clickable { onSelectedChanged(!selected) }
                .padding(horizontal = paddingCommon * 2, vertical = paddingCommon)
                .fillParentMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                )
            }


            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChanged,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }

        if (divider) {
            Divider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5F)
            )
        }
    }
}

private suspend fun writeInto(stream: OutputStream, context: Context, items: Map<Data, String>) {
    val bufOut = BufferedOutputStream(stream)
    val gzOut = GzipCompressorOutputStream(bufOut)
    val tarOut = TarArchiveOutputStream(gzOut)

    items.forEach { (data, type) ->
        val tmpFile = File.createTempFile(type, null, context.cacheDir)
        tmpFile.outputStream().use {
            data.writeTo(it)
        }
        val entry = TarArchiveEntry(tmpFile, "${type}_${data.id}.json")
        tarOut.putArchiveEntry(entry)
        tmpFile.inputStream().use {
            it.copyTo(tarOut)
        }
        tarOut.closeArchiveEntry()
    }

    tarOut.finish()
    gzOut.close()
    withContext(Dispatchers.IO) {
        bufOut.close()
    }
}

private suspend fun getUri(context: Context, items: Map<Data, String>): Uri {
    val sharedDir = exportedDir(context)
    if (!sharedDir.exists()) sharedDir.mkdir()
    val file = File(sharedDir, "${context.getString(R.string.title_exported, dateString())}.tar.gz")

    val fileOut = file.outputStream()
    writeInto(fileOut, context, items)
    withContext(Dispatchers.IO) {
        fileOut.close()
    }
    return FileProvider.getUriForFile(context, "com.zhufucdev.motion_emulator.file_provider", file)
}

private fun exportedDir(context: Context) = File(context.filesDir, "exported")

@Suppress("UNCHECKED_CAST")
private suspend fun import(context: Context, providers: ScreenProviders, uri: Uri): Int {
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
        val provider =
            providers.value.firstOrNull { it is EditorViewModel.StandardViewModel<*> && it.store.typeName == type }
                as EditorViewModel.StandardViewModel<*>?
                ?: continue
        val text = tarIn.readBytes().decodeToString()
        val record = provider.store.parseAndStore(text, true)
        (provider.data as SnapshotStateList<Data>).add(record)

        entry = tarIn.nextTarEntry
    }

    tarIn.close()
    gzIn.close()
    withContext(Dispatchers.IO) {
        fileIn.close()
    }

    return count
}