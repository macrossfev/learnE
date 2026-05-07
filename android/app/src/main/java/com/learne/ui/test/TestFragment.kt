package com.learne.ui.test

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.learne.databinding.FragmentTestBinding
import com.learne.di.ViewModelFactory
import com.learne.service.AudioPlayer

class TestFragment : Fragment() {

    private var _binding: FragmentTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TestViewModel
    private lateinit var audioPlayer: AudioPlayer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ViewModelFactory.createTestViewModel() as T
            }
        })[TestViewModel::class.java]

        audioPlayer = AudioPlayer()

        setupObservers()
        setupListeners()

        viewModel.loadCorpus("catti")
    }

    private fun setupObservers() {
        viewModel.currentWord.observe(viewLifecycleOwner) { word ->
            // Update test UI with current word
        }

        viewModel.options.observe(viewLifecycleOwner) { options ->
            // Update choice options
        }

        viewModel.score.observe(viewLifecycleOwner) { (correct, total) ->
            // Show test result
        }
    }

    private fun setupListeners() {
        binding.btnChoiceTest.setOnClickListener {
            viewModel.startChoiceTest(10)
        }

        binding.btnSpellTest.setOnClickListener {
            viewModel.startSpellTest(10)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioPlayer.release()
        _binding = null
    }
}