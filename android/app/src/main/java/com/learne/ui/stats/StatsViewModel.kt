package com.learne.ui.stats

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.ProgressRepository
import com.learne.data.repository.UserManager
import kotlinx.coroutines.launch

class StatsViewModel(
    private val corpusRepository: CorpusRepository,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val _learnedCount = MutableLiveData<Int>(0)
    val learnedCount: LiveData<Int> = _learnedCount

    private val _masteredCount = MutableLiveData<Int>(0)
    val masteredCount: LiveData<Int> = _masteredCount

    private val _totalWords = MutableLiveData<Int>(0)
    val totalWords: LiveData<Int> = _totalWords

    private val _corpusId = MutableLiveData<String>()

    fun loadCorpus(corpusId: String) {
        _corpusId.value = corpusId
        viewModelScope.launch {
            val words = corpusRepository.loadWords(corpusId)
            _totalWords.value = words.size

            _learnedCount.value = progressRepository.getLearnedCount(UserManager.userId, corpusId)
        }
        viewModelScope.launch {
            _masteredCount.value = progressRepository.getMasteredCount(UserManager.userId, corpusId)
        }
    }
}