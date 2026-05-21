package com.learne.ui.listen

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.data.db.AppDatabase
import com.learne.data.model.ListenHistory
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentListenReadBinding
import com.learne.service.AudioPlayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.ceil

class ListenReadFragment : Fragment() {

    companion object {
        fun newInstance(corpusId: String = "catti", groupIndex: Int = -1, planIndex: Int = -1): ListenReadFragment {
            val fragment = ListenReadFragment()
            fragment.arguments = Bundle().apply {
                putString("corpusId", corpusId)
                putInt("groupIndex", groupIndex)
                putInt("planIndex", planIndex)
            }
            return fragment
        }
    }

    // Group-level play modes
    enum class GroupPlayMode { LOOP_GROUP, SEQUENTIAL, RANDOM }

    private var _binding: FragmentListenReadBinding? = null
    private val binding get() = _binding!!
    private var allWords: List<Word> = emptyList()
    private var currentGroup: List<Word> = emptyList()
    private var currentGroupIndex = 0
    private var currentWordIndex = 0
    private var isPlaying = false
    private var repeatCount = 1
    private var groupPlayMode = GroupPlayMode.LOOP_GROUP

    // Shuffled group order state
    private var shuffledGroupOrder: List<Int> = emptyList()
    private var shuffleGroupPosition = 0

    // Group selection state
    private var selectedGroupIndex = 0

    private val audioPlayer = AudioPlayer()

    private var planIndexArg: Int = -1

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

    // Prevent duplicate playback
    private var playbackToken = 0

    private var groupSize: Int = 30
    private var currentCorpusId: String = "catti"
    private var groupIndexArg: Int = -1

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
        groupIndexArg = arguments?.getInt("groupIndex", -1) ?: -1
        planIndexArg = arguments?.getInt("planIndex", -1) ?: -1
        groupSize = UserPreferencesRepository.planGroupSize
        repeatCount = UserPreferencesRepository.repeatCount.coerceIn(1, 5)
        groupPlayMode = try {
            GroupPlayMode.valueOf(UserPreferencesRepository.listenGroupPlayMode)
        } catch (e: Exception) {
            GroupPlayMode.LOOP_GROUP
        }

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnStartListen.setOnClickListener { startListening() }

        setupGroupPlayModeButtons()
        setupPlayRepeatButtons()
        setupPlayControls()
        loadWords()
    }

    // ====== Group Selection Grid ======

    private fun setupGroupPlayModeButtons() {
        binding.btnModeGroupLoop.setOnClickListener {
            groupPlayMode = GroupPlayMode.LOOP_GROUP
            UserPreferencesRepository.listenGroupPlayMode = groupPlayMode.name
            updateGroupPlayModeButtonColors()
        }
        binding.btnModeGroupSeq.setOnClickListener {
            groupPlayMode = GroupPlayMode.SEQUENTIAL
            UserPreferencesRepository.listenGroupPlayMode = groupPlayMode.name
            updateGroupPlayModeButtonColors()
        }
        binding.btnModeGroupRandom.setOnClickListener {
            groupPlayMode = GroupPlayMode.RANDOM
            UserPreferencesRepository.listenGroupPlayMode = groupPlayMode.name
            updateGroupPlayModeButtonColors()
        }
        updateGroupPlayModeButtonColors()
    }

    private fun updateGroupPlayModeButtonColors() {
        val selectedColor = 0xFF0039CB.toInt()
        val unselectedColor = 0xFF808080.toInt()
        binding.btnModeGroupLoop.setBackgroundColor(if (groupPlayMode == GroupPlayMode.LOOP_GROUP) selectedColor else unselectedColor)
        binding.btnModeGroupSeq.setBackgroundColor(if (groupPlayMode == GroupPlayMode.SEQUENTIAL) selectedColor else unselectedColor)
        binding.btnModeGroupRandom.setBackgroundColor(if (groupPlayMode == GroupPlayMode.RANDOM) selectedColor else unselectedColor)
    }

    private fun setupPlayRepeatButtons() {
        binding.repeatButtons2.removeAllViews()
        buildRepeatButtons(binding.repeatButtons2)
    }

    private fun buildRepeatButtons(container: LinearLayout) {
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
                    updateRepeatButtonColors(container)
                    if (isPlaying) {
                        audioPlayer.release()
                        currentSubGroup = 0
                        repeatCurrent = 0
                        playCurrentWord()
                    }
                }
            }
            container.addView(btn)
        }
    }

    private fun updateRepeatButtonColors(container: LinearLayout) {
        for (i in 0 until container.childCount) {
            val btn = container.getChildAt(i) as Button
            val count = i + 1
            btn.setBackgroundColor(if (count == repeatCount) 0xFFE3000F.toInt() else 0xFF808080.toInt())
        }
    }

    private fun setupPlayControls() {
        binding.btnPlay.setOnClickListener { startAutoPlay() }
        binding.btnPause.setOnClickListener { pausePlaying() }
        binding.btnPrevWord.setOnClickListener { prevWord() }
        binding.btnNextWord.setOnClickListener { nextWord() }
        binding.btnFullscreen.setOnClickListener { enterFullScreenMode() }
    }

    private fun enterFullScreenMode() {
        val intent = android.content.Intent(requireContext(), ListenReadFullScreenActivity::class.java).apply {
            putExtra("corpusId", currentCorpusId)
            putExtra("groupIndex", currentGroupIndex)
            putExtra("wordIndex", currentWordIndex)
            putExtra("repeatCount", repeatCount)
            putExtra("isPlaying", isPlaying)
            putExtra("groupPlayMode", groupPlayMode.name)
        }
        startActivity(intent)
    }

    private fun buildGroupMap() {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutGroupMap.visibility = View.VISIBLE
        binding.layoutPlay.visibility = View.GONE

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val uidCorpus = "${UserManager.userId}_$currentCorpusId"
            val learned = db.progressDao().getLearnedCount(uidCorpus).first() ?: 0
            val mastered = db.progressDao().getMasteredCount(uidCorpus).first() ?: 0
            val completedGroups = if (planIndexArg >= 0) {
                UserPreferencesRepository.getPlanCompletedGroups(planIndexArg)
            } else {
                UserPreferencesRepository.getCompletedGroups(currentCorpusId)
            }
            val totalGroups = ceil(allWords.size.toDouble() / groupSize).toInt()

            binding.tvMapTitle.text = "听读模式"
            binding.tvProgressTop.text = "$totalGroups 组 ${completedGroups.size}/$totalGroups"
            binding.progressBar.progress = if (totalGroups > 0) completedGroups.size * 100 / totalGroups else 0

            // Group grid
            val dp4 = dip(4)
            val dp6 = dip(6)
            val colsPerRow = 5

            binding.layoutGroupGrid.removeAllViews()

            // Per-group review count
            val dueForReview = try {
                com.learne.data.repository.StudyRepository(requireContext()).getWordsDueForReview(currentCorpusId)
            } catch (e: Exception) { emptyList() }
            val duePerGroup = IntArray(totalGroups)
            for (word in dueForReview) {
                val idx = allWords.indexOfFirst { it.word == word.word }
                if (idx >= 0) {
                    val groupIdx = idx / groupSize
                    if (groupIdx < totalGroups) duePerGroup[groupIdx]++
                }
            }

            var row = 0
            while (row < totalGroups) {
                val gridRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp6 }
                }
                for (col in 0 until colsPerRow) {
                    val g = row + col
                    if (g >= totalGroups) break
                    val isCompleted = completedGroups.contains(g)
                    val start = g * groupSize + 1
                    val end = ((g + 1) * groupSize).coerceAtMost(allWords.size)
                    val dueCount = duePerGroup[g]

                    val card = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, dip(56)).apply { weight = 1f; marginEnd = dp6 }
                        setPadding(dp4, dp4, dp4, dp4)
                        val bg = GradientDrawable().apply {
                            if (isCompleted) {
                                setColor(0xFF4CAF50.toInt())
                            } else if (dueCount > 0) {
                                setColor(0xFFFFF3E0.toInt())
                                setStroke(dip(2), 0xFFFF9800.toInt())
                            } else {
                                setColor(0xFFF5F5F5.toInt())
                            }
                            cornerRadius = dip(8).toFloat()
                        }
                        background = bg
                        elevation = if (isCompleted) 3f else 1f
                        setOnClickListener {
                            selectedGroupIndex = g
                            binding.btnStartListen.isEnabled = true
                            // Highlight selected
                            for (i in 0 until binding.layoutGroupGrid.childCount) {
                                val r = binding.layoutGroupGrid.getChildAt(i) as LinearLayout
                                for (j in 0 until r.childCount) {
                                    (r.getChildAt(j) as? LinearLayout)?.alpha = 0.5f
                                }
                            }
                            this.alpha = 1.0f
                        }
                    }

                    card.addView(textView("${g + 1}", 16f, true, if (isCompleted) "#FFFFFF" else if (dueCount > 0) "#E65100" else "#333333").apply {
                        gravity = android.view.Gravity.CENTER
                    })
                    card.addView(textView("$start-$end", 9f, false, if (isCompleted) "#E8F5E9" else if (dueCount > 0) "#BF360C" else "#999999").apply {
                        gravity = android.view.Gravity.CENTER
                    })
                    if (isCompleted) {
                        card.addView(textView("✓", 11f, true, "#C8E6C9").apply {
                            gravity = android.view.Gravity.CENTER
                        })
                    } else if (dueCount > 0) {
                        card.addView(textView("$dueCount", 10f, true, "#E3000F").apply {
                            gravity = android.view.Gravity.CENTER
                        })
                    }

                    gridRow.addView(card)
                }
                binding.layoutGroupGrid.addView(gridRow)
                row += colsPerRow
            }

            // Achievements
            buildAchievements(completedGroups.size, totalGroups)
        }
    }

    private fun buildAchievements(completed: Int, total: Int) {
        // Achievements section - shown below group grid
        // We'll add them as small badges in a separate container if needed
        // For now, the progress bar and stats are sufficient
    }

    private fun showGroupMap() {
        stopPlaying()
        buildGroupMap()
    }

    private fun loadCurrentGroup() {
        val start = currentGroupIndex * groupSize
        val end = (start + groupSize).coerceAtMost(allWords.size)
        currentGroup = allWords.subList(start, end)
        currentWordIndex = 0
        currentSubGroup = 0
        repeatCurrent = 0
        updateProgress()
        showCurrentWordNormal()
    }

    private fun startListening() {
        selectedGroupIndex = selectedGroupIndex.coerceAtMost(ceil(allWords.size.toDouble() / groupSize).toInt() - 1)
        currentGroupIndex = selectedGroupIndex

        // Initialize group play order
        if (groupPlayMode == GroupPlayMode.RANDOM) {
            shuffledGroupOrder = (0 until ceil(allWords.size.toDouble() / groupSize).toInt()).toList().shuffled()
            shuffleGroupPosition = shuffledGroupOrder.indexOfFirst { it == currentGroupIndex }
            if (shuffleGroupPosition < 0) {
                shuffleGroupPosition = 0
                currentGroupIndex = shuffledGroupOrder[0]
            }
        }

        loadCurrentGroup()
    }

    private fun loadWords() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutGroupMap.visibility = View.GONE
        binding.layoutPlay.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = withTimeout(15000) {
                    CorpusRepository(requireContext()).loadWords(currentCorpusId)
                }
                allWords = result
                if (allWords.isEmpty()) {
                    Toast.makeText(context, "词库为空", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return@launch
                }
                // Use group from ChallengeMapFragment
                if (groupIndexArg >= 0) {
                    selectedGroupIndex = groupIndexArg
                    currentGroupIndex = groupIndexArg
                }
                binding.layoutLoading.visibility = View.GONE
                binding.layoutGroupMap.visibility = View.GONE
                binding.layoutPlay.visibility = View.VISIBLE
                loadCurrentGroup()
            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ====== Word Display ======

    private fun updateProgress() {
        val displayIndex = getActualWordIndex()
        val globalIndex = currentGroupIndex * groupSize + displayIndex + 1
        val total = allWords.size
        binding.tvProgressTop.text = "$globalIndex / $total"
        binding.progressBar.progress = if (total > 0) (globalIndex * 100 / total) else 0
    }

    private fun getCurrentWord(): Word {
        return currentGroup[getActualWordIndex()]
    }

    private fun getActualWordIndex(): Int {
        return currentWordIndex.coerceAtMost(currentGroup.size - 1)
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
        binding.tvStickySubgroup.text = getSubGroupLabel()
    }

    private fun getSubGroupLabel(): String = when (currentSubGroup) {
        0 -> "单词"
        1 -> "词组"
        2 -> "例句"
        else -> "单词"
    }

    private fun showCurrentWordNormal() {
        if (currentGroup.isEmpty()) return
        val word = getCurrentWord()
        binding.contentContainer.removeAllViews()

        updateStickyHeader()

        val dp12 = dip(12)
        val dp16 = dip(16)

        // Status with sub-group label
        val subGroupLabel = getSubGroupLabel()
        val statusText = TextView(requireContext()).apply {
            text = if (isPlaying) "正在播放 · $subGroupLabel" else "点击播放开始听读"
            textSize = 14f
            setTextColor(if (isPlaying) 0xFF0039CB.toInt() else 0xFFE3000F.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp16, 0, dp16, dip(8))
        }
        binding.contentContainer.addView(statusText)

        // Sub-group indicator bar (单词 / 词组 / 例句)
        val subGroupBar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp16, 0, dp16, dip(12))
            val labels = listOf("单词", "词组", "例句")
            for (i in labels.indices) {
                addView(TextView(requireContext()).apply {
                    text = labels[i]
                    textSize = 13f
                    setTypeface(null, if (i == currentSubGroup) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    setTextColor(if (i == currentSubGroup) 0xFF0039CB.toInt() else 0xFFCCCCCC.toInt())
                    setPadding(dip(12), dip(4), dip(12), dip(4))
                    val bg = GradientDrawable().apply {
                        setColor(if (i == currentSubGroup) 0xFFE8EAF6.toInt() else 0x00000000)
                        cornerRadius = dip(12).toFloat()
                    }
                    background = bg
                })
            }
        }
        binding.contentContainer.addView(subGroupBar)

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
            if (groupPlayMode == GroupPlayMode.LOOP_GROUP) {
                currentWordIndex = 0
            } else {
                nextGroup()
                return
            }
        }

        playbackToken++
        val token = playbackToken
        currentSubGroup = 0
        repeatCurrent = 0
        lastAudioDurationMs = 0
        updateProgress()
        playCurrentAudio(token)
    }

    private fun playCurrentAudio(token: Int) {
        if (!isPlaying || playbackToken != token) return

        if (currentSubGroup >= 3) {
            recordHistory()
            nextWord()
            return
        }

        val paths = subGroupPaths[currentSubGroup]
        val pairAudioIndex = repeatCurrent % 2 // 0=英文, 1=释义
        val word = getCurrentWord()

        updateProgress()
        updateStickyHeader()
        val path = CorpusRepository(requireContext()).getAudioPath(currentCorpusId, word.word, paths[pairAudioIndex])

        audioPlayer.play(path) { duration ->
            lastAudioDurationMs = duration
            repeatCurrent++

            val pauseMs = duration.coerceAtLeast(500)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (!isAdded) return@postDelayed
                if (repeatCurrent >= repeatCount * 2) {
                    repeatCurrent = 0
                    currentSubGroup++
                    playCurrentAudio(token)
                } else {
                    playCurrentAudio(token)
                }
            }, pauseMs)
        }
    }

    // ====== Navigation ======

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
            // Mark word as learned in progress tracking
            com.learne.data.repository.ProgressRepository(requireContext())
                .markWordLearned(UserManager.userId, currentCorpusId, word.word)
        }
    }

    private fun markGroupCompletedIfAllListened() {
        // Mark group completed when user has listened through all words in the group
        if (planIndexArg >= 0) {
            UserPreferencesRepository.markGroupCompletedForPlan(planIndexArg, currentGroupIndex)
        } else {
            UserPreferencesRepository.markGroupCompleted(currentCorpusId, currentGroupIndex)
        }
        // Notify ChallengeMapFragment
        parentFragmentManager.fragments.find { it is com.learne.ui.challenge.ChallengeMapFragment }?.let {
            (it as com.learne.ui.challenge.ChallengeMapFragment).refreshMap()
        }
    }

    private fun nextWord() {
        stopPlaying()

        when (groupPlayMode) {
            GroupPlayMode.RANDOM -> {
                // Within current group, sequential
                if (currentWordIndex >= currentGroup.size - 1) {
                    markGroupCompletedIfAllListened()
                    nextGroup()
                    return
                }
                currentWordIndex++
            }
            GroupPlayMode.LOOP_GROUP -> {
                // When looping back to start, mark group completed on first wrap
                val nextIndex = (currentWordIndex + 1) % currentGroup.size
                if (nextIndex == 0 && currentWordIndex == currentGroup.size - 1) {
                    markGroupCompletedIfAllListened()
                }
                currentWordIndex = nextIndex
            }
            GroupPlayMode.SEQUENTIAL -> {
                if (currentWordIndex >= currentGroup.size - 1) {
                    markGroupCompletedIfAllListened()
                    nextGroup()
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
        if (currentWordIndex > 0) currentWordIndex--

        UserPreferencesRepository.saveListenPosition(currentCorpusId, currentGroupIndex, currentWordIndex)
        updateProgress()
        showCurrentWordNormal()
    }

    private fun nextGroup() {
        stopPlaying()
        val totalGroups = ceil(allWords.size.toDouble() / groupSize).toInt()

        val nextIdx = when (groupPlayMode) {
            GroupPlayMode.RANDOM -> {
                shuffleGroupPosition++
                if (shuffleGroupPosition >= shuffledGroupOrder.size) {
                    shuffleGroupPosition = 0
                    shuffledGroupOrder = shuffledGroupOrder.shuffled()
                }
                shuffledGroupOrder[shuffleGroupPosition]
            }
            GroupPlayMode.SEQUENTIAL -> {
                if (currentGroupIndex >= totalGroups - 1) {
                    Toast.makeText(context, "所有组播放完毕", Toast.LENGTH_SHORT).show()
                    return
                }
                currentGroupIndex + 1
            }
            GroupPlayMode.LOOP_GROUP -> {
                // Loop within selected group shouldn't reach here normally
                currentGroupIndex
            }
        }

        currentGroupIndex = nextIdx.coerceAtMost(totalGroups - 1)
        selectedGroupIndex = currentGroupIndex

        val start = currentGroupIndex * groupSize
        val end = (start + groupSize).coerceAtMost(allWords.size)
        currentGroup = allWords.subList(start, end)
        currentWordIndex = 0
        currentSubGroup = 0
        repeatCurrent = 0

        UserPreferencesRepository.saveListenPosition(currentCorpusId, currentGroupIndex, 0)
        updateProgress()
        showCurrentWordNormal()
        if (isPlaying) playCurrentWord()
    }

    // ====== Helpers ======

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

    override fun onDestroyView() {
        stopPlaying()
        super.onDestroyView()
        _binding = null
    }
}
