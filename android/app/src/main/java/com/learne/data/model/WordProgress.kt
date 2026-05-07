package com.learne.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_progress")
data class WordProgress(
    @PrimaryKey
    val id: String, // userId_corpusId_word
    val corpusId: String,
    val word: String,
    val mastered: Boolean = false,
    val stage: Int = 0, // 0=新学, 1=1天, 2=3天, 3=7天, 4=15天
    val reviewCount: Int = 0,
    val lastReviewTime: Long = 0,
    val nextReviewTime: Long = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0
)