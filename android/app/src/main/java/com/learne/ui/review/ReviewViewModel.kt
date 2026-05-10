package com.learne.ui.review

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learne.data.model.Word
import com.learne.data.model.WrongWord
import com.learne.data.model.WordProgress
import com.learne.data.db.AppDatabase
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.ProgressRepository
import com.learne.data.repository.UserManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 复习模式：3步流程
 * Step 1: 展示单词+释义（自动播放音频）
 * Step 2: 选择题（从其他单词中选3个干扰项）
 * Step 3: 填词题（给出释义，输入对应单词）
 */
class ReviewViewModel(
    private val corpusRepository: CorpusRepository
) : ViewModel() {

    private val progressRepository = ProgressRepository(corpusRepository.context)

    // 复习列表
    private val _reviewWords = MutableLiveData<List<WordProgress>>()
    val reviewWords: LiveData<List<WordProgress>> = _reviewWords

    // 当前展示的 Word 详情（Step 1）
    private val _currentWord = MutableLiveData<Word>()
    val currentWord: LiveData<Word> = _currentWord

    // 当前步骤：1=展示, 2=选择, 3=填词
    private val _currentStep = MutableLiveData<Int>(1)
    val currentStep: LiveData<Int> = _currentStep

    // 当前索引
    private val _currentIndex = MutableLiveData<Int>(0)
    val currentIndex: LiveData<Int> = _currentIndex

    // 总进度
    private val _totalCount = MutableLiveData<Int>(0)
    val totalCount: LiveData<Int> = _totalCount

    // 是否已完成复习
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

    // 音频播放路径
    private val _currentAudioPath = MutableLiveData<String>()
    val currentAudioPath: LiveData<String> = _currentAudioPath

    // 待复习数量
    private val _reviewCount = MutableLiveData<Int>(0)
    val reviewCount: LiveData<Int> = _reviewCount

    private var corpusId: String = "catti"
    private var allWords: List<Word> = emptyList()
    private var reviewList: List<WordProgress> = emptyList()

    private val uid: String get() = UserManager.userId

    data class ChoiceOption(
        val meaning: String,
        val correct: Boolean
    )

    fun loadCorpus(newCorpusId: String) {
        corpusId = newCorpusId
        viewModelScope.launch {
            allWords = corpusRepository.loadWords(corpusId)
            val db = AppDatabase.getDatabase(corpusRepository.context ?: return@launch)
            val now = System.currentTimeMillis()
            val progressList = db.progressDao().getWordsForReview("${uid}_$corpusId", now).first()
            reviewList = progressList
            _reviewWords.value = progressList
            _reviewCount.value = progressList.size
            _totalCount.value = progressList.size

            if (progressList.isNotEmpty()) {
                _completed.value = false
                _currentStep.value = 1
                _currentIndex.value = 0
                showCurrentWord()
            } else {
                _reviewCount.value = 0
            }
        }
    }

    fun loadWrongWordsForReview(wrongWordStrings: List<String>) {
        viewModelScope.launch {
            allWords = corpusRepository.loadWords(corpusId)
            reviewList = wrongWordStrings.map { word ->
                WordProgress(id = "${uid}_${corpusId}_${word}", corpusId = "${uid}_$corpusId", word = word)
            }
            _reviewWords.value = reviewList
            _reviewCount.value = reviewList.size
            _totalCount.value = reviewList.size

            if (reviewList.isNotEmpty()) {
                _completed.value = false
                _currentStep.value = 1
                _currentIndex.value = 0
                showCurrentWord()
            } else {
                _reviewCount.value = 0
            }
        }
    }

    fun startReview() {
        if (reviewList.isEmpty()) return
        _completed.value = false
        _currentStep.value = 1
        _currentIndex.value = 0
        _selectedChoiceIndex.value = -1
        _choiceCorrect.value = false
        _choiceWrong.value = false
        _spellInput.value = ""
        _spellWrong.value = false
        _spellHint.value = ""
        showCurrentWord()
    }

    private fun showCurrentWord() {
        if (reviewList.isEmpty()) return
        val index = (_currentIndex.value ?: 0).coerceIn(0, reviewList.size - 1)
        val progress = reviewList[index]
        val word = allWords.find { it.word == progress.word }
        if (word != null) {
            _currentWord.value = word
            // Step 1 时自动播放音频
            if ((_currentStep.value ?: 1) == 1) {
                val path = corpusRepository.getAudioPath(corpusId, word.word, "words")
                _currentAudioPath.value = path
            }
        }
    }

    // ===== 导航 =====

    fun nextStep() {
        val step = (_currentStep.value ?: 1)
        if (step < 3) {
            if (step == 1) {
                prepareChoice()
            }
            _currentStep.value = step + 1
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
        if (index < reviewList.size - 1) {
            _currentIndex.value = index + 1
            _currentStep.value = 1
            _selectedChoiceIndex.value = -1
            _choiceCorrect.value = false
            _choiceWrong.value = false
            _spellInput.value = ""
            _spellWrong.value = false
            _spellHint.value = ""
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
            _selectedChoiceIndex.value = -1
            _choiceCorrect.value = false
            _choiceWrong.value = false
            _spellInput.value = ""
            _spellWrong.value = false
            _spellHint.value = ""
            showCurrentWord()
        }
    }

    // ===== 选择题 =====

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
            // 选对后自动进入填词
            viewModelScope.launch {
                kotlinx.coroutines.delay(800)
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
                kotlinx.coroutines.delay(1500)
                if ((_selectedChoiceIndex.value ?: -1) == index) {
                    _selectedChoiceIndex.value = -1
                    _choiceWrong.value = false
                }
            }
        }
    }

    // ===== 填词 =====

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
            // 正确 - 更新复习进度
            viewModelScope.launch {
                updateReviewProgress(word.word, true)
                nextWord()
            }
        } else {
            _spellWrong.value = true
            _spellHint.value = "正确答案：${word.word}"
            saveError(word, "spell", input, word.word)
            // 2秒后清除错误状态
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                _spellWrong.value = false
                _spellHint.value = ""
            }
        }
    }

    private suspend fun updateReviewProgress(word: String, correct: Boolean) {
        progressRepository.updateReviewProgress(uid, corpusId, word, correct)
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

    fun getAudioPath(type: String): String {
        val word = _currentWord.value ?: return ""
        return corpusRepository.getAudioPath(corpusId, word.word, type)
    }

    fun onAudioCompleted(durationMs: Long) {
        // 可以在这里处理音频播放完成后的逻辑（如自动进入下一步）
    }

    fun resetForNewReview() {
        _completed.value = false
        _currentStep.value = 1
        _currentIndex.value = 0
        _selectedChoiceIndex.value = -1
        _choiceCorrect.value = false
        _choiceWrong.value = false
        _spellInput.value = ""
        _spellWrong.value = false
        _spellHint.value = ""
    }
}
