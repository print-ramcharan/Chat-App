package com.codewithram.secretchat.data

import android.content.Context
import android.content.SharedPreferences

class SharedPrefsHelper(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("secret_chat_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString("jwt_token", token).apply()
    }

    fun getToken(): String? = prefs.getString("jwt_token", null)

    fun clearToken() {
        prefs.edit().remove("jwt_token").apply()
    }
}
