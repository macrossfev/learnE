package com.learne.ui.test

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import kotlinx.coroutines.launch
import kotlin.random.Random

class TestViewModel(
    private val corpusRepository: CorpusRepository
) : ViewModel() {

    private val _currentWord = MutableLiveData<Word>()
    val currentWord: LiveData<Word> = _currentWord

    private val _options = MutableLiveData<List<String>>()
    val options: LiveData<List<String>> = _options

    private val _testType = MutableLiveData<TestType>()
    val testType: LiveData<TestType> = _testType

    private val _score = MutableLiveData<Pair<Int, Int>>() // correct, total
    val score: LiveData<Pair<Int, Int>> = _score

    private var words: List<Word> = emptyList()
    private var testWords: List<Word> = emptyList()
    private var currentIndex = 0
    private var correctCount = 0

    private val _corpusId = MutableLiveData<String>()

    enum class TestType {
        CHOICE, SPELL
    }

    fun loadCorpus(corpusId: String) {
        _corpusId.value = corpusId
        viewModelScope.launch {
            words = corpusRepository.loadWords(corpusId)
        }
    }

    fun startChoiceTest(count: Int = 10) {
        _testType.value = TestType.CHOICE
        testWords = words.shuffled().take(count)
        currentIndex = 0
        correctCount = 0
        _score.value = Pair(0, 0)
        showCurrentQuestion()
    }

    fun startSpellTest(count: Int = 10) {
        _testType.value = TestType.SPELL
        testWords = words.shuffled().take(count)
        currentIndex = 0
        correctCount = 0
        _score.value = Pair(0, 0)
        showCurrentQuestion()
    }

    private fun showCurrentQuestion() {
        if (currentIndex < testWords.size) {
            val word = testWords[currentIndex]
            _currentWord.value = word

            if (_testType.value == TestType.CHOICE) {
                generateOptions(word)
            }
        } else {
            _score.value = Pair(correctCount, testWords.size)
        }
    }

    private fun generateOptions(correctWord: Word) {
        val correctMeaning = correctWord.meaning
        val wrongOptions = words
            .filter { it.word != correctWord.word }
            .shuffled()
            .take(3)
            .map { it.meaning }

        val allOptions = (wrongOptions + correctMeaning).shuffled()
        _options.value = allOptions
    }

    fun checkAnswer(selectedOption: String) {
        val word = _currentWord.value ?: return
        if (selectedOption == word.meaning) {
            correctCount++
        }
        nextQuestion()
    }

    fun checkSpelling(spelledWord: String): Boolean {
        val word = _currentWord.value ?: return false
        val correct = spelledWord.trim().lowercase() == word.word.lowercase()
        if (correct) {
            correctCount++
        }
        nextQuestion()
        return correct
    }

    private fun nextQuestion() {
        currentIndex++
        showCurrentQuestion()
    }

    fun getAudioPath(type: String): String? {
        val word = _currentWord.value ?: return null
        val corpusId = _corpusId.value ?: return null
        return corpusRepository.getAudioPath(corpusId, word.word, type)
    }
}