package com.zhufucdev.motion_emulator.extension

import android.content.pm.ApplicationInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import com.zhufucdev.me.stub.BLOCK_REF
import com.zhufucdev.me.stub.BlockBox
import com.zhufucdev.me.stub.Box
import com.zhufucdev.me.stub.Data
import com.zhufucdev.me.stub.EMPTY_REF
import com.zhufucdev.me.stub.EmptyBox
import com.zhufucdev.me.stub.NULL_REF

val ApplicationInfo.isSystemApp get() = flags and ApplicationInfo.FLAG_SYSTEM != 0

/**
 * To involve [MutableList.add], but avoid [IndexOutOfBoundsException]
 */
fun <T> MutableList<T>.insert(index: Int, element: T) {
    if (index >= size) {
        add(element)
    } else {
        add(index, element)
    }
}
