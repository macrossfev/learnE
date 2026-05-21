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

        // Clear stale callbacks from previous Activity instances
        HomeNavigation.startInteractiveLearn = null
        HomeNavigation.startListenRead = null
        HomeNavigation.startReview = null
        HomeNavigation.startReviewDirect = null
        HomeNavigation.startWrongWords = null
        HomeNavigation.startQuiz = null
        HomeNavigation.startDictation = null
        HomeNavigation.startFlashcard = null
        HomeNavigation.startDailyChallenge = null
        HomeNavigation.startStudyStats = null
        HomeNavigation.startUserCenter = null

        // Set up navigation callbacks
        val planIndex = intent.getIntExtra("planIndex", -1)
        HomeNavigation.startInteractiveLearn = { cid ->
            if (planIndex >= 0) {
                supportFragmentManager.commit {
                    replace(R.id.fragment_container, ChallengeMapFragment.newInstance(planIndex, "learn"))
                    setReorderingAllowed(true)
                    addToBackStack("home")
                }
            } else {
                switchFragment(InteractiveLearnFragment.newInstance(cid))
            }
        }
        HomeNavigation.startListenRead = { cid ->
            if (planIndex >= 0) {
                supportFragmentManager.commit {
                    replace(R.id.fragment_container, ChallengeMapFragment.newInstance(planIndex, "listen"))
                    setReorderingAllowed(true)
                    addToBackStack("home")
                }
            } else {
                switchFragment(ListenReadFragment.newInstance(cid))
            }
        }
        HomeNavigation.startReview = { cid ->
            if (planIndex >= 0) {
                supportFragmentManager.commit {
                    replace(R.id.fragment_container, ChallengeMapFragment.newInstance(planIndex, "review"))
                    setReorderingAllowed(true)
                    addToBackStack("home")
                }
            } else {
                switchFragment(InteractiveLearnFragment.newInstance(cid, InteractiveLearnFragment.Mode.REVIEW))
            }
        }
        HomeNavigation.startReviewDirect = { cid ->
            switchFragment(InteractiveLearnFragment.newInstance(cid, InteractiveLearnFragment.Mode.REVIEW_DIRECT))
        }
        HomeNavigation.startWrongWords = { cid ->
            switchFragment(InteractiveLearnFragment.newInstance(cid, InteractiveLearnFragment.Mode.WRONG))
        }
        HomeNavigation.startQuiz = { cid ->
            if (planIndex >= 0) {
                supportFragmentManager.commit {
                    replace(R.id.fragment_container, ChallengeMapFragment.newInstance(planIndex, "quiz"))
                    setReorderingAllowed(true)
                    addToBackStack("home")
                }
            } else {
                switchFragment(InteractiveLearnFragment.newInstance(cid))
            }
        }
        HomeNavigation.startDictation = { cid ->
            switchFragment(DictationFragment.newInstance(cid))
        }
        HomeNavigation.startFlashcard = { cid ->
            switchFragment(FlashcardFragment.newInstance(cid))
        }
        HomeNavigation.startDailyChallenge = {
            switchFragment(DailyChallengeFragment.newInstance(planIndex))
        }
        HomeNavigation.startStudyStats = {
            switchFragment(StudyStatsFragment.newInstance())
        }
        HomeNavigation.startUserCenter = {
            switchFragment(com.learne.ui.user.UserCenterFragment())
        }

        if (savedInstanceState == null) {
            val planIndex = intent.getIntExtra("planIndex", -1)
            val corpusId = intent.getStringExtra("corpusId")
                ?: UserPreferencesRepository.planCorpusId
                ?: "catti"
            supportFragmentManager.commit {
                replace(R.id.fragment_container, HomeFragment.newInstance(corpusId))
                setReorderingAllowed(true)
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

    override fun onDestroy() {
        HomeNavigation.clear()
        super.onDestroy()
    }
}
