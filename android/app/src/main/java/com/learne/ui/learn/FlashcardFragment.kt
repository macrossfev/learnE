package com.learne.ui.learn

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.ProgressRepository
import com.learne.data.repository.StudyRepository
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentFlashcardBinding
import com.learne.service.AudioPlayer
import kotlin.math.ceil

class FlashcardFragment : Fragment() {

    companion object {
        fun newInstance(corpusId: String = "catti"): FlashcardFragment {
            return FlashcardFragment().apply {
                arguments = Bundle().apply { putString("corpusId", corpusId) }
            }
        }
    }

    private var _binding: FragmentFlashcardBinding? = null
    private val binding get() = _binding!!

    private var allWords: List<Word> = emptyList()
    private var currentGroup: List<Word> = emptyList()
    private var currentGroupIndex = 0
    private var currentIndex = 0
    private var currentCorpusId: String = "catti"
    private var groupSize: Int = 30
    private var isFlipped = false
    private var directionENtoCN = true

    private val audioPlayer = AudioPlayer()
    private lateinit var progressRepo: ProgressRepository
    private lateinit var studyRepo: StudyRepository

    private var knownCount = 0
    private var vagueCount = 0
    private var unknownCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFlashcardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentCorpusId = arguments?.getString("corpusId") ?: UserPreferencesRepository.planCorpusId ?: "catti"
        groupSize = UserPreferencesRepository.planGroupSize
        progressRepo = ProgressRepository(requireContext())
        studyRepo = StudyRepository(requireContext())

        binding.btnFlip.setOnClickListener { flipCard() }
        binding.btnNext.setOnClickListener { nextCard() }
        binding.btnPrev.setOnClickListener { prevCard() }
        binding.btnPlay.setOnClickListener { playCurrentAudio() }
        binding.cardFront.setOnClickListener { flipCard() }

        binding.btnKnown.setOnClickListener { rateCard("known") }
        binding.btnVague.setOnClickListener { rateCard("vague") }
        binding.btnForgot.setOnClickListener { rateCard("forgot") }

        binding.tvDirection.setOnClickListener { toggleDirection() }

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
                binding.layoutLoading.visibility = View.GONE
                selectGroup(0)
            } catch (e: Exception) {
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun selectGroup(groupIndex: Int) {
        currentGroupIndex = groupIndex
        val start = groupIndex * groupSize
        val end = (start + groupSize).coerceAtMost(allWords.size)
        currentGroup = allWords.subList(start, end)
        currentIndex = 0
        isFlipped = false
        updateProgress()
        showCurrentCard()
    }

    private fun showCurrentCard() {
        if (currentGroup.isEmpty()) return
        val word = currentGroup[currentIndex]
        isFlipped = false

        binding.cardFront.visibility = View.VISIBLE
        binding.cardBack.visibility = View.GONE
        binding.layoutRating.visibility = View.GONE

        if (directionENtoCN) {
            binding.tvCardFrontContent.text = word.word
        } else {
            binding.tvCardFrontContent.text = word.meaning
        }
        binding.tvCardHint.text = "点击翻转查看答案"

        binding.tvCardBackWord.text = word.word
        binding.tvCardBackPhonetic.text = word.phonetic
        binding.tvCardBackPhonetic.visibility = if (word.phonetic.isEmpty()) View.GONE else View.VISIBLE
        binding.tvCardBackMeaning.text = word.meaning
        binding.tvCardBackExample.text = word.example
        binding.tvCardBackExample.visibility = if (word.example.isEmpty()) View.GONE else View.VISIBLE

        updateProgress()
    }

    private fun flipCard() {
        if (isFlipped) return
        isFlipped = true

        binding.cardFront.animate()
            .rotationY(90f)
            .setDuration(150)
            .withEndAction {
                binding.cardFront.visibility = View.GONE
                binding.cardFront.rotationY = 0f
                binding.cardBack.rotationY = -90f
                binding.cardBack.visibility = View.VISIBLE
                binding.cardBack.animate()
                    .rotationY(0f)
                    .setDuration(150)
                    .withEndAction {
                        binding.layoutRating.visibility = View.VISIBLE
                        playCurrentAudio()
                    }
                    .start()
            }
            .start()
    }

    private fun rateCard(rating: String) {
        if (currentGroup.isEmpty()) return
        val word = currentGroup[currentIndex]

        when (rating) {
            "known" -> {
                knownCount++
                lifecycleScope.launch {
                    progressRepo.updateReviewProgress(UserManager.userId, currentCorpusId, word.word, true)
                }
            }
            "vague" -> {
                vagueCount++
                lifecycleScope.launch {
                    progressRepo.recordLearned(UserManager.userId, currentCorpusId, word.word)
                }
            }
            "forgot" -> {
                unknownCount++
                lifecycleScope.launch {
                    progressRepo.updateReviewProgress(UserManager.userId, currentCorpusId, word.word, false)
                    studyRepo.addWrongWord(UserManager.userId, currentCorpusId, word.word, "flashcard")
                }
            }
        }

        nextCard()
    }

    private fun nextCard() {
        if (currentIndex < currentGroup.size - 1) {
            currentIndex++
            showCurrentCard()
            playCurrentAudio()
        } else {
            showStats()
        }
    }

    private fun prevCard() {
        if (currentIndex > 0) {
            currentIndex--
            showCurrentCard()
            playCurrentAudio()
        }
    }

    private fun toggleDirection() {
        directionENtoCN = !directionENtoCN
        binding.tvDirection.text = if (directionENtoCN) "英→中" else "中→英"
        if (!isFlipped) showCurrentCard()
    }

    private fun playCurrentAudio() {
        if (currentGroup.isEmpty()) return
        val word = currentGroup[currentIndex]
        val path = CorpusRepository(requireContext()).getAudioPath(currentCorpusId, word.word, "words")
        audioPlayer.play(path) { _ -> }
    }

    private fun updateProgress() {
        val total = allWords.size
        val global = currentGroupIndex * groupSize + currentIndex + 1
        binding.tvProgress.text = "$global / $total"
        val pct = if (total > 0) global * 100 / total else 0
        binding.progressBar.progress = pct
    }

    private fun showStats() {
        val total = knownCount + vagueCount + unknownCount
        val accuracy = if (total > 0) knownCount * 100 / total else 0
        val hasNextGroup = currentGroupIndex + 1 < ceil(allWords.size.toDouble() / groupSize).toInt()

        binding.contentContainer.removeAllViews()

        // Title
        binding.contentContainer.addView(android.widget.TextView(requireContext()).apply {
            text = "闪卡完成"
            textSize = 24f
            android.graphics.Typeface.BOLD
            setTextColor(0xFFE3000F.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(dip(16), dip(16), dip(16), dip(8))
        })

        // Stats
        binding.contentContainer.addView(android.widget.TextView(requireContext()).apply {
            text = "认识: $knownCount  模糊: $vagueCount  不认识: $unknownCount\n正确率: $accuracy%"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(dip(16), 0, dip(16), 0)
        })

        // Visual bar
        if (total > 0) {
            binding.contentContainer.addView(android.widget.TextView(requireContext()).apply {
                val knownBar = "█".repeat(knownCount * 20 / total)
                val vagueBar = "░".repeat(vagueCount * 20 / total)
                val unknownBar = "▒".repeat(20 - knownBar.length - vagueBar.length)
                text = "$knownBar$vagueBar$unknownBar  (█认识 ░模糊 ▒不认识)"
                textSize = 12f
                setTextColor(0xFF666666.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(dip(16), 8, dip(16), 16)
            })
        }

        // Buttons
        val btnRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dip(16) }
        }

        val btnBack = android.widget.Button(requireContext()).apply {
            text = "返回"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF808080.toInt())
            setOnClickListener { parentFragmentManager.popBackStack() }
        }
        btnRow.addView(btnBack, LinearLayout.LayoutParams(0, dip(48)).apply { weight = 1f; marginEnd = dip(8) })

        if (hasNextGroup) {
            val btnContinue = android.widget.Button(requireContext()).apply {
                text = "下一组"
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFFE3000F.toInt())
                setOnClickListener { selectGroup(currentGroupIndex + 1) }
            }
            btnRow.addView(btnContinue, LinearLayout.LayoutParams(0, dip(48)).apply { weight = 1f })
        }

        binding.contentContainer.addView(btnRow)

        binding.btnFlip.visibility = View.GONE
        binding.btnNext.visibility = View.GONE
        binding.btnPrev.visibility = View.GONE
        binding.btnPlay.visibility = View.GONE
    }

    private fun dip(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun stopAudio() {
        audioPlayer.release()
    }

    override fun onDestroyView() {
        stopAudio()
        // Save flashcard progress
        UserPreferencesRepository.saveListenPosition(currentCorpusId, currentGroupIndex, currentIndex)
        super.onDestroyView()
        _binding = null
    }
}
