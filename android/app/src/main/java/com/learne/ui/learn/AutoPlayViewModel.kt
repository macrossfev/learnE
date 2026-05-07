package com.learne.ui.learn

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayState {
    WORD,           // 播放单词发音
    WORD_MEANING,   // 播放单词中文释义
    PHRASE,         // 播放词组发音
    PHRASE_MEANING, // 播放词组释义
    EXAMPLE,        // 播放例句发音
    EXAMPLE_MEANING // 播放例句释义
}

class AutoPlayViewModel(
    private val corpusRepository: CorpusRepository
) : ViewModel() {

    private val _allWords = MutableLiveData<List<Word>>()
    private val _currentGroup = MutableLiveData<List<Word>>()
    val currentGroup: LiveData<List<Word>> = _currentGroup

    private val _currentWordIndex = MutableLiveData<Int>(0)
    val currentWordIndex: LiveData<Int> = _currentWordIndex

    private val _currentGroupIndex = MutableLiveData<Int>(0)
    val currentGroupIndex: LiveData<Int> = _currentGroupIndex

    private val _totalGroups = MutableLiveData<Int>(0)
    val totalGroups: LiveData<Int> = _totalGroups

    private val _playState = MutableLiveData<PlayState>(PlayState.WORD)
    val playState: LiveData<PlayState> = _playState

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentWord = MutableLiveData<Word>()
    val currentWord: LiveData<Word> = _currentWord

    private val _currentAudioPath = MutableLiveData<String>()
    val currentAudioPath: LiveData<String> = _currentAudioPath

    private val _corpusId = MutableLiveData<String>()
    private val _selectedGroupNumber = MutableLiveData<Int>(1)
    val selectedGroupNumber: LiveData<Int> = _selectedGroupNumber
    private val handler = Handler(Looper.getMainLooper())

    // 重复播放次数 1-3
    private val _repeatCount = MutableLiveData<Int>(1)
    val repeatCount: LiveData<Int> = _repeatCount
    private var currentRepeat = 0  // 当前第几次重复

    private val GROUP_SIZE = 50
    private var allWordsList: List<Word> = emptyList()
    private var isPaused = false

    fun loadCorpus(corpusId: String, savedGroupIndex: Int = 0, savedWordIndex: Int = 0) {
        _corpusId.value = corpusId
        viewModelScope.launch {
            val words = corpusRepository.loadWords(corpusId)
            allWordsList = words
            _allWords.value = words
            _totalGroups.value = (words.size / GROUP_SIZE) + if (words.size % GROUP_SIZE > 0) 1 else 0
            val groupIdx = if (savedGroupIndex in 0 until (_totalGroups.value ?: 1)) savedGroupIndex else 0
            val wordIdx = savedWordIndex.coerceAtLeast(0)
            loadGroup(groupIdx, wordIdx)
        }
    }

    fun preloadGroup(groupIndex: Int, startWordIndex: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val startIndex = groupIndex * GROUP_SIZE
            val endIndex = minOf(startIndex + GROUP_SIZE, allWordsList.size)
            val groupWords = allWordsList.subList(startIndex, endIndex)

            // 预加载音频文件信息
            val audioPaths = groupWords.map { word ->
                mapOf(
                    "word" to corpusRepository.getAudioPath(_corpusId.value ?: "catti", word.word, "words"),
                    "meaning" to corpusRepository.getAudioPath(_corpusId.value ?: "catti", word.word, "meanings"),
                    "phrase" to corpusRepository.getAudioPath(_corpusId.value ?: "catti", word.word, "phrases"),
                    "phrase_meaning" to corpusRepository.getAudioPath(_corpusId.value ?: "catti", word.word, "phrase_meanings"),
                    "example" to corpusRepository.getAudioPath(_corpusId.value ?: "catti", word.word, "examples"),
                    "example_meaning" to corpusRepository.getAudioPath(_corpusId.value ?: "catti", word.word, "example_meanings")
                )
            }

            withContext(Dispatchers.Main) {
                _currentGroup.value = groupWords
                _currentGroupIndex.value = groupIndex
                val idx = startWordIndex.coerceAtMost(groupWords.size - 1).coerceAtLeast(0)
                _currentWordIndex.value = idx
                _currentWord.value = groupWords.getOrNull(idx) ?: groupWords.firstOrNull()
            }
        }
    }

    fun selectGroup(groupNumber: Int) {
        val total = _totalGroups.value ?: 0
        val index = groupNumber - 1
        if (index in 0 until total) {
            _selectedGroupNumber.value = groupNumber
            stopAutoPlay()
            loadGroup(index)
        }
    }

    fun loadGroup(groupIndex: Int, startWordIndex: Int = 0) {
        val total = _totalGroups.value ?: 0
        if (groupIndex < 0 || groupIndex >= total) return

        preloadGroup(groupIndex, startWordIndex)
    }

    fun nextGroup() {
        val current = _currentGroupIndex.value ?: 0
        val total = _totalGroups.value ?: 0
        if (current < total - 1) {
            stopAutoPlay()
            loadGroup(current + 1)
        }
    }

    fun prevGroup() {
        val current = _currentGroupIndex.value ?: 0
        if (current > 0) {
            stopAutoPlay()
            loadGroup(current - 1)
        }
    }

    fun setRepeatCount(count: Int) {
        _repeatCount.value = count.coerceIn(1, 3)
    }

    fun startAutoPlay() {
        isPaused = false
        currentRepeat = 0
        _isPlaying.value = true
        playNextContent()
    }

    fun pauseAutoPlay() {
        isPaused = true
        _isPlaying.value = false
    }

    fun stopAutoPlay() {
        pauseAutoPlay()
        _playState.value = PlayState.WORD
        // Don't reset word index — preserve position for resuming
    }

    fun playNextContent() {
        if (isPaused) return

        val word = _currentWord.value ?: return
        val corpusId = _corpusId.value ?: return
        val state = _playState.value ?: PlayState.WORD

        // 获取当前播放内容的音频路径
        val audioType = when (state) {
            PlayState.WORD -> "words"
            PlayState.WORD_MEANING -> "meanings"
            PlayState.PHRASE -> "phrases"
            PlayState.PHRASE_MEANING -> "phrase_meanings"
            PlayState.EXAMPLE -> "examples"
            PlayState.EXAMPLE_MEANING -> "example_meanings"
        }

        val audioPath = corpusRepository.getAudioPath(corpusId, word.word, audioType)
        _currentAudioPath.value = audioPath
    }

    /**
     * 当前音频播放完成后调用，自动进入下一个状态
     * @param audioDurationMs 音频时长（毫秒），用作复读空隙
     */
    fun onAudioCompleted(audioDurationMs: Long = 0) {
        if (isPaused) return
        // 用与音频相当的时长做复读空隙，然后进入下一个状态
        val pauseDelay = audioDurationMs.coerceIn(1000, 5000)
        handler.postDelayed({
            if (!isPaused) {
                advancePlayState()
            }
        }, pauseDelay)
    }

    private fun advancePlayState() {
        val currentState = _playState.value ?: PlayState.WORD

        // 按顺序：单词 → 单词中文 → 词组 → 词组中文 → 例句 → 例句中文
        val states = listOf(
            PlayState.WORD,
            PlayState.WORD_MEANING,
            PlayState.PHRASE,
            PlayState.PHRASE_MEANING,
            PlayState.EXAMPLE,
            PlayState.EXAMPLE_MEANING
        )

        val currentIndex = states.indexOf(currentState)

        if (currentIndex < states.size - 1) {
            // 继续当前单词的下一个内容
            _playState.value = states[currentIndex + 1]
            playNextContent()
        } else {
            // 当前单词所有内容播放完毕，检查是否需要重复
            val repeat = repeatCount.value ?: 1
            currentRepeat++
            if (currentRepeat < repeat) {
                // 重复当前单词
                _playState.value = PlayState.WORD
                playNextContent()
            } else {
                // 不再重复，进入下一个单词
                currentRepeat = 0
                nextWord()
            }
        }
    }

    fun nextWord() {
        val currentIndex = _currentWordIndex.value ?: 0
        val group = _currentGroup.value ?: emptyList()

        if (currentIndex < group.size - 1) {
            _currentWordIndex.value = currentIndex + 1
            _currentWord.value = group[currentIndex + 1]
            _playState.value = PlayState.WORD
            playNextContent()
        } else {
            // 当前组播放完毕
            _isPlaying.value = false
            _playState.value = PlayState.WORD
        }
    }

    private val _isCardMode = MutableLiveData<Boolean>(false)
    val isCardMode: LiveData<Boolean> = _isCardMode

    fun toggleCardMode() {
        _isCardMode.value = !(_isCardMode.value ?: false)
    }

    fun exitCardMode() {
        _isCardMode.value = false
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacksAndMessages(null)
    }
}