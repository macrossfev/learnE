package com.learne.di

import android.content.Context
import com.learne.data.db.AppDatabase
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.ProgressRepository
import com.learne.ui.learn.LearnViewModel
import com.learne.ui.review.ReviewViewModel
import com.learne.ui.settings.SettingsViewModel
import com.learne.ui.stats.StatsViewModel
import com.learne.ui.test.TestViewModel

object ViewModelFactory {

    private lateinit var corpusRepository: CorpusRepository
    private lateinit var progressRepository: ProgressRepository

    fun initialize(context: Context) {
        corpusRepository = CorpusRepository(context)
        progressRepository = ProgressRepository(context)
    }

    fun getCorpusRepository(context: Context): CorpusRepository {
        if (!::corpusRepository.isInitialized) {
            initialize(context)
        }
        return corpusRepository
    }

    fun createLearnViewModel(): LearnViewModel {
        return LearnViewModel(corpusRepository, progressRepository)
    }

    fun createReviewViewModel(): ReviewViewModel {
        return ReviewViewModel(corpusRepository)
    }

    fun createStatsViewModel(): StatsViewModel {
        return StatsViewModel(corpusRepository, progressRepository)
    }

    fun createSettingsViewModel(): SettingsViewModel {
        return SettingsViewModel()
    }

    fun createTestViewModel(): TestViewModel {
        return TestViewModel(corpusRepository)
    }
}
