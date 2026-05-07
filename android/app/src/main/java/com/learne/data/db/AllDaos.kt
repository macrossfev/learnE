package com.learne.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.learne.data.model.*
import com.learne.data.model.ListenHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUser(id: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)
}

@Dao
interface WrongWordDao {

    @Query("SELECT * FROM wrong_words WHERE corpusId = :corpusId AND corrected = 0 ORDER BY wrongTime DESC")
    fun getWrongWords(corpusId: String): Flow<List<WrongWord>>

    @Query("SELECT * FROM wrong_words WHERE corpusId = :corpusId AND corrected = 0 LIMIT :limit")
    suspend fun getWrongWordsLimit(corpusId: String, limit: Int): List<WrongWord>

    @Query("SELECT COUNT(*) FROM wrong_words WHERE corpusId = :corpusId AND corrected = 0")
    fun getWrongWordCount(corpusId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wrongWord: WrongWord)

    @Update
    suspend fun update(wrongWord: WrongWord)

    @Query("UPDATE wrong_words SET corrected = 1 WHERE corpusId = :corpusId AND word = :word")
    suspend fun markAsCorrected(corpusId: String, word: String)

    @Query("DELETE FROM wrong_words WHERE corpusId = :corpusId AND word = :word")
    suspend fun delete(corpusId: String, word: String)

    @Query("SELECT * FROM wrong_words WHERE corpusId = :corpusId AND word = :word LIMIT 1")
    suspend fun getByWord(corpusId: String, word: String): WrongWord?
}

@Dao
interface StudyRecordDao {

    @Query("SELECT * FROM study_records WHERE corpusId = :corpusId ORDER BY date DESC LIMIT 30")
    fun getRecentRecords(corpusId: String): Flow<List<StudyRecord>>

    @Query("SELECT * FROM study_records WHERE date = :date AND corpusId = :corpusId LIMIT 1")
    suspend fun getByDate(date: String, corpusId: String): StudyRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: StudyRecord)

    @Update
    suspend fun update(record: StudyRecord)

    @Query("SELECT SUM(learnedCount) FROM study_records WHERE corpusId = :corpusId")
    fun getTotalLearned(corpusId: String): Flow<Int>

    @Query("SELECT SUM(masteredCount) FROM study_records WHERE corpusId = :corpusId")
    fun getTotalMastered(corpusId: String): Flow<Int>

    @Query("SELECT SUM(studyDuration) FROM study_records WHERE corpusId = :corpusId")
    fun getTotalDuration(corpusId: String): Flow<Long>
}

@Dao
interface DailyGoalDao {

    @Query("SELECT * FROM daily_goal WHERE corpusId = :corpusId LIMIT 1")
    suspend fun getByCorpus(corpusId: String): DailyGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: DailyGoal)

    @Update
    suspend fun update(goal: DailyGoal)

    @Query("UPDATE daily_goal SET currentCount = 0, streakDays = streakDays + 1, lastCheckIn = :time WHERE corpusId = :corpusId")
    suspend fun checkIn(corpusId: String, time: Long)

    @Query("UPDATE daily_goal SET currentCount = currentCount + 1 WHERE corpusId = :corpusId")
    suspend fun incrementProgress(corpusId: String)
}

@Dao
interface UserNoteDao {

    @Query("SELECT * FROM user_notes WHERE corpusId = :corpusId AND word = :word LIMIT 1")
    suspend fun getByWord(corpusId: String, word: String): UserNote?

    @Query("SELECT * FROM user_notes WHERE corpusId = :corpusId ORDER BY updateTime DESC")
    fun getAllNotes(corpusId: String): Flow<List<UserNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: UserNote)

    @Update
    suspend fun update(note: UserNote)

    @Query("DELETE FROM user_notes WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements ORDER BY unlocked DESC, progress DESC")
    fun getAllAchievements(): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements WHERE unlocked = 1")
    fun getUnlockedAchievements(): Flow<List<Achievement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(achievement: Achievement)

    @Update
    suspend fun update(achievement: Achievement)

    @Query("SELECT COUNT(*) FROM achievements WHERE unlocked = 1")
    fun getUnlockedCount(): Flow<Int>
}

@Dao
interface ReminderDao {

    @Query("SELECT * FROM study_reminder LIMIT 1")
    suspend fun get(): StudyReminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: StudyReminder)

    @Update
    suspend fun update(reminder: StudyReminder)
}

@Dao
interface ListenHistoryDao {

    @Query("SELECT * FROM listen_history WHERE userId = :userId AND corpusId = :corpusId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPosition(userId: String, corpusId: String): ListenHistory?

    @Query("SELECT * FROM listen_history WHERE userId = :userId AND corpusId = :corpusId ORDER BY timestamp DESC LIMIT 50")
    fun getHistory(userId: String, corpusId: String): Flow<List<ListenHistory>>

    @Query("SELECT COUNT(*) FROM listen_history WHERE userId = :userId AND corpusId = :corpusId")
    fun getHistoryCount(userId: String, corpusId: String): kotlinx.coroutines.flow.Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ListenHistory)

    @Query("DELETE FROM listen_history WHERE userId = :userId AND corpusId = :corpusId")
    suspend fun clearHistory(userId: String, corpusId: String)
}