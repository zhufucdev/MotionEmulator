package com.zhufucdev.motion_emulator.mock_location_plugin.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import com.zhufucdev.motion_emulator.mock_location_plugin.R

class TestItemView : FrameLayout {
    private val progressBar: ProgressBar
    private val nameText: TextView
    private val icon: AppCompatImageView

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        LayoutInflater.from(context).inflate(R.layout.test_item_view, this, true)
        progressBar = findViewById(R.id.test_item_progress)
        nameText = findViewById(R.id.test_item_name)
        icon = findViewById(R.id.test_item_icon)
        attrs?.let {
            val text =
                context.theme.obtainStyledAttributes(it, R.styleable.TestItemView, defStyleAttr, 0)
            nameText.text = text.getString(R.styleable.TestItemView_android_text)
        }
    }

    var status: TestStatus = TestStatus.ONGOING
        set(value) {
            field = value
            if (value == TestStatus.ONGOING) {
                progressBar.isVisible = true
                icon.isVisible = false
            } else {
                progressBar.isVisible = false
                icon.isVisible = true
                icon.setImageResource(
                    when (value) {
                        TestStatus.PASSED -> R.drawable.ic_baseline_check_circle_outline
                        TestStatus.FAILED -> R.drawable.ic_baseline_remove_circle_outline
                        TestStatus.UNKNOWN -> R.drawable.ic_baseline_help_outline
                        else -> error("unreachable")
                    }
                )
            }
        }
}

enum class TestStatus {
    ONGOING, PASSED, FAILED, UNKNOWN;
}