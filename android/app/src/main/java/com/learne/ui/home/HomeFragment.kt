package com.learne.ui.home

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
import kotlinx.coroutines.launch

object HomeNavigation {
    var startInteractiveLearn: ((String) -> Unit)? = null
    var startListenRead: ((String) -> Unit)? = null
}

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var corpusList: List<Corpus> = emptyList()

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

        val corpusId = UserPreferencesRepository.selectedCorpusId
        binding.tvCorpusName.text = getCorpusName(corpusId)

        binding.btnInteractiveLearn.setOnClickListener {
            HomeNavigation.startInteractiveLearn?.invoke(corpusId)
        }

        binding.btnListenRead.setOnClickListener {
            HomeNavigation.startListenRead?.invoke(corpusId)
        }

        binding.btnChangeCorpus.setOnClickListener {
            showCorpusSelection()
        }

        loadCorpusList()
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

    private fun showCorpusSelection() {
        val available = if (corpusList.isNotEmpty()) corpusList else listOf(Corpus.CET4, Corpus.CATTI)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("选择语料库")
            .setItems(available.map { "${it.name} - ${it.description} (${it.wordCount}词)" }.toTypedArray()) { _, which ->
                val selected = available[which]
                UserPreferencesRepository.selectCorpus(selected.id)
                binding.tvCorpusName.text = selected.name
                Toast.makeText(context, "已选择：${selected.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
    }

    private fun getCorpusName(id: String): String {
        return corpusList.find { it.id == id }?.name
            ?: when (id) {
                "catti" -> "CATTI"
                "cet4" -> "CET4"
                else -> id
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
