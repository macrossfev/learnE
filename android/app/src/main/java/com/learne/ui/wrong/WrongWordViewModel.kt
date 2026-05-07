package com.learne.ui.wrong

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learne.data.model.WrongWord
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.StudyRepository
import com.learne.data.repository.UserManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class WrongWordViewModel(
    private val studyRepository: StudyRepository,
    private val corpusRepository: CorpusRepository
) : ViewModel() {

    private val _wrongWords = MutableLiveData<List<WrongWord>>()
    val wrongWords: LiveData<List<WrongWord>> = _wrongWords

    private val _wrongCount = MutableLiveData<Int>(0)
    val wrongCount: LiveData<Int> = _wrongCount

    private val _wordDetails = MutableLiveData<Map<String, com.learne.data.model.Word>>()
    val wordDetails: LiveData<Map<String, com.learne.data.model.Word>> = _wordDetails

    private var corpusId = "catti"

    private val uid: String get() = UserManager.userId

    fun load(corpusId: String) {
        this.corpusId = corpusId
        viewModelScope.launch {
            studyRepository.getWrongWords(uid, corpusId).collect { list ->
                _wrongWords.value = list
                _wrongCount.value = list.size

                // 加载单词详情
                val words = corpusRepository.loadWords(corpusId)
                val details = words.associateBy { it.word }
                _wordDetails.value = details
            }
        }
    }

    fun markCorrected(wrong: WrongWord) {
        viewModelScope.launch {
            studyRepository.markWrongWordCorrected(uid, corpusId, wrong.word)
        }
    }

    fun clearCorrected() {
        viewModelScope.launch {
            val corrected = _wrongWords.value?.filter { it.corrected } ?: emptyList()
            corrected.forEach { wrong ->
                studyRepository.markWrongWordCorrected(uid, corpusId, wrong.word)
            }
        }
    }
}