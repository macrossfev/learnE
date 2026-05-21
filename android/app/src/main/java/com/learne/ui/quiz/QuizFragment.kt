package com.learne.ui.quiz

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.R
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.StudyRepository
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentQuizBinding
import com.learne.service.AudioPlayer
import com.learne.ui.challenge.ChallengeMapFragment
import kotlinx.coroutines.launch

class QuizFragment : Fragment() {

    enum class ExamType { COMPREHENSIVE, CHOICE_ONLY, SPELL_ONLY }

    companion object {
        fun newInstance(corpusId: String, groupIndex: Int, planIndex: Int = -1): QuizFragment {
            return QuizFragment().apply {
                arguments = Bundle().apply {
                    putString("corpusId", corpusId)
                    putInt("groupIndex", groupIndex)
                    putInt("planIndex", planIndex)
                }
            }
        }
    }

    private var _binding: FragmentQuizBinding? = null
    private val binding get() = _binding!!

    private var allWords: List<Word> = emptyList()
    private var quizWords: List<Word> = emptyList()
    private var currentIndex = 0
    private var currentCorpusId: String = "catti"
    private var groupIndex: Int = 0
    private var planIndex: Int = -1
    private var currentGroupSize: Int = 30

    private var correctCount = 0
    private var wrongCount = 0
    private var wrongWordsList: MutableList<Word> = mutableListOf()
    private var currentOptions: List<String> = emptyList()
    private var examType: ExamType = ExamType.COMPREHENSIVE

    private val audioPlayer = AudioPlayer()
    private lateinit var studyRepo: StudyRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentCorpusId = arguments?.getString("corpusId") ?: "catti"
        groupIndex = arguments?.getInt("groupIndex", 0) ?: 0
        planIndex = arguments?.getInt("planIndex", -1) ?: -1
        currentGroupSize = UserPreferencesRepository.planGroupSize
        studyRepo = StudyRepository(requireContext())

        binding.btnSpellPlay.setOnClickListener { playCurrentAudio() }
        binding.btnSpellSubmit.setOnClickListener { submitSpell() }
        binding.btnSpellNext.setOnClickListener { nextQuestion() }

        // Intercept system back button during exam
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    confirmExitExam()
                }
            }
        )

        loadWords()
    }

    private fun loadWords() {
        binding.layoutLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val corpusRepo = CorpusRepository(requireContext())
                allWords = corpusRepo.loadWords(currentCorpusId)
                if (allWords.isEmpty()) {
                    Toast.makeText(context, "词库为空", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return@launch
                }

                val start = groupIndex * currentGroupSize
                val end = (start + currentGroupSize).coerceAtMost(allWords.size)
                quizWords = allWords.subList(start, end).shuffled()
                if (quizWords.isEmpty()) {
                    Toast.makeText(context, "该组无可用单词", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return@launch
                }

                currentIndex = 0
                correctCount = 0
                wrongCount = 0
                wrongWordsList.clear()

                binding.layoutLoading.visibility = View.GONE
                binding.tvTitle.text = "组 ${groupIndex + 1} 考试"
                showExamTypeDialog()
            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun showExamTypeDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("选择考试类型")
            .setItems(arrayOf("综合考试（选择+拼写）", "选择题考试", "拼写考试（听写）")) { _, which ->
                examType = when (which) {
                    0 -> ExamType.COMPREHENSIVE
                    1 -> ExamType.CHOICE_ONLY
                    2 -> ExamType.SPELL_ONLY
                    else -> ExamType.COMPREHENSIVE
                }
                currentIndex = 0
                showQuestion()
            }
            .setOnCancelListener { parentFragmentManager.popBackStack() }
            .show()
    }

    private fun showQuestion() {
        if (currentIndex >= quizWords.size) {
            showResults()
            return
        }

        val word = quizWords[currentIndex]
        val progress = currentIndex + 1
        val total = quizWords.size
        binding.tvProgress.text = "$progress / $total"
        binding.progressBar.progress = progress * 100 / total

        when (examType) {
            ExamType.CHOICE_ONLY -> showChoiceQuestion(word)
            ExamType.SPELL_ONLY -> showSpellQuestion(word)
            ExamType.COMPREHENSIVE -> {
                if (currentIndex % 2 == 0) showChoiceQuestion(word) else showSpellQuestion(word)
            }
        }
    }

    private fun showChoiceQuestion(word: Word) {
        binding.choiceLayout.visibility = View.VISIBLE
        binding.spellLayout.visibility = View.GONE
        binding.tvWordForChoice.text = word.word

        // Wrong options from SAME GROUP only
        val groupMeanings = quizWords
            .filter { it.word != word.word }
            .map { it.meaning }
            .distinct()
        val wrongOptions = if (groupMeanings.size >= 3) {
            groupMeanings.shuffled().take(3)
        } else {
            // Not enough distinct meanings in group — fallback to allWords
            allWords.filter { it.word != word.word }.map { it.meaning }.distinct().shuffled().take(3)
        }
        currentOptions = (wrongOptions + word.meaning).shuffled()

        binding.choiceOptions.removeAllViews()
        for ((i, option) in currentOptions.withIndex()) {
            val btn = android.widget.Button(requireContext()).apply {
                text = option
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
                setPadding(dip(16), dip(16), dip(16), dip(16))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dip(8))
                }
                setOnClickListener { selectChoice(i) }
            }
            binding.choiceOptions.addView(btn)
        }
    }

    private fun showSpellQuestion(word: Word) {
        binding.choiceLayout.visibility = View.GONE
        binding.spellLayout.visibility = View.VISIBLE
        binding.tvSpellHint.text = "请根据听力拼写单词 (${word.meaning})"
        binding.etSpellInput.text?.clear()
        binding.tvSpellResult.visibility = View.GONE
        binding.btnSpellSubmit.visibility = View.VISIBLE
        binding.btnSpellNext.visibility = View.GONE
        playCurrentAudio()
    }

    private fun selectChoice(index: Int) {
        val word = quizWords[currentIndex]
        val selected = currentOptions[index]
        val correct = selected == word.meaning

        // Disable all buttons
        for (i in 0 until binding.choiceOptions.childCount) {
            val btn = binding.choiceOptions.getChildAt(i) as android.widget.Button
            btn.isEnabled = false
            val optText = currentOptions[i]
            if (optText == word.meaning) {
                btn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_success))
                btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            } else if (i == index && !correct) {
                btn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_error))
                btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
        }

        if (correct) {
            correctCount++
        } else {
            wrongCount++
            wrongWordsList.add(word)
            lifecycleScope.launch {
                studyRepo.addWrongWord(UserManager.userId, currentCorpusId, word.word, "quiz")
            }
        }

        binding.choiceLayout.postDelayed({ nextQuestion() }, 1200)
    }

    private fun submitSpell() {
        val input = binding.etSpellInput.text.toString().trim().lowercase()
        val answer = quizWords[currentIndex].word.trim().lowercase()

        if (input.isEmpty()) {
            Toast.makeText(context, "请输入答案", Toast.LENGTH_SHORT).show()
            return
        }

        val word = quizWords[currentIndex]
        if (input == answer) {
            correctCount++
            binding.tvSpellResult.text = "正确!"
            binding.tvSpellResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_success))
        } else {
            wrongCount++
            wrongWordsList.add(word)
            binding.tvSpellResult.text = "错误! 正确答案: ${word.word}"
            binding.tvSpellResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_error))
            lifecycleScope.launch {
                studyRepo.addWrongWord(UserManager.userId, currentCorpusId, word.word, "quiz")
            }
        }
        binding.tvSpellResult.visibility = View.VISIBLE
        binding.btnSpellSubmit.visibility = View.GONE
        // Auto-advance to next question after spell
        binding.spellLayout.postDelayed({ nextQuestion() }, 1500)
    }

    private fun nextQuestion() {
        audioPlayer.release()
        currentIndex++
        showQuestion()
    }

    private fun playCurrentAudio() {
        if (quizWords.isEmpty()) return
        val word = quizWords[currentIndex]
        val path = CorpusRepository(requireContext()).getAudioPath(currentCorpusId, word.word, "words")
        audioPlayer.play(path) { _ -> }
    }

    private fun showResults() {
        audioPlayer.release()
        binding.choiceLayout.visibility = View.GONE
        binding.spellLayout.visibility = View.GONE

        val accuracy = if (quizWords.isNotEmpty()) correctCount * 100 / quizWords.size else 0
        val passed = accuracy >= 80

        // Persist quiz result
        if (passed) {
            UserPreferencesRepository.markQuizPassed(currentCorpusId, groupIndex)
            UserPreferencesRepository.clearQuizFailed(currentCorpusId, groupIndex)
        } else {
            UserPreferencesRepository.markQuizFailed(currentCorpusId, groupIndex)
        }

        binding.tvProgress.text = if (passed) "考试通过!" else "考试未通过"
        binding.contentContainer.removeAllViews()

        // Title
        binding.contentContainer.addView(android.widget.TextView(requireContext()).apply {
            text = if (passed) "恭喜通过考试!" else "需要再努力"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (passed) ContextCompat.getColor(requireContext(), R.color.status_success) else ContextCompat.getColor(requireContext(), R.color.status_error))
            gravity = android.view.Gravity.CENTER
            setPadding(dip(16), dip(16), dip(16), dip(8))
        })

        // Stats
        binding.contentContainer.addView(android.widget.TextView(requireContext()).apply {
            text = "正确: $correctCount  错误: $wrongCount\n正确率: $accuracy%"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(dip(16), 0, dip(16), 0)
        })

        // Wrong words list
        if (wrongWordsList.isNotEmpty()) {
            binding.contentContainer.addView(android.widget.TextView(requireContext()).apply {
                text = "错误单词:\n${wrongWordsList.joinToString("\n") { "- ${it.word} (${it.meaning})" }}"
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
                gravity = android.view.Gravity.CENTER
                setPadding(dip(16), 16, dip(16), 16)
            })
        }

        // Buttons
        val btnRow = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dip(16) }
        }

        val btnBack = android.widget.Button(requireContext()).apply {
            text = "返回"
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
            setOnClickListener {
                notifyMapFragment()
                parentFragmentManager.popBackStack()
            }
        }
        btnRow.addView(btnBack, android.widget.LinearLayout.LayoutParams(0, dip(48)).apply {
            weight = 1f; marginEnd = dip(8)
        })

        if (!passed) {
            val btnRetake = android.widget.Button(requireContext()).apply {
                text = "再考一次"
                textSize = 16f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
                setOnClickListener {
                    currentIndex = 0
                    correctCount = 0
                    wrongCount = 0
                    wrongWordsList.clear()
                    quizWords = quizWords.shuffled()
                    showExamTypeDialog()
                }
            }
            btnRow.addView(btnRetake, android.widget.LinearLayout.LayoutParams(0, dip(48)).apply {
                weight = 1f
            })
        }

        binding.contentContainer.addView(btnRow)
    }

    private fun confirmExitExam() {
        // Only confirm during active exam (choice or spell layout visible)
        val inExam = binding.choiceLayout.visibility == View.VISIBLE ||
                     binding.spellLayout.visibility == View.VISIBLE
        if (!inExam) {
            notifyMapFragment()
            parentFragmentManager.popBackStack()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("确认退出")
            .setMessage("考试进度将丢失，确定退出吗？")
            .setPositiveButton("退出") { _, _ ->
                notifyMapFragment()
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton("继续考试", null)
            .show()
    }

    private fun notifyMapFragment() {
        parentFragmentManager.fragments.find { it is ChallengeMapFragment }?.let {
            (it as ChallengeMapFragment).refreshMap()
        }
    }

    private fun dip(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    override fun onDestroyView() {
        audioPlayer.release()
        super.onDestroyView()
        _binding = null
    }
}
