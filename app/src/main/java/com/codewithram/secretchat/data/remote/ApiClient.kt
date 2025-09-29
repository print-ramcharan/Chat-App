//package com.codewithram.secretchat.data.remote
//
//import okhttp3.OkHttpClient
//import okhttp3.logging.HttpLoggingInterceptor
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//
//object ApiClient {
//
//    private val logging = HttpLoggingInterceptor().apply {
//        level = HttpLoggingInterceptor.Level.BODY
//    }
//
//    private val client = OkHttpClient.Builder()
//        .addInterceptor(logging)
//        .build()
//
//
//
//    val apiService: ApiService = retrofit.create(ApiService::class.java)
//}

package com.codewithram.secretchat.data.remote

import android.content.Context
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    // Create a client WITHOUT authenticator (so TokenAuthenticator can call ApiService without infinite recursion)
    private val retrofitWithoutAuth = Retrofit.Builder()
        .baseUrl("http://192.168.0.169:4000")
//        .baseUrl("https://social-application-backend-hwrx.onrender.com")
        .client(OkHttpClient.Builder().addInterceptor(logging).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiServiceWithoutAuth = retrofitWithoutAuth.create(ApiService::class.java)

    // We keep apiService as lateinit and initialize it later (needs context)
    lateinit var apiService: ApiService
        private set

    // Call this ONCE in Application class or somewhere with a Context
    fun init(context: Context) {
        val authenticator = TokenAuthenticator(context.applicationContext, apiServiceWithoutAuth)

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .authenticator(authenticator)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.0.169:4000")
//            .baseUrl("https://social-application-backend-hwrx.onrender.com")

            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }
}


