package com.learne.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.learne.R
import com.learne.databinding.ActivityLoginBinding
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.ui.plan.StudyPlanActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        UserManager.init(applicationContext)
        UserPreferencesRepository.init(applicationContext)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If already logged in, go directly to plan
        if (UserManager.isLoggedIn) {
            startActivity(Intent(this, StudyPlanActivity::class.java))
            finish()
            return
        }

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (username == "admin" && password == "admin") {
                UserManager.login(username)
                startActivity(Intent(this, StudyPlanActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "用户名或密码错误", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
