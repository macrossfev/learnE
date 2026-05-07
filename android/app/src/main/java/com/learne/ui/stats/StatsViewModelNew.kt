package com.learne.ui.stats

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learne.data.model.*
import com.learne.data.repository.StudyRepository
import com.learne.data.repository.UserManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class StatsViewModelNew(private val repository: StudyRepository) : ViewModel() {

    private val _totalLearned = MutableLiveData<Int>(0)
    val totalLearned: LiveData<Int> = _totalLearned

    private val _totalMastered = MutableLiveData<Int>(0)
    val totalMastered: LiveData<Int> = _totalMastered

    private val _totalDuration = MutableLiveData<String>("0h")
    val totalDuration: LiveData<String> = _totalDuration

    private val _goalCurrent = MutableLiveData<Int>(0)
    val goalCurrent: LiveData<Int> = _goalCurrent

    private val _goalTarget = MutableLiveData<Int>(50)
    val goalTarget: LiveData<Int> = _goalTarget

    private val _goalProgress = MutableLiveData<Int>(0)
    val goalProgress: LiveData<Int> = _goalProgress

    private val _streakDays = MutableLiveData<Int>(0)
    val streakDays: LiveData<Int> = _streakDays

    private val _totalCheckIns = MutableLiveData<Int>(0)
    val totalCheckIns: LiveData<Int> = _totalCheckIns

    private val _achievements = MutableLiveData<List<Achievement>>()
    val achievements: LiveData<List<Achievement>> = _achievements

    private val _unlockedCount = MutableLiveData<String>("0/6")
    val unlockedCount: LiveData<String> = _unlockedCount

    private val _studyRecords = MutableLiveData<List<StudyRecord>>()
    val studyRecords: LiveData<List<StudyRecord>> = _studyRecords

    private val _corpusId = MutableLiveData<String>("catti")

    private val uid: String get() = UserManager.userId

    fun load(corpusId: String) {
        _corpusId.value = corpusId
        viewModelScope.launch {
            repository.initDailyGoal(uid, corpusId)
            repository.initAchievements()

            repository.getTotalLearned(uid, corpusId).collect {
                _totalLearned.value = it
                repository.checkAchievements(uid, corpusId)
            }
        }
        viewModelScope.launch {
            repository.getTotalMastered(uid, corpusId).collect {
                _totalMastered.value = it
            }
        }
        viewModelScope.launch {
            repository.getTotalDuration(uid, corpusId).collect { seconds ->
                val hours = seconds / 3600
                val mins = (seconds % 3600) / 60
                _totalDuration.value = if (hours > 0) "${hours}h${mins}m" else "${mins}m"
            }
        }
        viewModelScope.launch {
            val goal = repository.getDailyGoal(uid, corpusId)
            goal?.let {
                _goalCurrent.value = it.currentCount
                _goalTarget.value = it.targetCount
                _goalProgress.value = (it.currentCount * 100 / it.targetCount).coerceAtMost(100)
                _streakDays.value = it.streakDays
                _totalCheckIns.value = it.totalCheckIns
            }
        }
        viewModelScope.launch {
            repository.getAllAchievements().collect { list ->
                _achievements.value = list
                val unlocked = list.filter { it.unlocked }.size
                _unlockedCount.value = "${unlocked}/${list.size}"
            }
        }
        viewModelScope.launch {
            repository.getRecentRecords(uid, corpusId).collect {
                _studyRecords.value = it
            }
        }
    }

    fun setGoal(target: Int) {
        viewModelScope.launch {
            repository.setDailyGoal(uid, _corpusId.value ?: "catti", target)
            _goalTarget.value = target
            _goalProgress.value = (_goalCurrent.value ?: 0) * 100 / target
        }
    }

    fun checkIn() {
        viewModelScope.launch {
            repository.checkIn(uid, _corpusId.value ?: "catti")
            val goal = repository.getDailyGoal(uid, _corpusId.value ?: "catti")
            goal?.let {
                _streakDays.value = it.streakDays
                _totalCheckIns.value = it.totalCheckIns
            }
        }
    }
}