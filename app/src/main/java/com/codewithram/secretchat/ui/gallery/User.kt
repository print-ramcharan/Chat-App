package com.codewithram.secretchat.ui.gallery

import com.google.gson.annotations.SerializedName

enum class FriendshipStatus {
    NONE,
    FRIEND,
    SENT,
    RECEIVED
}

data class User(
    val id: String,
    @SerializedName("display_name")
    val displayName: String,
    var username: String,
    @SerializedName("avatar_data")
    val avatarData: String? = null,
    var friendshipStatus: FriendshipStatus = FriendshipStatus.NONE

)
