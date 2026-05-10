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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

    companion object {
        fun newInstance(planIndex: Int): ChallengeMapFragment {
            return ChallengeMapFragment().apply {
                arguments = Bundle().apply { putInt("planIndex", planIndex) }
            }
        }
    }

    private var _binding: FragmentChallengeMapBinding? = null
    private val binding get() = _binding!!

    private var planIndex: Int = 0
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
    private val COLOR_NOT_STUDIED = 0xFFF44336.toInt()   // Red
    private val COLOR_NEEDS_QUIZ = 0xFFFFC107.toInt()     // Yellow (studied, not tested)
    private val COLOR_QUIZ_FAILED = 0xFFFF9800.toInt()    // Orange (quiz failed)
    private val COLOR_COMPLETED = 0xFF4CAF50.toInt()      // Green (quiz passed)

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
        studyRepo = StudyRepository(requireContext())

        binding.btnBackToPlans.setOnClickListener { navigateBackToStudyPlan() }
        binding.tvPlanName.setOnClickListener { showRenameDialog() }
        binding.btnReviewAll.setOnClickListener { reviewAllPending() }
        binding.btnWrong.setOnClickListener { enterWrongMode() }

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
            val rowLayout = LinearLayout(requireContext()).apply {
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

                val color = when {
                    !isStudied -> COLOR_NOT_STUDIED
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

                val frameLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
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
                        setTextColor(0xFFFFFFFF.toInt())
                        if (showIcon.isNotEmpty()) setTypeface(null, android.graphics.Typeface.BOLD)
                        elevation = 4f
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
                            textSize = 12f
                            setTextColor(0xFFFFFFFF.toInt())
                            gravity = Gravity.CENTER
                            setBackgroundColor(iconBg)
                            layoutParams = LinearLayout.LayoutParams(dip(16), dip(16)).apply {
                                gravity = Gravity.TOP or Gravity.END
                            }
                        })
                    }
                }

                rowLayout.addView(frameLayout)

            // Milestone separator after every 50 groups - add as separate row
            if ((groupIndex + 1) % 50 == 0 && groupIndex + 1 < totalGroups) {
                binding.layoutMapGrid.addView(TextView(requireContext()).apply {
                    text = "🏁 目标 ${groupIndex + 1} 词已达成"
                    textSize = 12f
                    setTextColor(0xFFE3000F.toInt())
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

    private fun handleGroupClick(groupIndex: Int, isStudied: Boolean, quizPassed: Boolean, quizFailed: Boolean) {
        val plan = planSave ?: return

        when {
            !isStudied -> {
                // Not studied yet → enter LEARN mode
                enterLearnFromGroup(groupIndex)
            }
            quizPassed -> {
                // Already passed → ask if retest
                AlertDialog.Builder(requireContext())
                    .setTitle("组 ${groupIndex + 1} 已通过")
                    .setMessage("要重新考试吗？")
                    .setPositiveButton("考试") { _, _ -> launchQuiz(groupIndex) }
                    .setNegativeButton("取消", null)
                    .show()
            }
            quizFailed -> {
                // Failed → retest or relearn
                AlertDialog.Builder(requireContext())
                    .setTitle("组 ${groupIndex + 1} 未通过")
                    .setMessage("上次考试未通过，要再考一次还是重新学习？")
                    .setPositiveButton("再考一次") { _, _ -> launchQuiz(groupIndex) }
                    .setNeutralButton("重新学习") { _, _ -> enterLearnFromGroup(groupIndex) }
                    .setNegativeButton("取消", null)
                    .show()
            }
            else -> {
                // Studied but not tested → go to quiz
                launchQuiz(groupIndex)
            }
        }
    }

    private fun enterLearnFromGroup(groupIndex: Int) {
        val plan = planSave ?: return

        UserPreferencesRepository.planCorpusId = plan.corpusId
        UserPreferencesRepository.planGroupSize = plan.groupSize
        UserPreferencesRepository.planCurrentGroupIndex = groupIndex
        UserPreferencesRepository.planCurrentWordIndex = 0

        val fragment = InteractiveLearnFragment.newInstance(plan.corpusId, InteractiveLearnFragment.Mode.LEARN, planIndex, groupIndex, isGuided = true)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("challenge_map")
            .commit()
    }

    private fun launchQuiz(groupIndex: Int) {
        val plan = planSave ?: return
        val fragment = QuizFragment.newInstance(plan.corpusId, groupIndex, planIndex)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("challenge_map")
            .commit()
    }

    private fun reviewAllPending() {
        val plan = planSave ?: return
        val fragment = InteractiveLearnFragment.newInstance(plan.corpusId, InteractiveLearnFragment.Mode.REVIEW, planIndex, isGuided = true)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("challenge_map")
            .commit()
    }

    private fun enterWrongMode() {
        val plan = planSave ?: return
        val fragment = InteractiveLearnFragment.newInstance(plan.corpusId, InteractiveLearnFragment.Mode.WRONG, planIndex, isGuided = true)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("challenge_map")
            .commit()
    }

    private fun navigateBackToStudyPlan() {
        val intent = Intent(requireContext(), com.learne.ui.plan.StudyPlanActivity::class.java)
        startActivity(intent)
        activity?.finish()
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
            setTextColor(if (a.unlocked) 0xFFE3000F.toInt() else 0xFFCCCCCC.toInt())
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
