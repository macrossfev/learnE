package com.learne.ui.challenge

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.R
import com.learne.data.model.Achievement
import com.learne.data.model.Word
import com.learne.data.model.WordProgress
import com.learne.data.repository.StudyRepository
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentChallengeMapBinding
import com.learne.ui.learn.InteractiveLearnFragment
import com.learne.ui.quiz.QuizFragment
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.ceil

class ChallengeMapFragment : Fragment() {

    fun refreshMap() {
        buildMap()
    }

    companion object {
        fun newInstance(planIndex: Int, mode: String = "learn"): ChallengeMapFragment {
            return ChallengeMapFragment().apply {
                arguments = Bundle().apply {
                    putInt("planIndex", planIndex)
                    putString("navMode", mode)
                }
            }
        }
    }

    private var _binding: FragmentChallengeMapBinding? = null
    private val binding get() = _binding!!

    private var planIndex: Int = 0
    private var navMode: String = "learn" // "learn", "listen", "review", "quiz"
    private var planSave: UserPreferencesRepository.PlanSave? = null
    private var allWords: List<Word> = emptyList()
    private var totalGroups: Int = 0
    private var completedGroups: MutableSet<Int> = mutableSetOf()
    private var quizPassedGroups: Set<Int> = emptySet()
    private var quizFailedGroups: Set<Int> = emptySet()
    private var wordsDueForReview: Set<WordProgress> = emptySet()
    private var masteredCount: Int = 0

    private lateinit var studyRepo: StudyRepository

    // Colors
    private val COLOR_NOT_STUDIED = 0xFF9E9E9E.toInt()    // Gray (not studied)
    private val COLOR_NEEDS_QUIZ = 0xFFFFC107.toInt()      // Yellow (studied, not tested)
    private val COLOR_QUIZ_FAILED = 0xFFFF9800.toInt()     // Orange (quiz failed)
    private val COLOR_REVIEW_DUE = 0xFFE3000F.toInt()      // Red (review due, highest priority)
    private val COLOR_COMPLETED = 0xFF4CAF50.toInt()       // Green (quiz passed 100%)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChallengeMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        planIndex = arguments?.getInt("planIndex", 0) ?: 0
        navMode = arguments?.getString("navMode", "learn") ?: "learn"
        studyRepo = StudyRepository(requireContext())

        binding.btnBackToPlans.setOnClickListener { navigateBackToStudyPlan() }
        binding.tvPlanName.setOnClickListener { showRenameDialog() }
        binding.btnWrong.setOnClickListener { enterWrongMode() }
        binding.btnStarred.setOnClickListener { enterStarredMode() }
        binding.btnProgress.setOnClickListener { showGroupProgress() }

        loadPlanAndWords()
    }

    private fun loadPlanAndWords() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutMapGrid.removeAllViews()

        lifecycleScope.launch {
            try {
                planSave = UserPreferencesRepository.loadPlan(planIndex)
                if (planSave == null) {
                    planSave = findPlanByCorpus()
                    if (planSave == null) {
                        binding.layoutLoading.visibility = View.GONE
                        Toast.makeText(context, "未找到计划", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                val plan = planSave!!
                val words = withTimeout(15000) {
                    com.learne.data.repository.CorpusRepository(requireContext()).loadWords(plan.corpusId)
                }
                allWords = words

                totalGroups = if (plan.totalGroups > 0) plan.totalGroups else {
                    ceil(allWords.size.toDouble() / plan.groupSize).toInt()
                }

                completedGroups = UserPreferencesRepository.getPlanCompletedGroups(planIndex).toMutableSet()
                quizPassedGroups = UserPreferencesRepository.getQuizPassedGroups(plan.corpusId)
                quizFailedGroups = UserPreferencesRepository.getQuizFailedGroups(plan.corpusId)

                // Get review status
                wordsDueForReview = studyRepo.getWordsDueForReview(plan.corpusId).toSet()
                masteredCount = studyRepo.getMasteredWordCount(plan.corpusId)

                // Load streak
                loadStreak(plan.corpusId)

                binding.layoutLoading.visibility = View.GONE
                buildMap()
                loadAchievements()
            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun findPlanByCorpus(): UserPreferencesRepository.PlanSave? {
        if (UserPreferencesRepository.hasActivePlan) {
            val corpusId = UserPreferencesRepository.planCorpusId
            val groupSize = UserPreferencesRepository.planGroupSize
            val tw = allWords.size
            val tg = if (tw > 0 && groupSize > 0) ceil(tw.toDouble() / groupSize).toInt() else 0
            return UserPreferencesRepository.PlanSave(
                name = "默认学习", corpusId = corpusId, groupSize = groupSize,
                totalWords = tw, totalGroups = tg, createdAt = 0,
                currentGroupIndex = UserPreferencesRepository.planCurrentGroupIndex,
                currentWordIndex = UserPreferencesRepository.planCurrentWordIndex
            )
        }
        return null
    }

    private fun buildMap() {
        val plan = planSave ?: return
        binding.layoutMapGrid.removeAllViews()

        binding.tvPlanName.text = plan.name

        val completedCount = completedGroups.size
        val pct = if (totalGroups > 0) completedCount * 100 / totalGroups else 0
        binding.tvProgressText.text = "$completedCount / $totalGroups ($pct%)"
        binding.progressBar.progress = pct

        // Count groups needing review
        val reviewGroupCount = countGroupsWithReview()
        binding.tvReviewCount.text = "$reviewGroupCount 组待复习"

        val groupsPerRow = 5
        val totalRows = ceil(totalGroups.toDouble() / groupsPerRow).toInt()

        for (row in 0 until totalRows) {
            var rowLayout = LinearLayout(requireContext()).apply {
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

                val isStudied = completedGroups.contains(groupIndex)
                val quizPassed = quizPassedGroups.contains(groupIndex)
                val quizFailed = quizFailedGroups.contains(groupIndex)

                val hasReview = groupHasReview(groupIndex)

                val color = when {
                    hasReview -> COLOR_REVIEW_DUE
                    quizPassed -> COLOR_COMPLETED
                    quizFailed -> COLOR_QUIZ_FAILED
                    isStudied -> COLOR_NEEDS_QUIZ
                    else -> COLOR_NOT_STUDIED
                }
                val showIcon = when {
                    quizPassed -> "✓"
                    quizFailed -> "!"
                    isStudied -> "→"
                    else -> ""
                }

                val frameLayout = FrameLayout(requireContext()).apply {
                    val size = dip(56)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = dip(6)
                    }
                    setBackgroundResource(android.R.drawable.dialog_holo_light_frame)

                    // Main button
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
                            handleGroupClick(groupIndex, isStudied, quizPassed, quizFailed)
                        }
                    })

                    // Icon overlay
                    if (showIcon.isNotEmpty()) {
                        val iconBg = when (showIcon) {
                            "✓" -> 0xFF2E7D32.toInt()
                            "!" -> 0xFFE65100.toInt()
                            else -> 0xFF757575.toInt()
                        }
                        addView(TextView(requireContext()).apply {
                            text = showIcon
                            textSize = 14f
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            gravity = Gravity.CENTER
                            setBackgroundColor(iconBg)
                            layoutParams = FrameLayout.LayoutParams(dip(20), dip(20)).apply {
                                gravity = Gravity.TOP or Gravity.END
                            }
                        })
                    }

                    // Review countdown badge
                    if (isStudied) {
                        val reviewCount = groupReviewWordCount(groupIndex)
                        if (reviewCount > 0) {
                            addView(TextView(requireContext()).apply {
                                text = "$reviewCount"
                                textSize = 9f
                                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                                gravity = Gravity.CENTER
                                setBackgroundColor(0xFFE65100.toInt())
                                setPadding(dip(2), 0, dip(2), 0)
                                layoutParams = FrameLayout.LayoutParams(dip(16), dip(14)).apply {
                                    topMargin = dip(2)
                                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                }
                            })
                        }
                    }
                }

                rowLayout.addView(frameLayout)

            // Milestone separator after every 50 groups
            if ((groupIndex + 1) % 50 == 0 && groupIndex + 1 < totalGroups) {
                binding.layoutMapGrid.addView(rowLayout)
                rowLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dip(8) }
                }
                binding.layoutMapGrid.addView(TextView(requireContext()).apply {
                    text = "🏁 目标 ${groupIndex + 1} 词已达成"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dip(4)
                        bottomMargin = dip(4)
                    }
                })
            }
        }

        binding.layoutMapGrid.addView(rowLayout)

        // Auto-scroll to first uncompleted group
        val firstUncompletedRow = (0 until totalGroups).firstOrNull { !completedGroups.contains(it) }
        if (firstUncompletedRow != null) {
            val targetRow = firstUncompletedRow / groupsPerRow
            binding.scrollMap.postDelayed({
                if (targetRow < binding.layoutMapGrid.childCount) {
                    val rowView = binding.layoutMapGrid.getChildAt(targetRow)
                    val scrollY = rowView.top - binding.scrollMap.height / 3
                    binding.scrollMap.smoothScrollTo(0, maxOf(0, scrollY))
                }
            }, 300)
        }
        }
    }

    private fun countGroupsWithReview(): Int {
        val dueWords = wordsDueForReview.map { it.word }.toSet()
        val groupsNeedingReview = mutableSetOf<Int>()
        for (word in dueWords) {
            val globalIndex = allWords.indexOfFirst { it.word == word }
            if (globalIndex >= 0) {
                groupsNeedingReview.add(globalIndex / planSave!!.groupSize)
            }
        }
        return groupsNeedingReview.size
    }

    private fun groupHasReview(groupIndex: Int): Boolean {
        val start = groupIndex * planSave!!.groupSize
        val end = (start + planSave!!.groupSize).coerceAtMost(allWords.size)
        val dueWords = wordsDueForReview.map { it.word }.toSet()
        for (i in start until end) {
            if (allWords.getOrNull(i)?.word in dueWords) return true
        }
        return false
    }

    private fun groupIsFullyMastered(groupIndex: Int): Boolean {
        return completedGroups.contains(groupIndex) && masteredCount >= allWords.size
    }

    private fun groupReviewWordCount(groupIndex: Int): Int {
        val start = groupIndex * planSave!!.groupSize
        val end = (start + planSave!!.groupSize).coerceAtMost(allWords.size)
        val dueWords = wordsDueForReview.map { it.word }.toSet()
        var count = 0
        for (i in start until end) {
            if (allWords.getOrNull(i)?.word in dueWords) count++
        }
        return count
    }

    private fun handleGroupClick(groupIndex: Int, isStudied: Boolean, quizPassed: Boolean, quizFailed: Boolean) {
        val plan = planSave ?: return

        UserPreferencesRepository.planCorpusId = plan.corpusId
        UserPreferencesRepository.planGroupSize = plan.groupSize
        UserPreferencesRepository.planCurrentGroupIndex = groupIndex
        UserPreferencesRepository.planCurrentWordIndex = 0

        when (navMode) {
            "learn" -> enterLearnMode(groupIndex)
            "listen" -> enterListenMode(groupIndex)
            "review" -> enterReviewMode(groupIndex)
            "quiz" -> {
                if (isStudied) {
                    launchQuiz(groupIndex)
                } else {
                    Toast.makeText(context, "请先学习该组后再考试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun enterLearnMode(groupIndex: Int) {
        val plan = planSave ?: return
        val fragment = InteractiveLearnFragment.newInstance(plan.corpusId, InteractiveLearnFragment.Mode.LEARN, planIndex, groupIndex, isGuided = true)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, "challenge_map")
            .addToBackStack("challenge_map")
            .commit()
    }

    private fun enterListenMode(groupIndex: Int) {
        val plan = planSave ?: return
        UserPreferencesRepository.planCorpusId = plan.corpusId
        UserPreferencesRepository.planGroupSize = plan.groupSize
        UserPreferencesRepository.planCurrentGroupIndex = groupIndex
        UserPreferencesRepository.planCurrentWordIndex = 0
        val fragment = com.learne.ui.listen.ListenReadFragment.newInstance(plan.corpusId, groupIndex)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("challenge_map")
            .commit()
    }

    private fun launchQuiz(groupIndex: Int) {
        val plan = planSave ?: return
        val fragment = QuizFragment.newInstance(plan.corpusId, groupIndex, planIndex)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, "challenge_map")
            .addToBackStack("challenge_map")
            .commit()
    }

    private fun enterReviewMode(groupIndex: Int) {
        val plan = planSave ?: return
        val fragment = InteractiveLearnFragment.newInstance(plan.corpusId, InteractiveLearnFragment.Mode.REVIEW, planIndex, groupIndex, isGuided = true)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, "challenge_map")
            .addToBackStack("challenge_map")
            .commit()
    }

    private fun enterWrongMode() {
        val plan = planSave ?: return
        val fragment = InteractiveLearnFragment.newInstance(plan.corpusId, InteractiveLearnFragment.Mode.WRONG, planIndex, isGuided = true)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, "challenge_map")
            .addToBackStack("challenge_map")
            .commit()
    }

    private fun enterStarredMode() {
        val plan = planSave ?: return
        val fragment = InteractiveLearnFragment.newInstance(plan.corpusId, InteractiveLearnFragment.Mode.STARRED, planIndex, isGuided = true)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, "challenge_map")
            .addToBackStack("challenge_map")
            .commit()
    }

    private fun showGroupProgress() {
        val plan = planSave ?: return
        val dp16 = dip(16)
        val dp8 = dip(8)
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp16, dp16, dp16)

            val totalGroups = ceil(plan.totalWords.toDouble() / plan.groupSize).toInt()
            val completed = completedGroups.size

            addView(TextView(requireContext()).apply {
                text = "总组数: $totalGroups"
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                setPadding(0, dp8, 0, 0)
            })
            addView(TextView(requireContext()).apply {
                text = "已学: $completed 组"
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.status_success))
                setPadding(0, dp8, 0, 0)
            })
            addView(TextView(requireContext()).apply {
                text = "未学: ${totalGroups - completed} 组"
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
                setPadding(0, dp8, 0, 0)
            })
            addView(TextView(requireContext()).apply {
                text = "通过考试: ${quizPassedGroups.size} 组"
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.accent))
                setPadding(0, dp8, 0, 0)
            })

            if (completed > 0) {
                addView(TextView(requireContext()).apply {
                    text = "已完成组: ${completedGroups.sorted().joinToString(", ") { "${it + 1}" }}"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.accent))
                    setPadding(0, dp16, 0, 0)
                })
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("学习进度")
            .setView(dialogView)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun navigateBackToStudyPlan() {
        parentFragmentManager.popBackStack()
    }

    private fun showRenameDialog() {
        val plan = planSave ?: return
        val editText = EditText(requireContext()).apply {
            setText(plan.name)
            setSelection(plan.name.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("重命名学习")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    UserPreferencesRepository.renamePlan(planIndex, newName)
                    binding.tvPlanName.text = newName
                    planSave = plan.copy(name = newName)
                    Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun onGroupCompleted(groupIndex: Int) {
        completedGroups += groupIndex
        UserPreferencesRepository.markGroupCompletedForPlan(planIndex, groupIndex)
        UserPreferencesRepository.markGroupCompleted(planSave?.corpusId ?: "", groupIndex)

        lifecycleScope.launch {
            studyRepo.checkAchievements(UserManager.userId, planSave?.corpusId ?: "")
        }
        buildMap()
    }

    private fun loadAchievements() {
        lifecycleScope.launch {
            studyRepo.getAllAchievements().collect { achievements ->
                if (achievements.isEmpty()) {
                    binding.layoutAchievements.visibility = View.GONE
                    return@collect
                }
                binding.layoutAchievements.visibility = View.VISIBLE
                binding.layoutAchievementBadges.removeAllViews()
                val dp4 = dip(4)
                for (a in achievements) {
                    binding.layoutAchievementBadges.addView(makeAchievementBadge(a))
                }
            }
        }
    }

    private fun makeAchievementBadge(a: Achievement): TextView {
        return TextView(requireContext()).apply {
            text = if (a.unlocked) a.title else "??"
            textSize = 11f
            setTextColor(if (a.unlocked) ContextCompat.getColor(requireContext(), R.color.primary) else ContextCompat.getColor(requireContext(), R.color.gundam_gray))
            gravity = Gravity.CENTER
            setPadding(dip(8), dip(4), dip(8), dip(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dip(4) }
        }
    }

    private fun loadStreak(corpusId: String) {
        lifecycleScope.launch {
            try {
                val streak = com.learne.data.db.AppDatabase.getDatabase(requireContext()).dailyGoalDao()
                    .getStreakDays("${UserManager.userId}_$corpusId") ?: 0
                binding.tvStreak.text = if (streak > 0) "连续 $streak 天" else ""
            } catch (e: Exception) {
                binding.tvStreak.text = ""
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (allWords.isNotEmpty() && planSave != null) {
            lifecycleScope.launch {
                completedGroups = UserPreferencesRepository.getPlanCompletedGroups(planIndex).toMutableSet()
                val corpusCompleted = UserPreferencesRepository.getCompletedGroups(planSave?.corpusId ?: "")
                completedGroups.addAll(corpusCompleted)
                wordsDueForReview = studyRepo.getWordsDueForReview(planSave!!.corpusId).toSet()
                masteredCount = studyRepo.getMasteredWordCount(planSave!!.corpusId)
                quizPassedGroups = UserPreferencesRepository.getQuizPassedGroups(planSave!!.corpusId)
                quizFailedGroups = UserPreferencesRepository.getQuizFailedGroups(planSave!!.corpusId)
                loadStreak(planSave!!.corpusId)
                buildMap()
            }
        }
    }

    private fun dip(dp: Int): Int {
        val scale = requireContext().resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
