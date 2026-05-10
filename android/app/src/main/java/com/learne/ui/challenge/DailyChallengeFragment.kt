package com.learne.ui.challenge

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.ProgressRepository
import com.learne.data.repository.StudyRepository
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentDailyChallengeBinding
import com.learne.service.AudioPlayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DailyChallengeFragment : Fragment() {

    companion object {
        fun newInstance(): DailyChallengeFragment {
            return DailyChallengeFragment()
        }
    }

    private var _binding: FragmentDailyChallengeBinding? = null
    private val binding get() = _binding!!

    private var allWords: List<Word> = emptyList()
    private var challengeWords: List<Word> = emptyList()
    private var currentIndex = 0
    private var currentCorpusId: String = "catti"
    private var correctCount = 0
    private var wrongCount = 0
    private var streakCount = 0
    private var maxStreak = 0

    // Choice question state
    private var currentQuestionType: QuestionType = QuestionType.CHOICE
    private var currentOptions: List<String> = emptyList()

    private enum class QuestionType {
        CHOICE,    // Multiple choice: EN -> pick CN
        SPELL,     // Dictation: hear audio -> type word
        FLASHCARD  // Flashcard: see EN -> recall -> show CN -> self-rate
    }

    private val audioPlayer = AudioPlayer()
    private lateinit var studyRepo: StudyRepository
    private lateinit var progressRepo: ProgressRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailyChallengeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentCorpusId = UserPreferencesRepository.planCorpusId ?: "catti"
        studyRepo = StudyRepository(requireContext())
        progressRepo = ProgressRepository(requireContext())

        binding.btnChoiceOption1.setOnClickListener { selectChoice(0) }
        binding.btnChoiceOption2.setOnClickListener { selectChoice(1) }
        binding.btnChoiceOption3.setOnClickListener { selectChoice(2) }
        binding.btnChoiceOption4.setOnClickListener { selectChoice(3) }
        binding.btnSpellSubmit.setOnClickListener { submitSpell() }
        binding.btnSpellNext.setOnClickListener { nextQuestion() }
        binding.btnSpellPlay.setOnClickListener { playCurrentAudio() }
        binding.btnCardFlip.setOnClickListener { flipCard() }
        binding.btnCardKnown.setOnClickListener { rateCard(true) }
        binding.btnCardForgot.setOnClickListener { rateCard(false) }
        binding.cardFront.setOnClickListener { flipCard() }

        loadWords()
    }

    private fun loadWords() {
        binding.layoutLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                allWords = CorpusRepository(requireContext()).loadWords(currentCorpusId)
                if (allWords.isEmpty()) {
                    Toast.makeText(context, "词库为空", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return@launch
                }

                // Select 10 random words: prioritize wrong words + review + random
                val wrongWords = try {
                    studyRepo.getWrongWords(UserManager.userId, currentCorpusId).first()
                        .sortedByDescending { it.wrongCount }
                        .mapNotNull { ww -> allWords.find { w -> w.word == ww.word } }
                } catch (e: Exception) { emptyList() }

                val learnedWords = try {
                    progressRepo.getWordsForReview(currentCorpusId).first()
                        .mapNotNull { wp -> allWords.find { w -> w.word == wp.word } }
                } catch (e: Exception) { emptyList() }

                // Pick from wrong words first (up to 4)
                val wrongSet: List<com.learne.data.model.Word> = wrongWords.take(4)
                // Then from review words not in wrongSet (up to 3)
                val wrongSetWords = wrongSet.map { it.word }.toSet()
                val reviewSet: List<com.learne.data.model.Word> = learnedWords.filter { it.word !in wrongSetWords }.shuffled().take(3)
                // Then random remaining (up to 3)
                val usedWords: Set<String> = wrongSet.map { it.word }.union(reviewSet.map { it.word }.toSet())
                val randomSet: List<com.learne.data.model.Word> = allWords.filter { it.word !in usedWords }.shuffled().take(3)

                challengeWords = (wrongSet + reviewSet + randomSet).shuffled().take(10)
                if (challengeWords.isEmpty()) {
                    Toast.makeText(context, "可用单词不足", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return@launch
                }

                currentIndex = 0
                correctCount = 0
                wrongCount = 0
                streakCount = 0
                maxStreak = 0

                binding.layoutLoading.visibility = View.GONE
                showQuestion()
            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun showQuestion() {
        if (currentIndex >= challengeWords.size) {
            showResults()
            return
        }

        val word = challengeWords[currentIndex]
        val progress = currentIndex + 1
        val total = challengeWords.size
        binding.tvProgress.text = "$progress / $total"
        binding.progressBar.progress = (progress * 100 / total)

        // Determine question type
        currentQuestionType = when (currentIndex % 3) {
            0 -> QuestionType.CHOICE
            1 -> QuestionType.SPELL
            else -> QuestionType.FLASHCARD
        }

        when (currentQuestionType) {
            QuestionType.CHOICE -> showChoiceQuestion(word)
            QuestionType.SPELL -> showSpellQuestion(word)
            QuestionType.FLASHCARD -> showFlashcardQuestion(word)
        }
    }

    private fun showChoiceQuestion(word: Word) {
        binding.choiceLayout.visibility = View.VISIBLE
        binding.spellLayout.visibility = View.GONE
        binding.cardLayout.visibility = View.GONE
        binding.tvWordForChoice.text = word.word

        // Generate options: 1 correct + 3 random meanings
        val wrongOptions = allWords
            .filter { it.word != word.word }
            .map { it.meaning }
            .shuffled()
            .take(3)
        currentOptions = (wrongOptions + word.meaning).shuffled()

        binding.btnChoiceOption1.text = currentOptions[0]
        binding.btnChoiceOption2.text = currentOptions[1]
        binding.btnChoiceOption3.text = currentOptions[2]
        binding.btnChoiceOption4.text = currentOptions[3]

        // Reset button colors
        listOf(binding.btnChoiceOption1, binding.btnChoiceOption2,
            binding.btnChoiceOption3, binding.btnChoiceOption4).forEach { btn ->
            btn.isEnabled = true
            btn.setBackgroundColor(Color.parseColor("#0039CB"))
        }
    }

    private fun showSpellQuestion(word: Word) {
        binding.choiceLayout.visibility = View.GONE
        binding.spellLayout.visibility = View.VISIBLE
        binding.cardLayout.visibility = View.GONE

        binding.tvSpellHint.text = "提示: ${word.meaning}"
        binding.etSpellInput.text?.clear()
        binding.tvSpellResult.visibility = View.GONE
        binding.btnSpellSubmit.visibility = View.VISIBLE
        binding.btnSpellNext.visibility = View.GONE

        playCurrentAudio()
    }

    private fun showFlashcardQuestion(word: Word) {
        binding.choiceLayout.visibility = View.GONE
        binding.spellLayout.visibility = View.GONE
        binding.cardLayout.visibility = View.VISIBLE

        binding.tvCardFront.text = word.word
        binding.tvCardBackMeaning.text = word.meaning
        binding.tvCardBackPhonetic.text = word.phonetic
        binding.tvCardBackPhonetic.visibility = if (word.phonetic.isEmpty()) View.GONE else View.VISIBLE
        binding.cardBack.visibility = View.GONE
        binding.cardFront.visibility = View.VISIBLE
        binding.btnCardFlip.visibility = View.VISIBLE
        binding.btnCardKnown.visibility = View.GONE
        binding.btnCardForgot.visibility = View.GONE
        binding.tvCardRating.visibility = View.GONE
    }

    private fun selectChoice(index: Int) {
        val word = challengeWords[currentIndex]
        val selected = currentOptions[index]
        val correct = selected == word.meaning

        // Disable all buttons
        listOf(binding.btnChoiceOption1, binding.btnChoiceOption2,
            binding.btnChoiceOption3, binding.btnChoiceOption4).forEach btn@{ btn ->
            btn.isEnabled = false
            val btnIndex = listOf(binding.btnChoiceOption1, binding.btnChoiceOption2,
                binding.btnChoiceOption3, binding.btnChoiceOption4).indexOf(btn)
            val optText = currentOptions[btnIndex]
            if (optText == word.meaning) {
                btn.setBackgroundColor(Color.parseColor("#4CAF50"))
            } else if (btnIndex == index && !correct) {
                btn.setBackgroundColor(Color.parseColor("#F44336"))
            }
        }

        if (correct) {
            correctCount++
            streakCount++
            if (streakCount > maxStreak) maxStreak = streakCount
            lifecycleScope.launch {
                progressRepo.updateReviewProgress(UserManager.userId, currentCorpusId, word.word, true)
                studyRepo.recordCorrectAnswer(UserManager.userId, currentCorpusId, word.word)
            }
        } else {
            wrongCount++
            streakCount = 0
            lifecycleScope.launch {
                studyRepo.addWrongWord(UserManager.userId, currentCorpusId, word.word, "daily_challenge")
            }
        }

        // Auto-advance after delay
        binding.btnSpellPlay.postDelayed({ nextQuestion() }, 1200)
    }

    private fun submitSpell() {
        val input = binding.etSpellInput.text.toString().trim().lowercase()
        val answer = challengeWords[currentIndex].word.trim().lowercase()

        if (input.isEmpty()) {
            Toast.makeText(context, "请输入答案", Toast.LENGTH_SHORT).show()
            return
        }

        val word = challengeWords[currentIndex]
        if (input == answer) {
            correctCount++
            streakCount++
            if (streakCount > maxStreak) maxStreak = streakCount
            binding.tvSpellResult.text = "正确!"
            binding.tvSpellResult.setTextColor(Color.parseColor("#4CAF50"))
            lifecycleScope.launch {
                progressRepo.updateReviewProgress(UserManager.userId, currentCorpusId, word.word, true)
            }
        } else {
            wrongCount++
            streakCount = 0
            binding.tvSpellResult.text = "错误! 正确答案: ${word.word}"
            binding.tvSpellResult.setTextColor(Color.parseColor("#F44336"))
            lifecycleScope.launch {
                studyRepo.addWrongWord(UserManager.userId, currentCorpusId, word.word, "daily_challenge")
            }
        }
        binding.tvSpellResult.visibility = View.VISIBLE
        binding.btnSpellSubmit.visibility = View.GONE
        binding.btnSpellNext.visibility = View.VISIBLE
    }

    private fun flipCard() {
        binding.cardFront.visibility = View.GONE
        binding.cardBack.visibility = View.VISIBLE
        binding.btnCardFlip.visibility = View.GONE
        binding.btnCardKnown.visibility = View.VISIBLE
        binding.btnCardForgot.visibility = View.VISIBLE
        binding.tvCardRating.visibility = View.VISIBLE
        playCurrentAudio()
    }

    private fun rateCard(known: Boolean) {
        val word = challengeWords[currentIndex]
        if (known) {
            correctCount++
            streakCount++
            if (streakCount > maxStreak) maxStreak = streakCount
            lifecycleScope.launch {
                progressRepo.updateReviewProgress(UserManager.userId, currentCorpusId, word.word, true)
                studyRepo.recordCorrectAnswer(UserManager.userId, currentCorpusId, word.word)
            }
        } else {
            wrongCount++
            streakCount = 0
            lifecycleScope.launch {
                studyRepo.addWrongWord(UserManager.userId, currentCorpusId, word.word, "daily_challenge")
            }
        }
        nextQuestion()
    }

    private fun nextQuestion() {
        audioPlayer.release()
        currentIndex++
        showQuestion()
    }

    private fun playCurrentAudio() {
        if (challengeWords.isEmpty()) return
        val word = challengeWords[currentIndex]
        val path = CorpusRepository(requireContext()).getAudioPath(currentCorpusId, word.word, "words")
        audioPlayer.play(path) { _ -> }
    }

    private fun showResults() {
        audioPlayer.release()
        binding.choiceLayout.visibility = View.GONE
        binding.spellLayout.visibility = View.GONE
        binding.cardLayout.visibility = View.GONE

        val accuracy = if (challengeWords.isNotEmpty()) correctCount * 100 / challengeWords.size else 0
        val streakText = if (maxStreak >= 3) " 最高连击: $maxStreak" else ""

        binding.tvProgress.text = "挑战完成"
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(android.widget.TextView(requireContext()).apply {
            text = "每日挑战"
            textSize = 28f
            android.graphics.Typeface.BOLD
            setTextColor(0xFFE3000F.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(dip(16), dip(16), dip(16), dip(8))
        })
        binding.contentContainer.addView(android.widget.TextView(requireContext()).apply {
            text = "正确: $correctCount  错误: $wrongCount\n正确率: $accuracy%$streakText"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(dip(16), 8, dip(16), 8)
        })
        binding.contentContainer.addView(android.widget.Button(requireContext()).apply {
            text = "返回"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFE3000F.toInt())
            setOnClickListener { parentFragmentManager.popBackStack() }
        }, android.widget.LinearLayout.LayoutParams(dip(150), dip(48)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            topMargin = dip(24)
        })
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
