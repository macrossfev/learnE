package com.learne.ui.listen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.StudyRepository
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentDictationBinding
import com.learne.service.AudioPlayer
import kotlin.math.ceil

class DictationFragment : Fragment() {

    companion object {
        fun newInstance(corpusId: String = "catti"): DictationFragment {
            return DictationFragment().apply {
                arguments = Bundle().apply { putString("corpusId", corpusId) }
            }
        }
    }

    private var _binding: FragmentDictationBinding? = null
    private val binding get() = _binding!!

    private var allWords: List<Word> = emptyList()
    private var currentGroup: List<Word> = emptyList()
    private var currentGroupIndex = 0
    private var currentIndex = 0
    private var correctCount = 0
    private var wrongCount = 0
    private var currentCorpusId: String = "catti"
    private var groupSize: Int = 30

    private val audioPlayer = AudioPlayer()
    private lateinit var studyRepo: StudyRepository

    private var consecutiveWrong = 0 // 连续答错次数

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDictationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentCorpusId = arguments?.getString("corpusId") ?: UserPreferencesRepository.planCorpusId ?: "catti"
        groupSize = UserPreferencesRepository.planGroupSize
        studyRepo = StudyRepository(requireContext())

        binding.btnPlay.setOnClickListener { playCurrentAudio() }
        binding.btnSubmit.setOnClickListener { submitAnswer() }
        binding.btnNext.setOnClickListener { nextWord() }
        binding.btnHint.setOnClickListener { showHint() }

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
                setupGroupSpinner()
                binding.layoutLoading.visibility = View.GONE
                selectGroup(0)
            } catch (e: Exception) {
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun setupGroupSpinner() {
        val totalGroups = ceil(allWords.size.toDouble() / groupSize).toInt()
        val groupNames = List(totalGroups) { "第${it + 1}组" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, groupNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spGroup.adapter = adapter
        binding.spGroup.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectGroup(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun selectGroup(groupIndex: Int) {
        stopAudio()
        currentGroupIndex = groupIndex
        val start = groupIndex * groupSize
        val end = (start + groupSize).coerceAtMost(allWords.size)
        currentGroup = allWords.subList(start, end)
        currentIndex = 0
        correctCount = 0
        wrongCount = 0
        consecutiveWrong = 0
        binding.etInput.setText("")
        binding.tvResult.text = ""
        binding.tvResult.visibility = View.GONE
        binding.tvHint.visibility = View.GONE
        binding.tvHint.text = ""
        binding.btnHint.visibility = View.VISIBLE
        binding.btnHint.isEnabled = true
        updateProgress()
        binding.btnSubmit.visibility = View.VISIBLE
        binding.btnNext.visibility = View.GONE
    }

    private fun updateProgress() {
        val total = allWords.size
        val global = currentGroupIndex * groupSize + currentIndex + 1
        binding.tvWordProgress.text = "$global / $total  组 ${currentGroupIndex + 1}/${binding.spGroup.adapter.count}"
    }

    private fun playCurrentAudio() {
        if (currentGroup.isEmpty()) return
        val word = currentGroup[currentIndex]
        val path = CorpusRepository(requireContext()).getAudioPath(currentCorpusId, word.word, "words")
        audioPlayer.play(path) { _ -> }
    }

    private fun submitAnswer() {
        if (currentGroup.isEmpty()) return
        val input = binding.etInput.text.toString().trim().lowercase()
        val answer = currentGroup[currentIndex].word.trim().lowercase()

        if (input.isEmpty()) {
            Toast.makeText(context, "请输入答案", Toast.LENGTH_SHORT).show()
            return
        }

        if (input == answer) {
            correctCount++
            binding.tvResult.text = "正确!"
            binding.tvResult.setTextColor(0xFF4CAF50.toInt())
            consecutiveWrong = 0
            lifecycleScope.launch {
                studyRepo.recordCorrectAnswer(UserManager.userId, currentCorpusId, currentGroup[currentIndex].word)
            }
        } else {
            wrongCount++
            consecutiveWrong++
            binding.tvResult.text = "错误! 正确答案: ${currentGroup[currentIndex].word}"
            binding.tvResult.setTextColor(0xFFF44336.toInt())

            // Add to wrong words
            lifecycleScope.launch {
                studyRepo.addWrongWord(UserManager.userId, currentCorpusId, currentGroup[currentIndex].word, "dictation")
            }

            // Auto-show hint after 2 consecutive wrong
            if (consecutiveWrong >= 2) {
                showHint()
            }
        }
        binding.tvResult.visibility = View.VISIBLE
        binding.btnSubmit.visibility = View.GONE
        binding.btnNext.visibility = View.VISIBLE
    }

    private fun nextWord() {
        stopAudio()
        if (currentIndex < currentGroup.size - 1) {
            currentIndex++
            consecutiveWrong = 0
            binding.etInput.setText("")
            binding.tvResult.text = ""
            binding.tvResult.visibility = View.GONE
            binding.tvHint.visibility = View.GONE
            binding.tvHint.text = ""
            binding.btnHint.isEnabled = true
            binding.btnSubmit.visibility = View.VISIBLE
            binding.btnNext.visibility = View.GONE
            updateProgress()
            playCurrentAudio()
        } else {
            showStats()
        }
    }

    private fun showHint() {
        if (currentGroup.isEmpty()) return
        val word = currentGroup[currentIndex].word
        val revealed = when {
            word.length <= 2 -> word[0] + "_"
            consecutiveWrong >= 3 -> {
                // Show first + last + length
                val middle = "_".repeat(word.length - 2)
                "${word[0]}$middle${word.last()}"
            }
            consecutiveWrong >= 2 -> {
                // Show first letter
                val rest = "_".repeat(word.length - 1)
                "${word[0]}$rest"
            }
            else -> {
                val rest = "_".repeat(word.length - 1)
                "${word[0]}$rest"
            }
        }
        binding.tvHint.text = "提示: $revealed"
        binding.tvHint.visibility = View.VISIBLE
        binding.btnHint.isEnabled = false
    }

    private fun showStats() {
        val total = correctCount + wrongCount
        val accuracy = if (total > 0) correctCount * 100 / total else 0
        val hasNextGroup = currentGroupIndex + 1 < binding.spGroup.adapter.count
        binding.tvWordProgress.text = "听写完成"
        binding.contentContainer.removeAllViews()

        // Title
        binding.contentContainer.addView(android.widget.TextView(requireContext()).apply {
            text = "听写完成"
            textSize = 24f
            android.graphics.Typeface.BOLD
            setTextColor(0xFFE3000F.toInt())
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

        // Accuracy bar visualization
        if (total > 0) {
            binding.contentContainer.addView(android.widget.TextView(requireContext()).apply {
                val correctBar = "█".repeat(correctCount * 20 / total)
                val wrongBar = "░".repeat(20 - correctBar.length)
                text = "$correctBar$wrongBar"
                textSize = 14f
                setTextColor(0xFF4CAF50.toInt())
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
                setOnClickListener {
                    selectGroup(currentGroupIndex + 1)
                    binding.spGroup.setSelection(currentGroupIndex + 1)
                }
            }
            btnRow.addView(btnContinue, LinearLayout.LayoutParams(0, dip(48)).apply { weight = 1f })
        }

        binding.contentContainer.addView(btnRow)

        binding.btnSubmit.visibility = View.GONE
        binding.btnNext.visibility = View.GONE
    }

    private fun stopAudio() {
        audioPlayer.release()
    }

    private fun dip(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    override fun onDestroyView() {
        stopAudio()
        // Save listening position for resume
        UserPreferencesRepository.saveListenPosition(currentCorpusId, currentGroupIndex, currentIndex)
        super.onDestroyView()
        _binding = null
    }
}
