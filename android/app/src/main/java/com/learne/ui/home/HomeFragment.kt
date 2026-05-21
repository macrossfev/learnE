package com.learne.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.data.model.Corpus
import com.learne.data.repository.CorpusLoader
import com.learne.data.repository.StudyRepository
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

object HomeNavigation {
    var startInteractiveLearn: ((String) -> Unit)? = null
    var startListenRead: ((String) -> Unit)? = null
    var startReview: ((String) -> Unit)? = null
    var startReviewDirect: ((String) -> Unit)? = null
    var startWrongWords: ((String) -> Unit)? = null
    var startQuiz: ((String) -> Unit)? = null
    var startDictation: ((String) -> Unit)? = null
    var startFlashcard: ((String) -> Unit)? = null
    var startDailyChallenge: (() -> Unit)? = null
    var startStudyStats: (() -> Unit)? = null
    var startUserCenter: (() -> Unit)? = null

    fun clear() {
        startInteractiveLearn = null
        startListenRead = null
        startReview = null
        startQuiz = null
        startDictation = null
        startFlashcard = null
        startDailyChallenge = null
        startStudyStats = null
        startUserCenter = null
    }
}

class HomeFragment : Fragment() {

    companion object {
        fun newInstance(corpusId: String? = null): HomeFragment {
            return HomeFragment().apply {
                arguments = Bundle().apply {
                    putString("corpusId", corpusId)
                }
            }
        }
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var corpusList: List<Corpus> = emptyList()
    private var currentCorpusId: String = "catti"
    private var completedGroupCount: Int = 0
    private var reviewWordCount: Int = 0
    private var quizAvailableGroups: Set<Int> = emptySet()
    private var planIndex: Int = -1 // 已学习但未考试的组

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        UserPreferencesRepository.init(requireContext())

        currentCorpusId = arguments?.getString("corpusId")
            ?: UserPreferencesRepository.planCorpusId
            ?: UserPreferencesRepository.selectedCorpusId

        planIndex = activity?.intent?.getIntExtra("planIndex", -1) ?: -1

        updatePlanDisplay()
        loadCompletedGroupCount()
        loadReviewAndQuizData()
        updateContinueLearnButton()

        binding.btnInteractiveLearn.setOnClickListener {
            HomeNavigation.startInteractiveLearn?.invoke(currentCorpusId)
        }

        binding.btnListenRead.setOnClickListener {
            HomeNavigation.startListenRead?.invoke(currentCorpusId)
        }

        binding.btnReview.setOnClickListener {
            if (reviewWordCount == 0) {
                Toast.makeText(context, "暂无需要复习的单词", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            HomeNavigation.startReview?.invoke(currentCorpusId)
        }
        binding.btnReviewDirect.setOnClickListener {
            HomeNavigation.startReviewDirect?.invoke(currentCorpusId)
        }
        binding.btnWrongWords.setOnClickListener {
            HomeNavigation.startWrongWords?.invoke(currentCorpusId)
        }

        binding.btnQuiz.setOnClickListener {
            if (quizAvailableGroups.isEmpty()) {
                Toast.makeText(context, "暂无需要考试的组，请先学习新组", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            HomeNavigation.startQuiz?.invoke(currentCorpusId)
        }

        binding.btnDictation.setOnClickListener {
            if (completedGroupCount == 0) {
                Toast.makeText(context, "请先完成考试后再使用听写模式", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            HomeNavigation.startDictation?.invoke(currentCorpusId)
        }

        binding.btnFlashcard.setOnClickListener {
            if (completedGroupCount == 0) {
                Toast.makeText(context, "请先完成考试后再使用闪卡模式", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            HomeNavigation.startFlashcard?.invoke(currentCorpusId)
        }

        binding.btnDailyChallenge.setOnClickListener {
            HomeNavigation.startDailyChallenge?.invoke()
        }

        binding.btnStudyStats.setOnClickListener {
            HomeNavigation.startStudyStats?.invoke()
        }

        binding.btnUserCenter.setOnClickListener {
            HomeNavigation.startUserCenter?.invoke()
        }

        binding.btnBackToMap.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnContinueLearn.setOnClickListener {
            HomeNavigation.startInteractiveLearn?.invoke(currentCorpusId)
        }

        loadCorpusList()
    }

    private fun updateContinueLearnButton() {
        lifecycleScope.launch {
            val groupIdx = if (planIndex >= 0) {
                val plan = UserPreferencesRepository.loadPlan(planIndex)
                plan?.currentGroupIndex ?: 0
            } else {
                UserPreferencesRepository.getLearnPosition(currentCorpusId).first
            }
            if (groupIdx > 0) {
                binding.btnContinueLearn.text = "继续学习 第${groupIdx + 1}组"
                binding.btnContinueLearn.visibility = View.VISIBLE
            } else {
                binding.btnContinueLearn.visibility = View.GONE
            }

            // Today overview
            val todayCount = UserPreferencesRepository.getTodayLearnedCount(currentCorpusId)
            binding.tvGroupSize.text = "每组 ${UserPreferencesRepository.planGroupSize} 个单词 | 今日已学 $todayCount 词"
        }
    }

    private fun loadCompletedGroupCount() {
        lifecycleScope.launch {
            completedGroupCount = if (planIndex >= 0) {
                UserPreferencesRepository.getPlanCompletedGroups(planIndex).size
            } else {
                UserPreferencesRepository.getCompletedGroups(currentCorpusId).size
            }
        }
    }

    private fun loadReviewAndQuizData() {
        val studyRepo = StudyRepository(requireContext())
        lifecycleScope.launch {
            try {
                val reviewWords = studyRepo.getWordsDueForReview(currentCorpusId)
                reviewWordCount = reviewWords.size
            } catch (e: Exception) {
                reviewWordCount = 0
            }

            val completed = if (planIndex >= 0) {
                UserPreferencesRepository.getPlanCompletedGroups(planIndex)
            } else {
                UserPreferencesRepository.getCompletedGroups(currentCorpusId)
            }
            val passed = UserPreferencesRepository.getQuizPassedGroups(currentCorpusId)
            quizAvailableGroups = (completed - passed).toSet()

            // Update review/wrong buttons with counts
            if (reviewWordCount > 0) {
                binding.btnReviewDirect.text = "全量复习 ($reviewWordCount)"
            }
        }
    }

    private fun updatePlanDisplay() {
        binding.tvCorpusName.text = getCorpusName(currentCorpusId)
        binding.tvGroupSize.text = "每组 ${UserPreferencesRepository.planGroupSize} 个单词"
    }

    private fun loadCorpusList() {
        lifecycleScope.launch {
            try {
                val result = CorpusLoader.loadCorpusList()
                if (result.isNotEmpty()) {
                    corpusList = result
                } else {
                    corpusList = listOf(Corpus.CET4, Corpus.CATTI)
                }
            } catch (e: Exception) {
                corpusList = listOf(Corpus.CET4, Corpus.CATTI)
            }
        }
    }

    private fun getCorpusName(id: String): String {
        return corpusList.find { it.id == id }?.name
            ?: when (id) {
                "catti" -> "CATTI"
                "cet4" -> "CET4"
                else -> id
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
