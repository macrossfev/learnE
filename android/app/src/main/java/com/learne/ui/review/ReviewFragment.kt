package com.learne.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.learne.data.model.WordProgress
import com.learne.databinding.FragmentReviewBinding
import com.learne.di.ViewModelFactory
import com.learne.service.AudioPlayer

class ReviewFragment : Fragment() {

    companion object {
        private const val ARG_WRONG_WORD_STRINGS = "wrong_word_strings"

        fun newInstance(corpusId: String? = null): ReviewFragment {
            return ReviewFragment().apply {
                arguments = Bundle().apply {
                    putString("corpusId", corpusId)
                }
            }
        }

        fun newInstanceForWrongWords(wrongWords: List<com.learne.data.model.WrongWord>): ReviewFragment {
            return ReviewFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_WRONG_WORD_STRINGS, ArrayList(wrongWords.map { it.word }))
                }
            }
        }
    }

    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReviewViewModel
    private lateinit var audioPlayer: AudioPlayer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ReviewViewModel(
                    ViewModelFactory.getCorpusRepository(requireContext())
                ) as T
            }
        })[ReviewViewModel::class.java]

        audioPlayer = AudioPlayer()

        setupObservers()
        setupListeners()

        val wrongWordStrings = arguments?.getStringArrayList(ARG_WRONG_WORD_STRINGS)
        if (wrongWordStrings != null) {
            viewModel.loadWrongWordsForReview(wrongWordStrings)
        } else {
            val corpusId = arguments?.getString("corpusId") ?: "catti"
            viewModel.loadCorpus(corpusId)
        }
    }

    private fun setupObservers() {
        viewModel.reviewCount.observe(viewLifecycleOwner) { count ->
            binding.tvReviewCount.text = "待复习: $count 个"
            binding.btnStartReview.isEnabled = count > 0
        }

        viewModel.completed.observe(viewLifecycleOwner) { completed ->
            binding.reviewContent.visibility = if (completed) View.GONE else View.VISIBLE
            binding.reviewComplete.visibility = if (completed) View.VISIBLE else View.GONE
        }

        viewModel.currentWord.observe(viewLifecycleOwner) { word ->
            binding.tvWord.text = word.word
            binding.tvPhonetic.text = word.phonetic
            binding.tvMeaning.text = word.meaning
            binding.tvPhrase.text = word.phrase
            binding.tvPhraseMeaning.text = word.phraseMeaning
            binding.tvExample.text = word.example
            binding.tvExampleMeaning.text = word.exampleMeaning
            // 选择题步骤也显示单词
            binding.tvChoiceWord.text = word.word
            binding.tvChoicePhonetic.text = word.phonetic
            // 填词步骤显示释义
            binding.tvSpellMeaning.text = word.meaning
        }

        viewModel.currentIndex.observe(viewLifecycleOwner) { index ->
            val total = viewModel.totalCount.value ?: 0
            binding.tvProgress.text = "${index + 1} / $total"
        }

        viewModel.currentStep.observe(viewLifecycleOwner) { step ->
            binding.tvStepIndicator.text = "步骤 $step/3"

            // Step 1: 单词展示
            binding.step1Display.visibility = if (step == 1) View.VISIBLE else View.GONE
            // Step 2: 选择题
            binding.step2Choice.visibility = if (step == 2) View.VISIBLE else View.GONE
            // Step 3: 填词
            binding.step3Spell.visibility = if (step == 3) View.VISIBLE else View.GONE

            // 按钮状态
            binding.btnPrevStep.isEnabled = step > 1
        }

        viewModel.choiceOptions.observe(viewLifecycleOwner) { options ->
            val choiceContainer = binding.choiceOptionsContainer
            choiceContainer.removeAllViews()
            val letters = listOf("A", "B", "C", "D")
            options.forEachIndexed { index, option ->
                val btn = com.google.android.material.button.MaterialButton(requireContext()).apply {
                    text = "${letters[index]}. ${option.meaning}"
                    setOnClickListener { viewModel.selectChoice(index) }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(32, 28, 32, 28)
                    minimumHeight = 0
                    minWidth = 0
                }
                choiceContainer.addView(btn)
            }
        }

        viewModel.selectedChoiceIndex.observe(viewLifecycleOwner) { selectedIndex ->
            // Highlight will be handled via choiceCorrect/choiceWrong
        }

        viewModel.choiceCorrect.observe(viewLifecycleOwner) { correct ->
            binding.choiceFeedback.visibility = if (correct) View.VISIBLE else View.GONE
            if (correct) {
                binding.choiceFeedback.text = "✓ 正确"
                binding.choiceFeedback.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            }
        }

        viewModel.choiceWrong.observe(viewLifecycleOwner) { wrong ->
            binding.choiceFeedback.visibility = if (wrong) View.VISIBLE else if (!(viewModel.choiceCorrect.value ?: false)) View.GONE else binding.choiceFeedback.visibility
            if (wrong) {
                binding.choiceFeedback.text = "✗ 错误，请重试"
                binding.choiceFeedback.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            }
        }

        viewModel.spellWrong.observe(viewLifecycleOwner) { wrong ->
            if (wrong) {
                binding.spellHint.visibility = View.VISIBLE
                binding.spellHint.text = viewModel.spellHint.value ?: ""
            } else {
                binding.spellHint.visibility = View.GONE
            }
        }

        viewModel.currentAudioPath.observe(viewLifecycleOwner) { path ->
            if (path.isNotEmpty()) {
                audioPlayer.play(path)
            }
        }
    }

    private fun setupListeners() {
        binding.btnStartReview.setOnClickListener {
            viewModel.startReview()
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnNextStep.setOnClickListener {
            val step = viewModel.currentStep.value ?: 1
            if (step < 3) {
                viewModel.nextStep()
            } else {
                // Step 3 时提示完成填词
                viewModel.onSpellSubmit()
            }
        }

        binding.btnPrevStep.setOnClickListener {
            viewModel.prevStep()
        }

        binding.btnPrevWord.setOnClickListener {
            viewModel.prevWord()
        }

        binding.btnNextWord.setOnClickListener {
            val step = viewModel.currentStep.value ?: 1
            if (step < 3) {
                android.widget.Toast.makeText(requireContext(), "请完成当前单词测试", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.nextWord()
        }

        binding.btnSpellConfirm.setOnClickListener {
            viewModel.onSpellSubmit()
        }

        binding.etSpellInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.onSpellSubmit()
                true
            } else {
                false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioPlayer.release()
        _binding = null
    }
}
