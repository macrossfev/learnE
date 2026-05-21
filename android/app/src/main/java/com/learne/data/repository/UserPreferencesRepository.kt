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
    private const val KEY_LISTEN_PLAY_MODE = "listen_play_mode"
    private const val KEY_LISTEN_GROUP_PLAY_MODE = "listen_group_play_mode"
    private const val KEY_LISTEN_BLIND_MODE = "listen_blind_mode"
    private const val KEY_PLAN_CORPUS = "plan_corpus"
    private const val KEY_PLAN_GROUP_SIZE = "plan_group_size"

    @Volatile
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

    var listenPlayMode: String
        get() {
            checkPrefs()
            return prefs!!.getString(KEY_LISTEN_PLAY_MODE, "ORDER") ?: "ORDER"
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putString(KEY_LISTEN_PLAY_MODE, value) }
        }

    var listenGroupPlayMode: String
        get() {
            checkPrefs()
            return prefs!!.getString(KEY_LISTEN_GROUP_PLAY_MODE, "LOOP_GROUP") ?: "LOOP_GROUP"
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putString(KEY_LISTEN_GROUP_PLAY_MODE, value) }
        }

    var listenBlindMode: Boolean
        get() {
            checkPrefs()
            return prefs!!.getBoolean(KEY_LISTEN_BLIND_MODE, false)
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putBoolean(KEY_LISTEN_BLIND_MODE, value) }
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

    // Study Plan (multiple named plans, stored as JSON)
    data class PlanSave(
        val name: String,
        val corpusId: String,
        val groupSize: Int,
        val totalWords: Int,
        val totalGroups: Int,
        val createdAt: Long,
        var currentGroupIndex: Int = 0,
        var currentWordIndex: Int = 0,
        val completedGroups: String = "[]"
    )

    private fun getPlanSaves(): MutableList<PlanSave> {
        checkPrefs()
        val json = prefs!!.getString("plan_saves", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<PlanSave>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(PlanSave(
                    name = obj.getString("name"),
                    corpusId = obj.getString("corpusId"),
                    groupSize = obj.getInt("groupSize"),
                    totalWords = obj.optInt("totalWords", 0),
                    totalGroups = obj.optInt("totalGroups", 0),
                    createdAt = obj.getLong("createdAt"),
                    currentGroupIndex = obj.optInt("currentGroupIndex", 0),
                    currentWordIndex = obj.optInt("currentWordIndex", 0),
                    completedGroups = obj.optString("completedGroups", "[]")
                ))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun savePlanSaves(list: List<PlanSave>) {
        checkPrefs()
        val arr = org.json.JSONArray()
        for (plan in list) {
            arr.put(org.json.JSONObject().apply {
                put("name", plan.name)
                put("corpusId", plan.corpusId)
                put("groupSize", plan.groupSize)
                put("totalWords", plan.totalWords)
                put("totalGroups", plan.totalGroups)
                put("createdAt", plan.createdAt)
                put("currentGroupIndex", plan.currentGroupIndex)
                put("currentWordIndex", plan.currentWordIndex)
                put("completedGroups", plan.completedGroups)
            })
        }
        prefs!!.edit { putString("plan_saves", arr.toString()) }
    }

    fun getAllPlanSaves(): List<PlanSave> = getPlanSaves()

    fun createPlan(name: String, corpusId: String, groupSize: Int, totalWords: Int = 0) {
        val list = getPlanSaves().toMutableList()
        list.removeAll { it.name == name }
        val totalGroups = if (totalWords > 0 && groupSize > 0) kotlin.math.ceil(totalWords.toDouble() / groupSize).toInt() else 0
        list.add(PlanSave(name, corpusId, groupSize, totalWords, totalGroups, System.currentTimeMillis()))
        savePlanSaves(list)
    }

    fun deletePlan(index: Int) {
        val list = getPlanSaves().toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            savePlanSaves(list)
        }
    }

    fun loadPlan(index: Int): PlanSave? {
        val list = getPlanSaves()
        return list.getOrNull(index)
    }

    fun savePlanProgress(planIndex: Int, groupIndex: Int, wordIndex: Int) {
        val list = getPlanSaves().toMutableList()
        if (planIndex in list.indices) {
            list[planIndex].currentGroupIndex = groupIndex
            list[planIndex].currentWordIndex = wordIndex
            savePlanSaves(list)
        }
    }

    fun getPlanIndex(name: String): Int {
        return getPlanSaves().indexOfFirst { it.name == name }
    }

    fun markGroupCompletedForPlan(planIndex: Int, groupIndex: Int) {
        val list = getPlanSaves().toMutableList()
        if (planIndex !in list.indices) return
        val plan = list[planIndex]
        val completed = try {
            val arr = org.json.JSONArray(plan.completedGroups)
            List(arr.length()) { arr.getInt(it) }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
        if (!completed.contains(groupIndex)) {
            completed.add(groupIndex)
        }
        val newArr = org.json.JSONArray()
        completed.forEach { newArr.put(it) }
        list[planIndex] = plan.copy(
            completedGroups = newArr.toString(),
            currentGroupIndex = groupIndex + 1,
            currentWordIndex = 0
        )
        savePlanSaves(list)
    }

    fun getPlanCompletedGroups(planIndex: Int): List<Int> {
        val plan = getPlanSaves().getOrNull(planIndex) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(plan.completedGroups)
            List(arr.length()) { arr.getInt(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Quiz pass/fail tracking (SharedPreferences per corpus)
    fun getQuizPassedGroups(corpusId: String): Set<Int> {
        val json = prefs?.getString("quiz_passed_$corpusId", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            List(arr.length()) { i -> arr.getInt(i) }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    fun markQuizPassed(corpusId: String, groupIndex: Int) {
        val existing = getQuizPassedGroups(corpusId).toMutableSet()
        existing.add(groupIndex)
        val newArr = org.json.JSONArray(existing.toList())
        prefs?.edit { putString("quiz_passed_$corpusId", newArr.toString()) }
    }

    fun getQuizFailedGroups(corpusId: String): Set<Int> {
        val json = prefs?.getString("quiz_failed_$corpusId", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            List(arr.length()) { i -> arr.getInt(i) }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    fun markQuizFailed(corpusId: String, groupIndex: Int) {
        val existing = getQuizFailedGroups(corpusId)
        val newArr = org.json.JSONArray(existing.toMutableList().also { it.add(groupIndex) })
        prefs?.edit { putString("quiz_failed_$corpusId", newArr.toString()) }
    }

    fun clearQuizFailed(corpusId: String, groupIndex: Int) {
        val existing = getQuizFailedGroups(corpusId).toMutableSet()
        existing.remove(groupIndex)
        val newArr = org.json.JSONArray(existing.toList())
        prefs?.edit { putString("quiz_failed_$corpusId", newArr.toString()) }
    }

    fun renamePlan(index: Int, newName: String) {
        val list = getPlanSaves().toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(name = newName)
            savePlanSaves(list)
        }
    }

    fun updatePlanTotalWords(planIndex: Int, totalWords: Int) {
        val list = getPlanSaves().toMutableList()
        if (planIndex in list.indices) {
            val plan = list[planIndex]
            val totalGroups = if (totalWords > 0 && plan.groupSize > 0) kotlin.math.ceil(totalWords.toDouble() / plan.groupSize).toInt() else 0
            list[planIndex] = plan.copy(totalWords = totalWords, totalGroups = totalGroups)
            savePlanSaves(list)
        }
    }

    // Interactive Learn: group completion tracking
    fun getCompletedGroups(corpusId: String): List<Int> {
        checkPrefs()
        val json = prefs!!.getString("completed_groups_$corpusId", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            List(arr.length()) { arr.getInt(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun markGroupCompleted(corpusId: String, groupIndex: Int) {
        checkPrefs()
        val completed = getCompletedGroups(corpusId).toMutableList()
        if (!completed.contains(groupIndex)) {
            completed.add(groupIndex)
        }
        val arr = org.json.JSONArray()
        completed.forEach { arr.put(it) }
        prefs!!.edit { putString("completed_groups_$corpusId", arr.toString()) }
    }

    fun isGroupCompleted(corpusId: String, groupIndex: Int): Boolean {
        return getCompletedGroups(corpusId).contains(groupIndex)
    }

    // Legacy single-plan support (for backward compatibility)
    var hasActivePlan: Boolean
        get() {
            checkPrefs()
            return prefs!!.getString(KEY_PLAN_CORPUS, null) != null
        }
        private set(value) {
            checkPrefs()
            if (!value) prefs!!.edit { remove(KEY_PLAN_CORPUS); remove(KEY_PLAN_GROUP_SIZE) }
        }

    var planCorpusId: String
        get() {
            checkPrefs()
            return prefs!!.getString(KEY_PLAN_CORPUS, "") ?: ""
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putString(KEY_PLAN_CORPUS, value) }
        }

    var planGroupSize: Int
        get() {
            checkPrefs()
            return prefs!!.getInt(KEY_PLAN_GROUP_SIZE, 30).coerceAtLeast(1)
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putInt(KEY_PLAN_GROUP_SIZE, value) }
        }

    fun savePlan(corpusId: String, groupSize: Int) {
        planCorpusId = corpusId
        planGroupSize = groupSize
        planCurrentGroupIndex = 0
        planCurrentWordIndex = 0
    }

    var planCurrentGroupIndex: Int
        get() {
            checkPrefs()
            return prefs!!.getInt("plan_current_group", 0)
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putInt("plan_current_group", value) }
        }

    var planCurrentWordIndex: Int
        get() {
            checkPrefs()
            return prefs!!.getInt("plan_current_word", 0)
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putInt("plan_current_word", value) }
        }

    fun updatePlanProgress(groupIndex: Int, wordIndex: Int) {
        planCurrentGroupIndex = groupIndex
        planCurrentWordIndex = wordIndex
    }

    fun clearPlan() {
        hasActivePlan = false
        planCurrentGroupIndex = 0
        planCurrentWordIndex = 0
    }

    // Daily challenge completion tracking
    fun isDailyChallengeCompleted(corpusId: String, date: String): Boolean {
        checkPrefs()
        return prefs!!.getBoolean("daily_challenge_${corpusId}_${date}", false)
    }

    fun markDailyChallengeCompleted(corpusId: String, date: String) {
        checkPrefs()
        prefs!!.edit { putBoolean("daily_challenge_${corpusId}_${date}", true) }
    }

    fun getDailyChallengeScore(corpusId: String, date: String): Int {
        checkPrefs()
        return prefs!!.getInt("daily_challenge_score_${corpusId}_${date}", 0)
    }

    fun saveDailyChallengeScore(corpusId: String, date: String, score: Int) {
        checkPrefs()
        prefs!!.edit { putInt("daily_challenge_score_${corpusId}_${date}", score) }
    }

    // Plan-level daily challenge tracking
    fun markDailyChallengeCompletedForPlan(planIndex: Int, date: String) {
        checkPrefs()
        prefs!!.edit { putBoolean("plan_daily_challenge_${planIndex}_${date}", true) }
    }

    fun isDailyChallengeCompletedForPlan(planIndex: Int, date: String): Boolean {
        checkPrefs()
        return prefs!!.getBoolean("plan_daily_challenge_${planIndex}_${date}", false)
    }

    fun clearAll() {
        checkPrefs()
        prefs!!.edit { clear() }
    }

    // Learn position save/restore (non-plan mode)
    fun saveLearnPosition(corpusId: String, groupIndex: Int, wordIndex: Int, step: Int = 1) {
        checkPrefs()
        prefs!!.edit {
            putInt("learn_pos_group_$corpusId", groupIndex)
            putInt("learn_pos_word_$corpusId", wordIndex)
            putInt("learn_pos_step_$corpusId", step)
        }
    }

    fun getLearnPosition(corpusId: String): Triple<Int, Int, Int> {
        checkPrefs()
        val group = prefs!!.getInt("learn_pos_group_$corpusId", 0)
        val word = prefs!!.getInt("learn_pos_word_$corpusId", 0)
        val step = prefs!!.getInt("learn_pos_step_$corpusId", 1)
        return Triple(group, word, step)
    }

    // Today's learned word count
    fun recordWordLearned(corpusId: String) {
        checkPrefs()
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val key = "learned_today_${today}_$corpusId"
        val count = prefs!!.getInt(key, 0)
        prefs!!.edit { putInt(key, count + 1) }
    }

    fun getTodayLearnedCount(corpusId: String): Int {
        checkPrefs()
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val key = "learned_today_${today}_$corpusId"
        return prefs!!.getInt(key, 0)
    }
}
