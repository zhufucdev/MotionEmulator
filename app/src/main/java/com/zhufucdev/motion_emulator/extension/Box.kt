package com.zhufucdev.motion_emulator.extension

import com.zhufucdev.me.stub.BLOCK_REF
import com.zhufucdev.me.stub.BlockBox
import com.zhufucdev.me.stub.Box
import com.zhufucdev.me.stub.Data
import com.zhufucdev.me.stub.EMPTY_REF
import com.zhufucdev.me.stub.EmptyBox
import com.zhufucdev.motion_emulator.data.DataStore

fun <T : Data> StoredBox(ref: String, store: DataStore<T>) =
    when (ref) {
        EMPTY_REF -> EmptyBox()
        BLOCK_REF -> BlockBox()
        else -> store[ref]?.let { Box(it) } ?: EmptyBox()
    }
