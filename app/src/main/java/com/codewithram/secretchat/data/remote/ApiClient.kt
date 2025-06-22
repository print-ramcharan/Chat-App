package com.codewithram.secretchat.data.remote

import com.codewithram.secretchat.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    private val retrofit = Retrofit.Builder()
//        .baseUrl("${ServerConfig.ipAddress.address}") // <-- Change to your backend URL
//        .baseUrl("http://192.168.203.231:4000") // <-- Change to your backend URL
//        .baseUrl("http://192.168.0.190:4000") // <-- Change to your backend URL
        .baseUrl("https://social-application-backend-hwrx.onrender.com")
//        .baseUrl("${ServerConfig.ipAddress.address}/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
