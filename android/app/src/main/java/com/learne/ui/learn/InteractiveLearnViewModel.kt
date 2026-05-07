package com.learne.ui.learn

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learne.data.model.Word
import com.learne.data.model.WrongWord
import com.learne.data.db.AppDatabase
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.ProgressRepository
import com.learne.data.repository.UserManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.*

/**
 * 交互学习模式：3步流程 per word
 * Step 1: 展示单词+释义（自动播放6段音频序列）
 * Step 2: 选择题（从其他单词中选3个干扰项）
 * Step 3: 填词（给出释义，输入对应单词）
 * 全部完成后标记掌握并写入 word_progress
 */
class InteractiveLearnViewModel(
    private val corpusRepository: CorpusRepository,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val uid: String get() = UserManager.userId

    // 学习列表
    private var allWords: List<Word> = emptyList()
    private var learnList: List<Word> = emptyList()

    private var corpusId: String = "catti"

    // 当前展示的单词
    private val _currentWord = MutableLiveData<Word>()
    val currentWord: LiveData<Word> = _currentWord

    // 当前步骤：1=展示, 2=选择, 3=填词
    private val _currentStep = MutableLiveData<Int>(1)
    val currentStep: LiveData<Int> = _currentStep

    // 当前索引
    private val _currentIndex = MutableLiveData<Int>(0)
    val currentIndex: LiveData<Int> = _currentIndex

    // 总数
    private val _totalCount = MutableLiveData<Int>(0)
    val totalCount: LiveData<Int> = _totalCount

    // 是否已完成学习
    private val _completed = MutableLiveData<Boolean>(false)
    val completed: LiveData<Boolean> = _completed

    // 选择题选项
    private val _choiceOptions = MutableLiveData<List<ChoiceOption>>(emptyList())
    val choiceOptions: LiveData<List<ChoiceOption>> = _choiceOptions

    private val _selectedChoiceIndex = MutableLiveData<Int>(-1)
    val selectedChoiceIndex: LiveData<Int> = _selectedChoiceIndex

    private val _choiceCorrect = MutableLiveData<Boolean>(false)
    val choiceCorrect: LiveData<Boolean> = _choiceCorrect

    private val _choiceWrong = MutableLiveData<Boolean>(false)
    val choiceWrong: LiveData<Boolean> = _choiceWrong

    // 填词
    private val _spellInput = MutableLiveData<String>("")
    val spellInput: LiveData<String> = _spellInput

    private val _spellWrong = MutableLiveData<Boolean>(false)
    val spellWrong: LiveData<Boolean> = _spellWrong

    private val _spellHint = MutableLiveData<String>("")
    val spellHint: LiveData<String> = _spellHint

    // 音频播放路径（6段序列）
    private val _currentAudioPath = MutableLiveData<String>()
    val currentAudioPath: LiveData<String> = _currentAudioPath

    // 今日已学数量
    private val _todayLearnedCount = MutableLiveData<Int>(0)
    val todayLearnedCount: LiveData<Int> = _todayLearnedCount

    // Loading state - 初始为false，因为不自动加载
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error message
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    data class ChoiceOption(
        val meaning: String,
        val correct: Boolean
    )

    // ====== 音频序列播放 ======

    private val audioTypes = listOf("words", "meanings", "phrases", "phrase_meanings", "examples", "example_meanings")
    private var audioQueue: List<String> = emptyList()
    private var audioQueueIndex = 0
    private var isPlayingAudioSequence = false

    fun loadCorpus(newCorpusId: String, groupSize: Int = 20) {
        corpusId = newCorpusId
        android.util.Log.d("InteractiveLearnViewModel", "loadCorpus called, setting loading=true")
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            android.util.Log.d("InteractiveLearnViewModel", "loadCorpus coroutine start: $corpusId")
            try {
                android.util.Log.d("InteractiveLearnViewModel", "Loading words from network...")
                allWords = withTimeout(15000) {
                    corpusRepository.loadWords(corpusId)
                }
                android.util.Log.d("InteractiveLearnViewModel", "Words loaded: ${allWords.size}")

                // 暂时跳过数据库，直接显示前20个单词
                learnList = allWords.take(groupSize)
                _totalCount.value = learnList.size
                android.util.Log.d("InteractiveLearnViewModel", "Learn list: ${learnList.size}")

                if (learnList.isNotEmpty()) {
                    _completed.value = false
                    _currentStep.value = 1
                    _currentIndex.value = 0
                    _todayLearnedCount.value = 0
                    android.util.Log.d("InteractiveLearnViewModel", "Showing first word")
                    showCurrentWord()
                } else {
                    android.util.Log.d("InteractiveLearnViewModel", "No words")
                    _completed.value = true
                }
            } catch (e: TimeoutCancellationException) {
                android.util.Log.e("InteractiveLearnViewModel", "Connection timeout after 15s")
                _errorMessage.value = "网络连接超时，请检查网络"
                learnList = emptyList()
                _totalCount.value = 0
                _completed.value = true
            } catch (e: Exception) {
                android.util.Log.e("InteractiveLearnViewModel", "Error: ${e.message}", e)
                _errorMessage.value = "错误：${e.message ?: "未知"}"
                learnList = emptyList()
                _totalCount.value = 0
                _completed.value = true
            } finally {
                android.util.Log.d("InteractiveLearnViewModel", "loadCorpus finally, setting loading=false")
                _isLoading.value = false
                android.util.Log.d("InteractiveLearnViewModel", "loadCorpus done")
            }
        }
    }

    fun startLearn() {
        if (learnList.isEmpty()) return
        _completed.value = false
        _currentStep.value = 1
        _currentIndex.value = 0
        _todayLearnedCount.value = 0
        resetQuizState()
        showCurrentWord()
    }

    private fun showCurrentWord() {
        if (learnList.isEmpty()) return
        val index = (_currentIndex.value ?: 0).coerceIn(0, learnList.size - 1)
        val word = learnList[index]
        _currentWord.value = word
        // Step 1 时播放6段音频序列
        if ((_currentStep.value ?: 1) == 1) {
            playAudioSequence(word)
        }
    }

    private fun playAudioSequence(word: Word) {
        isPlayingAudioSequence = true
        audioQueue = audioTypes.map { corpusRepository.getAudioPath(corpusId, word.word, it) }
        audioQueueIndex = 0
        playNextAudioInSequence()
    }

    private fun playNextAudioInSequence() {
        if (audioQueueIndex < audioQueue.size) {
            _currentAudioPath.value = audioQueue[audioQueueIndex]
        } else {
            isPlayingAudioSequence = false
        }
    }

    fun onAudioCompleted(durationMs: Long) {
        if (isPlayingAudioSequence) {
            audioQueueIndex++
            playNextAudioInSequence()
        }
    }

    // ====== 导航 ======

    fun nextStep() {
        val step = (_currentStep.value ?: 1)
        if (step < 3) {
            if (step == 1) {
                prepareChoice()
            }
            _currentStep.value = step + 1
            // 进入选择题时停止音频序列
            isPlayingAudioSequence = false
        }
    }

    fun prevStep() {
        val step = (_currentStep.value ?: 1)
        if (step > 1) {
            _currentStep.value = step - 1
            _spellInput.value = ""
            _spellWrong.value = false
            _selectedChoiceIndex.value = -1
            _choiceWrong.value = false
        }
    }

    fun nextWord() {
        val index = (_currentIndex.value ?: 0)
        if (index < learnList.size - 1) {
            _currentIndex.value = index + 1
            _currentStep.value = 1
            _todayLearnedCount.value = (_todayLearnedCount.value ?: 0) + 1
            resetQuizState()
            showCurrentWord()
        } else {
            _completed.value = true
        }
    }

    fun prevWord() {
        val index = (_currentIndex.value ?: 0)
        if (index > 0) {
            _currentIndex.value = index - 1
            _currentStep.value = 1
            resetQuizState()
            showCurrentWord()
        }
    }

    private fun resetQuizState() {
        _selectedChoiceIndex.value = -1
        _choiceCorrect.value = false
        _choiceWrong.value = false
        _spellInput.value = ""
        _spellWrong.value = false
        _spellHint.value = ""
    }

    // ====== 选择题 =====

    private fun prepareChoice() {
        val word = _currentWord.value ?: return
        val options = generateChoices(word, allWords)
        _choiceOptions.value = options
        _selectedChoiceIndex.value = -1
        _choiceCorrect.value = false
        _choiceWrong.value = false
    }

    private fun generateChoices(currentWord: Word, allWords: List<Word>): List<ChoiceOption> {
        val others = allWords.filter { it.word != currentWord.word }
        val shuffled = others.shuffled().take(3)
        val wrongOptions = shuffled.map { ChoiceOption(it.meaning, false) }
        val correctOption = ChoiceOption(currentWord.meaning, true)
        return (wrongOptions + correctOption).shuffled()
    }

    fun selectChoice(index: Int) {
        val options = _choiceOptions.value ?: return
        val option = options[index]
        val word = _currentWord.value ?: return

        _selectedChoiceIndex.value = index

        if (option.correct) {
            _choiceCorrect.value = true
            _choiceWrong.value = false
            viewModelScope.launch {
                delay(800)
                if ((_selectedChoiceIndex.value ?: -1) == index) {
                    _currentStep.value = 3
                    _spellInput.value = ""
                    _spellWrong.value = false
                }
            }
        } else {
            _choiceWrong.value = true
            _choiceCorrect.value = false
            saveError(word, "choice", option.meaning, word.meaning)
            viewModelScope.launch {
                delay(1500)
                if ((_selectedChoiceIndex.value ?: -1) == index) {
                    _selectedChoiceIndex.value = -1
                    _choiceWrong.value = false
                }
            }
        }
    }

    // ====== 填词 =====

    fun onSpellInputChanged(input: String) {
        _spellInput.value = input
        _spellWrong.value = false
    }

    fun onSpellSubmit() {
        val word = _currentWord.value ?: return
        val input = (_spellInput.value ?: "").trim()
        if (input.isEmpty()) return

        val expected = word.word.lowercase()
        val actual = input.lowercase()

        if (actual == expected) {
            viewModelScope.launch {
                markAsLearned(word)
                nextWord()
            }
        } else {
            _spellWrong.value = true
            _spellHint.value = "正确答案：${word.word}"
            saveError(word, "spell", input, word.word)
            viewModelScope.launch {
                delay(2000)
                _spellWrong.value = false
                _spellHint.value = ""
                _spellInput.value = ""
            }
        }
    }

    private suspend fun markAsLearned(word: Word) {
        progressRepository.recordLearned(uid, corpusId, word.word)
    }

    private fun saveError(word: Word, type: String, userAnswer: String, correctAnswer: String) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(corpusRepository.context ?: return@launch)
            val uidCorpus = "${uid}_$corpusId"
            val existing = db.wrongWordDao().getByWord(uidCorpus, word.word)
            if (existing != null) {
                db.wrongWordDao().update(
                    existing.copy(wrongCount = existing.wrongCount + 1, lastWrongTime = System.currentTimeMillis())
                )
            } else {
                val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
                val wrongWord = WrongWord(
                    id = "${uid}_${corpusId}_${word.word}_${timestamp}",
                    corpusId = uidCorpus,
                    word = word.word,
                    testType = type
                )
                db.wrongWordDao().insert(wrongWord)
            }
        }
    }

    fun onMarkUnfamiliar() {
        val word = _currentWord.value ?: return
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(corpusRepository.context ?: return@launch)
            val uidCorpus = "${uid}_$corpusId"
            val existing = db.wrongWordDao().getByWord(uidCorpus, word.word)
            if (existing == null) {
                val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
                val wrongWord = WrongWord(
                    id = "${uid}_${corpusId}_${word.word}_${timestamp}",
                    corpusId = uidCorpus,
                    word = word.word,
                    testType = "focus"
                )
                db.wrongWordDao().insert(wrongWord)
            }
        }
    }

    fun resetForNewLearn() {
        _completed.value = false
        _currentStep.value = 1
        _currentIndex.value = 0
        _todayLearnedCount.value = 0
        resetQuizState()
    }
}
