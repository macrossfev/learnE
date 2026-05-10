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
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentHomeBinding
import android.widget.RadioGroup
import kotlinx.coroutines.launch

object HomeNavigation {
    var startInteractiveLearn: ((String) -> Unit)? = null
    var startListenRead: ((String) -> Unit)? = null
    var startDictation: ((String) -> Unit)? = null
    var startFlashcard: ((String) -> Unit)? = null
    var startDailyChallenge: (() -> Unit)? = null
    var startStudyStats: (() -> Unit)? = null
    var startWrongWords: (() -> Unit)? = null
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

        updatePlanDisplay()

        binding.btnInteractiveLearn.setOnClickListener {
            HomeNavigation.startInteractiveLearn?.invoke(currentCorpusId)
        }

        binding.btnListenRead.setOnClickListener {
            HomeNavigation.startListenRead?.invoke(currentCorpusId)
        }

        binding.btnDictation.setOnClickListener {
            HomeNavigation.startDictation?.invoke(currentCorpusId)
        }

        binding.btnFlashcard.setOnClickListener {
            HomeNavigation.startFlashcard?.invoke(currentCorpusId)
        }

        binding.btnDailyChallenge.setOnClickListener {
            HomeNavigation.startDailyChallenge?.invoke()
        }

        binding.btnStudyStats.setOnClickListener {
            HomeNavigation.startStudyStats?.invoke()
        }

        binding.btnWrongWords.setOnClickListener {
            HomeNavigation.startWrongWords?.invoke()
        }

        binding.btnChangePlan.setOnClickListener {
            showPlanSelection()
        }

        binding.btnNewPlan.setOnClickListener {
            val intent = Intent(requireContext(), com.learne.ui.plan.StudyPlanActivity::class.java)
            startActivity(intent)
            activity?.finish()
        }

        loadCorpusList()
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

    private fun showPlanSelection() {
        val available = if (corpusList.isNotEmpty()) corpusList else listOf(Corpus.CET4, Corpus.CATTI)

        val groupSizeInput = android.widget.EditText(requireContext()).apply {
            hint = "每组单词数"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(UserPreferencesRepository.planGroupSize.toString())
            setPadding(dip(16), dip(16), dip(16), dip(16))
        }

        var selectedIndex = available.indexOfFirst { it.id == currentCorpusId }.coerceAtLeast(0)

        val radioGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dip(24), dip(16), dip(24), dip(8))
            for ((i, corpus) in available.withIndex()) {
                addView(android.widget.RadioButton(requireContext()).apply {
                    id = View.generateViewId()
                    text = "${corpus.name} - ${corpus.description}"
                    textSize = 14f
                    isChecked = (i == selectedIndex)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedIndex = i
                    }
                })
            }
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(radioGroup)
            addView(groupSizeInput)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("调整学习计划")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val groupSize = groupSizeInput.text.toString().toIntOrNull()?.coerceIn(1, 200) ?: 30
                val selected = available[selectedIndex]
                UserPreferencesRepository.savePlan(selected.id, groupSize)
                currentCorpusId = selected.id
                updatePlanDisplay()
                Toast.makeText(context, "已更新计划", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
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
