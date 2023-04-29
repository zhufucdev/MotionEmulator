package com.zhufucdev.motion_emulator.ui.apps

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.AppMeta
import com.zhufucdev.motion_emulator.data.AppMetas

class AppItemAdapter(private val appMetas: AppMetas) : RecyclerView.Adapter<AppItemViewHolder>() {
    var appsSnapshot = emptyList<AppMeta>()

    init {
        refresh()
        setHasStableIds(true)
    }

    fun refresh() {
        appsSnapshot = appMetas.list()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        AppItemViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        )

    override fun getItemCount(): Int = appsSnapshot.size

    override fun onBindViewHolder(holder: AppItemViewHolder, position: Int) {
        val meta = appsSnapshot[position]
        holder.use(meta)
        holder.onToggle {
            if (it) {
                appMetas.markPositive(meta.packageName)
            }
            else {
                appMetas.markNegative(meta.packageName)
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return appsSnapshot[position].packageName.hashCode().toLong()
    }
}

class AppItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val title by lazy { itemView.findViewById<AppCompatTextView>(R.id.title_app_name) }
    private val packageName by lazy { itemView.findViewById<AppCompatTextView>(R.id.text_package_name) }
    private val icon by lazy { itemView.findViewById<AppCompatImageView>(R.id.icon_app) }
    private val toggle by lazy { itemView.findViewById<AppCompatCheckBox>(R.id.toggle_app) }

    fun use(meta: AppMeta) {
        toggle.setOnCheckedChangeListener(null)
        itemView.setOnClickListener(null)
        title.text = meta.name ?: title.context.getText(R.string.title_unknown)
        packageName.text = meta.packageName
        icon.setImageDrawable(meta.icon ?: AppCompatResources.getDrawable(icon.context, R.drawable.ic_baseline_help_24))
        toggle.isChecked = meta.positive
    }

    fun onToggle(l: (Boolean) -> Unit) {
        toggle.setOnCheckedChangeListener { _, isChecked ->
            l(isChecked)
        }

        itemView.setOnClickListener {
            toggle.isChecked = !toggle.isChecked
            l(toggle.isChecked)
        }
    }
}

class SnappingLinearSmoothScroller(context: Context, position: Int) : LinearSmoothScroller(context) {
    init {
        targetPosition = position
    }

    override fun getVerticalSnapPreference(): Int = SNAP_TO_START
}