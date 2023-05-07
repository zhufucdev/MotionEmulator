package com.zhufucdev.mock_location_plugin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.zhufucdev.mock_location_plugin.Availability
import com.zhufucdev.mock_location_plugin.R
import com.zhufucdev.mock_location_plugin.databinding.FragmentTestBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

class TestFragment : Fragment() {

    private lateinit var binding: FragmentTestBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTestBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewModel: TestViewModel by viewModels()
        viewModel.serviceAvailable.observe(viewLifecycleOwner) {
            binding.testAvailability.status = it
        }
        viewModel.developerSet.observe(viewLifecycleOwner) {
            binding.testDeveloperOptions.status = it
        }
        viewModel.providerConnected.observe(viewLifecycleOwner) {
            binding.testEmulationProvider.status = it
            binding.cardTimeout.root.isVisible = it == TestStatus.UNKNOWN
        }
        viewModel.carrying.observe(viewLifecycleOwner) { running ->
            binding.textTitle.setText(if (running) R.string.title_carrying else R.string.title_test_done)

            if (running) {
                binding.btnPrimary.setText(R.string.action_cancel_test)
                binding.btnPrimary.setIconResource(R.drawable.ic_baseline_cancel)
            } else {
                binding.btnPrimary.setText(R.string.action_rerun_test)
                binding.btnPrimary.setIconResource(R.drawable.ic_baseline_refresh)
            }
        }

        val testScope = CoroutineScope(Dispatchers.Default)
        var testJob = testScope.launch {
            Availability.test(requireContext(), viewModel)
        }

        binding.btnPrimary.setOnClickListener {
            if (viewModel.carrying.value == true) {
                lifecycleScope.launch {
                    testJob.cancelAndJoin()
                    viewModel.cancel()
                }
            } else {
                testJob = testScope.launch {
                    Availability.test(requireContext(), viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        inForeground = true
    }

    override fun onStop() {
        super.onStop()
        inForeground = false
    }

    companion object {
        var inForeground: Boolean = false
            private set
    }
}