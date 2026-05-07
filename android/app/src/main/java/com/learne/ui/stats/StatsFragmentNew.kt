package com.learne.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.learne.R
import com.learne.databinding.FragmentStatsNewBinding
import com.learne.data.repository.StudyRepository

class StatsFragmentNew : Fragment() {

    private var _binding: FragmentStatsNewBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: StatsViewModelNew
    private lateinit var achievementAdapter: AchievementAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StatsViewModelNew(StudyRepository(requireContext())) as T
            }
        })[StatsViewModelNew::class.java]

        achievementAdapter = AchievementAdapter()
        binding.rvAchievements.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAchievements.adapter = achievementAdapter

        setupObservers()
        setupListeners()

        viewModel.load("catti")
    }

    private fun setupObservers() {
        viewModel.totalLearned.observe(viewLifecycleOwner) {
            binding.tvTotalLearned.text = it.toString()
        }

        viewModel.totalMastered.observe(viewLifecycleOwner) {
            binding.tvTotalMastered.text = it.toString()
        }

        viewModel.totalDuration.observe(viewLifecycleOwner) {
            binding.tvTotalDuration.text = it
        }

        viewModel.goalCurrent.observe(viewLifecycleOwner) { current ->
            binding.tvGoalCurrent.text = current.toString()
            val target = viewModel.goalTarget.value ?: 50
            binding.progressGoal.progress = (current * 100 / target).coerceAtMost(100)
        }

        viewModel.goalTarget.observe(viewLifecycleOwner) { target ->
            binding.tvGoalTarget.text = "/$target"
            binding.etGoalTarget.setText(target.toString())
        }

        viewModel.streakDays.observe(viewLifecycleOwner) { days ->
            binding.tvStreak.text = "连续打卡: ${days}天"
        }

        viewModel.totalCheckIns.observe(viewLifecycleOwner) { count ->
            binding.tvTotalCheckins.text = "总打卡: ${count}次"
        }

        viewModel.achievements.observe(viewLifecycleOwner) { list ->
            achievementAdapter.updateData(list)
        }

        viewModel.unlockedCount.observe(viewLifecycleOwner) { count ->
            binding.tvUnlockedCount.text = count
        }

        viewModel.studyRecords.observe(viewLifecycleOwner) { records: List<com.learne.data.model.StudyRecord> ->
            updateChart(records)
        }
    }

    private fun setupListeners() {
        binding.btnSetGoal.setOnClickListener {
            val target = binding.etGoalTarget.text.toString().toIntOrNull() ?: 50
            viewModel.setGoal(target)
        }

        binding.btnCheckIn.setOnClickListener {
            viewModel.checkIn()
        }
    }

    private fun updateChart(records: List<com.learne.data.model.StudyRecord>) {
        binding.chartContainer.removeAllViews()
        val maxCount = records.maxOfOrNull { it.learnedCount } ?: 1

        records.takeLast(7).forEach { record ->
            val height = (record.learnedCount * 80 / maxCount).coerceAtLeast(4)
            val bar = TextView(requireContext())
            bar.layoutParams = LinearLayout.LayoutParams(20, height, 1f)
            bar.setBackgroundColor(resources.getColor(R.color.primary, null))
            binding.chartContainer.addView(bar)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}