package com.zhufucdev.motion_emulator.ui

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.databinding.ActivitySettingsBinding

private const val TITLE_TAG = "settingsActivityTitle"

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private lateinit var binding: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarToolbar)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, HeaderFragment())
                .commit()
        } else {
            title = savedInstanceState.getCharSequence(TITLE_TAG)
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                supportActionBar!!.setTitle(R.string.title_activity_settings)
            }
        }

        binding.appBarToolbar.setNavigationOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0)
                supportFragmentManager.popBackStack()
            else
                finish()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current activity title so we can set it again after a configuration change
        outState.putCharSequence(TITLE_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .setTransition(TRANSIT_FRAGMENT_FADE)
            .addToBackStack(null)
            .commit()
        title = pref.title
        return true
    }

    override fun setTitle(title: CharSequence?) {
        supportActionBar?.title = title
        super.setTitle(title)
    }

    override fun setTitle(titleId: Int) {
        supportActionBar?.setTitle(titleId)
        super.setTitle(titleId)
    }

    class HeaderFragment : M3PreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.header_preferences, rootKey)
        }
    }

    class MapsFragment : M3PreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.maps_preferences, rootKey)
        }
    }

    class NamingFragment : M3PreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.naming_preferences, rootKey)
            init()
        }

        private fun init() {
            val customTimeFormatSwitch = findPreference<SwitchPreferenceCompat>("customize_time_format")!!
            val timeFormat = findPreference<EditTextPreference>("time_format")!!
            timeFormat.isEnabled = customTimeFormatSwitch.isChecked
            customTimeFormatSwitch.setOnPreferenceChangeListener { _, use ->
                timeFormat.isEnabled = use as Boolean
                true
            }
        }
    }

    class EmulationFragment : M3PreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.emulation_preferences, rootKey)
            init()
        }

        private fun init() {
            val portPreference = findPreference<EditTextPreference>("provider_port")!!
            val tlsPreference = findPreference<SwitchPreferenceCompat>("provider_tls")!!
            val methodPreference = findPreference<ListPreference>("method")!!

            fun Int.isValidPort() = this in 1024..65535
            portPreference.setOnBindEditTextListener { input ->
                input.inputType = InputType.TYPE_CLASS_NUMBER
                input.doAfterTextChanged {
                    val port = it.toString().toIntOrNull()
                    if (port == null || !port.isValidPort()) {
                        input.error = getString(R.string.text_input_invalid)
                    }
                }
            }
            portPreference.setOnPreferenceChangeListener { _, newValue ->
                newValue.toString().toIntOrNull()?.isValidPort() == true
            }

            tlsPreference.setOnPreferenceClickListener { _ ->

                true
            }

            methodPreference.setOnPreferenceChangeListener { _, newValue ->

                true
            }
        }
    }
}

class ListPreferenceM3DialogFragment : ListPreferenceDialogFragmentCompat() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(preference.dialogTitle)
            .setNegativeButton(preference.negativeButtonText, this)
        val content = context?.let { onCreateDialogView(it) }
        if (content == null) {
            builder.setMessage(preference.dialogMessage)
        } else {
            onBindDialogView(content)
            builder.setView(content)
        }
        onPrepareDialogBuilder(builder)
        return builder.create()
    }

    companion object {
        fun show(instance: ListPreference, parent: Fragment) {
            val dialog = ListPreferenceM3DialogFragment()
            dialog.apply {
                arguments = bundleOf("key" to instance.key)
                setTargetFragment(parent, 0)
            }

            dialog.show(parent.parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
        }
    }
}

class EditTextPreferenceM3DialogFragment : EditTextPreferenceDialogFragmentCompat() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(preference.dialogTitle)
            .setPositiveButton(preference.positiveButtonText, this)
            .setNegativeButton(preference.negativeButtonText, this)
        val content = context?.let { onCreateDialogView(it) }
        if (content == null) {
            builder.setMessage(preference.dialogMessage)
        } else {
            onBindDialogView(content)
            builder.setView(content)
        }
        onPrepareDialogBuilder(builder)
        return builder.create()
    }

    companion object {
        fun show(instance: EditTextPreference, parent: Fragment) {
            val dialog = EditTextPreferenceM3DialogFragment()
            dialog.apply {
                arguments = bundleOf("key" to instance.key)
                setTargetFragment(parent, 0)
            }

            dialog.show(parent.parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
        }
    }
}

abstract class M3PreferenceFragment : PreferenceFragmentCompat() {
    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference -> {
                ListPreferenceM3DialogFragment.show(preference, this)
            }

            is EditTextPreference -> {
                EditTextPreferenceM3DialogFragment.show(preference, this)
            }

            else -> {
                super.onDisplayPreferenceDialog(preference)
            }
        }
    }
}