package com.learne.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.learne.R
import com.learne.databinding.ActivityLoginBinding
import com.learne.data.repository.UserManager
import com.learne.ui.main.MainActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("LoginActivity", "onCreate start")

        UserManager.init(applicationContext)
        android.util.Log.d("LoginActivity", "UserManager initialized, isLoggedIn=${UserManager.isLoggedIn}")

        // 使用 ViewBinding 正确方式
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        android.util.Log.d("LoginActivity", "setContentView done")

        // 自动登录并跳转
        if (!UserManager.isLoggedIn) {
            UserManager.login("admin")
            android.util.Log.d("LoginActivity", "login called")
        }

        // 显示加载提示
        binding.btnLogin.text = "正在加载..."
        binding.btnLogin.isEnabled = false

        android.util.Log.d("LoginActivity", "Starting MainActivity")
        startActivity(Intent(this, MainActivity::class.java))
        android.util.Log.d("LoginActivity", "finish")
        finish()
    }
}
