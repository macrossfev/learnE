package com.learne.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.learne.databinding.FragmentStatsBinding
import com.learne.di.ViewModelFactory

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: StatsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ViewModelFactory.createStatsViewModel() as T
            }
        })[StatsViewModel::class.java]

        setupObservers()
        viewModel.loadCorpus("catti")
    }

    private fun setupObservers() {
        viewModel.learnedCount.observe(viewLifecycleOwner) { count ->
            binding.tvLearnedCount.text = "已学习: $count"
        }

        viewModel.masteredCount.observe(viewLifecycleOwner) { count ->
            binding.tvMasteredCount.text = "已掌握: $count"
        }

        viewModel.totalWords.observe(viewLifecycleOwner) { total ->
            // Can update total words display if needed
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}