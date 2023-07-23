package com.zhufucdev.stub_plugin

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.zhufucdev.stub.Method
import com.zhufucdev.stub.SETTINGS_PROVIDER_AUTHORITY

object MePlugin {
    private fun defaultQuery(context: Context, schema: String): Cursor =
        context.contentResolver.query(
            Uri.parse("content://$SETTINGS_PROVIDER_AUTHORITY/$schema"),
            null,
            null,
            null,
            null
        )!!

    fun queryServer(context: Context): Server {
        with(defaultQuery(context, "server")) {
            val port = getInt(0)
            val tls = getInt(1)
            return Server(port, tls == 1)
        }
    }

    fun queryMethod(context: Context): Method {
        with(defaultQuery(context, "method")) {
            return Method.valueOf(getString(0).uppercase())
        }
    }
}