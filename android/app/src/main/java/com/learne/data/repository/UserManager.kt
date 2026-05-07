package com.learne.data.repository

import android.content.Context
import androidx.core.content.edit

object UserManager {
    private const val PREFS_NAME = "learne_auth"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_LOGGED_IN = "logged_in"

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun checkPrefs() {
        if (prefs == null) throw IllegalStateException("UserManager not initialized. Call init() first.")
    }

    var userId: String
        get() {
            checkPrefs()
            return prefs!!.getString(KEY_USER_ID, "admin") ?: "admin"
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putString(KEY_USER_ID, value) }
        }

    var isLoggedIn: Boolean
        get() {
            checkPrefs()
            return prefs!!.getBoolean(KEY_LOGGED_IN, false)
        }
        set(value) {
            checkPrefs()
            prefs!!.edit { putBoolean(KEY_LOGGED_IN, value) }
        }

    fun login(userId: String) {
        this.userId = userId
        isLoggedIn = true
    }

    fun logout() {
        checkPrefs()
        prefs!!.edit {
            remove(KEY_USER_ID)
            putBoolean(KEY_LOGGED_IN, false)
        }
    }
}
