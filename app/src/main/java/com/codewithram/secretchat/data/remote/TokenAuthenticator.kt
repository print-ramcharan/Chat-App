package com.codewithram.secretchat.data.remote

import RefreshRequest
import android.content.Context
import android.content.Intent
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import kotlinx.coroutines.runBlocking
class TokenAuthenticator(
    private val context: Context,
    private val apiService: ApiService
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val prefs = context.getSharedPreferences("secret_chat_prefs", Context.MODE_PRIVATE)
        val refreshToken = prefs.getString("refresh_token", null) ?: return null

        val newAccessToken = runBlocking {
            try {
                val refreshResponse = apiService.refreshToken(RefreshRequest(refreshToken))
                if (refreshResponse.isSuccessful) {
                    val newToken = refreshResponse.body()?.access_token ?: return@runBlocking null
                    prefs.edit().putString("auth_token", newToken).apply()
                    return@runBlocking newToken
                } else {
                    notifyTokenExpired()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                notifyTokenExpired()
            }
            null
        }

        return newAccessToken?.let {
            response.request.newBuilder()
                .header("Authorization", "Bearer $it")
                .build()
        }
    }

    private fun notifyTokenExpired() {
        val intent = Intent("com.codewithram.secretchat.TOKEN_EXPIRED")
        context.sendBroadcast(intent)
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
