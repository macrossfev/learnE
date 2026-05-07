package com.learne.ui.listen

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.learne.data.db.AppDatabase
import com.learne.data.model.ListenHistory
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.ActivityListenReadFullscreenBinding
import com.learne.service.AudioPlayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.ceil

/**
 * 大屏模式：全屏听读（上下分屏）
 * 上屏：英文（单词/词组/例句）
 * 下屏：中文释义
 *
 * 播放逻辑（以重复2次为例）:
 *   1. 显示单词+释义 → 播放: 单词-释义-单词-释义
 *   2. 显示词组+释义 → 播放: 词组-释义-词组-释义
 *   3. 显示例句+释义 → 播放: 例句-释义-例句-释义
 *   → 下一个词
 */
class ListenReadFullScreenActivity : AppCompatActivity() {

    companion object {
        private val GROUP_LABELS = listOf("单词", "词组", "例句")
    }

    private lateinit var binding: ActivityListenReadFullscreenBinding
    private val audioPlayer = AudioPlayer()

    // 3个子组，每组[英文音频, 释义音频]
    private val subGroupPaths = listOf(
        listOf("words", "meanings"),
        listOf("phrases", "phrase_meanings"),
        listOf("examples", "example_meanings")
    )

    private var allWords: List<Word> = emptyList()
    private var currentGroup: List<Word> = emptyList()
    private var currentGroupIndex = 0
    private var currentWordIndex = 0
    private var repeatCount = 1
    private var isPlaying = false
    private var currentCorpusId: String = "catti"

    private var lastAudioDurationMs: Long = 0

    // 当前播放状态
    private var currentSubGroup = 0 // 0=单词, 1=词组, 2=例句
    private var repeatCurrent = 0   // 当前重复次数

    private val GROUP_SIZE = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )

        binding = ActivityListenReadFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentCorpusId = intent.getStringExtra("corpusId") ?: "catti"
        currentGroupIndex = intent.getIntExtra("groupIndex", 0)
        currentWordIndex = intent.getIntExtra("wordIndex", 0)
        repeatCount = intent.getIntExtra("repeatCount", 1)
        isPlaying = intent.getBooleanExtra("isPlaying", false)

        binding.btnExitFullscreen.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPlayPause.setOnClickListener {
            if (isPlaying) pausePlaying() else resumePlaying()
        }
        binding.btnPrev.setOnClickListener { prevWord() }
        binding.btnNext.setOnClickListener { nextWord() }

        loadWords()
    }

    private fun loadWords() {
        lifecycleScope.launch {
            try {
                val result = withTimeout(15000) {
                    CorpusRepository(this@ListenReadFullScreenActivity).loadWords(currentCorpusId)
                }
                allWords = result
                if (allWords.isEmpty()) {
                    Toast.makeText(this@ListenReadFullScreenActivity, "词库为空", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
                selectGroup(currentGroupIndex)
                if (isPlaying) {
                    startAutoPlay()
                } else {
                    showDefaultContent()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ListenReadFullScreenActivity, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun selectGroup(groupIndex: Int) {
        currentGroupIndex = groupIndex
        val start = groupIndex * GROUP_SIZE
        val end = (start + GROUP_SIZE).coerceAtMost(allWords.size)
        currentGroup = allWords.subList(start, end)
    }

    private fun updateProgress() {
        val globalIndex = currentGroupIndex * GROUP_SIZE + currentWordIndex + 1
        binding.tvProgress.text = "$globalIndex / ${allWords.size}"
    }

    // ====== Content display ======

    private fun showDefaultContent() {
        if (currentGroup.isEmpty()) return
        val word = currentGroup[currentWordIndex]
        updateProgress()
        binding.tvAudioType.text = "点击播放开始"
        showWordContent(word, 0)
    }

    private fun showWordContent(word: Word, subGroup: Int) {
        when (subGroup) {
            0 -> { // 单词
                binding.tvAudioType.text = GROUP_LABELS[0]
                val enSize = getWordTextSize(word.word)
                binding.tvLine1.text = word.word
                binding.tvLine1.textSize = enSize
                binding.tvLine2.text = word.meaning
            }
            1 -> { // 词组
                binding.tvAudioType.text = GROUP_LABELS[1]
                if (word.phrase.isNotBlank()) {
                    val enSize = getWordTextSize(word.phrase)
                    binding.tvLine1.text = word.phrase
                    binding.tvLine1.textSize = enSize
                } else {
                    binding.tvLine1.text = word.word
                    binding.tvLine1.textSize = getWordTextSize(word.word)
                }
                binding.tvLine2.text = word.phraseMeaning.takeIf { it.isNotBlank() } ?: word.meaning
            }
            2 -> { // 例句
                binding.tvAudioType.text = GROUP_LABELS[2]
                if (word.example.isNotBlank()) {
                    val enSize = getWordTextSize(word.example)
                    binding.tvLine1.text = word.example
                    binding.tvLine1.textSize = enSize
                } else {
                    binding.tvLine1.text = word.word
                    binding.tvLine1.textSize = getWordTextSize(word.word)
                }
                binding.tvLine2.text = word.exampleMeaning.takeIf { it.isNotBlank() } ?: word.meaning
            }
        }
        binding.tvLine2.textSize = 40f
        binding.tvLine3.text = ""
    }

    private fun getWordTextSize(text: String): Float {
        return when {
            text.length > 20 -> 36f
            text.length > 15 -> 44f
            text.length > 10 -> 56f
            else -> 72f
        }
    }

    // ====== Audio playback ======

    private fun startAutoPlay() {
        isPlaying = true
        binding.btnPlayPause.text = "暂 停"
        currentSubGroup = 0
        repeatCurrent = 0
        playCurrentWord()
    }

    private fun pausePlaying() {
        isPlaying = false
        binding.btnPlayPause.text = "播 放"
        audioPlayer.release()
    }

    private fun resumePlaying() {
        isPlaying = true
        binding.btnPlayPause.text = "暂 停"
        playCurrentWord()
    }

    private fun playCurrentWord() {
        if (!isPlaying || currentGroup.isEmpty()) return
        if (currentWordIndex >= currentGroup.size) {
            nextWord()
            return
        }

        currentSubGroup = 0
        repeatCurrent = 0
        lastAudioDurationMs = 0
        showWordContent(currentGroup[currentWordIndex], 0)
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
            // 3个子组全部完成
            recordHistory()
            nextWord()
            return
        }

        val paths = subGroupPaths[currentSubGroup]
        val pairAudioIndex = repeatCurrent % 2 // 0=英文, 1=释义

        showWordContent(currentGroup[currentWordIndex], currentSubGroup)

        val path = CorpusRepository(this).getAudioPath(currentCorpusId, currentGroup[currentWordIndex].word, paths[pairAudioIndex])

        audioPlayer.play(path) { duration ->
            lastAudioDurationMs = duration
            repeatCurrent++

            val pauseMs = duration.coerceAtLeast(500)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (repeatCurrent >= repeatCount * 2) {
                    // 当前子组完成（2个音频 × repeatCount次）
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

    private fun nextWord() {
        if (currentWordIndex < currentGroup.size - 1) {
            currentWordIndex++
            savePosition()
            updateProgress()
            if (isPlaying) {
                audioPlayer.release()
                playCurrentWord()
            } else {
                showDefaultContent()
            }
        } else {
            val totalGroups = ceil(allWords.size.toDouble() / GROUP_SIZE).toInt()
            if (currentGroupIndex < totalGroups - 1) {
                selectGroup(currentGroupIndex + 1)
                currentWordIndex = 0
                savePosition()
                updateProgress()
                if (isPlaying) {
                    audioPlayer.release()
                    playCurrentWord()
                } else {
                    showDefaultContent()
                }
            } else {
                isPlaying = false
                binding.btnPlayPause.text = "播 放"
                Toast.makeText(this, "全部播放完毕", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun prevWord() {
        if (currentWordIndex > 0) {
            currentWordIndex--
            savePosition()
            updateProgress()
            if (isPlaying) {
                audioPlayer.release()
                playCurrentWord()
            } else {
                showDefaultContent()
            }
        } else if (currentGroupIndex > 0) {
            // 返回上一组
            selectGroup(currentGroupIndex - 1)
            currentWordIndex = currentGroup.size - 1
            savePosition()
            updateProgress()
            if (isPlaying) {
                audioPlayer.release()
                playCurrentWord()
            } else {
                showDefaultContent()
            }
        }
    }

    private fun savePosition() {
        UserPreferencesRepository.saveListenPosition(currentCorpusId, currentGroupIndex, currentWordIndex)
    }

    private fun recordHistory() {
        if (currentGroup.isEmpty()) return
        val word = currentGroup[currentWordIndex]
        val history = ListenHistory(
            id = "user_${currentCorpusId}_${word.word}_${System.currentTimeMillis()}",
            userId = "user",
            corpusId = currentCorpusId,
            word = word.word,
            wordIndex = currentGroupIndex * GROUP_SIZE + currentWordIndex,
            groupIndex = currentGroupIndex,
            duration = lastAudioDurationMs * repeatCount,
            completedAudio = 3,
            repeatCount = repeatCount
        )
        lifecycleScope.launch {
            AppDatabase.getDatabase(this@ListenReadFullScreenActivity).listenHistoryDao().insert(history)
        }
    }

    override fun onDestroy() {
        audioPlayer.release()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }
}
