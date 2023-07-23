package com.zhufucdev.motion_emulator.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.SharedPreferences
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.zhufucdev.motion_emulator.sharedPreferences
import com.zhufucdev.stub.Method
import com.zhufucdev.stub.SETTINGS_PROVIDER_AUTHORITY

private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
    addURI(SETTINGS_PROVIDER_AUTHORITY, "server", 1)
    addURI(SETTINGS_PROVIDER_AUTHORITY, "method", 2)
}

class SettingsProvider : ContentProvider() {
    private lateinit var preferences: SharedPreferences
    private val port get() = preferences.getInt("provider_port", 20230)
    private val tls get() = preferences.getBoolean("provider_tls", true)
    private val method get() = preferences.getString("method", Method.XPOSED_ONLY.name.lowercase())
    override fun onCreate(): Boolean {
        preferences = context?.sharedPreferences() ?: return false
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            1 -> {
                val cursor = MatrixCursor(arrayOf("port", "tls"), 1)
                cursor.addRow(arrayOf(port, if (tls) 1 else 0))
                cursor
            }

            2 -> {
                val cursor = MatrixCursor(arrayOf("method"), 1)
                cursor.addRow(arrayOf(method))
                cursor
            }

            else -> {
                null
            }
        }
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }
}