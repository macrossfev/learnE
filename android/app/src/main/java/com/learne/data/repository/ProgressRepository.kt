package com.learne.data.repository

import android.content.Context
import com.learne.data.model.WordProgress
import com.learne.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow

class ProgressRepository(context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val progressDao = database.progressDao()

    private val reviewIntervals = listOf(0L, 1, 3, 7, 15) // stage 0-4, 间隔天数
    private val maxStage = 4

    suspend fun recordLearned(userId: String, corpusId: String, word: String) {
        val id = "${userId}_${corpusId}_$word"
        val existing = progressDao.getProgressById(id)
        if (existing == null) {
            val now = System.currentTimeMillis()
            progressDao.insert(WordProgress(
                id = id, corpusId = corpusId, word = word,
                stage = 1,
                nextReviewTime = now + (1L * 24 * 60 * 60 * 1000) // 1天后复习
            ))
        }
    }

    suspend fun markAsMastered(userId: String, corpusId: String, word: String) {
        val id = "${userId}_${corpusId}_$word"
        val progress = progressDao.getProgressById(id) ?: WordProgress(
            id = id,
            corpusId = corpusId,
            word = word
        )
        progressDao.update(progress.copy(mastered = true, stage = maxStage))
    }

    suspend fun updateReviewProgress(userId: String, corpusId: String, word: String, correct: Boolean) {
        val id = "${userId}_${corpusId}_$word"
        val progress = progressDao.getProgressById(id) ?: WordProgress(
            id = id,
            corpusId = corpusId,
            word = word
        )
        val now = System.currentTimeMillis()

        val (newStage, nextReview) = if (correct) {
            val s = minOf((progress.stage + 1), maxStage)
            val days = reviewIntervals[s]
            if (s >= maxStage) {
                // stage 达到最大值，标记掌握
                s to 0L
            } else {
                s to now + (days * 24 * 60 * 60 * 1000)
            }
        } else {
            // 答错重置到 stage 1（1天后复习）
            1 to now + (1 * 24 * 60 * 60 * 1000)
        }

        progressDao.update(progress.copy(
            stage = newStage,
            mastered = nextReview == 0L,
            reviewCount = progress.reviewCount + 1,
            lastReviewTime = now,
            nextReviewTime = nextReview,
            correctCount = progress.correctCount + if (correct) 1 else 0,
            wrongCount = progress.wrongCount + if (!correct) 1 else 0
        ))
    }

    fun getWordsForReview(corpusId: String): Flow<List<WordProgress>> {
        val now = System.currentTimeMillis()
        return progressDao.getWordsForReview(corpusId, now)
    }

    fun getMasteredCount(corpusId: String): Flow<Int> {
        return progressDao.getMasteredCount(corpusId)
    }

    fun getLearnedCount(corpusId: String): Flow<Int> {
        return progressDao.getLearnedCount(corpusId)
    }
}