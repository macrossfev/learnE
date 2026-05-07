package com.learne.data.model

import com.google.gson.annotations.SerializedName

data class Word(
    val word: String,
    val phonetic: String,
    val pos: String,
    val meaning: String,
    val phrase: String,
    @SerializedName("phrase_meaning")
    val phraseMeaning: String,
    val example: String,
    @SerializedName("example_meaning")
    val exampleMeaning: String,
    val freq: Int
)