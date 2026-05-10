package com.learne.ui.wrong

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.learne.databinding.FragmentWrongWordsBinding
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.StudyRepository
import com.learne.ui.review.ReviewFragment

class WrongWordsFragment : Fragment() {

    private var _binding: FragmentWrongWordsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: WrongWordViewModel
    private lateinit var adapter: WrongWordAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWrongWordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WrongWordViewModel(
                    StudyRepository(requireContext()),
                    CorpusRepository(requireContext())
                ) as T
            }
        })[WrongWordViewModel::class.java]

        adapter = WrongWordAdapter { wrong ->
            viewModel.markCorrected(wrong)
        }
        binding.rvWrongWords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWrongWords.adapter = adapter

        setupObservers()
        setupListeners()

        viewModel.load("catti")
    }

    private fun setupObservers() {
        viewModel.wrongWords.observe(viewLifecycleOwner) { list ->
            val details = viewModel.wordDetails.value ?: emptyMap()
            adapter.updateData(list, details)
        }

        viewModel.wrongCount.observe(viewLifecycleOwner) { count ->
            binding.tvWrongCount.text = count.toString()
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnReviewWrong.setOnClickListener {
            val wrongWords = viewModel.wrongWords.value
            if (wrongWords.isNullOrEmpty()) {
                Toast.makeText(context, "暂无错题", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val fragment = ReviewFragment.newInstanceForWrongWords(wrongWords)
            parentFragmentManager.beginTransaction()
                .replace(com.learne.R.id.fragment_container, fragment)
                .addToBackStack("wrong_words")
                .commit()
        }

        binding.btnClearWrong.setOnClickListener {
            viewModel.clearCorrected()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}