package com.zhufucdev.motion_emulator.mock_location_plugin.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import com.zhufucdev.motion_emulator.mock_location_plugin.R
import com.zhufucdev.motion_emulator.mock_location_plugin.databinding.FragmentGuidanceBinding

class GuidanceFragment : Fragment() {
    private lateinit var binding: FragmentGuidanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentGuidanceBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnOpenAbout.setOnClickListener {
            tryStartActivity {
                startActivity(Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS))
            }
        }
        binding.btnOpenDeveloper.setOnClickListener {
            tryStartActivity {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        }
    }

    private fun tryStartActivity(block: () -> Unit) {
        try {
            block()
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(
                requireActivity().findViewById(R.id.fab),
                R.string.text_activity_not_found,
                Snackbar.LENGTH_INDEFINITE
            ).show()
        }
    }
}