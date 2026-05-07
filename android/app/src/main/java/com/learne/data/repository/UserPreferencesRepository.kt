package com.learne.data.repository

import android.content.Context
import androidx.core.content.edit

/**
 * 用户偏好设置：语料库选择、学习模式偏好等
 */
object UserPreferencesRepository {
    private const val PREFS_NAME = "learne_prefs"
    private const val KEY_SELECTED_CORPUS = "selected_corpus"
    private const val KEY_HAS_SELECTED_CORPUS = "has_selected_corpus"
    private const val KEY_REPEAT_COUNT = "repeat_count"
    private const val KEY_LAST_LISTEN_GROUP = "last_listen_group"
    private const val KEY_LAST_LISTEN_WORD_INDEX = "last_listen_word_index"
    private const val KEY_LAST_LISTEN_CORPUS = "last_listen_corpus"

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun checkPrefs() {
        if (prefs == null) throw IllegalStateException("UserPreferencesRepository not initialized. Call init() first.")
    }

    var selectedCorpusId: String
        get() {
            checkPrefs()
            return prefs!!.getString(KEY_SELECTED_CORPUS, "catti") ?: "catti"
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putString(KEY_SELECTED_CORPUS, value) }
        }

    var hasSelectedCorpus: Boolean
        get() {
            checkPrefs()
            return prefs!!.getBoolean(KEY_HAS_SELECTED_CORPUS, false)
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putBoolean(KEY_HAS_SELECTED_CORPUS, value) }
        }

    var repeatCount: Int
        get() {
            checkPrefs()
            return prefs!!.getInt(KEY_REPEAT_COUNT, 1)
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putInt(KEY_REPEAT_COUNT, value) }
        }

    fun selectCorpus(corpusId: String) {
        selectedCorpusId = corpusId
        hasSelectedCorpus = true
    }

    // Listen-read resume position
    var lastListenCorpus: String
        get() {
            checkPrefs()
            return prefs!!.getString(KEY_LAST_LISTEN_CORPUS, "") ?: ""
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putString(KEY_LAST_LISTEN_CORPUS, value) }
        }

    var lastListenGroupIndex: Int
        get() {
            checkPrefs()
            return prefs!!.getInt(KEY_LAST_LISTEN_GROUP, 0)
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putInt(KEY_LAST_LISTEN_GROUP, value) }
        }

    var lastListenWordIndex: Int
        get() {
            checkPrefs()
            return prefs!!.getInt(KEY_LAST_LISTEN_WORD_INDEX, 0)
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putInt(KEY_LAST_LISTEN_WORD_INDEX, value) }
        }

    fun saveListenPosition(corpusId: String, groupIndex: Int, wordIndex: Int) {
        lastListenCorpus = corpusId
        lastListenGroupIndex = groupIndex
        lastListenWordIndex = wordIndex
    }
}
