package com.learne.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String,
    val username: String,
    val passwordHash: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 错题记录
 */
@Entity(tableName = "wrong_words")
data class WrongWord(
    @PrimaryKey
    val id: String, // corpusId_word_timestamp
    val corpusId: String,
    val word: String,
    val testType: String, // choice, spell, listen
    val wrongTime: Long = System.currentTimeMillis(),
    val wrongCount: Int = 1,
    val lastWrongTime: Long = System.currentTimeMillis(),
    val corrected: Boolean = false // 是否已纠正
)

/**
 * 学习记录（每日统计）
 */
@Entity(tableName = "study_records")
data class StudyRecord(
    @PrimaryKey
    val date: String, // YYYY-MM-DD
    val corpusId: String,
    val learnedCount: Int = 0,
    val masteredCount: Int = 0,
    val reviewedCount: Int = 0,
    val testCount: Int = 0,
    val testCorrectCount: Int = 0,
    val studyDuration: Long = 0 // 学习时长（秒）
)

/**
 * 每日目标
 */
@Entity(tableName = "daily_goal")
data class DailyGoal(
    @PrimaryKey
    val id: String, // corpusId
    val corpusId: String,
    val targetCount: Int = 50, // 目标学习数量
    val currentCount: Int = 0, // 当前已完成
    val streakDays: Int = 0, // 连续打卡天数
    val lastCheckIn: Long = 0, // 上次打卡时间
    val totalCheckIns: Int = 0 // 总打卡次数
)

/**
 * 用户笔记
 */
@Entity(tableName = "user_notes")
data class UserNote(
    @PrimaryKey
    val id: String, // corpusId_word
    val corpusId: String,
    val word: String,
    val note: String = "",
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

/**
 * 成就徽章
 */
@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey
    val id: String,
    val type: String, // learn, master, test, streak
    val title: String,
    val description: String,
    val icon: String,
    val unlocked: Boolean = false,
    val unlockTime: Long = 0,
    val progress: Int = 0,
    val target: Int
)

/**
 * 学习提醒设置
 */
@Entity(tableName = "study_reminder")
data class StudyReminder(
    @PrimaryKey
    val id: Int = 1,
    val enabled: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    val message: String = "开始今天的学习吧！"
)

/**
 * 星标单词（学习中标记不熟，独立于错题本）
 */
@Entity(tableName = "starred_words")
data class StarredWord(
    @PrimaryKey
    val id: String,
    val corpusId: String,
    val word: String,
    val starredTime: Long = System.currentTimeMillis()
)