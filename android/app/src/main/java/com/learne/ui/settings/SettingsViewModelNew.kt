package com.learne.ui.settings

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learne.data.repository.StudyRepository
import kotlinx.coroutines.launch

class SettingsViewModelNew(private val repository: StudyRepository) : ViewModel() {

    private lateinit var prefs: android.content.SharedPreferences

    private fun ensurePrefs(context: android.content.Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences("learne_settings", android.content.Context.MODE_PRIVATE)
        }
    }

    private fun readSetting(key: String, default: String): String {
        return try { prefs.getString(key, default) ?: default } catch (e: UninitializedPropertyAccessException) { default }
    }
    private fun writeSetting(key: String, value: String) {
        try { prefs.edit().putString(key, value).apply() } catch (e: UninitializedPropertyAccessException) { /* ignore */ }
    }

    private val _corpusId = MutableLiveData<String>("catti")
    val corpusId: LiveData<String> = _corpusId

    private val _learnMode = MutableLiveData<String>("auto")
    val learnMode: LiveData<String> = _learnMode

    private val _playSpeed = MutableLiveData<Float>(1.0f)
    val playSpeed: LiveData<Float> = _playSpeed

    private val _playInterval = MutableLiveData<Int>(2000)
    val playInterval: LiveData<Int> = _playInterval

    private val _nightMode = MutableLiveData<Boolean>(false)
    val nightMode: LiveData<Boolean> = _nightMode

    private val _showPhonetic = MutableLiveData<Boolean>(true)
    val showPhonetic: LiveData<Boolean> = _showPhonetic

    private val _showExample = MutableLiveData<Boolean>(true)
    val showExample: LiveData<Boolean> = _showExample

    private val _reminderEnabled = MutableLiveData<Boolean>(false)
    val reminderEnabled: LiveData<Boolean> = _reminderEnabled

    private val _reminderHour = MutableLiveData<Int>(9)
    val reminderHour: LiveData<Int> = _reminderHour

    private val _reminderMinute = MutableLiveData<Int>(0)
    val reminderMinute: LiveData<Int> = _reminderMinute

    fun initPrefs(context: android.content.Context) {
        ensurePrefs(context)
        _corpusId.value = readSetting("corpus_id", "catti")
        _nightMode.value = prefs.getBoolean("night_mode", false)
        _showPhonetic.value = prefs.getBoolean("show_phonetic", true)
        _showExample.value = prefs.getBoolean("show_example", true)
    }

    fun load() {
        viewModelScope.launch {
            val reminder = repository.getReminder()
            reminder?.let {
                _reminderEnabled.value = it.enabled
                _reminderHour.value = it.hour
                _reminderMinute.value = it.minute
            }
        }
    }

    fun setCorpus(id: String) {
        _corpusId.value = id
        writeSetting("corpus_id", id)
    }

    fun setLearnMode(mode: String) {
        _learnMode.value = mode
        writeSetting("learn_mode", mode)
    }

    fun setPlaySpeed(speed: Float) {
        _playSpeed.value = speed
        writeSetting("play_speed", speed.toString())
    }

    fun setPlayInterval(interval: Int) {
        _playInterval.value = interval
        writeSetting("play_interval", interval.toString())
    }

    fun setNightMode(enabled: Boolean) {
        _nightMode.value = enabled
        prefs.edit().putBoolean("night_mode", enabled).apply()
    }

    fun setShowPhonetic(show: Boolean) {
        _showPhonetic.value = show
        prefs.edit().putBoolean("show_phonetic", show).apply()
    }

    fun setShowExample(show: Boolean) {
        _showExample.value = show
        prefs.edit().putBoolean("show_example", show).apply()
    }

    fun setReminderEnabled(enabled: Boolean) {
        _reminderEnabled.value = enabled
        saveReminder()
    }

    fun setReminderTime(hour: Int, minute: Int) {
        _reminderHour.value = hour
        _reminderMinute.value = minute
        saveReminder()
    }

    private fun saveReminder() {
        viewModelScope.launch {
            repository.setReminder(
                _reminderEnabled.value ?: false,
                _reminderHour.value ?: 9,
                _reminderMinute.value ?: 0,
                "开始今天的学习吧！"
            )
        }
    }
}