package com.learne.data.repository

import android.content.Context
import androidx.room.Room
import com.learne.data.db.AppDatabase
import com.learne.data.model.WordProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ProgressRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val progressDao = db.progressDao()

    companion object {
        // Spaced repetition intervals: stage 0→1→2→3→4
        // After answering correctly at stage N, next review is after intervals[N]
        private val REVIEW_INTERVALS_MS = longArrayOf(
            1 * 60 * 60 * 1000L,    // stage 0→1: 1 hour
            24 * 60 * 60 * 1000L,   // stage 1→2: 1 day
            3 * 24 * 60 * 60 * 1000L,  // stage 2→3: 3 days
            7 * 24 * 60 * 60 * 1000L,  // stage 3→4: 7 days
            14 * 24 * 60 * 60 * 1000L  // stage 4→4: 14 days (max stage, stays at 4)
        )
    }

    fun getWordsForReview(corpusId: String): Flow<List<WordProgress>> {
        val now = System.currentTimeMillis()
        val fullCorpusId = "${UserManager.userId}_$corpusId"
        return progressDao.getWordsForReview(fullCorpusId, now)
    }

    suspend fun updateReviewProgress(userId: String, corpusId: String, word: String, correct: Boolean) {
        val uid = "${userId}_$corpusId"
        val existing = progressDao.getByWordAndCorpus(word, uid)
        val now = System.currentTimeMillis()

        if (correct) {
            // Answer correct: advance stage and push nextReviewTime forward
            val currentStage = existing?.stage ?: 0
            val newStage = minOf(4, currentStage + 1)
            val interval = REVIEW_INTERVALS_MS[newStage]
            val nextReview = now + interval

            if (existing != null) {
                progressDao.update(existing.copy(
                    stage = newStage,
                    nextReviewTime = nextReview,
                    lastReviewTime = now,
                    reviewCount = existing.reviewCount + 1
                ))
            } else {
                progressDao.insert(WordProgress(
                    id = "${uid}_$word",
                    word = word,
                    corpusId = uid,
                    stage = newStage,
                    nextReviewTime = nextReview,
                    lastReviewTime = now,
                    reviewCount = 1
                ))
            }
        } else {
            // Answer wrong: reset stage to 0, review again in 1 hour
            if (existing != null) {
                progressDao.update(existing.copy(
                    stage = 0,
                    nextReviewTime = now + REVIEW_INTERVALS_MS[0],
                    lastReviewTime = now,
                    reviewCount = existing.reviewCount + 1,
                    wrongCount = existing.wrongCount + 1
                ))
            } else {
                progressDao.insert(WordProgress(
                    id = "${uid}_$word",
                    word = word,
                    corpusId = uid,
                    stage = 0,
                    nextReviewTime = now + REVIEW_INTERVALS_MS[0],
                    lastReviewTime = now,
                    reviewCount = 1,
                    wrongCount = 1
                ))
            }
        }
    }

    suspend fun markWordLearned(userId: String, corpusId: String, word: String) {
        val uid = "${userId}_$corpusId"
        val existing = progressDao.getByWordAndCorpus(word, uid)
        val now = System.currentTimeMillis()

        if (existing == null) {
            progressDao.insert(WordProgress(
                id = "${uid}_$word",
                word = word,
                corpusId = uid,
                stage = 0,
                nextReviewTime = now + REVIEW_INTERVALS_MS[0],
                lastReviewTime = now,
                correctCount = 1
            ))
        } else {
            progressDao.update(existing.copy(
                correctCount = existing.correctCount + 1,
                lastReviewTime = now
            ))
        }
    }

    suspend fun getProgress(userId: String, corpusId: String, word: String): WordProgress? {
        val uid = "${userId}_$corpusId"
        return progressDao.getByWordAndCorpus(word, uid)
    }

    fun getAllProgress(corpusId: String): Flow<List<WordProgress>> {
        val uid = "${UserManager.userId}_$corpusId"
        return progressDao.getByCorpusId(uid)
    }

    suspend fun getLearnedCount(userId: String, corpusId: String): Int {
        val uid = "${userId}_$corpusId"
        return progressDao.getLearnedCount(uid).first()
    }

    suspend fun getMasteredCount(userId: String, corpusId: String): Int {
        val uid = "${userId}_$corpusId"
        return progressDao.getMasteredCount(uid).first()
    }
}
