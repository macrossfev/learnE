package com.learne.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.data.db.AppDatabase
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentUserCenterBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UserCenterFragment : Fragment() {

    companion object {
        fun newInstance(): UserCenterFragment {
            return UserCenterFragment()
        }
    }

    private var _binding: FragmentUserCenterBinding? = null
    private val binding get() = _binding!!
    private var currentCorpusId: String = "catti"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentCorpusId = UserPreferencesRepository.planCorpusId ?: "catti"
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnLogout.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("退出") { _, _ ->
                    UserManager.logout()
                    val intent = android.content.Intent(requireContext(), com.learne.ui.auth.LoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        loadUserData()
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                val studyDao = db.studyRecordDao()
                val wrongWordDao = db.wrongWordDao()
                val goalDao = db.dailyGoalDao()
                val userDao = db.userDao()

                // User info
                val userId = UserManager.userId
                val user = userDao.getUser(userId)
                binding.tvUsername.text = user?.username ?: "用户"

                // Stats
                val uidCorpus = "${UserManager.userId}_$currentCorpusId"
                val totalLearned = studyDao.getTotalLearned(uidCorpus).first() ?: 0
                val totalMastered = studyDao.getTotalMastered(uidCorpus).first() ?: 0
                val totalDuration = studyDao.getTotalDuration(uidCorpus).first() ?: 0L
                val totalMinutes = totalDuration / 60

                val streak = goalDao.getStreakDays(uidCorpus) ?: 0
                val wrongCount = wrongWordDao.getWrongWordCount(uidCorpus).first() ?: 0

                binding.tvStreak.text = "$streak"
                binding.tvLearned.text = "$totalLearned"
                binding.tvMastered.text = "$totalMastered"
                binding.tvWrongCount.text = "$wrongCount"
                binding.tvTotalTime.text = "${totalMinutes}分钟"

                // Today's summary
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
                val todayRecord = studyDao.getByDateAndCorpus(today, uidCorpus)
                val todayLearned = todayRecord?.learnedCount ?: 0
                val todayMinutes = (todayRecord?.studyDuration ?: 0) / 60

                val wrongWordsList = wrongWordDao.getWrongWords(uidCorpus).first()
                val todayCorrect = wrongWordsList.filter { it.corrected }.size

                binding.tvTodaySummary.text = buildString {
                    append("今日学习 $todayLearned 个单词，学习 ${todayMinutes} 分钟\n")
                    append("今日复习 $todayCorrect 个错题")
                }

                // Ability level
                val masteryPct = if (totalLearned > 0) totalMastered * 100 / totalLearned else 0
                val level = when {
                    masteryPct >= 80 -> "高级（掌握率 ${masteryPct}%）"
                    masteryPct >= 50 -> "中级（掌握率 ${masteryPct}%）"
                    masteryPct >= 20 -> "初级（掌握率 ${masteryPct}%）"
                    else -> "入门（掌握率 ${masteryPct}%）"
                }
                binding.tvLevel.text = level
                binding.tvAbility.text = buildString {
                    appendLine("已学 $totalLearned 词，已掌握 $totalMastered 词")
                    appendLine("错题 $wrongCount 个，学习时长 ${totalMinutes} 分钟")
                    append("连续学习 $streak 天")
                }
                binding.progressMastery.progress = masteryPct
                binding.progressMastery.max = 100

            } catch (e: Exception) {
                binding.tvLevel.text = "数据加载失败"
                binding.tvAbility.text = e.message
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
