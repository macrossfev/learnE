package com.learne.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.learne.data.model.Corpus

class SettingsViewModel : ViewModel() {

    private val _selectedCorpus = MutableLiveData<Corpus>()
    val selectedCorpus: LiveData<Corpus> = _selectedCorpus

    private val _corpusList = MutableLiveData<List<Corpus>>()
    val corpusList: LiveData<List<Corpus>> = _corpusList

    init {
        _corpusList.value = listOf(Corpus.CET4, Corpus.CATTI)
        _selectedCorpus.value = Corpus.CET4
    }

    fun selectCorpus(corpus: Corpus) {
        _selectedCorpus.value = corpus
    }
}