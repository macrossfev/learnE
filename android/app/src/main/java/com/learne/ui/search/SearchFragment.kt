package com.learne.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.learne.databinding.FragmentSearchBinding
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.StudyRepository

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SearchViewModel
    private lateinit var adapter: SearchResultsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(
                    CorpusRepository(requireContext()),
                    StudyRepository(requireContext())
                ) as T
            }
        })[SearchViewModel::class.java]

        adapter = SearchResultsAdapter { word ->
            viewModel.selectWord(word)
            showNotePanel(word)
        }
        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = adapter

        setupObservers()
        setupListeners()

        viewModel.loadCorpus("catti")
    }

    private fun setupObservers() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            adapter.updateData(results)
        }

        viewModel.note.observe(viewLifecycleOwner) { note ->
            binding.etNote.setText(note)
        }
    }

    private fun setupListeners() {
        binding.btnSearch.setOnClickListener {
            val query = binding.etSearch.text.toString()
            if (query.isNotEmpty()) {
                viewModel.search(query)
            }
        }
        binding.btnSaveNote.setOnClickListener {
            viewModel.saveNote(binding.etNote.text.toString())
        }
    }

    private fun showNotePanel(word: com.learne.data.model.Word) {
        binding.cardNote.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}