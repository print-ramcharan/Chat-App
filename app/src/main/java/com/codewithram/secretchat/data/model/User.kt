package com.codewithram.secretchat.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("access_token")
    val token: String,

    @SerializedName("refresh_token")
    val refresh_token: String,

    val user: User
)


data class User(
    val id : String,
    val username: String,
    val display_name: String,
    val phone_number: String,
    val avatar_url: String?
)

