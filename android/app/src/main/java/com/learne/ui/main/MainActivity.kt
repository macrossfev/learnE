package com.learne.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.learne.R
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.ui.challenge.ChallengeMapFragment
import com.learne.ui.challenge.DailyChallengeFragment
import com.learne.ui.stats.StudyStatsFragment
import com.learne.ui.home.HomeNavigation
import com.learne.ui.home.HomeFragment
import com.learne.ui.learn.FlashcardFragment
import com.learne.ui.learn.InteractiveLearnFragment
import com.learne.ui.listen.DictationFragment
import com.learne.ui.listen.ListenReadFragment

import com.learne.ui.wrong.WrongWordsFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!UserManager.isLoggedIn) {
            startActivity(Intent(this, com.learne.ui.auth.LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Set up navigation callbacks
        HomeNavigation.startInteractiveLearn = { cid ->
            switchFragment(InteractiveLearnFragment.newInstance(cid))
        }
        HomeNavigation.startListenRead = { cid ->
            switchFragment(ListenReadFragment.newInstance(cid))
        }
        HomeNavigation.startDictation = { cid ->
            switchFragment(DictationFragment.newInstance(cid))
        }
        HomeNavigation.startFlashcard = { cid ->
            switchFragment(FlashcardFragment.newInstance(cid))
        }
        HomeNavigation.startDailyChallenge = {
            switchFragment(DailyChallengeFragment.newInstance())
        }
        HomeNavigation.startStudyStats = {
            switchFragment(StudyStatsFragment.newInstance())
        }
        HomeNavigation.startWrongWords = {
            switchFragment(WrongWordsFragment())
        }

        if (savedInstanceState == null) {
            val planIndex = intent.getIntExtra("planIndex", -1)
            if (planIndex >= 0) {
                // Enter challenge map for the selected plan
                supportFragmentManager.commit {
                    replace(R.id.fragment_container, ChallengeMapFragment.newInstance(planIndex))
                    setReorderingAllowed(true)
                }
            } else {
                // Fallback: show home screen
                val corpusId = intent.getStringExtra("corpusId")
                    ?: UserPreferencesRepository.planCorpusId
                    ?: "catti"
                supportFragmentManager.commit {
                    replace(R.id.fragment_container, HomeFragment.newInstance(corpusId))
                    setReorderingAllowed(true)
                }
            }
        }
    }

    private fun switchFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
            setReorderingAllowed(true)
            addToBackStack("home")
        }
    }
}
