package com.learne.data.repository

import android.content.Context
import com.learne.data.db.AppDatabase
import com.learne.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.*

class StudyRepository(context: Context) {

    private val context: Context = context
    private val database = AppDatabase.getDatabase(context)
    private val wrongWordDao = database.wrongWordDao()
    private val studyRecordDao = database.studyRecordDao()
    private val dailyGoalDao = database.dailyGoalDao()
    private val userNoteDao = database.userNoteDao()
    private val achievementDao = database.achievementDao()
    private val reminderDao = database.reminderDao()

    // ========== 星标单词 ==========

    fun getStarredWords(userId: String, corpusId: String): Flow<List<com.learne.data.model.StarredWord>> {
        return database.starredWordDao().getStarredWords("${userId}_$corpusId")
    }

    suspend fun addStarredWord(userId: String, corpusId: String, word: String) {
        val uid = "${userId}_$corpusId"
        val existing = database.starredWordDao().getByWord(uid, word)
        if (existing == null) {
            database.starredWordDao().insert(com.learne.data.model.StarredWord(
                id = "${userId}_${corpusId}_${word}_${System.currentTimeMillis()}",
                corpusId = uid,
                word = word
            ))
        }
    }

    suspend fun removeStarredWord(userId: String, corpusId: String, word: String) {
        database.starredWordDao().delete("${userId}_$corpusId", word)
    }

    // ========== 错题本 ==========

    fun getWrongWords(userId: String, corpusId: String): Flow<List<WrongWord>> {
        return wrongWordDao.getWrongWords("${userId}_$corpusId")
    }

    suspend fun addWrongWord(userId: String, corpusId: String, word: String, testType: String) {
        val uid = "${userId}_$corpusId"
        val existing = wrongWordDao.getByWord(uid, word)
        if (existing != null) {
            // Reactivate if previously corrected
            wrongWordDao.update(existing.copy(
                wrongCount = existing.wrongCount + 1,
                lastWrongTime = System.currentTimeMillis(),
                corrected = false
            ))
        } else {
            val id = "${userId}_${corpusId}_${word}_${System.currentTimeMillis()}"
            wrongWordDao.insert(WrongWord(
                id = id,
                corpusId = uid,
                word = word,
                testType = testType
            ))
        }
    }

    suspend fun markWrongWordCorrected(userId: String, corpusId: String, word: String) {
        wrongWordDao.markAsCorrected("${userId}_$corpusId", word)
    }

    /**
     * Record a correct answer for a wrong word. Auto-marks as corrected after 3 consecutive correct.
     */
    suspend fun recordCorrectAnswer(userId: String, corpusId: String, word: String) {
        val uid = "${userId}_$corpusId"
        val existing = wrongWordDao.getByWord(uid, word) ?: return
        if (existing.corrected) return
        if (existing.wrongCount >= 3) {
            // Mark as corrected if wrong frequently before
            wrongWordDao.markAsCorrected(uid, word)
        } else {
            // Decrease wrongCount as a "consecutive correct" tracker
            wrongWordDao.update(existing.copy(wrongCount = (existing.wrongCount - 1).coerceAtLeast(0)))
        }
    }

    fun getWrongWordCount(userId: String, corpusId: String): Flow<Int> {
        return wrongWordDao.getWrongWordCount("${userId}_$corpusId")
    }

    // ========== 学习记录 ==========

    fun getRecentRecords(userId: String, corpusId: String): Flow<List<StudyRecord>> {
        return studyRecordDao.getRecentRecords("${userId}_$corpusId")
    }

    suspend fun updateStudyRecord(userId: String, corpusId: String, learned: Int, mastered: Int, reviewed: Int, duration: Long) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val uid = "${userId}_$corpusId"
        val record = studyRecordDao.getByDate(date, uid)
        if (record != null) {
            studyRecordDao.update(record.copy(
                learnedCount = record.learnedCount + learned,
                masteredCount = record.masteredCount + mastered,
                reviewedCount = record.reviewedCount + reviewed,
                studyDuration = record.studyDuration + duration
            ))
        } else {
            studyRecordDao.insert(StudyRecord(
                date = date,
                corpusId = uid,
                learnedCount = learned,
                masteredCount = mastered,
                reviewedCount = reviewed,
                studyDuration = duration
            ))
        }
    }

    fun getTotalLearned(userId: String, corpusId: String): Flow<Int> {
        return studyRecordDao.getTotalLearned("${userId}_$corpusId")
    }

    fun getTotalMastered(userId: String, corpusId: String): Flow<Int> {
        return studyRecordDao.getTotalMastered("${userId}_$corpusId")
    }

    fun getTotalDuration(userId: String, corpusId: String): Flow<Long> {
        return studyRecordDao.getTotalDuration("${userId}_$corpusId")
    }

    // ========== 每日目标 ==========

    suspend fun getDailyGoal(userId: String, corpusId: String): DailyGoal? {
        return dailyGoalDao.getByCorpus("${userId}_$corpusId")
    }

    suspend fun setDailyGoal(userId: String, corpusId: String, target: Int) {
        val uid = "${userId}_$corpusId"
        val goal = dailyGoalDao.getByCorpus(uid)
        if (goal != null) {
            dailyGoalDao.update(goal.copy(targetCount = target))
        } else {
            dailyGoalDao.insert(DailyGoal(id = uid, corpusId = uid, targetCount = target))
        }
    }

    suspend fun updateProgress(userId: String, corpusId: String) {
        dailyGoalDao.incrementProgress("${userId}_$corpusId")
    }

    suspend fun checkIn(userId: String, corpusId: String) {
        dailyGoalDao.checkIn("${userId}_$corpusId", System.currentTimeMillis())
    }

    suspend fun initDailyGoal(userId: String, corpusId: String) {
        val uid = "${userId}_$corpusId"
        val existing = dailyGoalDao.getByCorpus(uid)
        if (existing == null) {
            dailyGoalDao.insert(DailyGoal(id = uid, corpusId = uid))
        }
    }

    // ========== 用户笔记 ==========

    suspend fun saveNote(userId: String, corpusId: String, word: String, note: String) {
        val id = "${userId}_${corpusId}_${word}"
        val uid = "${userId}_$corpusId"
        val existing = userNoteDao.getByWord(uid, word)
        if (existing != null) {
            userNoteDao.update(existing.copy(note = note, updateTime = System.currentTimeMillis()))
        } else {
            userNoteDao.insert(UserNote(id = id, corpusId = uid, word = word, note = note))
        }
    }

    suspend fun getNote(userId: String, corpusId: String, word: String): UserNote? {
        return userNoteDao.getByWord("${userId}_$corpusId", word)
    }

    fun getAllNotes(userId: String, corpusId: String): Flow<List<UserNote>> {
        return userNoteDao.getAllNotes("${userId}_$corpusId")
    }

    // ========== 成就系统 ==========

    fun getAllAchievements(): Flow<List<Achievement>> {
        return achievementDao.getAllAchievements()
    }

    suspend fun checkAchievements(userId: String, corpusId: String) {
        val uid = "${userId}_$corpusId"
        val totalLearned = studyRecordDao.getTotalLearned(uid).firstOrNull() ?: 0
        val totalMastered = studyRecordDao.getTotalMastered(uid).firstOrNull() ?: 0
        val completedGroups = UserPreferencesRepository.getCompletedGroups(corpusId).size

        checkAndUnlock("learn_100", "学习达人", "累计学习100个单词", totalLearned, 100)
        checkAndUnlock("learn_500", "词汇大师", "累计学习500个单词", totalLearned, 500)
        checkAndUnlock("master_50", "初露锋芒", "掌握50个单词", totalMastered, 50)
        checkAndUnlock("master_200", "词汇专家", "掌握200个单词", totalMastered, 200)
        checkAndUnlock("group_50", "初出茅庐", "完成50个组的学习", completedGroups, 50)
        checkAndUnlock("group_100", "稳步前进", "完成100个组的学习", completedGroups, 100)
        checkAndUnlock("group_200", "词汇达人", "完成200个组的学习", completedGroups, 200)
    }

    /**
     * Get all words due for review. Caller maps to groups.
     */
    suspend fun getWordsDueForReview(corpusId: String): List<com.learne.data.model.WordProgress> {
        val uidCorpus = "${UserManager.userId}_$corpusId"
        return try {
            AppDatabase.getDatabase(context).progressDao()
                .getWordsForReview(uidCorpus, System.currentTimeMillis()).first()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMasteredWordCount(corpusId: String): Int {
        val uidCorpus = "${UserManager.userId}_$corpusId"
        return try {
            AppDatabase.getDatabase(context).progressDao()
                .getMasteredCount(uidCorpus).first()
        } catch (e: Exception) {
            0
        }
    }

    private suspend fun checkAndUnlock(id: String, title: String, desc: String, progress: Int, target: Int) {
        val achievement = achievementDao.getAllAchievements().firstOrNull()?.find { it.id == id }
        if (achievement == null) {
            achievementDao.insert(Achievement(
                id = id,
                type = "learn",
                title = title,
                description = desc,
                icon = "star",
                progress = progress,
                target = target,
                unlocked = progress >= target,
                unlockTime = if (progress >= target) System.currentTimeMillis() else 0
            ))
        } else if (!achievement.unlocked && progress >= target) {
            achievementDao.update(achievement.copy(
                progress = progress,
                unlocked = true,
                unlockTime = System.currentTimeMillis()
            ))
        } else {
            achievementDao.update(achievement.copy(progress = progress))
        }
    }

    suspend fun initAchievements() {
        val existing = achievementDao.getAllAchievements().firstOrNull()
        if (existing == null || existing.isEmpty()) {
            achievementDao.insert(Achievement(id = "learn_100", type = "learn", title = "学习达人", description = "累计学习100个单词", icon = "star", target = 100))
            achievementDao.insert(Achievement(id = "learn_500", type = "learn", title = "词汇大师", description = "累计学习500个单词", icon = "star", target = 500))
            achievementDao.insert(Achievement(id = "master_50", type = "master", title = "初露锋芒", description = "掌握50个单词", icon = "medal", target = 50))
            achievementDao.insert(Achievement(id = "master_200", type = "master", title = "词汇专家", description = "掌握200个单词", icon = "medal", target = 200))
            achievementDao.insert(Achievement(id = "streak_7", type = "streak", title = "坚持不懈", description = "连续打卡7天", icon = "fire", target = 7))
            achievementDao.insert(Achievement(id = "streak_30", type = "streak", title = "习惯养成", description = "连续打卡30天", icon = "fire", target = 30))
            achievementDao.insert(Achievement(id = "group_50", type = "group", title = "初出茅庐", description = "完成50个组的学习", icon = "star", target = 50))
            achievementDao.insert(Achievement(id = "group_100", type = "group", title = "稳步前进", description = "完成100个组的学习", icon = "star", target = 100))
            achievementDao.insert(Achievement(id = "group_200", type = "group", title = "词汇达人", description = "完成200个组的学习", icon = "medal", target = 200))
        }
    }

    // ========== 学习提醒 ==========

    suspend fun getReminder(): StudyReminder? {
        return reminderDao.get()
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int, message: String) {
        val reminder = reminderDao.get() ?: StudyReminder()
        reminderDao.insert(reminder.copy(enabled = enabled, hour = hour, minute = minute, message = message))
    }
}