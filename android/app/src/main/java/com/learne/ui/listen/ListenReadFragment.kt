package com.learne.ui.listen

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.data.db.AppDatabase
import com.learne.data.model.ListenHistory
import com.learne.data.model.Word
import com.learne.data.repository.CorpusLoader
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentListenReadBinding
import com.learne.service.AudioPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.ceil

class ListenReadFragment : Fragment() {

    companion object {
        fun newInstance(corpusId: String = "catti"): ListenReadFragment {
            val fragment = ListenReadFragment()
            fragment.arguments = Bundle().apply { putString("corpusId", corpusId) }
            return fragment
        }
    }

    // Playback modes
    enum class PlayMode { ORDER, SHUFFLE, LOOP_GROUP }

    private var _binding: FragmentListenReadBinding? = null
    private val binding get() = _binding!!
    private var allWords: List<Word> = emptyList()
    private var currentGroup: List<Word> = emptyList()
    private var currentGroupIndex = 0
    private var currentWordIndex = 0
    private var isPlaying = false
    private var repeatCount = 1
    private var playMode = PlayMode.ORDER

    // Shuffled order state
    private var shuffledOrder: List<Int> = emptyList()
    private var shufflePosition = 0

    // Blind mode state
    private var isBlindMode = false

    private val audioPlayer = AudioPlayer()

    // 3个子组，每组[英文音频, 释义音频]
    private val subGroupPaths = listOf(
        listOf("words", "meanings"),
        listOf("phrases", "phrase_meanings"),
        listOf("examples", "example_meanings")
    )

    // 播放状态
    private var currentSubGroup = 0 // 0=单词, 1=词组, 2=例句
    private var repeatCurrent = 0   // 当前子组内重复计数
    private var lastAudioDurationMs: Long = 0
    private var isFullScreenMode = false
    private var fullScreenActivity: Activity? = null

    private var groupSize: Int = 30
    private var currentCorpusId: String = "catti"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListenReadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentCorpusId = arguments?.getString("corpusId") ?: UserPreferencesRepository.planCorpusId ?: "catti"
        groupSize = UserPreferencesRepository.planGroupSize
        repeatCount = UserPreferencesRepository.repeatCount.coerceIn(1, 5)
        playMode = try {
            PlayMode.valueOf(UserPreferencesRepository.listenPlayMode)
        } catch (e: Exception) {
            PlayMode.ORDER
        }

        binding.tvCorpusName.text = getCorpusName(currentCorpusId)
        binding.btnPlay.setOnClickListener { startAutoPlay() }
        binding.btnPause.setOnClickListener { pausePlaying() }
        binding.btnPrevWord.setOnClickListener { prevWord() }
        binding.btnNextWord.setOnClickListener { nextWord() }
        binding.btnFullscreen.setOnClickListener { enterFullScreenMode() }

        setupGroupSpinner()
        setupRepeatButtons()
        setupPlayModeButtons()
        loadWords()
    }

    private fun setupGroupSpinner() {
        binding.spGroup.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectGroup(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupRepeatButtons() {
        binding.repeatButtons.removeAllViews()
        val dp8 = dip(8)
        for (count in 1..5) {
            val btn = Button(requireContext()).apply {
                text = "×$count"
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(if (count == repeatCount) 0xFFE3000F.toInt() else 0xFF808080.toInt())
                setPadding(dp8, 0, dp8, 0)
                layoutParams = LinearLayout.LayoutParams(dip(36), dip(30)).apply {
                    marginEnd = dip(4)
                }
                setOnClickListener {
                    repeatCount = count
                    UserPreferencesRepository.repeatCount = count
                    updateRepeatButtonColors()
                    if (isPlaying) {
                        audioPlayer.release()
                        currentSubGroup = 0
                        repeatCurrent = 0
                        playCurrentWord()
                    }
                }
            }
            binding.repeatButtons.addView(btn)
        }
    }

    private fun updateRepeatButtonColors() {
        for (i in 0 until binding.repeatButtons.childCount) {
            val btn = binding.repeatButtons.getChildAt(i) as Button
            val count = i + 1
            btn.setBackgroundColor(if (count == repeatCount) 0xFFE3000F.toInt() else 0xFF808080.toInt())
        }
    }

    private fun setupPlayModeButtons() {
        binding.modeButtons.removeAllViews()
        val dp8 = dip(8)
        val modes = listOf(
            Pair(PlayMode.ORDER, "顺序"),
            Pair(PlayMode.SHUFFLE, "乱序"),
            Pair(PlayMode.LOOP_GROUP, "循环")
        )
        for ((mode, label) in modes) {
            val btn = Button(requireContext()).apply {
                text = label
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(if (mode == playMode) 0xFFE3000F.toInt() else 0xFF808080.toInt())
                setPadding(dp8, 0, dp8, 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dip(30)).apply {
                    marginEnd = dip(4)
                }
                setOnClickListener {
                    playMode = mode
                    UserPreferencesRepository.listenPlayMode = mode.name
                    updateModeButtonColors()
                    if (mode == PlayMode.SHUFFLE) reshuffle()
                    stopPlaying()
                }
            }
            binding.modeButtons.addView(btn)
        }
        // Blind mode toggle
        val blindBtn = Button(requireContext()).apply {
            text = "盲听"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(if (isBlindMode) 0xFFE3000F.toInt() else 0xFF808080.toInt())
            setPadding(dp8, 0, dp8, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dip(30))
            setOnClickListener { toggleBlindMode() }
        }
        binding.modeButtons.addView(blindBtn)
    }

    private fun updateModeButtonColors() {
        val modes = listOf(PlayMode.ORDER, PlayMode.SHUFFLE, PlayMode.LOOP_GROUP)
        for (i in 0 until binding.modeButtons.childCount) {
            val btn = binding.modeButtons.getChildAt(i) as Button
            if (i < modes.size) {
                btn.setBackgroundColor(if (modes[i] == playMode) 0xFFE3000F.toInt() else 0xFF808080.toInt())
            } else {
                // Blind mode button
                btn.setBackgroundColor(if (isBlindMode) 0xFFE3000F.toInt() else 0xFF808080.toInt())
            }
        }
    }

    private fun reshuffle() {
        shuffledOrder = currentGroup.indices.toList().shuffled()
        shufflePosition = 0
    }

    private fun getActualWordIndex(): Int {
        return when (playMode) {
            PlayMode.SHUFFLE -> {
                if (shuffledOrder.isEmpty() || shufflePosition >= shuffledOrder.size) reshuffle()
                shuffledOrder[shufflePosition]
            }
            else -> currentWordIndex
        }
    }

    private fun loadWords() {
        binding.layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = withTimeout(15000) {
                    CorpusRepository(requireContext()).loadWords(currentCorpusId)
                }
                allWords = result
                binding.layoutLoading.visibility = View.GONE
                if (allWords.isEmpty()) {
                    Toast.makeText(context, "词库为空", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                setupGroups()

                // Resume from last position
                val lastCorpus = UserPreferencesRepository.lastListenCorpus
                val lastGroup = UserPreferencesRepository.lastListenGroupIndex
                val lastWord = UserPreferencesRepository.lastListenWordIndex
                if (lastCorpus == currentCorpusId && lastGroup >= 0 && lastGroup < binding.spGroup.adapter.count && lastWord >= 0) {
                    selectGroupWithResume(lastGroup, lastWord)
                    Toast.makeText(context, "从上次位置继续", Toast.LENGTH_SHORT).show()
                } else {
                    selectGroup(0)
                }
            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupGroups() {
        val totalGroups = ceil(allWords.size.toDouble() / groupSize).toInt()
        val groupNames = List(totalGroups) { "第${it + 1}组 (${getGroupWordRange(it)})" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, groupNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spGroup.adapter = adapter
    }

    private fun getGroupWordRange(groupIndex: Int): String {
        val start = groupIndex * groupSize + 1
        val end = ((groupIndex + 1) * groupSize).coerceAtMost(allWords.size)
        return "$start-$end"
    }

    private fun selectGroup(groupIndex: Int) {
        stopPlaying()
        currentGroupIndex = groupIndex
        val start = groupIndex * groupSize
        val end = (start + groupSize).coerceAtMost(allWords.size)
        currentGroup = allWords.subList(start, end)
        currentWordIndex = 0
        currentSubGroup = 0
        repeatCurrent = 0
        if (playMode == PlayMode.SHUFFLE) reshuffle()
        UserPreferencesRepository.saveListenPosition(currentCorpusId, currentGroupIndex, currentWordIndex)
        updateProgress()
        showCurrentWordNormal()
    }

    private fun selectGroupWithResume(groupIndex: Int, wordIndex: Int) {
        stopPlaying()
        currentGroupIndex = groupIndex
        val start = groupIndex * groupSize
        val end = (start + groupSize).coerceAtMost(allWords.size)
        currentGroup = allWords.subList(start, end)
        currentWordIndex = wordIndex.coerceAtMost(currentGroup.size - 1)
        currentSubGroup = 0
        repeatCurrent = 0
        if (playMode == PlayMode.SHUFFLE) reshuffle()
        UserPreferencesRepository.saveListenPosition(currentCorpusId, currentGroupIndex, currentWordIndex)
        updateProgress()
        showCurrentWordNormal()
    }

    private fun updateProgress() {
        val displayIndex = getActualWordIndex()
        val globalIndex = currentGroupIndex * groupSize + displayIndex + 1
        val total = allWords.size
        binding.tvWordProgress.text = "$globalIndex / $total"
        binding.progressBar.progress = (globalIndex * 100 / total)
    }

    private fun getCurrentWord(): Word {
        return currentGroup[getActualWordIndex()]
    }

    private fun updateStickyHeader() {
        if (currentGroup.isEmpty()) return
        val word = getCurrentWord()
        binding.tvStickyWord.text = word.word
        binding.tvStickyPhonetic.text = word.phonetic
        binding.tvStickyMeaning.text = when (currentSubGroup) {
            0 -> word.meaning
            1 -> if (word.phrase.isNotBlank()) "${word.phrase}  ${word.phraseMeaning}" else ""
            2 -> if (word.example.isNotBlank()) "${word.example}  ${word.exampleMeaning}" else ""
            else -> word.meaning
        }
        binding.tvStickyPhonetic.visibility = if (word.phonetic.isNotBlank()) View.VISIBLE else View.GONE
    }

    // ====== Normal mode: show all content ======

    private fun showCurrentWordNormal() {
        if (currentGroup.isEmpty()) return
        val word = getCurrentWord()
        binding.contentContainer.removeAllViews()

        updateStickyHeader()

        val dp12 = dip(12)
        val dp16 = dip(16)

        // Status
        val statusText = TextView(requireContext()).apply {
            text = if (isPlaying) "正在播放..." else "点击播放开始听读"
            textSize = 14f
            setTextColor(0xFFE3000F.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp16, 0, dp16, dp16)
        }
        binding.contentContainer.addView(statusText)

        // Word card
        val wordCard = makeCard()
        wordCard.addView(textView(word.word, 32f, true, "#000000"))
        wordCard.addView(textView(word.phonetic, 16f, false, "#808080"))
        wordCard.addView(spacer(dip(8)))
        wordCard.addView(textView("释义", 14f, true, "#E3000F"))
        wordCard.addView(textView(word.meaning, 20f, false, "#000000"))
        wordCard.setPadding(dp12, dp12, dp12, dp12)
        binding.contentContainer.addView(wordCard, cardParams())

        // Phrase card
        if (word.phrase.isNotBlank()) {
            val phraseCard = makeCard()
            phraseCard.addView(divider(0xFFE3000F.toInt()))
            phraseCard.addView(textView("短语", 14f, true, "#0039CB").apply { setPadding(0, dip(8), 0, 0) })
            phraseCard.addView(textView(word.phrase, 18f, false, "#000000").apply { setPadding(0, dip(4), 0, 0) })
            if (word.phraseMeaning.isNotBlank()) {
                phraseCard.addView(textView(word.phraseMeaning, 14f, false, "#808080").apply { setPadding(0, dip(4), 0, 0) })
            }
            phraseCard.setPadding(dp12, dp12, dp12, dp12)
            binding.contentContainer.addView(phraseCard, cardParams())
        }

        // Example card
        if (word.example.isNotBlank()) {
            val exampleCard = makeCard()
            exampleCard.addView(divider(0xFF0039CB.toInt()))
            exampleCard.addView(textView("例句", 14f, true, "#0039CB").apply { setPadding(0, dip(8), 0, 0) })
            exampleCard.addView(textView(word.example, 16f, false, "#000000").apply { setPadding(0, dip(4), 0, 0) })
            if (word.exampleMeaning.isNotBlank()) {
                exampleCard.addView(textView(word.exampleMeaning, 14f, false, "#808080").apply { setPadding(0, dip(4), 0, 0) })
            }
            exampleCard.setPadding(dp12, dp12, dp12, dp12)
            binding.contentContainer.addView(exampleCard, cardParams())
        }
    }

    // ====== Audio playback ======

    private fun startAutoPlay() {
        isPlaying = true
        currentSubGroup = 0
        repeatCurrent = 0
        playCurrentWord()
    }

    private fun pausePlaying() {
        isPlaying = false
        audioPlayer.release()
    }

    private fun stopPlaying() {
        isPlaying = false
        audioPlayer.release()
    }

    private fun playCurrentWord() {
        if (!isPlaying || currentGroup.isEmpty()) return
        if (currentWordIndex >= currentGroup.size) {
            if (playMode == PlayMode.LOOP_GROUP) {
                // Loop back to start of current group
                currentWordIndex = 0
                if (playMode == PlayMode.SHUFFLE) reshuffle()
            } else {
                nextGroup()
                return
            }
        }

        currentSubGroup = 0
        repeatCurrent = 0
        lastAudioDurationMs = 0
        updateStatusText()
        playCurrentAudio()
    }

    /**
     * 播放当前子组的当前重复音频
     * 每个子组（单词/词组/例句）包含2个音频（英文+释义），每个重复repeatCount次
     * 例如重复2次：英文-释义-英文-释义
     */
    private fun playCurrentAudio() {
        if (!isPlaying) return

        if (currentSubGroup >= 3) {
            recordHistory()
            nextWord()
            return
        }

        val paths = subGroupPaths[currentSubGroup]
        val pairAudioIndex = repeatCurrent % 2 // 0=英文, 1=释义
        val word = getCurrentWord()

        updateStatusText()
        updateStickyHeader()
        val path = CorpusRepository(requireContext()).getAudioPath(currentCorpusId, word.word, paths[pairAudioIndex])

        audioPlayer.play(path) { duration ->
            lastAudioDurationMs = duration
            repeatCurrent++

            val pauseMs = duration.coerceAtLeast(500)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (repeatCurrent >= repeatCount * 2) {
                    repeatCurrent = 0
                    currentSubGroup++
                    playCurrentAudio()
                } else {
                    playCurrentAudio()
                }
            }, pauseMs)
        }
    }

    // ====== Navigation ======

    private fun updateStatusText() {
        // Status text updated during playback
    }

    private fun recordHistory() {
        if (currentGroup.isEmpty()) return
        val word = getCurrentWord()
        val history = ListenHistory(
            id = "user_${currentCorpusId}_${word.word}_${System.currentTimeMillis()}",
            userId = "user",
            corpusId = currentCorpusId,
            word = word.word,
            wordIndex = currentGroupIndex * groupSize + getActualWordIndex(),
            groupIndex = currentGroupIndex,
            duration = lastAudioDurationMs * repeatCount,
            completedAudio = 3,
            repeatCount = repeatCount
        )
        lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).listenHistoryDao().insert(history)
        }
    }

    private fun nextWord() {
        stopPlaying()

        when (playMode) {
            PlayMode.SHUFFLE -> {
                shufflePosition++
                if (shufflePosition >= currentGroup.size) {
                    shufflePosition = 0
                    reshuffle()
                }
            }
            PlayMode.LOOP_GROUP -> {
                currentWordIndex = (currentWordIndex + 1) % currentGroup.size
                if (currentWordIndex == 0) reshuffle()
            }
            else -> { // ORDER
                if (currentWordIndex >= currentGroup.size - 1) {
                    stopPlaying()
                    Toast.makeText(context, "本组播放完毕", Toast.LENGTH_SHORT).show()
                    return
                }
                currentWordIndex++
            }
        }

        UserPreferencesRepository.saveListenPosition(currentCorpusId, currentGroupIndex, currentWordIndex)
        updateProgress()
        showCurrentWordNormal()
        if (isPlaying) playCurrentWord()
    }

    private fun prevWord() {
        stopPlaying()

        when (playMode) {
            PlayMode.SHUFFLE -> {
                shufflePosition = (shufflePosition - 1).coerceAtLeast(0)
            }
            PlayMode.LOOP_GROUP -> {
                currentWordIndex = (currentWordIndex - 1 + currentGroup.size) % currentGroup.size
            }
            else -> {
                if (currentWordIndex > 0) currentWordIndex--
            }
        }

        UserPreferencesRepository.saveListenPosition(currentCorpusId, currentGroupIndex, currentWordIndex)
        updateProgress()
        showCurrentWordNormal()
    }

    private fun nextGroup() {
        stopPlaying()
        val groupCount = binding.spGroup.adapter.count
        if (currentGroupIndex < groupCount - 1) {
            selectGroup(currentGroupIndex + 1)
            binding.spGroup.setSelection(currentGroupIndex)
        }
    }

    // ====== Full screen mode ======

    private fun enterFullScreenMode() {
        val intent = android.content.Intent(requireContext(), ListenReadFullScreenActivity::class.java).apply {
            putExtra("corpusId", currentCorpusId)
            putExtra("groupIndex", currentGroupIndex)
            putExtra("wordIndex", currentWordIndex)
            putExtra("repeatCount", repeatCount)
            putExtra("isPlaying", isPlaying)
            putExtra("playMode", playMode.name)
        }
        startActivity(intent)
    }

    // ====== Helpers ======

    private fun toggleBlindMode() {
        isBlindMode = !isBlindMode
        UserPreferencesRepository.listenBlindMode = isBlindMode
        updateModeButtonColors()
        if (isBlindMode) {
            showBlindContent()
        } else {
            showCurrentWordNormal()
        }
    }

    private fun showBlindContent() {
        binding.contentContainer.removeAllViews()
        val dp16 = dip(16)
        binding.tvStickyWord.text = "盲听模式"
        binding.tvStickyPhonetic.visibility = View.GONE
        binding.tvStickyMeaning.text = "仔细听，点击下方按钮查看内容"

        val statusText = TextView(requireContext()).apply {
            text = if (isPlaying) "正在播放音频..." else "点击播放开始盲听"
            textSize = 18f
            setTextColor(0xFFE3000F.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp16, dp16, dp16, dp16)
            gravity = android.view.Gravity.CENTER
        }
        binding.contentContainer.addView(statusText)

        val viewBtn = Button(requireContext()).apply {
            text = "查看内容"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF0039CB.toInt())
            setPadding(dp16, dp16, dp16, dp16)
            layoutParams = LinearLayout.LayoutParams(dip(180), dip(50)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = dip(16)
            }
            setOnClickListener { showCurrentWordNormal() }
        }
        binding.contentContainer.addView(viewBtn)
    }

    // Override showCurrentWord to redirect in blind mode
    private fun showCurrentWordBlind() {
        if (isBlindMode) {
            showBlindContent()
            return
        }
        showCurrentWordNormal()
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = 4f
        }
    }

    private fun cardParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dip(8)
        return params
    }

    private fun textView(text: String, size: Float, bold: Boolean, color: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            this.textSize = size
            setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            setTextColor(android.graphics.Color.parseColor(color))
        }
    }

    private fun spacer(size: Int): TextView {
        return TextView(requireContext()).apply { textSize = 1f; setPadding(0, size, 0, 0) }
    }

    private fun divider(color: Int): View {
        return View(requireContext()).apply {
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dip(2))
        }
    }

    private fun dip(dp: Int): Int {
        val scale = requireContext().resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun getCorpusName(id: String): String {
        return when (id) {
            "catti" -> "CATTI"
            "cet4" -> "CET4"
            else -> id
        }
    }

    override fun onDestroyView() {
        stopPlaying()
        super.onDestroyView()
        _binding = null
    }
}
