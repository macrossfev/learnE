package com.learne.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.learne.data.model.*

@Database(
    entities = [
        User::class,
        WordProgress::class,
        WrongWord::class,
        StudyRecord::class,
        DailyGoal::class,
        UserNote::class,
        Achievement::class,
        StudyReminder::class,
        ListenHistory::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun progressDao(): ProgressDao
    abstract fun wrongWordDao(): WrongWordDao
    abstract fun studyRecordDao(): StudyRecordDao
    abstract fun dailyGoalDao(): DailyGoalDao
    abstract fun userNoteDao(): UserNoteDao
    abstract fun achievementDao(): AchievementDao
    abstract fun reminderDao(): ReminderDao
    abstract fun listenHistoryDao(): ListenHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "learne_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}