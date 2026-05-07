package com.learne.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 听读历史
 */
@Entity(tableName = "listen_history")
data class ListenHistory(
    @PrimaryKey
    val id: String,
    val userId: String,
    val corpusId: String,
    val word: String,
    val wordIndex: Int,
    val groupIndex: Int,
    val duration: Long = 0,
    val completedAudio: Int = 0,
    val repeatCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)
