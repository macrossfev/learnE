package com.learne.ui.stats

import android.graphics.Color
import androidx.core.content.ContextCompat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.R
import com.learne.data.db.AppDatabase
import com.learne.data.db.HeatmapRecord
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentStudyStatsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StudyStatsFragment : Fragment() {

    companion object {
        fun newInstance(): StudyStatsFragment {
            return StudyStatsFragment()
        }
    }

    private var _binding: FragmentStudyStatsBinding? = null
    private val binding get() = _binding!!

    private var currentCorpusId: String = "catti"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudyStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentCorpusId = UserPreferencesRepository.planCorpusId ?: "catti"
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                val studyDao = db.studyRecordDao()
                val goalDao = db.dailyGoalDao()

                val uidCorpus = "${UserManager.userId}_$currentCorpusId"

                val totalLearned = studyDao.getTotalLearned(uidCorpus).first() ?: 0
                val totalMastered = studyDao.getTotalMastered(uidCorpus).first() ?: 0
                val totalDuration = studyDao.getTotalDuration(uidCorpus).first() ?: 0L
                val totalMinutes = totalDuration / 60

                updateSummaryTexts(totalLearned, totalMastered, totalMinutes)

                val streak = goalDao.getStreakDays(uidCorpus) ?: 0
                binding.tvStreak.text = if (streak > 0) "连续学习 $streak 天" else "暂无连续学习记录"

                loadHeatmap(db, uidCorpus)
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    private fun updateSummaryTexts(learned: Int, mastered: Int, minutes: Long) {
        binding.tvTotalLearned.text = "$learned"
        binding.tvTotalMastered.text = "$mastered"
        binding.tvTotalTime.text = "${minutes}分钟"
    }

    private fun loadHeatmap(db: AppDatabase, uidCorpus: String) {
        lifecycleScope.launch {
            try {
                val calendar = Calendar.getInstance()
                val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

                calendar.add(Calendar.DAY_OF_YEAR, -90)
                val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

                val records = db.studyRecordDao().getHeatmapData(uidCorpus, startDate)
                val recordMap = records.associate { it.date to it.learnedCount }

                buildHeatmapGrid(recordMap)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun buildHeatmapGrid(recordMap: Map<String, Int>) {
        binding.heatmapContainer.removeAllViews()

        val calendar = Calendar.getInstance()
        // Go back to the start of the week (Sunday)
        calendar.add(Calendar.DAY_OF_YEAR, -(90 + calendar.get(Calendar.DAY_OF_WEEK) - 1))

        val dayLabels = arrayOf("日", "一", "二", "三", "四", "五", "六")

        // Weekday labels
        val labelRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            for (day in dayLabels) {
                addView(TextView(requireContext()).apply {
                    text = day
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dip(14), dip(14)).apply {
                        marginEnd = 2
                    }
                })
            }
        }

        // Build weeks
        val today = Calendar.getInstance()
        val currentWeek = Calendar.getInstance()
        currentWeek.time = calendar.time

        while (currentWeek.before(today) || currentWeek.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
            val weekRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2 }
            }

            for (dayOfWeek in 0..6) {
                val cellCalendar = currentWeek.clone() as Calendar
                cellCalendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY + dayOfWeek)

                if (cellCalendar.after(today)) break

                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cellCalendar.time)
                val count = recordMap[dateStr] ?: 0

                val cellColor = when {
                    count == 0 -> Color.parseColor("#E0E0E0")
                    count < 5 -> Color.parseColor("#C6E48B")
                    count < 10 -> Color.parseColor("#7BC96F")
                    count < 20 -> Color.parseColor("#239A3B")
                    else -> Color.parseColor("#196127")
                }

                weekRow.addView(TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(dip(14), dip(14)).apply {
                        marginEnd = 2
                    }
                    background = createCellBackground(cellColor)
                    tag = "$dateStr: $count 词"
                    setOnClickListener {
                        android.widget.Toast.makeText(context, tag.toString(), android.widget.Toast.LENGTH_SHORT).show()
                    }
                })
            }

            binding.heatmapContainer.addView(weekRow)
            currentWeek.add(Calendar.WEEK_OF_YEAR, 1)
        }
    }

    private fun createCellBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 2f
        }
    }

    private fun dip(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
