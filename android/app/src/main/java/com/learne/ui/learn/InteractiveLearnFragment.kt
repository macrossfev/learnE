package com.learne.ui.learn

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.ProgressRepository
import com.learne.data.repository.StudyRepository
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentInteractiveLearnBinding
import com.learne.service.AudioPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.math.ceil

class InteractiveLearnFragment : Fragment() {

    companion object {
        fun newInstance(corpusId: String = "catti", mode: Mode = Mode.LEARN, planIndex: Int = -1, groupIndex: Int = -1, isGuided: Boolean = false): InteractiveLearnFragment {
            return InteractiveLearnFragment().apply {
                currentCorpusId = corpusId
                this.modeArg = mode
                this.planIndexArg = planIndex
                this.groupIndexArg = groupIndex
                this.isGuided = isGuided
            }
        }
    }

    private var modeArg: Mode = Mode.LEARN
    private var planIndexArg: Int = -1
    private var groupIndexArg: Int = -1
    private var isGuided: Boolean = false

    private var _binding: FragmentInteractiveLearnBinding? = null
    private val binding get() = _binding!!

    private lateinit var corpusRepo: CorpusRepository
    private lateinit var progressRepo: ProgressRepository
    private lateinit var studyRepo: StudyRepository

    // All words in corpus
    private var allWords: List<Word> = emptyList()
    // Current group words
    private var currentGroupWords: List<Word> = emptyList()
    private var currentGroupIndex = 0
    private var currentIndexInGroup = 0
    private var currentStep = 1 // 1=display, 2=choice, 3=spell
    private var isSpinnerReady = false

    // Mode: learn / review / review_direct / wrong
    enum class Mode { LEARN, REVIEW, REVIEW_DIRECT, WRONG }
    private var currentMode = Mode.LEARN

    // Review mode words
    private var reviewWords: List<Word> = emptyList()

    // Review direct mode words (words due for review, no display step)
    private var reviewDirectWords: List<Word> = emptyList()
    private var reviewDirectIndex = 0

    // Wrong mode words
    private var wrongWordList: List<com.learne.data.model.WrongWord> = emptyList()
    private var wrongWordDetails: List<Word> = emptyList()
    private var wrongWordIndex = 0

    private var currentCorpusId: String = "catti"
    private var groupSize = 30

    // Daily goal (tracked but not displayed)
    private var dailyGoalTarget = 50
    private var dailyGoalProgress = 0

    private val audioTypes = listOf("words", "meanings")
    private var audioQueue: List<String> = emptyList()
    private var audioQueueIndex = 0
    private var isPlayingAudioSequence = false
    private val audioPlayer = AudioPlayer()

    // Choice state
    private var choiceOptions: List<ChoiceOption> = emptyList()
    private var selectedChoiceIndex = -1

    // Session stats
    private var sessionCorrectCount = 0
    private var sessionWrongCount = 0
    private var sessionTotalCount = 0

    data class ChoiceOption(
        val meaning: String,
        val correct: Boolean
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInteractiveLearnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        corpusRepo = CorpusRepository(requireContext())
        progressRepo = ProgressRepository(requireContext())
        studyRepo = StudyRepository(requireContext())

        groupSize = UserPreferencesRepository.planGroupSize

        binding.btnTabLearn.setOnClickListener { switchMode(Mode.LEARN) }
        binding.btnTabReview.setOnClickListener { switchMode(Mode.REVIEW) }
        binding.btnTabWrong.setOnClickListener { switchMode(Mode.WRONG) }
        binding.btnPrev.setOnClickListener { prevWord() }
        binding.btnNext.setOnClickListener {
            if (currentMode == Mode.WRONG) {
                removeWrongWord()
            } else {
                nextWord()
            }
        }
        binding.btnUnfamiliar.setOnClickListener { markUnfamiliar() }

        loadWords()
    }

    private fun resetSessionStats() {
        sessionCorrectCount = 0
        sessionWrongCount = 0
        sessionTotalCount = 0
    }

    private fun loadWords() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutMain.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = withTimeout(15000) {
                    corpusRepo.loadWords(currentCorpusId)
                }
                allWords = result
                if (allWords.isEmpty()) {
                    Toast.makeText(context, "词库为空", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return@launch
                }

                // Initialize currentGroupIndex: use explicit groupIndex, then plan state
                if (groupIndexArg >= 0) {
                    currentGroupIndex = groupIndexArg
                } else if (planIndexArg >= 0) {
                    val plan = UserPreferencesRepository.loadPlan(planIndexArg)
                    currentGroupIndex = plan?.currentGroupIndex ?: 0
                }

                setupGroupSpinner()
                setupDashboard()
                switchMode(modeArg)
            } catch (e: Exception) {
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun setupGroupSpinner() {
        val totalGroups = ceil(allWords.size.toDouble() / groupSize).toInt()
        val completedGroups = if (planIndexArg >= 0) {
            UserPreferencesRepository.getPlanCompletedGroups(planIndexArg)
        } else {
            UserPreferencesRepository.getCompletedGroups(currentCorpusId)
        }
        val groupNames = List(totalGroups) { i ->
            val start = i * groupSize + 1
            val end = ((i + 1) * groupSize).coerceAtMost(allWords.size)
            val completed = if (completedGroups.contains(i)) " ✓" else ""
            "第${i + 1}组 ($start-$end)$completed"
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, groupNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spGroup.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (currentMode == Mode.LEARN && isSpinnerReady) selectGroup(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        binding.spGroup.adapter = adapter
        // Set position to currentGroupIndex AFTER listener is set, then mark ready
        binding.spGroup.setSelection(currentGroupIndex)
        isSpinnerReady = true
    }

    private fun setupDashboard() {
        binding.layoutDashboard.visibility = View.VISIBLE
        binding.layoutMain.visibility = View.GONE
        binding.layoutStats.removeAllViews()
        lifecycleScope.launch {
            val db = com.learne.data.db.AppDatabase.getDatabase(requireContext())
            val uidCorpus = "${UserManager.userId}_$currentCorpusId"
            val mastered = db.progressDao().getMasteredCount(uidCorpus).first()
            val learned = db.progressDao().getLearnedCount(uidCorpus).first()
            val wrongs = db.wrongWordDao().getWrongWords(uidCorpus).first().size
            val completedGroups = if (planIndexArg >= 0) {
                UserPreferencesRepository.getPlanCompletedGroups(planIndexArg)
            } else {
                UserPreferencesRepository.getCompletedGroups(currentCorpusId)
            }
            val totalGroups = ceil(allWords.size.toDouble() / groupSize).toInt()

            binding.layoutStats.addView(textView("已学习: $learned 词", 16f, false, "#000000").apply { setPadding(dip(8), dip(4), dip(8), dip(4)) })
            binding.layoutStats.addView(textView("已掌握: $mastered 词", 16f, false, "#4CAF50").apply { setPadding(dip(8), dip(4), dip(8), dip(4)) })
            binding.layoutStats.addView(textView("错题: $wrongs 词", 16f, false, "#F44336").apply { setPadding(dip(8), dip(4), dip(8), dip(4)) })
            binding.layoutStats.addView(textView("已完成组: ${completedGroups.size}/$totalGroups", 16f, false, "#0039CB").apply { setPadding(dip(8), dip(4), dip(8), dip(4)) })
        }
        binding.btnStartLearning.setOnClickListener {
            binding.layoutDashboard.visibility = View.GONE
            binding.layoutMain.visibility = View.VISIBLE
        }
    }

    private fun switchMode(mode: Mode) {
        currentMode = mode
        audioPlayer.release()
        isPlayingAudioSequence = false
        resetSessionStats()

        // Hide mode tabs and group spinner in guided mode (entered from ChallengeMap)
        binding.layoutModeTabs.visibility = if (isGuided) View.GONE else View.VISIBLE

        // Update tab buttons
        val activeColor = 0xFFE3000F.toInt()
        val inactiveColor = 0xFF808080.toInt()
        binding.btnTabLearn.setBackgroundColor(if (mode == Mode.LEARN) activeColor else inactiveColor)
        binding.btnTabReview.setBackgroundColor(if (mode == Mode.REVIEW) activeColor else inactiveColor)
        binding.btnTabWrong.setBackgroundColor(if (mode == Mode.WRONG) activeColor else inactiveColor)

        when (mode) {
            Mode.LEARN -> {
                binding.spGroup.visibility = if (isGuided) View.GONE else View.VISIBLE
                binding.btnUnfamiliar.visibility = View.VISIBLE
                binding.btnNext.text = "已掌握"
                binding.layoutDashboard.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                selectGroup(currentGroupIndex)
            }
            Mode.REVIEW -> {
                binding.spGroup.visibility = View.GONE
                binding.btnUnfamiliar.visibility = View.VISIBLE
                binding.btnNext.text = "已掌握"
                binding.layoutDashboard.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                loadReviewWords()
            }
            Mode.REVIEW_DIRECT -> {
                binding.spGroup.visibility = View.GONE
                binding.btnUnfamiliar.visibility = View.GONE
                binding.btnNext.text = "下一词"
                binding.layoutDashboard.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                loadReviewDirectWords()
            }
            Mode.WRONG -> {
                binding.spGroup.visibility = View.GONE
                binding.btnUnfamiliar.visibility = View.GONE
                binding.btnNext.text = "移除错题"
                binding.layoutDashboard.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                loadWrongWords()
            }
        }
    }

    // ====== LEARN MODE ======

    private fun selectGroup(groupIndex: Int) {
        currentGroupIndex = groupIndex
        val start = groupIndex * groupSize
        val end = (start + groupSize).coerceAtMost(allWords.size)
        currentGroupWords = allWords.subList(start, end)
        currentIndexInGroup = 0
        currentStep = 1
        resetInputState()
        binding.layoutLoading.visibility = View.GONE
        binding.layoutMain.visibility = View.VISIBLE
        showCurrentWord()
    }

    // ====== REVIEW MODE ======

    private fun loadReviewWords() {
        lifecycleScope.launch {
            try {
                val progressList = progressRepo.getWordsForReview("${UserManager.userId}_$currentCorpusId").first()
                val words = allWords.filter { w ->
                    progressList.any { p -> p.word == w.word }
                }
                reviewWords = words
                if (reviewWords.isEmpty()) {
                    showEmptyState("暂无待复习的单词")
                } else {
                    currentStep = 1
                    currentIndexInGroup = 0
                    binding.layoutLoading.visibility = View.GONE
                    binding.layoutMain.visibility = View.VISIBLE
                    showCurrentReviewWord()
                }
            } catch (e: Exception) {
                showEmptyState("加载复习数据失败")
            }
        }
    }

    // ====== REVIEW DIRECT MODE (choice first, reveal word after) ======

    private fun loadReviewDirectWords() {
        lifecycleScope.launch {
            try {
                val progressList = progressRepo.getWordsForReview("${UserManager.userId}_$currentCorpusId").first()
                reviewDirectWords = allWords.filter { w ->
                    progressList.any { p -> p.word == w.word }
                }
                if (reviewDirectWords.isEmpty()) {
                    showEmptyState("暂无待复习的单词")
                } else {
                    currentStep = 2 // Start directly at choice
                    reviewDirectIndex = 0
                    binding.layoutLoading.visibility = View.GONE
                    binding.layoutMain.visibility = View.VISIBLE
                    showCurrentReviewDirectWord()
                }
            } catch (e: Exception) {
                showEmptyState("加载复习数据失败")
            }
        }
    }

    private fun showCurrentReviewDirectWord() {
        if (reviewDirectWords.isEmpty()) return
        val word = reviewDirectWords[reviewDirectIndex.coerceAtMost(reviewDirectWords.size - 1)]
        binding.contentContainer.removeAllViews()
        when (currentStep) {
            2 -> showChoicesDirect(word)
            3 -> showSpellInput(word)
            else -> showChoicesDirect(word)
        }
        updateProgressUI()
    }

    private fun showChoicesDirect(word: Word) {
        audioPlayer.release()
        isPlayingAudioSequence = false

        val dp8 = dip(8)
        val dp16 = dip(16)

        binding.contentContainer.addView(textView("选择正确的释义", 20f, true, "#000000").apply {
            setPadding(dp16, dp12(dp8), dp16, dp8)
        })

        choiceOptions = generateChoices(word)
        selectedChoiceIndex = -1

        for ((index, option) in choiceOptions.withIndex()) {
            binding.contentContainer.addView(android.widget.Button(requireContext()).apply {
                text = option.meaning
                textSize = 15f
                setTextColor(0xFF000000.toInt())
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(dp16, dp16, dp16, dp16)
                gravity = android.view.Gravity.CENTER or android.view.Gravity.START
                minLines = 2
                setOnClickListener { selectChoiceDirect(index, word) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp8) })
        }
    }

    private fun selectChoiceDirect(index: Int, word: Word) {
        if (selectedChoiceIndex >= 0) return
        selectedChoiceIndex = index
        val option = choiceOptions[index]

        val choiceBtn = binding.contentContainer.getChildAt(index + 1) as? android.widget.Button
        if (choiceBtn != null) {
            if (option.correct) {
                choiceBtn.setBackgroundColor(0xFF4CAF50.toInt())
                choiceBtn.setTextColor(0xFFFFFFFF.toInt())
                sessionTotalCount++
                sessionCorrectCount++
            } else {
                choiceBtn.setBackgroundColor(0xFFF44336.toInt())
                choiceBtn.setTextColor(0xFFFFFFFF.toInt())
                sessionTotalCount++
                sessionWrongCount++
            }

            // Reveal the word after choice
            val dp8 = dip(8)
            val dp16 = dip(16)
            binding.contentContainer.addView(textView("${word.word}  ${word.meaning}", 18f, true, "#E3000F").apply {
                setPadding(dp16, dp16, dp16, dp8)
                gravity = android.view.Gravity.CENTER
            })

            viewLifecycleOwner.lifecycleScope.launch {
                delay(1500)
                currentStep = 3
                resetInputState()
                showCurrentReviewDirectWord()
            }

            if (!option.correct) {
                Toast.makeText(context, "错误! 正确答案: ${word.word}", Toast.LENGTH_SHORT).show()
                saveErrorNoWrong(word)
            } else {
                Toast.makeText(context, "正确!", Toast.LENGTH_SHORT).show()
                markAsLearned(word)
                lifecycleScope.launch {
                    studyRepo.recordCorrectAnswer(UserManager.userId, currentCorpusId, word.word)
                }
            }
        }
    }

    // ====== WRONG MODE ======

    private fun loadWrongWords() {
        lifecycleScope.launch {
            val wrongWords = studyRepo.getWrongWords(UserManager.userId, currentCorpusId).first()
            wrongWordList = wrongWords
            if (wrongWords.isEmpty()) {
                showEmptyState("暂无错题")
            } else {
                wrongWordIndex = 0
                // Load word details
                wrongWordDetails = allWords.filter { w -> wrongWords.any { ww -> ww.word == w.word } }
                binding.layoutLoading.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                showCurrentWrongWord()
            }
        }
    }

    private fun showEmptyState(message: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutMain.visibility = View.VISIBLE
        binding.contentContainer.removeAllViews()
        binding.layoutBottomButtons.visibility = View.GONE
        binding.contentContainer.addView(textView(message, 18f, false, "#808080").apply {
            gravity = android.view.Gravity.CENTER
        })
    }

    // ====== Content Display ======

    private fun showCurrentWord() {
        val words = getActiveWordList()
        if (words.isEmpty()) return
        val word = words[getCurrentIndex()]
        binding.contentContainer.removeAllViews()
        when (currentStep) {
            1 -> showWordDisplay(word)
            2 -> showChoices(word)
            3 -> showSpellInput(word)
        }
        updateProgressUI()
    }

    private fun showCurrentReviewWord() {
        if (reviewWords.isEmpty()) return
        val word = reviewWords[getCurrentIndex()]
        binding.contentContainer.removeAllViews()
        when (currentStep) {
            1 -> showWordDisplay(word)
            2 -> showChoices(word)
            3 -> showSpellInput(word)
        }
        updateProgressUI()
    }

    private fun showCurrentWrongWord() {
        if (wrongWordDetails.isEmpty()) return
        val word = wrongWordDetails[wrongWordIndex.coerceAtMost(wrongWordDetails.size - 1)]
        binding.contentContainer.removeAllViews()
        when (currentStep) {
            1 -> showWordDisplay(word)
            2 -> showChoices(word)
            3 -> showSpellInput(word)
        }
        updateProgressUI()
    }

    private fun getActiveWordList(): List<Word> = when (currentMode) {
        Mode.LEARN -> currentGroupWords
        Mode.REVIEW -> reviewWords
        Mode.REVIEW_DIRECT -> reviewDirectWords
        Mode.WRONG -> wrongWordDetails
    }
    private fun getCurrentIndex(): Int = currentIndexInGroup

    private fun updateProgressUI() {
        val (current, total) = when (currentMode) {
            Mode.LEARN -> currentIndexInGroup + 1 to currentGroupWords.size
            Mode.REVIEW -> getCurrentIndex() + 1 to reviewWords.size
            Mode.REVIEW_DIRECT -> reviewDirectIndex + 1 to reviewDirectWords.size
            Mode.WRONG -> wrongWordIndex + 1 to wrongWordDetails.size
        }
        binding.tvProgress.text = "$current / $total"
        binding.tvStep.text = "步骤 $currentStep/3"
        val progress = (current * 100) / total
        binding.progressBar.progress = progress
    }

    // ====== Step 1: Word Display ======

    private fun showWordDisplay(word: Word) {
        val dp8 = dip(8)
        val dp12 = dip(12)
        val dp16 = dip(16)

        val card = verticalLayout(dip(16), true)
        card.setBackgroundColor(0xFFFFFFFF.toInt())

        card.addView(textView(word.word, 28f, true, "#000000").apply {
            setPadding(dp16, dp12, dp16, 0)
        })
        if (word.phonetic.isNotBlank()) {
            card.addView(textView(word.phonetic, 16f, false, "#808080").apply {
                setPadding(dp16, 4, dp16, 0)
            })
        }

        card.addView(textView("释义", 14f, true, "#E3000F").apply {
            setPadding(dp16, dp12, dp16, 4)
        })
        card.addView(textView(word.meaning, 16f, false, "#000000").apply {
            setPadding(dp16, 0, dp16, dp12)
        })

        card.setPadding(dp12, dp12, dp12, dp12)
        binding.contentContainer.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp8) })

        // Phrase
        if (word.phrase.isNotBlank()) {
            val phraseCard = verticalLayout(dip(16), true)
            phraseCard.setBackgroundColor(0xFFFFFFFF.toInt())
            phraseCard.addView(textView("短语", 14f, true, "#0039CB").apply {
                setPadding(dp16, dp12, dp16, 4)
            })
            phraseCard.addView(textView(word.phrase, 16f, false, "#000000").apply {
                setPadding(dp16, 0, dp16, 0)
            })
            if (word.phraseMeaning.isNotBlank()) {
                phraseCard.addView(textView(word.phraseMeaning, 14f, false, "#808080").apply {
                    setPadding(dp16, 4, dp16, dp12)
                })
            }
            phraseCard.setPadding(dp12, 0, dp12, dp12)
            binding.contentContainer.addView(phraseCard, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp8) })
        }

        // Example
        if (word.example.isNotBlank()) {
            val exampleCard = verticalLayout(dip(16), true)
            exampleCard.setBackgroundColor(0xFFFFFFFF.toInt())
            exampleCard.addView(textView("例句", 14f, true, "#0039CB").apply {
                setPadding(dp16, dp12, dp16, 4)
            })
            exampleCard.addView(textView(word.example, 15f, false, "#000000").apply {
                setPadding(dp16, 0, dp16, 0)
            })
            if (word.exampleMeaning.isNotBlank()) {
                exampleCard.addView(textView(word.exampleMeaning, 14f, false, "#808080").apply {
                    setPadding(dp16, 4, dp16, dp12)
                })
            }
            exampleCard.setPadding(dp12, 0, dp12, dp12)
            binding.contentContainer.addView(exampleCard)
        }

        // Next step button
        binding.contentContainer.addView(android.widget.Button(requireContext()).apply {
            text = "下一步"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFE3000F.toInt())
            setPadding(dp12, dp12, dp12, dp12)
            setOnClickListener { nextStep() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip(48)
        ).apply { topMargin = dp16 })

        playAudioSequence(word)
    }

    // ====== Step 2: Multiple Choice ======

    private fun showChoices(word: Word) {
        audioPlayer.release()
        isPlayingAudioSequence = false

        val dp8 = dip(8)
        val dp16 = dip(16)

        binding.contentContainer.addView(textView("选择正确的释义", 20f, true, "#000000").apply {
            setPadding(dp16, dp12(dp8), dp16, dp8)
        })
        binding.contentContainer.addView(textView(word.word, 18f, true, "#E3000F").apply {
            setPadding(dp16, 0, dp16, dp16)
        })

        choiceOptions = generateChoices(word)
        selectedChoiceIndex = -1

        for ((index, option) in choiceOptions.withIndex()) {
            binding.contentContainer.addView(android.widget.Button(requireContext()).apply {
                text = option.meaning
                textSize = 15f
                setTextColor(0xFF000000.toInt())
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(dp16, dp16, dp16, dp16)
                gravity = android.view.Gravity.CENTER or android.view.Gravity.START
                minLines = 2
                setOnClickListener { selectChoice(index) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp8) })
        }
    }

    private fun generateChoices(currentWord: Word): List<ChoiceOption> {
        val pool = allWords.filter { it.word != currentWord.word }.shuffled().take(3)
        val wrongOptions = pool.map { ChoiceOption(it.meaning, false) }
        val correctOption = ChoiceOption(currentWord.meaning, true)
        return (wrongOptions + correctOption).shuffled()
    }

    private fun selectChoice(index: Int) {
        if (selectedChoiceIndex >= 0) return
        selectedChoiceIndex = index
        val option = choiceOptions[index]
        val word = getActiveWord()[getCurrentIndexForWrong().coerceIn(0, getActiveWord().size - 1)]

        val choiceBtn = binding.contentContainer.getChildAt(index + 2) as? android.widget.Button
        if (choiceBtn != null) {
            if (option.correct) {
                choiceBtn.setBackgroundColor(0xFF4CAF50.toInt())
                choiceBtn.setTextColor(0xFFFFFFFF.toInt())
                Toast.makeText(context, "正确!", Toast.LENGTH_SHORT).show()
                sessionTotalCount++
                sessionCorrectCount++
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(800)
                    currentStep = 3
                    resetInputState()
                    showCurrentWord()
                }
            } else {
                choiceBtn.setBackgroundColor(0xFFF44336.toInt())
                choiceBtn.setTextColor(0xFFFFFFFF.toInt())
                Toast.makeText(context, "错误，再想想", Toast.LENGTH_SHORT).show()
                sessionTotalCount++
                sessionWrongCount++
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(1500)
                    choiceBtn.setBackgroundColor(0xFFFFFFFF.toInt())
                    choiceBtn.setTextColor(0xFF000000.toInt())
                    selectedChoiceIndex = -1
                }
                saveErrorNoWrong(word)
            }
        }
    }

    // ====== Step 3: Spelling ======

    private fun showSpellInput(word: Word) {
        audioPlayer.release()
        isPlayingAudioSequence = false

        val dp8 = dip(8)
        val dp16 = dip(16)

        binding.contentContainer.addView(textView("根据释义写出单词", 20f, true, "#000000").apply {
            setPadding(dp16, dp12(dp8), dp16, dp8)
        })
        binding.contentContainer.addView(textView(word.meaning, 18f, false, "#E3000F").apply {
            setPadding(dp16, 0, dp16, dp16)
        })

        val editText = EditText(requireContext()).apply {
            hint = "输入单词..."
            textSize = 18f
            setPadding(dp16, dp12(dp8), dp16, dp12(dp8))
            setBackgroundColor(0xFFFFFFFF.toInt())
            setMaxLines(1)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    binding.btnNext.isEnabled = s?.isNotBlank() == true
                }
            })
        }
        binding.contentContainer.addView(editText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp16) })

        binding.contentContainer.addView(android.widget.Button(requireContext()).apply {
            text = "提交"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFE3000F.toInt())
            setPadding(dp12(dp8), dp12(dp8), dp12(dp8), dp12(dp8))
            setOnClickListener {
                val input = editText.text.toString().trim()
                if (input.isEmpty()) return@setOnClickListener
                if (input.lowercase(Locale.getDefault()) == word.word.lowercase()) {
                    Toast.makeText(context, "正确!", Toast.LENGTH_SHORT).show()
                    markAsLearned(word)
                    incrementDailyGoal()
                    advanceWord()
                } else {
                    Toast.makeText(context, "错误，正确答案: ${word.word}", Toast.LENGTH_LONG).show()
                    editText.setText("")
                    saveErrorNoWrong(word)
                }
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip(48)
        ))

        binding.btnNext.isEnabled = false
    }

    private fun resetInputState() {
        selectedChoiceIndex = -1
        binding.btnNext.isEnabled = true
    }

    // ====== Navigation ======

    private fun nextStep() {
        if (currentStep < 3) {
            currentStep++
            resetInputState()
            showCurrentWord()
        }
    }

    private fun nextWord() {
        audioPlayer.release()
        isPlayingAudioSequence = false
        advanceWord()
    }

    private fun advanceWord() {
        when (currentMode) {
            Mode.LEARN -> {
                if (currentIndexInGroup < currentGroupWords.size - 1) {
                    currentIndexInGroup++
                    currentStep = 1
                    resetInputState()
                    showCurrentWord()
                } else {
                    markGroupCompleted()
                    showCompletion()
                }
            }
            Mode.REVIEW -> {
                if (getCurrentIndex() < reviewWords.size - 1) {
                    currentIndexInGroup++
                    currentStep = 1
                    resetInputState()
                    showCurrentReviewWord()
                } else {
                    showCompletion()
                }
            }
            Mode.REVIEW_DIRECT -> {
                if (reviewDirectIndex < reviewDirectWords.size - 1) {
                    reviewDirectIndex++
                    currentStep = 2
                    resetInputState()
                    showCurrentReviewDirectWord()
                } else {
                    showCompletion()
                }
            }
            Mode.WRONG -> {
                if (wrongWordIndex < wrongWordDetails.size - 1) {
                    wrongWordIndex++
                    currentStep = 1
                    resetInputState()
                    showCurrentWrongWord()
                } else {
                    showCompletion()
                }
            }
        }
    }

    private fun prevWord() {
        audioPlayer.release()
        isPlayingAudioSequence = false
        when (currentMode) {
            Mode.LEARN -> {
                if (currentIndexInGroup > 0) {
                    currentIndexInGroup--
                    currentStep = 1
                    resetInputState()
                    showCurrentWord()
                }
            }
            Mode.REVIEW -> {
                if (getCurrentIndex() > 0) {
                    currentIndexInGroup--
                    currentStep = 1
                    resetInputState()
                    showCurrentReviewWord()
                }
            }
            Mode.REVIEW_DIRECT -> {
                if (reviewDirectIndex > 0) {
                    reviewDirectIndex--
                    currentStep = 2
                    resetInputState()
                    showCurrentReviewDirectWord()
                }
            }
            Mode.WRONG -> {
                if (wrongWordIndex > 0) {
                    wrongWordIndex--
                    currentStep = 1
                    resetInputState()
                    showCurrentWrongWord()
                }
            }
        }
    }

    private fun markUnfamiliar() {
        val word = getActiveWord()[getCurrentIndexForWrong().coerceIn(0, getActiveWord().size - 1)]
        lifecycleScope.launch {
            studyRepo.addStarredWord(UserManager.userId, currentCorpusId, word.word)
        }
        Toast.makeText(context, "已加星标", Toast.LENGTH_SHORT).show()
    }

    private fun removeWrongWord() {
        val word = wrongWordDetails.getOrNull(wrongWordIndex.coerceAtMost(wrongWordDetails.size - 1)) ?: return
        lifecycleScope.launch {
            val uidCorpus = "${UserManager.userId}_$currentCorpusId"
            studyRepo.markWrongWordCorrected(UserManager.userId, currentCorpusId, word.word)
            wrongWordDetails = wrongWordDetails.filter { it.word != word.word }
            wrongWordList = wrongWordList.filter { it.word != word.word }
            if (wrongWordDetails.isEmpty()) {
                showEmptyState("暂无错题")
            } else {
                wrongWordIndex = wrongWordIndex.coerceAtMost(wrongWordDetails.size - 1)
                showCurrentWrongWord()
            }
        }
        Toast.makeText(context, "已移除", Toast.LENGTH_SHORT).show()
    }

    private fun showCompletion() {
        binding.contentContainer.removeAllViews()
        binding.layoutBottomButtons.visibility = View.GONE

        val dp8 = dip(8)
        val dp16 = dip(16)
        val accuracy = if (sessionTotalCount > 0) sessionCorrectCount * 100 / sessionTotalCount else 0
        val modeLabel = when (currentMode) {
            Mode.LEARN -> "学习完成!"
            Mode.REVIEW, Mode.REVIEW_DIRECT -> "复习完成!"
            Mode.WRONG -> "错题复习完成!"
        }

        // Title card
        binding.contentContainer.addView(textView(modeLabel, 28f, true, "#E3000F").apply {
            gravity = android.view.Gravity.CENTER
            setPadding(dp16, dp16, dp16, dp16)
        })

        // Word count
        binding.contentContainer.addView(textView("本次学习了 ${getActiveWordList().size} 个单词", 18f, false, "#808080").apply {
            gravity = android.view.Gravity.CENTER
            setPadding(dp16, 0, dp16, 0)
        })

        // Accuracy visualization
        if (sessionTotalCount > 0) {
            val dp4 = dip(4)
            val barLength = 20
            val correctLen = sessionCorrectCount * barLength / sessionTotalCount
            val wrongLen = barLength - sessionCorrectCount * barLength / sessionTotalCount
            val correctBar = "█".repeat(correctLen)
            val wrongBar = "░".repeat(wrongLen)

            binding.contentContainer.addView(textView("$correctBar$wrongBar", 16f, false, "#4CAF50").apply {
                gravity = android.view.Gravity.CENTER
                setPadding(dp16, 8, dp16, 4)
            })
            binding.contentContainer.addView(textView("正确率: $accuracy% ($sessionCorrectCount 对 $sessionWrongCount 错)", 16f, false, "#0039CB").apply {
                gravity = android.view.Gravity.CENTER
                setPadding(dp16, 0, dp16, 8)
            })
        } else {
            binding.contentContainer.addView(textView("未参与答题", 14f, false, "#CCCCCC").apply {
                gravity = android.view.Gravity.CENTER
                setPadding(dp16, 0, dp16, 8)
            })
        }

        // Show hard words (low accuracy in overall history)
        lifecycleScope.launch {
            val hardWords = getHardWordsForGroup()
            if (hardWords.isNotEmpty()) {
                binding.contentContainer.addView(textView("重点词汇: ${hardWords.joinToString(", ")}", 14f, false, "#F44336").apply {
                    gravity = android.view.Gravity.CENTER
                    setPadding(dp16, 8, dp16, 8)
                })
            }
        }

        // Next group / Quiz button
        val hasNextGroup = currentMode == Mode.LEARN &&
            currentGroupIndex + 1 < ceil(allWords.size.toDouble() / groupSize).toInt()

        val btnRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp16 }
        }

        val btnBack = android.widget.Button(requireContext()).apply {
            text = "返回首页"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF808080.toInt())
            setPadding(dp16, dp16, dp16, dp16)
            setOnClickListener { parentFragmentManager.popBackStack() }
        }

        // Guided mode: show "开始考试" button after learning a group
        if (isGuided && currentMode == Mode.LEARN) {
            val btnQuiz = android.widget.Button(requireContext()).apply {
                text = "开始考试"
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF0039CB.toInt())
                setPadding(dp16, dp16, dp16, dp16)
                setOnClickListener {
                    val quizFragment = com.learne.ui.quiz.QuizFragment.newInstance(
                        currentCorpusId, currentGroupIndex, planIndexArg
                    )
                    parentFragmentManager.beginTransaction()
                        .replace(com.learne.R.id.fragment_container, quizFragment)
                        .addToBackStack("challenge_map")
                        .commit()
                }
            }
            btnRow.addView(btnBack, LinearLayout.LayoutParams(0, dip(56)).apply { weight = 1f; marginEnd = dip(8) })
            btnRow.addView(btnQuiz, LinearLayout.LayoutParams(0, dip(56)).apply { weight = 1f })
        } else if (hasNextGroup) {
            val btnNextGroup = android.widget.Button(requireContext()).apply {
                text = "下一组 →"
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFFE3000F.toInt())
                setPadding(dp16, dp16, dp16, dp16)
                setOnClickListener {
                    selectGroup(currentGroupIndex + 1)
                }
            }
            btnRow.addView(btnBack, LinearLayout.LayoutParams(0, dip(56)).apply { weight = 1f; marginEnd = dip(8) })
            btnRow.addView(btnNextGroup, LinearLayout.LayoutParams(0, dip(56)).apply { weight = 1f })
        } else {
            btnRow.addView(btnBack, LinearLayout.LayoutParams(dip(200), dip(56)))
        }

        binding.contentContainer.addView(btnRow)
    }

    // ====== Audio ======

    private fun playAudioSequence(word: Word) {
        isPlayingAudioSequence = true
        audioQueue = audioTypes.map { corpusRepo.getAudioPath(currentCorpusId, word.word, it) }
        audioQueueIndex = 0
        playNextAudioInSequence()
    }

    private suspend fun getHardWordsForGroup(): List<String> {
        val db = com.learne.data.db.AppDatabase.getDatabase(requireContext())
        val uidCorpus = "${UserManager.userId}_$currentCorpusId"
        val start = currentGroupIndex * groupSize
        val end = (start + groupSize).coerceAtMost(allWords.size)
        val hardWords = mutableListOf<String>()
        for (i in start until end) {
            val word = allWords[i].word
            val progress = db.progressDao().getProgressById("${uidCorpus}_$word")
            if (progress != null && progress.wrongCount > 0 && progress.correctCount + progress.wrongCount > 0) {
                val accuracy = progress.correctCount * 100 / (progress.correctCount + progress.wrongCount)
                if (accuracy < 50) {
                    hardWords += word
                }
            }
        }
        return hardWords
    }

    private fun playNextAudioInSequence() {
        if (!isPlayingAudioSequence) return
        if (audioQueueIndex < audioQueue.size) {
            val path = audioQueue[audioQueueIndex]
            audioPlayer.play(path) {
                audioQueueIndex++
                playNextAudioInSequence()
            }
        } else {
            isPlayingAudioSequence = false
        }
    }

    // ====== Progress ======

    private fun markAsLearned(word: Word) {
        lifecycleScope.launch {
            progressRepo.recordLearned(UserManager.userId, currentCorpusId, word.word)
        }
        if (currentMode == Mode.REVIEW) {
            lifecycleScope.launch {
                progressRepo.updateReviewProgress(UserManager.userId, currentCorpusId, word.word, true)
            }
        }
    }

    private fun saveErrorNoWrong(word: Word) {
        // Learning phase error: don't add to wrong book, just track for progress
    }

    private fun saveError(word: Word, type: String, userAnswer: String, correctAnswer: String) {
        lifecycleScope.launch {
            studyRepo.addWrongWord(UserManager.userId, currentCorpusId, word.word, type)
        }
        if (currentMode == Mode.REVIEW) {
            lifecycleScope.launch {
                progressRepo.updateReviewProgress(UserManager.userId, currentCorpusId, word.word, false)
            }
        }
    }

    private fun markGroupCompleted() {
        // Only save to plan-level (avoid cross-plan contamination via corpus-level)
        if (planIndexArg >= 0) {
            UserPreferencesRepository.markGroupCompletedForPlan(planIndexArg, currentGroupIndex)
        } else {
            // Fallback to corpus-level only when no plan context
            UserPreferencesRepository.markGroupCompleted(currentCorpusId, currentGroupIndex)
        }

        // Notify parent ChallengeMapFragment if applicable
        parentFragmentManager.fragments.find { it is com.learne.ui.challenge.ChallengeMapFragment }?.let {
            (it as com.learne.ui.challenge.ChallengeMapFragment).onGroupCompleted(currentGroupIndex)
        }

        // Refresh spinner labels to show completion checkmarks
        setupGroupSpinner()
    }

    private fun incrementDailyGoal() {
        lifecycleScope.launch {
            studyRepo.updateProgress(UserManager.userId, currentCorpusId)
        }
    }

    // ====== View Helpers ======

    private fun getActiveWord(): List<Word> = when (currentMode) {
        Mode.LEARN -> currentGroupWords
        Mode.REVIEW -> reviewWords
        Mode.REVIEW_DIRECT -> reviewDirectWords
        Mode.WRONG -> wrongWordDetails
    }

    private fun getCurrentIndexForWrong(): Int = when (currentMode) {
        Mode.LEARN -> currentIndexInGroup
        Mode.REVIEW -> getCurrentIndex()
        Mode.REVIEW_DIRECT -> reviewDirectIndex
        Mode.WRONG -> wrongWordIndex
    }

    private fun verticalLayout(padding: Int = 0, hasElevation: Boolean = false): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            if (padding > 0) setPadding(padding, padding, padding, padding)
            if (hasElevation) elevation = 4f
        }
    }

    private fun textView(text: String, size: Float, bold: Boolean, color: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            this.textSize = size
            setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            setTextColor(android.graphics.Color.parseColor(color))
        }
    }

    private fun dip(dp: Int): Int {
        val scale = requireContext().resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun dp12(dp: Int) = dip(dp)

    override fun onDestroyView() {
        audioPlayer.release()
        // Save progress when leaving
        if (planIndexArg >= 0 && currentMode == Mode.LEARN) {
            UserPreferencesRepository.savePlanProgress(planIndexArg, currentGroupIndex, currentIndexInGroup)
        }
        super.onDestroyView()
        _binding = null
    }
}
