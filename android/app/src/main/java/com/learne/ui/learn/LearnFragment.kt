package com.learne.ui.learn

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.learne.R
import com.learne.databinding.FragmentLearnBinding
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.ProgressRepository
import com.learne.data.repository.UserManager
import com.learne.service.AudioPlayer
import kotlinx.coroutines.launch

class LearnFragment : Fragment() {

    private var _binding: FragmentLearnBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AutoPlayViewModel
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var progressRepository: ProgressRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearnBinding.inflate(inflater, container, false)
        prefs = requireContext().getSharedPreferences("learne_settings", android.content.Context.MODE_PRIVATE)
        progressRepository = ProgressRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AutoPlayViewModel(CorpusRepository(requireContext())) as T
            }
        })[AutoPlayViewModel::class.java]

        audioPlayer = AudioPlayer()

        setupGroupSpinner()
        setupObservers()
        setupListeners()

        // Load saved corpus (from settings SharedPreferences)
        val savedCorpusId = prefs.getString("corpus_id", "catti") ?: "catti"
        val savedGroupIndex = prefs.getInt("${savedCorpusId}_last_group", 0)
        val savedWordIndex = prefs.getInt("${savedCorpusId}_last_word", 0)
        viewModel.loadCorpus(savedCorpusId, savedGroupIndex, savedWordIndex)
    }

    private fun setupGroupSpinner() {
        // Spinner items will be populated when corpus is loaded
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("1"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spGroupSelect.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.currentWord.observe(viewLifecycleOwner) { word ->
            binding.tvWord.text = word.word
            binding.tvPhonetic.text = word.phonetic
            binding.tvMeaning.text = word.meaning
            binding.tvPhrase.text = word.phrase
            binding.tvPhraseMeaning.text = word.phraseMeaning
            binding.tvExample.text = word.example
            binding.tvExampleMeaning.text = word.exampleMeaning
            // 卡片模式同步更新
            binding.tvCardWord.text = word.word
            binding.tvCardPhonetic.text = word.phonetic
            binding.tvCardMeaning.text = word.meaning
            binding.tvCardPhrase.text = word.phrase
            binding.tvCardPhraseMeaning.text = word.phraseMeaning
            binding.tvCardExample.text = word.example
            binding.tvCardExampleMeaning.text = word.exampleMeaning
        }

        viewModel.currentWordIndex.observe(viewLifecycleOwner) { index ->
            binding.progressAutoPlay.progress = index + 1
            val group = viewModel.currentGroup.value ?: emptyList()
            binding.tvWordCount.text = "Word: ${index + 1}/${group.size}"
        }

        viewModel.currentGroupIndex.observe(viewLifecycleOwner) { groupIndex ->
            val total = viewModel.totalGroups.value ?: 0
            binding.tvGroupInfo.text = "Group ${groupIndex + 1}/$total"
            // 更新Spinner选项
            updateGroupSpinner(total, groupIndex + 1)
            // 保存组位置
            saveLastPosition()
        }

        viewModel.playState.observe(viewLifecycleOwner) { state ->
            val stateText = when (state) {
                PlayState.WORD -> "▶ 播放单词发音"
                PlayState.WORD_MEANING -> "▶ 播放单词中文"
                PlayState.PHRASE -> "▶ 播放词组发音"
                PlayState.PHRASE_MEANING -> "▶ 播放词组中文"
                PlayState.EXAMPLE -> "▶ 播放例句发音"
                PlayState.EXAMPLE_MEANING -> "▶ 播放例句中文"
            }
            binding.tvPlayState.text = stateText
            binding.tvCardPlayState.text = stateText
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnStartAuto.visibility = if (isPlaying) View.GONE else View.VISIBLE
            binding.btnPauseAuto.visibility = if (isPlaying) View.VISIBLE else View.GONE
            // 播放时保持屏幕常亮，停止后恢复系统熄屏
            binding.root.keepScreenOn = isPlaying
        }

        viewModel.currentAudioPath.observe(viewLifecycleOwner) { path ->
            if (path.isNotEmpty()) {
                audioPlayer.play(path) { duration ->
                    viewModel.onAudioCompleted(duration)
                }
            }
        }

        viewModel.repeatCount.observe(viewLifecycleOwner) { count ->
            // 高亮当前选中的重复按钮
            binding.btnRepeat1.alpha = if (count == 1) 1.0f else 0.4f
            binding.btnRepeat2.alpha = if (count == 2) 1.0f else 0.4f
            binding.btnRepeat3.alpha = if (count == 3) 1.0f else 0.4f
        }

        viewModel.isCardMode.observe(viewLifecycleOwner) { isCardMode ->
            binding.layoutCardMode.visibility = if (isCardMode) View.VISIBLE else View.GONE
            // 卡片模式下同步显示当前单词内容
            if (isCardMode) {
                syncCardMode()
            }
        }
    }

    private fun updateGroupSpinner(totalGroups: Int, selectedGroup: Int) {
        val items = (1..totalGroups).map { "Group " + it.toString() + "/" + totalGroups }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spGroupSelect.adapter = adapter
        binding.spGroupSelect.setSelection(selectedGroup - 1)
    }

    private fun setupListeners() {
        binding.btnStartAuto.setOnClickListener {
            viewModel.startAutoPlay()
        }

        binding.btnPauseAuto.setOnClickListener {
            viewModel.pauseAutoPlay()
        }

        binding.btnPrevGroup.setOnClickListener {
            viewModel.prevGroup()
        }

        binding.btnNextGroup.setOnClickListener {
            viewModel.nextGroup()
        }

        // Spinner选择组
        binding.spGroupSelect.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val groupNumber = position + 1
                val currentGroup = viewModel.selectedGroupNumber.value ?: 1
                if (groupNumber != currentGroup) {
                    viewModel.selectGroup(groupNumber)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.btnPlay.setOnClickListener {
            viewModel.currentWord.value?.let { word ->
                val corpusId = "catti"
                val path = "http://macrossfev.diskstation.me:44000/learne/corpora/$corpusId/audio/words/${word.word}.mp3"
                audioPlayer.play(path)
            }
        }

        binding.btnMastered.setOnClickListener {
            viewModel.currentWord.value?.let { word ->
                val corpusId = prefs.getString("corpus_id", "cet4") ?: "cet4"
                viewModel.pauseAutoPlay()
                lifecycleScope.launch {
                    progressRepository.markWordLearned(UserManager.userId, corpusId, word.word)
                }
                viewModel.nextWord()
                saveLastPosition()
            }
        }

        binding.btnNext.setOnClickListener {
            viewModel.currentWord.value?.let { word ->
                val corpusId = prefs.getString("corpus_id", "cet4") ?: "cet4"
                lifecycleScope.launch {
                    progressRepository.markWordLearned(UserManager.userId, corpusId, word.word)
                }
            }
            viewModel.nextWord()
            saveLastPosition()
        }

        binding.btnRepeat1.setOnClickListener { viewModel.setRepeatCount(1) }
        binding.btnRepeat2.setOnClickListener { viewModel.setRepeatCount(2) }
        binding.btnRepeat3.setOnClickListener { viewModel.setRepeatCount(3) }

        binding.btnCardMode.setOnClickListener {
            viewModel.toggleCardMode()
        }

        // 切换到交互学习模式
        binding.btnModeSwitch.setOnClickListener {
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .navigate(R.id.action_auto_to_learn)
        }
    }

    private fun syncCardMode() {
        val word = viewModel.currentWord.value ?: return
        binding.tvCardWord.text = word.word
        binding.tvCardPhonetic.text = word.phonetic
        binding.tvCardMeaning.text = word.meaning
        binding.tvCardPhrase.text = word.phrase
        binding.tvCardPhraseMeaning.text = word.phraseMeaning
        binding.tvCardExample.text = word.example
        binding.tvCardExampleMeaning.text = word.exampleMeaning
    }

    private fun saveLastPosition() {
        val corpusId = prefs.getString("corpus_id", "cet4") ?: "cet4"
        val groupIndex = viewModel.currentGroupIndex.value ?: 0
        val wordIndex = viewModel.currentWordIndex.value ?: 0
        prefs.edit()
            .putInt("${corpusId}_last_group", groupIndex)
            .putInt("${corpusId}_last_word", wordIndex)
            .apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopAutoPlay()
        audioPlayer.release()
        _binding = null
    }
}