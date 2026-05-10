package com.learne.data.repository

import com.learne.data.model.Word

/**
 * 语料库内容过滤器：检测不适合学习的不当词汇
 */
object CorpusContentFilter {

    // 不当词汇关键词列表（中文释义）
    private val INAPPROPRIATE_MEANINGS = listOf(
        // 暴力/伤害相关
        "强奸", "性侵犯", "性侵", "猥亵", "强暴",
        "杀人", "谋杀", "杀害", "屠杀", "杀戮",
        "虐待", "酷刑", "折磨", "残害", "肢解",
        "自杀", "自残", "自焚",
        "暴力", "血腥", "恐怖", "恐怖主义",
        // 色情相关
        "色情", "淫秽", "妓女", "卖淫",
        "生殖器", "阴茎", "阴道", "肛门",
        "性交", "做爱", "口交",
        // 毒品相关
        "海洛因", "可卡因", "大麻", "冰毒", "鸦片",
        "吸毒", "贩毒", "制毒",
        // 侮辱/歧视
        "白痴", "弱智", "蠢货", "婊子", "贱人",
        "强奸犯", "杀人犯", "罪犯",
        // 政治敏感
        "法西斯", "纳粹", "独裁",
        // 其他不当
        "死刑", "绞刑", "枪决",
        "尸", "尸体", "骷髅",
        "鬼", "魔鬼", "恶魔"
    )

    // 不当英文单词
    private val INAPPROPRIATE_WORDS = listOf(
        "rape", "rapist", "murder", "kill", "slaughter", "massacre",
        "torture", "abuse", "suicide", "homicide", "genocide",
        "porn", "pornography", "prostitute", "whore", "slut",
        "nazi", "fascist", "terrorist",
        "heroin", "cocaine", "meth", "opium"
    )

    /**
     * 检查单个单词是否包含不当内容
     */
    fun isInappropriate(word: Word): Boolean {
        val meaning = word.meaning.lowercase()
        val wordText = word.word.lowercase()

        if (INAPPROPRIATE_WORDS.any { wordText == it }) return true
        if (INAPPROPRIATE_MEANINGS.any { meaning.contains(it) }) return true
        return false
    }

    /**
     * 扫描整个词库，返回不当单词列表
     */
    fun scanCorpus(words: List<Word>): List<Word> {
        return words.filter { isInappropriate(it) }
    }

    /**
     * 生成扫描报告
     */
    fun generateReport(words: List<Word>): String {
        val flagged = scanCorpus(words)
        val total = words.size
        return buildString {
            appendLine("语料库扫描报告")
            appendLine("总单词数: $total")
            appendLine("标记单词数: ${flagged.size}")
            appendLine("-".repeat(40))
            if (flagged.isNotEmpty()) {
                flagged.forEach { w ->
                    appendLine("${w.word} - ${w.meaning}")
                }
            }
        }
    }
}
