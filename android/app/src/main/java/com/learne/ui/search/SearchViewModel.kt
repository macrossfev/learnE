package com.learne.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learne.data.model.UserNote
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.StudyRepository
import com.learne.data.repository.UserManager
import kotlinx.coroutines.launch

class SearchViewModel(
    private val corpusRepository: CorpusRepository,
    private val studyRepository: StudyRepository
) : ViewModel() {

    private val _searchResults = MutableLiveData<List<Word>>()
    val searchResults: LiveData<List<Word>> = _searchResults

    private val _selectedWord = MutableLiveData<Word>()
    val selectedWord: LiveData<Word> = _selectedWord

    private val _note = MutableLiveData<String>()
    val note: LiveData<String> = _note

    private val _allWords = MutableLiveData<List<Word>>()
    private var corpusId = "catti"

    private val uid: String get() = UserManager.userId

    fun loadCorpus(id: String) {
        corpusId = id
        viewModelScope.launch {
            val words = corpusRepository.loadWords(id)
            _allWords.value = words
        }
    }

    fun search(query: String) {
        val all = _allWords.value ?: emptyList()
        val results = all.filter {
            it.word.contains(query, ignoreCase = true) ||
            it.meaning.contains(query, ignoreCase = true)
        }.take(20)
        _searchResults.value = results
    }

    fun selectWord(word: Word) {
        _selectedWord.value = word
        viewModelScope.launch {
            val existingNote = studyRepository.getNote(uid, corpusId, word.word)
            _note.value = existingNote?.note ?: ""
        }
    }

    fun saveNote(text: String) {
        val word = _selectedWord.value ?: return
        viewModelScope.launch {
            studyRepository.saveNote(uid, corpusId, word.word, text)
            _note.value = text
        }
    }
}