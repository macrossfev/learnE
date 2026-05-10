package com.learne.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "corpus_cache")
data class CorpusCache(
    @PrimaryKey
    val corpusId: String,
    val jsonData: String,
    val timestamp: Long
)
