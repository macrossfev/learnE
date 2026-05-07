package com.learne.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.learne.data.model.WordProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM word_progress WHERE id = :id")
    suspend fun getProgressById(id: String): WordProgress?

    @Query("SELECT * FROM word_progress WHERE corpusId = :corpusId AND mastered = 0 AND (nextReviewTime <= :now OR stage = 0) ORDER BY stage ASC, nextReviewTime ASC")
    fun getWordsForReview(corpusId: String, now: Long): Flow<List<WordProgress>>

    @Query("SELECT COUNT(*) FROM word_progress WHERE corpusId = :corpusId AND mastered = 1")
    fun getMasteredCount(corpusId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM word_progress WHERE corpusId = :corpusId")
    fun getLearnedCount(corpusId: String): Flow<Int>

    @Query("SELECT word FROM word_progress WHERE corpusId = :corpusId")
    suspend fun getAllLearnedWords(corpusId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: WordProgress)

    @Update
    suspend fun update(progress: WordProgress)
}