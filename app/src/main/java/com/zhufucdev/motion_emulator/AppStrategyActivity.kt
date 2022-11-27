package com.zhufucdev.motion_emulator

import android.database.MatrixCursor
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.BaseColumns
import android.view.Menu
import android.view.MenuItem
import android.widget.CursorAdapter
import android.widget.SearchView
import android.widget.SimpleCursorAdapter
import com.zhufucdev.motion_emulator.apps.AppItemAdapter
import com.zhufucdev.motion_emulator.apps.AppMetas
import com.zhufucdev.motion_emulator.apps.SnappingLinearSmoothScroller
import com.zhufucdev.motion_emulator.databinding.ActivityAppStrategyBinding

class AppStrategyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppStrategyBinding
    private lateinit var mainAdapter: AppItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppStrategyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeToolbar(binding.appBarToolbar)

        AppMetas.require(this)
        initializeList()
    }

    private fun initializeList() {
        mainAdapter = AppItemAdapter()
        binding.listApps.adapter = mainAdapter

        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = true
            mainAdapter.refresh()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun initializeStrategy(menu: Menu) {
        menu.findItem(R.id.bypass_mode).isChecked = AppMetas.bypassMode
        menu.findItem(R.id.show_system).isChecked = AppMetas.showSystemApps
    }

    private fun initializeSearch(view: SearchView) {
        val adapter = SimpleCursorAdapter(
            this,
            android.R.layout.simple_list_item_2,
            null,
            arrayOf("name", "packageName"),
            intArrayOf(android.R.id.text1, android.R.id.text2),
            CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
        )

        view.suggestionsAdapter = adapter

        var lastResults = emptyList<Int>()
        view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                val cursor = MatrixCursor(arrayOf(BaseColumns._ID, "name", "packageName"))
                var i = 0
                val results = arrayListOf<Int>()
                mainAdapter.appsSnapshot.forEachIndexed { index, meta ->
                    if (meta.name?.contains(newText, true) == true
                        || meta.packageName.contains(newText, true)
                    ) {
                        cursor.addRow(arrayOf(i, meta.name, meta.packageName))
                        results.add(index)
                    }
                    i++
                }
                lastResults = results
                adapter.changeCursor(cursor)
                return true
            }
        })

        view.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return false
            }

            override fun onSuggestionClick(position: Int): Boolean {
                val table = lastResults
                if (table.size > position) {
                    val scroller = SnappingLinearSmoothScroller(this@AppStrategyActivity, table[position])
                    binding.listApps.layoutManager!!.startSmoothScroll(scroller)
                }
                view.isIconified = false
                view.onActionViewCollapsed()
                return true
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_strategy_actionbar, menu)
        initializeStrategy(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.show_system -> {
                item.isChecked = !item.isChecked
                AppMetas.showSystemApps = item.isChecked
                mainAdapter.refresh()
                true
            }

            R.id.bypass_mode -> {
                item.isChecked = !item.isChecked
                AppMetas.bypassMode = item.isChecked
                true
            }

            else -> false
        }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val actionView = menu.findItem(R.id.app_bar_search).actionView as SearchView
        initializeSearch(actionView)
        return true
    }
}