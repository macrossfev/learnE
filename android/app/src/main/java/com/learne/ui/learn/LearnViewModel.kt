package com.learne.ui.learn

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.ProgressRepository
import com.learne.data.repository.UserManager
import kotlinx.coroutines.launch

class LearnViewModel(
    private val corpusRepository: CorpusRepository,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val uid: String get() = UserManager.userId

    private val _currentWord = MutableLiveData<Word>()
    val currentWord: LiveData<Word> = _currentWord

    private val _wordIndex = MutableLiveData<Int>()
    val wordIndex: LiveData<Int> = _wordIndex

    private val _totalWords = MutableLiveData<Int>()
    val totalWords: LiveData<Int> = _totalWords

    private val _corpusId = MutableLiveData<String>()

    private var words: List<Word> = emptyList()

    fun loadCorpus(corpusId: String) {
        _corpusId.value = corpusId
        viewModelScope.launch {
            words = corpusRepository.loadWords(corpusId)
            _totalWords.value = words.size
            loadNextWord()
        }
    }

    fun loadNextWord() {
        val currentIndex = _wordIndex.value ?: -1
        val nextIndex = currentIndex + 1
        if (nextIndex < words.size) {
            _wordIndex.value = nextIndex
            _currentWord.value = words[nextIndex]
        }
    }

    fun markAsMastered() {
        _currentWord.value?.let { word ->
            viewModelScope.launch {
                progressRepository.markAsMastered(uid, _corpusId.value ?: "", word.word)
            }
        }
        loadNextWord()
    }

    fun getAudioPath(type: String): String? {
        val word = _currentWord.value ?: return null
        val corpusId = _corpusId.value ?: return null
        return corpusRepository.getAudioPath(corpusId, word.word, type)
    }
}