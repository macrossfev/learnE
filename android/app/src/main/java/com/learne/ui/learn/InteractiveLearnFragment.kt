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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.R
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
import kotlin.Triple
import kotlin.math.ceil

class InteractiveLearnFragment : Fragment() {

    companion object {
        fun newInstance(corpusId: String = "catti", mode: Mode = Mode.LEARN, planIndex: Int = -1, groupIndex: Int = -1, isGuided: Boolean = false): InteractiveLearnFragment {
            return InteractiveLearnFragment().apply {
                arguments = Bundle().apply {
                    putString("corpusId", corpusId)
                    putString("mode", mode.name)
                    putInt("planIndex", planIndex)
                    putInt("groupIndex", groupIndex)
                    putBoolean("isGuided", isGuided)
                }
            }
        }
    }

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
    private var autoNextJob: kotlinx.coroutines.Job? = null
    private var currentGroupIndex = 0
    private var currentIndexInGroup = 0
    private var currentStep = 1 // 1=display, 2=choice, 3=spell
    private var restoredStep = 1 // step restored from saved position
    private var isSpinnerReady = false

    // Mode: learn / review / review_direct / wrong
    enum class Mode { LEARN, REVIEW, REVIEW_DIRECT, WRONG, STARRED }
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

    // Starred words
    private var starredWordList: List<com.learne.data.model.StarredWord> = emptyList()
    private var starredWordDetails: List<Word> = emptyList()
    private var starredWordIndex = 0

    private var currentCorpusId: String = "catti"
    private var groupSize = 30

    // Dashboard state
    private var selectedDashboardGroupIndex = 0

    // Review mode: -1 = all groups, >= 0 = specific group
    private var reviewGroupIndex: Int = -1

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

        // Read args from Bundle (survives Fragment recreation)
        currentCorpusId = arguments?.getString("corpusId") ?: "catti"
        currentMode = try { Mode.valueOf(arguments?.getString("mode") ?: "LEARN") } catch (_: Exception) { Mode.LEARN }
        planIndexArg = arguments?.getInt("planIndex", -1) ?: -1
        groupIndexArg = arguments?.getInt("groupIndex", -1) ?: -1
        isGuided = arguments?.getBoolean("isGuided", false) ?: false

        corpusRepo = CorpusRepository(requireContext())
        progressRepo = ProgressRepository(requireContext())
        studyRepo = StudyRepository(requireContext())

        groupSize = UserPreferencesRepository.planGroupSize

        binding.btnBack.setOnClickListener { confirmExit() }
        binding.btnPrev.setOnClickListener { prevWord() }
        binding.btnNext.setOnClickListener {
            if (currentMode == Mode.WRONG) {
                removeWrongWord()
            } else if (currentMode == Mode.STARRED) {
                removeStarredWord()
            } else {
                nextWord()
            }
        }
        binding.btnUnfamiliar.setOnClickListener { markUnfamiliar() }
        binding.btnStartLearn.setOnClickListener { startLearning(Mode.LEARN) }
        binding.btnStartReview.setOnClickListener { startLearning(Mode.REVIEW) }

        // Intercept system back button during learning
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    confirmExit()
                }
            }
        )

        loadWords()
    }

    private fun showGroupProgress() {
        lifecycleScope.launch {
            val completedGroups = if (planIndexArg >= 0) {
                UserPreferencesRepository.getPlanCompletedGroups(planIndexArg)
            } else {
                UserPreferencesRepository.getCompletedGroups(currentCorpusId)
            }
            val totalGroups = ceil(allWords.size.toDouble() / groupSize).toInt()

            val dueForReview = studyRepo.getWordsDueForReview(currentCorpusId)
            val dueGroups = dueForReview.map { due ->
                val idx = allWords.indexOfFirst { it.word == due.word } / groupSize
                idx.coerceIn(0, totalGroups - 1)
            }.toSet().size

            // Show dialog
            val dp8 = dip(8)
            val dp16 = dip(16)
            val dialogView = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp16, dp16, dp16, dp16)

                addView(textView("总组数: $totalGroups", 15f, false, "#FFFFFF").apply {
                    setPadding(0, dp8, 0, 0)
                })
                addView(textView("已学: ${completedGroups.size} 组", 15f, false, "#4CAF50").apply {
                    setPadding(0, dp8, 0, 0)
                })
                addView(textView("未学: ${totalGroups - completedGroups.size} 组", 15f, false, "#808080").apply {
                    setPadding(0, dp8, 0, 0)
                })
                addView(textView("待复习: ${dueForReview.size} 词 ($dueGroups 组)", 15f, false, "#E3000F").apply {
                    setPadding(0, dp8, 0, 0)
                })

                if (completedGroups.isNotEmpty()) {
                    val sortedGroups = completedGroups.sorted()
                    addView(textView("已完成组: ${sortedGroups.joinToString(", ") { "${it + 1}" }}", 13f, false, "#0039CB").apply {
                        setPadding(0, dp16, 0, 0)
                    })
                }
            }

            android.app.AlertDialog.Builder(requireContext())
                .setTitle("学习进度")
                .setView(dialogView)
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun updateProgressTopBar() {
        lifecycleScope.launch {
            val completedGroups = if (planIndexArg >= 0) {
                UserPreferencesRepository.getPlanCompletedGroups(planIndexArg)
            } else {
                UserPreferencesRepository.getCompletedGroups(currentCorpusId)
            }
            val totalGroups = ceil(allWords.size.toDouble() / groupSize).toInt()
            binding.tvProgressTop.text = "$totalGroups 组 ${completedGroups.size}/${totalGroups}"
        }
    }

    private fun resetSessionStats() {
        sessionCorrectCount = 0
        sessionWrongCount = 0
        sessionTotalCount = 0
    }

    private fun loadWords() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutMain.visibility = View.GONE
        binding.layoutGroupMap.visibility = View.GONE

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

                if (groupIndexArg >= 0) {
                    currentGroupIndex = groupIndexArg
                } else if (planIndexArg >= 0) {
                    val plan = UserPreferencesRepository.loadPlan(planIndexArg)
                    currentGroupIndex = plan?.currentGroupIndex ?: 0
                    currentIndexInGroup = plan?.currentWordIndex ?: 0
                } else {
                    // Restore last learn position for non-plan mode
                    val (savedGroup, savedWord, savedStep) = UserPreferencesRepository.getLearnPosition(currentCorpusId)
                    currentGroupIndex = savedGroup
                    currentIndexInGroup = savedWord
                    restoredStep = savedStep
                }

                setupGroupSpinner()

                // When guided from challenge map, skip group selection and start learning directly
                if (isGuided) {
                    binding.layoutLoading.visibility = View.GONE
                    startLearning(currentMode)
                } else {
                    updateProgressTopBar()
                    buildGroupMap()
                }
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
                if (currentMode == Mode.REVIEW && isSpinnerReady) {
                    reviewGroupIndex = position
                    loadReviewWordsForGroup(position)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        binding.spGroup.adapter = adapter
        // Set position to currentGroupIndex AFTER listener is set, then mark ready
        binding.spGroup.setSelection(currentGroupIndex)
        isSpinnerReady = true
    }

    private fun buildGroupMap() {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutGroupMap.visibility = View.VISIBLE
        binding.layoutMain.visibility = View.GONE

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
            val dueForReview = studyRepo.getWordsDueForReview(currentCorpusId)

            // Title
            binding.tvMapTitle.text = "学习模式"
            binding.tvMapProgress.text = "${completedGroups.size} / $totalGroups"
            binding.progressBar.progress = if (totalGroups > 0) completedGroups.size * 100 / totalGroups else 0

            // Stats row
            binding.layoutStatsRow.removeAllViews()
            listOf(
                Triple("已学", "$learned", "#0039CB"),
                Triple("已掌握", "$mastered", "#4CAF50"),
                Triple("待复习", "${dueForReview.size}", "#E3000F"),
                Triple("错题", "$wrongs", "#F44336")
            ).forEach { (label, value, color) ->
                binding.layoutStatsRow.addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
                    addView(textView(value, 18f, true, color).apply { gravity = android.view.Gravity.CENTER })
                    addView(textView(label, 10f, false, "#808080").apply { gravity = android.view.Gravity.CENTER })
                })
            }

            // Group grid
            val dp4 = dip(4)
            val dp6 = dip(6)
            val colsPerRow = 5

            binding.layoutGroupGrid.removeAllViews()

            // Per-group review count
            val duePerGroup = IntArray(totalGroups)
            for (word in dueForReview) {
                val idx = allWords.indexOfFirst { it.word == word.word }
                if (idx >= 0) {
                    val groupIdx = idx / groupSize
                    if (groupIdx < totalGroups) duePerGroup[groupIdx]++
                }
            }

            var row = 0
            while (row < totalGroups) {
                val gridRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp6 }
                }
                for (col in 0 until colsPerRow) {
                    val g = row + col
                    if (g >= totalGroups) break
                    val isCompleted = completedGroups.contains(g)
                    val start = g * groupSize + 1
                    val end = ((g + 1) * groupSize).coerceAtMost(allWords.size)
                    val dueCount = duePerGroup[g]

                    // Card
                    val card = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, dip(56)).apply { weight = 1f; marginEnd = dp6 }
                        setPadding(dp4, dp4, dp4, dp4)
                        val bg = android.graphics.drawable.GradientDrawable().apply {
                            if (isCompleted) {
                                setColor(ContextCompat.getColor(requireContext(), R.color.status_success))
                            } else if (dueCount > 0) {
                                setColor(0xFFFFF3E0.toInt())
                                setStroke(dip(2), 0xFFFF9800.toInt())
                            } else {
                                setColor(0xFFF5F5F5.toInt())
                            }
                            cornerRadius = dip(8).toFloat()
                        }
                        background = bg
                        elevation = if (isCompleted) 3f else 1f

                        setOnClickListener {
                            selectedDashboardGroupIndex = g
                            startLearning(Mode.LEARN)
                        }
                    }

                    card.addView(textView("${g + 1}", 16f, true, if (isCompleted) "#FFFFFF" else if (dueCount > 0) "#E65100" else "#FFFFFF").apply {
                        gravity = android.view.Gravity.CENTER
                    })
                    card.addView(textView("$start-$end", 9f, false, if (isCompleted) "#E8F5E9" else if (dueCount > 0) "#BF360C" else "#C0C0C0").apply {
                        gravity = android.view.Gravity.CENTER
                    })
                    if (isCompleted) {
                        card.addView(textView("✓", 11f, true, "#C8E6C9").apply {
                            gravity = android.view.Gravity.CENTER
                        })
                    } else if (dueCount > 0) {
                        card.addView(textView("$dueCount", 10f, true, "#E3000F").apply {
                            gravity = android.view.Gravity.CENTER
                        })
                    }

                    gridRow.addView(card)
                }
                binding.layoutGroupGrid.addView(gridRow)
                row += colsPerRow
            }

            // Achievement section
            buildAchievements(binding.layoutGroupGrid, completedGroups.size, totalGroups)
        }
    }

    private fun buildAchievements(container: LinearLayout, completedGroups: Int, totalGroups: Int) {
        val dp8 = dip(8)
        val dp12 = dip(12)
        val dp16 = dip(16)

        // Milestone tracking
        val milestones10 = completedGroups / 10  // How many 10-group milestones achieved
        val milestones50 = completedGroups / 50  // How many 50-group milestones
        val next10 = (milestones10 + 1) * 10
        val next50 = (milestones50 + 1) * 50

        val achievementCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ContextCompat.getColor(requireContext(), R.color.white))
                cornerRadius = dip(12).toFloat()
            }
            elevation = 2f
            setPadding(dp16, dp16, dp16, dp16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp12 }
        }

        // Title
        achievementCard.addView(textView("🏆 成就", 16f, true, "#FFFFFF").apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dp8)
        })

        // 10-group milestones
        val maxSmallMilestones = totalGroups / 10
        if (maxSmallMilestones > 0) {
            achievementCard.addView(textView("小组目标 (每10组)", 12f, false, "#808080").apply {
                setPadding(0, 0, 0, dp8)
            })
            val smallRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp8)
            }
            for (i in 0 until maxSmallMilestones) {
                val achieved = completedGroups >= (i + 1) * 10
                val label = "${(i + 1) * 10}"
                smallRow.addView(TextView(requireContext()).apply {
                    text = if (achieved) "★" else "☆"
                    textSize = 18f
                    setTextColor(if (achieved) ContextCompat.getColor(requireContext(), R.color.mecha_gold) else 0xFFCCCCCC.toInt())
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
                })
            }
            achievementCard.addView(smallRow)
        }

        // 50-group milestone
        val maxBigMilestones = totalGroups / 50
        if (maxBigMilestones > 0) {
            achievementCard.addView(textView("大组目标 (每50组)", 12f, false, "#808080").apply {
                setPadding(0, 0, 0, dp8)
            })
            val bigRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp8)
            }
            for (i in 0 until maxBigMilestones) {
                val achieved = completedGroups >= (i + 1) * 50
                val label = "${(i + 1) * 50}"
                bigRow.addView(TextView(requireContext()).apply {
                    text = if (achieved) "🌟" else "☆"
                    textSize = 18f
                    setTextColor(if (achieved) 0xFFFF6B35.toInt() else 0xFFCCCCCC.toInt())
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
                })
            }
            achievementCard.addView(bigRow)
        }

        // Progress to next milestone
        val progressText = when {
            milestones50 > 0 -> "已达成 $milestones50 个大目标 · $milestones10 个小目标"
            milestones10 > 0 -> "已达成 $milestones10 个小目标"
            else -> "完成 $next10 组解锁第一个小目标"
        }
        achievementCard.addView(textView(progressText, 12f, false, "#808080").apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp8, 0, 0)
        })

        // Progress bar to next milestone
        val nextTarget = if (next10 <= totalGroups) next10 else totalGroups
        val currentInCycle = completedGroups % 10
        val progressPct = if (nextTarget > 0) (currentInCycle * 100) / 10 else 0
        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = progressPct
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dip(8)
            ).apply { topMargin = dp8 }
            progressDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFE0E0E0.toInt())
            }
        }
        achievementCard.addView(progressBar)

        container.addView(achievementCard)
    }

    private fun startLearning(mode: Mode) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutGroupMap.visibility = View.GONE
        binding.layoutMain.visibility = View.VISIBLE
        // Only use dashboard selection when not guided
        if (!isGuided) {
            currentGroupIndex = selectedDashboardGroupIndex
        }

        when (mode) {
            Mode.LEARN -> {
                binding.spGroup.visibility = View.VISIBLE
                binding.btnUnfamiliar.visibility = View.VISIBLE
                binding.btnNext.text = "已掌握"
                binding.spGroup.setSelection(currentGroupIndex)
                selectGroup(currentGroupIndex)
            }
            Mode.REVIEW -> {
                binding.spGroup.visibility = View.VISIBLE
                binding.btnUnfamiliar.visibility = View.VISIBLE
                binding.btnNext.text = "已掌握"
                binding.spGroup.setSelection(currentGroupIndex)
                loadReviewWordsForGroup(currentGroupIndex)
            }
            else -> {}
        }
    }

    private fun switchMode(mode: Mode) {
        currentMode = mode
        audioPlayer.release()
        isPlayingAudioSequence = false
        resetSessionStats()

        when (mode) {
            Mode.LEARN -> {
                binding.spGroup.visibility = if (isGuided) View.GONE else View.VISIBLE
                binding.btnUnfamiliar.visibility = View.VISIBLE
                binding.btnNext.text = "已掌握"
                binding.layoutGroupMap.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                currentGroupIndex = selectedDashboardGroupIndex
                selectGroup(currentGroupIndex)
            }
            Mode.REVIEW -> {
                binding.spGroup.visibility = View.VISIBLE
                binding.btnUnfamiliar.visibility = View.VISIBLE
                binding.btnNext.text = "已掌握"
                binding.layoutGroupMap.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                currentGroupIndex = selectedDashboardGroupIndex
                binding.spGroup.setSelection(currentGroupIndex)
                loadReviewWordsForGroup(currentGroupIndex)
            }
            Mode.REVIEW_DIRECT -> {
                binding.spGroup.visibility = View.GONE
                binding.btnUnfamiliar.visibility = View.GONE
                binding.btnNext.text = "下一词"
                binding.layoutGroupMap.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                loadReviewDirectWords()
            }
            Mode.WRONG -> {
                binding.spGroup.visibility = View.GONE
                binding.btnUnfamiliar.visibility = View.GONE
                binding.btnNext.text = "移除错题"
                binding.layoutGroupMap.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                loadWrongWords()
            }
            Mode.STARRED -> {
                binding.spGroup.visibility = View.GONE
                binding.btnUnfamiliar.visibility = View.VISIBLE
                binding.btnNext.text = "移除星标"
                binding.layoutGroupMap.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                loadStarredWords()
            }
        }
    }

    // ====== LEARN MODE ======

    private fun selectGroup(groupIndex: Int, startWordIndex: Int = 0) {
        autoNextJob?.cancel()
        currentGroupIndex = groupIndex
        val start = groupIndex * groupSize
        val end = (start + groupSize).coerceAtMost(allWords.size)
        currentGroupWords = allWords.subList(start, end)
        currentIndexInGroup = startWordIndex.coerceAtMost(currentGroupWords.size - 1)
        currentStep = restoredStep.coerceIn(1, 3)
        restoredStep = 1 // reset after use
        resetInputState()
        binding.layoutLoading.visibility = View.GONE
        binding.layoutMain.visibility = View.VISIBLE
        showCurrentWord()
    }

    // ====== REVIEW MODE ======

    private fun loadReviewWordsForGroup(groupIndex: Int) {
        currentGroupIndex = groupIndex
        lifecycleScope.launch {
            try {
                val start = groupIndex * groupSize
                val end = (start + groupSize).coerceAtMost(allWords.size)
                val groupWords = allWords.subList(start, end)

                val db = com.learne.data.db.AppDatabase.getDatabase(requireContext())
                val uidCorpus = "${UserManager.userId}_$currentCorpusId"
                val progressList = studyRepo.getWordsDueForReview(currentCorpusId)
                val dueWords = progressList.map { it.word }.toSet()

                reviewWords = groupWords.filter { it.word in dueWords }
                if (reviewWords.isEmpty()) {
                    showEmptyState("该组暂无待复习的单词")
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

        binding.contentContainer.addView(textView("选择正确的释义", 20f, true, "#FFFFFF").apply {
            setPadding(dp16, dip(dp8), dp16, dp8)
        })

        choiceOptions = generateChoices(word)
        selectedChoiceIndex = -1

        for ((index, option) in choiceOptions.withIndex()) {
            binding.contentContainer.addView(android.widget.Button(requireContext()).apply {
                text = option.meaning
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
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
                choiceBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_success))
                choiceBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                sessionTotalCount++
                sessionCorrectCount++
            } else {
                choiceBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_error))
                choiceBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
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

    // ====== STARRED MODE ======

    private fun loadStarredWords() {
        lifecycleScope.launch {
            val starredWords = studyRepo.getStarredWords(UserManager.userId, currentCorpusId).first()
            starredWordList = starredWords
            if (starredWords.isEmpty()) {
                showEmptyState("暂无星标单词")
            } else {
                starredWordIndex = 0
                // Load word details
                starredWordDetails = allWords.filter { w -> starredWords.any { sw -> sw.word == w.word } }
                binding.layoutLoading.visibility = View.GONE
                binding.layoutMain.visibility = View.VISIBLE
                showCurrentStarredWord()
            }
        }
    }

    private fun showCurrentStarredWord() {
        if (starredWordDetails.isEmpty()) return
        val word = starredWordDetails[starredWordIndex.coerceAtMost(starredWordDetails.size - 1)]
        binding.contentContainer.removeAllViews()
        when (currentStep) {
            1 -> showWordDisplay(word)
            2 -> showChoices(word)
            3 -> showSpellInput(word)
        }
        updateProgressUI()
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
        Mode.STARRED -> starredWordDetails
    }
    private fun getCurrentIndex(): Int = currentIndexInGroup

    private fun updateProgressUI() {
        val (current, total) = when (currentMode) {
            Mode.LEARN -> currentIndexInGroup + 1 to currentGroupWords.size
            Mode.REVIEW -> getCurrentIndex() + 1 to reviewWords.size
            Mode.REVIEW_DIRECT -> reviewDirectIndex + 1 to reviewDirectWords.size
            Mode.WRONG -> wrongWordIndex + 1 to wrongWordDetails.size
            Mode.STARRED -> starredWordIndex + 1 to starredWordDetails.size
        }
        binding.tvProgress.text = "$current / $total"
        binding.tvStep.text = "步骤 $currentStep/3"
        val progress = (current * 100) / total
        binding.progressBarLearn.progress = progress
    }

    // ====== Step 1: Word Display ======

    // ====== Exit Confirmation ======

    private fun confirmExit() {
        // Only show confirmation when actively learning (layoutMain visible)
        if (binding.layoutMain.visibility != View.VISIBLE) {
            parentFragmentManager.popBackStack()
            return
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("确认退出")
            .setMessage("学习进度将不会保存，确定退出吗？")
            .setPositiveButton("退出") { _, _ -> parentFragmentManager.popBackStack() }
            .setNegativeButton("继续学习", null)
            .show()
    }

    private fun showWordDisplay(word: Word) {
        val dp8 = dip(8)
        val dp12 = dip(12)
        val dp16 = dip(16)

        val card = verticalLayout(dip(16), true)
        card.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))

        card.addView(textView(word.word, 28f, true, "#FFFFFF").apply {
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
        card.addView(textView(word.meaning, 16f, false, "#FFFFFF").apply {
            setPadding(dp16, 0, dp16, dp12)
        })

        card.setPadding(dp12, dp12, dp12, dp12)
        binding.contentContainer.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp8) })

        // Phrase
        if (word.phrase.isNotBlank()) {
            val phraseCard = verticalLayout(dip(16), true)
            phraseCard.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            phraseCard.addView(textView("短语", 14f, true, "#0039CB").apply {
                setPadding(dp16, dp12, dp16, 4)
            })
            phraseCard.addView(textView(word.phrase, 16f, false, "#FFFFFF").apply {
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
            exampleCard.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            exampleCard.addView(textView("例句", 14f, true, "#0039CB").apply {
                setPadding(dp16, dp12, dp16, 4)
            })
            exampleCard.addView(textView(word.example, 15f, false, "#FFFFFF").apply {
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
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_error))
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

        binding.contentContainer.addView(textView("选择正确的释义", 20f, true, "#FFFFFF").apply {
            setPadding(dp16, dip(dp8), dp16, dp8)
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
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
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
                choiceBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_success))
                choiceBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
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
                choiceBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_error))
                choiceBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                Toast.makeText(context, "错误，再想想", Toast.LENGTH_SHORT).show()
                sessionTotalCount++
                sessionWrongCount++
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(1500)
                    choiceBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
                    choiceBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
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

        binding.contentContainer.addView(textView("根据释义写出单词", 20f, true, "#FFFFFF").apply {
            setPadding(dp16, dip(dp8), dp16, dp8)
        })
        binding.contentContainer.addView(textView(word.meaning, 18f, false, "#E3000F").apply {
            setPadding(dp16, 0, dp16, dp16)
        })

        val editText = EditText(requireContext()).apply {
            hint = "输入单词..."
            textSize = 18f
            setPadding(dp16, dip(dp8), dp16, dip(dp8))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            setMaxLines(1)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    val input = text.toString().trim()
                    if (input.isNotEmpty()) {
                        val submitBtn = binding.contentContainer.findViewWithTag<android.widget.Button>("btn_submit_spell")
                        submitBtn?.performClick()
                    }
                    true
                } else false
            }
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
            tag = "btn_submit_spell"
            text = "提交"
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_error))
            setPadding(dip(dp8), dip(dp8), dip(dp8), dip(dp8))
            setOnClickListener {
                val input = editText.text.toString().trim()
                if (input.isEmpty()) return@setOnClickListener
                if (isSpellingMatch(input, word.word)) {
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

        // Skip button
        binding.contentContainer.addView(android.widget.Button(requireContext()).apply {
            text = "跳过"
            textSize = 14f
            setTextColor(0xFF808080.toInt())
            setBackgroundColor(0xFFF5F5F5.toInt())
            setPadding(dip(dp8), dip(dp8), dip(dp8), dip(dp8))
            setOnClickListener {
                Toast.makeText(context, "正确答案: ${word.word}", Toast.LENGTH_LONG).show()
                saveErrorNoWrong(word)
                advanceWord()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip(40)
        ).apply { topMargin = dp8 })

        binding.btnNext.isEnabled = false
    }

    // ====== Spelling Variant Matching ======

    private fun isSpellingMatch(input: String, answer: String): Boolean {
        val inputLower = input.lowercase(Locale.getDefault())
        val answerLower = answer.lowercase(Locale.getDefault())
        if (inputLower == answerLower) return true
        // Check common spelling variants
        val variants = getSpellingVariants(answerLower)
        return inputLower in variants
    }

    private fun getSpellingVariants(word: String): Set<String> {
        val variants = mutableSetOf(word)
        // -ing form: running -> run, swimming -> swim
        if (word.endsWith("ing") && word.length > 4) {
            val stem = word.dropLast(3)
            if (stem.length >= 3) {
                variants.add(stem.dropLast(1))         // running -> run (double consonant)
                variants.add(stem + "e")               // making -> make
            }
        }
        // -ed form: learned -> learn, stopped -> stop
        if (word.endsWith("ed") && word.length > 3) {
            val stem = word.dropLast(2)
            if (stem.length >= 3) {
                variants.add(stem)                     // learned -> learn
                variants.add(stem.dropLast(1))         // stopped -> stop (double consonant)
            }
            // -ied -> -y: carried -> carry
            if (word.endsWith("ied") && word.length > 4) {
                variants.add(word.dropLast(3) + "y")
            }
        }
        // -s/-es/-ies: runs -> run, boxes -> box, studies -> study
        if (word.endsWith("ies") && word.length > 4) {
            variants.add(word.dropLast(3) + "y")
        } else if (word.endsWith("es") && word.length > 3) {
            variants.add(word.dropLast(2))              // boxes -> box
        } else if (word.endsWith("s") && !word.endsWith("ss") && word.length > 3) {
            variants.add(word.dropLast(1))              // runs -> run
        }
        // -er/-est: faster -> fast
        if (word.endsWith("er") && word.length > 4) {
            variants.add(word.dropLast(2))
        }
        if (word.endsWith("est") && word.length > 5) {
            variants.add(word.dropLast(3))
        }
        // -ly: quickly -> quick
        if (word.endsWith("ly") && word.length > 4) {
            variants.add(word.dropLast(2))
        }
        // -ful: careful -> care
        if (word.endsWith("ful") && word.length > 5) {
            variants.add(word.dropLast(3))
        }
        // -ness: happiness -> happy
        if (word.endsWith("ness") && word.length > 6) {
            val stem = word.dropLast(4)
            variants.add(stem + "y")
        }
        // -ment: development -> develop
        if (word.endsWith("ment") && word.length > 6) {
            variants.add(word.dropLast(4))
        }
        // -able/-ible: readable -> read
        if (word.endsWith("able") && word.length > 6) variants.add(word.dropLast(4))
        if (word.endsWith("ible") && word.length > 6) variants.add(word.dropLast(4))
        // British/American
        if (word.contains("our")) variants.add(word.replace("our", "or"))
        if (word.contains("or") && word.length > 4) variants.add(word.replaceFirst("or", "our"))
        if (word.endsWith("ise") && word.length > 4) variants.add(word.dropLast(3) + "ize")
        if (word.endsWith("ize") && word.length > 4) variants.add(word.dropLast(3) + "ise")
        if (word.endsWith("yse") && word.length > 4) variants.add(word.dropLast(3) + "yze")
        if (word.endsWith("yze") && word.length > 4) variants.add(word.dropLast(3) + "yse")

        // Remove original word and filter too-short variants
        return variants.filter { it.length >= 2 && it != word }.toSet() + setOf(word)
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
            Mode.STARRED -> {
                if (starredWordIndex < starredWordDetails.size - 1) {
                    starredWordIndex++
                    currentStep = 1
                    resetInputState()
                    showCurrentStarredWord()
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
            Mode.STARRED -> {
                if (starredWordIndex > 0) {
                    starredWordIndex--
                    currentStep = 1
                    resetInputState()
                    showCurrentStarredWord()
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

    private fun removeStarredWord() {
        val word = starredWordDetails.getOrNull(starredWordIndex.coerceAtMost(starredWordDetails.size - 1)) ?: return
        lifecycleScope.launch {
            studyRepo.removeStarredWord(UserManager.userId, currentCorpusId, word.word)
            starredWordList = starredWordList.filter { it.word != word.word }
            starredWordDetails = starredWordDetails.filter { it.word != word.word }
            if (starredWordDetails.isEmpty()) {
                showEmptyState("暂无星标单词")
            } else {
                starredWordIndex = starredWordIndex.coerceAtMost(starredWordDetails.size - 1)
                showCurrentStarredWord()
            }
        }
        Toast.makeText(context, "已移除星标", Toast.LENGTH_SHORT).show()
    }

    private fun showCompletion() {
        binding.contentContainer.removeAllViews()
        binding.layoutBottomButtons.visibility = View.GONE

        val dp8 = dip(8)
        val dp16 = dip(16)
        val accuracy = if (sessionTotalCount > 0) sessionCorrectCount * 100 / sessionTotalCount else 0
        val modeLabel = when (currentMode) {
            Mode.LEARN -> if (isGuided) "学习完成!" else "本组学习完成"
            Mode.REVIEW, Mode.REVIEW_DIRECT -> "复习完成!"
            Mode.WRONG -> "错题复习完成!"
            Mode.STARRED -> "星标单词复习完成!"
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

        // Listen-read consolidate button (LEARN mode only)
        if (currentMode == Mode.LEARN) {
            binding.contentContainer.addView(android.widget.Button(requireContext()).apply {
                text = "听读巩固"
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                setBackgroundColor(0xFF0039CB.toInt())
                setPadding(dp16, dp16, dp16, dp16)
                setOnClickListener {
                    val listenFragment = com.learne.ui.listen.ListenReadFragment.newInstance(currentCorpusId, planIndexArg, currentGroupIndex)
                    parentFragmentManager.beginTransaction()
                        .replace(com.learne.R.id.fragment_container, listenFragment)
                        .addToBackStack("learn")
                        .commit()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dip(44)
            ).apply { topMargin = dp8 })
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
            text = "返回"
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            setBackgroundColor(0xFF808080.toInt())
            setPadding(dp16, dp16, dp16, dp16)
            setOnClickListener {
                // Guided mode: mark group completed (learning through ChallengeMap)
                // Non-guided mode: only save progress position, group completion requires exam pass
                if (isGuided) {
                    markGroupCompleted()
                }
                parentFragmentManager.popBackStack()
            }
        }

        // Guided mode: always show exam + continue learning after completing a group
        if (isGuided && currentMode == Mode.LEARN) {
            val btnQuiz = android.widget.Button(requireContext()).apply {
                text = "开始考试"
                textSize = 16f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
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
            val btnContinue = android.widget.Button(requireContext()).apply {
                text = "继续学习"
                textSize = 16f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_success))
                setPadding(dp16, dp16, dp16, dp16)
                setOnClickListener {
                    selectGroup(currentGroupIndex)
                }
            }
            btnRow.addView(btnQuiz, LinearLayout.LayoutParams(0, dip(48)).apply { weight = 1f; marginEnd = dip(4) })
            btnRow.addView(btnContinue, LinearLayout.LayoutParams(0, dip(48)).apply { weight = 1f; marginEnd = dip(4) })
            btnRow.addView(btnBack, LinearLayout.LayoutParams(0, dip(48)).apply { weight = 1f })
        } else if (currentMode == Mode.STARRED || currentMode == Mode.WRONG || currentMode == Mode.REVIEW || currentMode == Mode.REVIEW_DIRECT) {
            // Non-LEARN modes: just return button
            btnRow.addView(btnBack, LinearLayout.LayoutParams(dip(200), dip(56)))
        } else if (hasNextGroup) {
            val btnNextGroup = android.widget.Button(requireContext()).apply {
                text = "下一组 →"
                textSize = 18f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_error))
                setPadding(dp16, dp16, dp16, dp16)
                setOnClickListener {
                    autoNextJob?.cancel()
                    selectGroup(currentGroupIndex + 1)
                }
            }
            btnRow.addView(btnBack, LinearLayout.LayoutParams(0, dip(56)).apply { weight = 1f; marginEnd = dip(8) })
            btnRow.addView(btnNextGroup, LinearLayout.LayoutParams(0, dip(56)).apply { weight = 1f })

            // Auto-advance countdown for non-guided LEARN mode
            val countdownView = textView("3 秒后自动进入下一组...", 14f, false, "#808080").apply {
                gravity = android.view.Gravity.CENTER
                setPadding(dp16, dp16, dp16, dp16)
            }
            binding.contentContainer.addView(countdownView)

            autoNextJob = lifecycleScope.launch {
                for (sec in 3 downTo 1) {
                    countdownView.text = "$sec 秒后自动进入下一组..."
                    delay(1000)
                }
                selectGroup(currentGroupIndex + 1)
            }
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
            progressRepo.markWordLearned(UserManager.userId, currentCorpusId, word.word)
        }
        UserPreferencesRepository.recordWordLearned(currentCorpusId)
        if (currentMode == Mode.REVIEW) {
            lifecycleScope.launch {
                progressRepo.updateReviewProgress(UserManager.userId, currentCorpusId, word.word, true)
            }
        }
    }

    private fun saveErrorNoWrong(word: Word) {
        // Learning phase errors are NOT added to wrong book
        // Wrong book only records answers from Quiz (exam) phase
        // Errors during learning are just tracked for progress stats
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
        Mode.STARRED -> starredWordDetails
    }

    private fun getCurrentIndexForWrong(): Int = when (currentMode) {
        Mode.LEARN -> currentIndexInGroup
        Mode.REVIEW -> getCurrentIndex()
        Mode.REVIEW_DIRECT -> reviewDirectIndex
        Mode.WRONG -> wrongWordIndex
        Mode.STARRED -> starredWordIndex
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


    override fun onDestroyView() {
        audioPlayer.release()
        autoNextJob?.cancel()
        // Save progress when leaving
        if (planIndexArg >= 0 && currentMode == Mode.LEARN) {
            UserPreferencesRepository.savePlanProgress(planIndexArg, currentGroupIndex, currentIndexInGroup)
        } else if (currentMode == Mode.LEARN) {
            UserPreferencesRepository.saveLearnPosition(currentCorpusId, currentGroupIndex, currentIndexInGroup, currentStep)
        }
        super.onDestroyView()
        _binding = null
    }
}
