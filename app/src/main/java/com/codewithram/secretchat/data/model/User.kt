package com.codewithram.secretchat.data.model

data class LoginResponse(
    val token: String,
    val user: User
)

data class User(
    val id : String,
    val username: String,
    val display_name: String,
    val phone_number: String,
    val avatar_url: String?
)

