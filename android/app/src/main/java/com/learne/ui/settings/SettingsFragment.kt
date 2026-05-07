package com.learne.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.learne.R
import com.learne.data.model.Corpus
import com.learne.databinding.FragmentSettingsBinding
import com.learne.di.ViewModelFactory

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ViewModelFactory.createSettingsViewModel() as T
            }
        })[SettingsViewModel::class.java]

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.selectedCorpus.observe(viewLifecycleOwner) { corpus ->
            updateSelection(corpus)
        }
    }

    private fun setupListeners() {
        binding.rgCorpus.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_cet4 -> viewModel.selectCorpus(Corpus.CET4)
                R.id.rb_catti -> viewModel.selectCorpus(Corpus.CATTI)
            }
        }
    }

    private fun updateSelection(corpus: Corpus) {
        binding.rbCet4.isChecked = corpus.id == "cet4"
        binding.rbCatti.isChecked = corpus.id == "catti"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}