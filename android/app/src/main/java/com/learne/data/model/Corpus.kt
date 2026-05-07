package com.learne.data.model

data class Corpus(
    val id: String,
    val name: String,
    val description: String,
    val wordCount: Int
) {
    companion object {
        val CET4 = Corpus("cet4", "CET4", "大学英语四级", 3393)
        val CATTI = Corpus("catti", "CATTI", "翻译专业资格考试", 4807)
    }
}