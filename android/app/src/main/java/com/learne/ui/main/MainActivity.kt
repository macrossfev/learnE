package com.learne.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.learne.R
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.ui.home.HomeNavigation
import com.learne.ui.home.HomeFragment
import com.learne.ui.learn.InteractiveLearnFragment
import com.learne.ui.listen.ListenReadFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UserManager.init(applicationContext)
        UserPreferencesRepository.init(applicationContext)
        if (!UserManager.isLoggedIn) {
            UserManager.login("admin")
        }
        setContentView(R.layout.activity_main)

        // 设置导航回调
        HomeNavigation.startInteractiveLearn = { corpusId ->
            switchFragment(InteractiveLearnFragment.newInstance(corpusId))
        }
        HomeNavigation.startListenRead = { corpusId ->
            switchFragment(ListenReadFragment.newInstance(corpusId))
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, HomeFragment())
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
}
