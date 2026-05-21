package com.learne.ui.listen

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.learne.R
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
    private var selectedGroupIndex = 0
    private var currentIndex = 0
    private var correctCount = 0
    private var wrongCount = 0
    private var currentCorpusId: String = "catti"
    private var groupSize: Int = 30
    private var totalGroups: Int = 0

    private val audioPlayer = AudioPlayer()
    private lateinit var studyRepo: StudyRepository

    private var consecutiveWrong = 0 // 连续答错次数

    // Colors for group map
    private val COLOR_COMPLETED = 0xFF4CAF50.toInt()   // Green (quiz passed)
    private val COLOR_AVAILABLE = 0xFFFFC107.toInt()    // Yellow (quiz passed, available for dictation)
    private val COLOR_LOCKED = 0xFF9E9E9E.toInt()       // Gray (not quiz-passed)

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

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnPlayAudio.setOnClickListener { playCurrentAudio() }
        binding.btnSubmit.setOnClickListener { submitAnswer() }
        binding.btnNext.setOnClickListener { nextWord() }

        binding.btnStartDictation.setOnClickListener {
            startDictationForGroup(selectedGroupIndex)
        }

        loadWords()
    }

    private fun loadWords() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutGroupMap.visibility = View.GONE
        binding.layoutDictation.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val allCorpusWords = CorpusRepository(requireContext()).loadWords(currentCorpusId)
                if (allCorpusWords.isEmpty()) {
                    Toast.makeText(context, "词库为空", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return@launch
                }
                // Filter to only quiz-passed groups
                val passedGroups = UserPreferencesRepository.getQuizPassedGroups(currentCorpusId)
                allWords = allCorpusWords.mapIndexedNotNull { idx, word ->
                    val groupIdx = idx / groupSize
                    if (passedGroups.contains(groupIdx)) word else null
                }
                if (allWords.isEmpty()) {
                    Toast.makeText(context, "暂无考试通过的单词，请先完成考试", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return@launch
                }

                totalGroups = ceil(allWords.size.toDouble() / groupSize).toInt()
                buildGroupMap()

                binding.layoutLoading.visibility = View.GONE
                binding.layoutGroupMap.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun buildGroupMap() {
        binding.layoutGroupGrid.removeAllViews()

        val passedGroups = UserPreferencesRepository.getQuizPassedGroups(currentCorpusId)
        val dictationCompleted = UserPreferencesRepository.getCompletedGroups("dictation_$currentCorpusId").toSet()

        val groupsPerRow = 5
        val totalRows = ceil(totalGroups.toDouble() / groupsPerRow).toInt()

        // Find first available group for default selection
        selectedGroupIndex = 0

        var rowLayout: LinearLayout? = null

        for (row in 0 until totalRows) {
            rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dip(8) }
            }

            for (col in 0 until groupsPerRow) {
                val groupIndex = row * groupsPerRow + col
                if (groupIndex >= totalGroups) break

                val isDictationCompleted = dictationCompleted.contains(groupIndex)
                val color = when {
                    isDictationCompleted -> COLOR_COMPLETED
                    else -> COLOR_AVAILABLE
                }
                val showIcon = if (isDictationCompleted) "✓" else ""

                val frameLayout = FrameLayout(requireContext()).apply {
                    val size = dip(56)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = dip(6)
                    }
                    setBackgroundResource(android.R.drawable.dialog_holo_light_frame)

                    addView(Button(requireContext()).apply {
                        text = "${groupIndex + 1}"
                        textSize = 14f
                        setBackgroundColor(color)
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                        if (showIcon.isNotEmpty()) setTypeface(null, android.graphics.Typeface.BOLD)
                        elevation = 4f
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        setOnClickListener {
                            selectedGroupIndex = groupIndex
                            highlightSelectedGroup()
                        }
                    })
                }

                rowLayout.addView(frameLayout)
            }

            binding.layoutGroupGrid.addView(rowLayout)
        }

        // Default: select first group
        highlightSelectedGroup()
    }

    private fun highlightSelectedGroup() {
        for (i in 0 until binding.layoutGroupGrid.childCount) {
            val row = binding.layoutGroupGrid.getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val frame = row.getChildAt(j) as? FrameLayout ?: continue
                val btn = frame.getChildAt(0) as? Button ?: continue
                val groupIndex = i * 5 + j
                if (groupIndex == selectedGroupIndex) {
                    btn.elevation = 8f
                    btn.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    btn.elevation = 4f
                    btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                }
            }
        }
    }

    private fun startDictationForGroup(groupIndex: Int) {
        binding.layoutGroupMap.visibility = View.GONE
        binding.layoutDictation.visibility = View.VISIBLE

        selectGroup(groupIndex)
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

        binding.etAnswer.setText("")
        binding.tvResult.text = ""
        binding.tvResult.visibility = View.GONE
        binding.tvHint.visibility = View.VISIBLE
        binding.tvHint.text = "点击播放按钮听发音"
        binding.btnSubmit.visibility = View.VISIBLE
        binding.btnNext.visibility = View.GONE

        updateProgress()
    }

    private fun updateProgress() {
        val total = currentGroup.size
        val current = currentIndex + 1
        binding.tvProgressTop.text = "$current / $total  组 ${currentGroupIndex + 1}/$totalGroups"
    }

    private fun playCurrentAudio() {
        if (currentGroup.isEmpty()) return
        val word = currentGroup[currentIndex]
        val path = CorpusRepository(requireContext()).getAudioPath(currentCorpusId, word.word, "words")
        audioPlayer.play(path) { _ -> }
    }

    private fun submitAnswer() {
        if (currentGroup.isEmpty()) return
        val input = binding.etAnswer.text.toString().trim().lowercase()
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
            binding.etAnswer.setText("")
            binding.tvResult.text = ""
            binding.tvResult.visibility = View.GONE
            binding.tvHint.visibility = View.VISIBLE
            binding.tvHint.text = "点击播放按钮听发音"
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
            else -> {
                // Show first letter
                val rest = "_".repeat(word.length - 1)
                "${word[0]}$rest"
            }
        }
        binding.tvHint.text = "提示: $revealed"
        binding.tvHint.visibility = View.VISIBLE
    }

    private fun showStats() {
        val total = correctCount + wrongCount
        val accuracy = if (total > 0) correctCount * 100 / total else 0
        val hasNextGroup = currentGroupIndex + 1 < totalGroups

        // Mark group as completed for dictation
        UserPreferencesRepository.markGroupCompleted("dictation_$currentCorpusId", currentGroupIndex)

        binding.tvProgressTop.text = "听写完成"

        // Switch back to group map and show stats overlay
        binding.layoutDictation.visibility = View.GONE
        binding.layoutGroupMap.visibility = View.VISIBLE

        // Rebuild map to reflect completion
        buildGroupMap()

        // Show stats in a dialog-like overlay
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(16), dip(16), dip(16), dip(16))

            addView(TextView(requireContext()).apply {
                text = "听写完成"
                textSize = 24f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFE3000F.toInt())
                gravity = Gravity.CENTER
                setPadding(dip(16), dip(16), dip(16), dip(8))
            })

            addView(TextView(requireContext()).apply {
                text = "正确: $correctCount  错误: $wrongCount\n正确率: $accuracy%"
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dip(16), 0, dip(16), 0)
            })

            // Accuracy bar visualization
            if (total > 0) {
                addView(TextView(requireContext()).apply {
                    val correctBar = "█".repeat(correctCount * 20 / total)
                    val wrongBar = "░".repeat(20 - correctBar.length)
                    text = "$correctBar$wrongBar"
                    textSize = 14f
                    setTextColor(0xFF4CAF50.toInt())
                    gravity = Gravity.CENTER
                    setPadding(dip(16), dip(8), dip(16), dip(16))
                })
            }
        }

        val btnPositive = if (hasNextGroup) "下一组" else "确定"
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton(btnPositive) { _, _ ->
                if (hasNextGroup) {
                    selectedGroupIndex = currentGroupIndex + 1
                    startDictationForGroup(selectedGroupIndex)
                }
            }
            .setNegativeButton("返回") { _, _ ->
                parentFragmentManager.popBackStack()
            }
            .setCancelable(false)
            .create()
        dialog.show()
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
