package com.codewithram.secretchat.ui.gallery

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

enum class FriendshipStatus {
    NONE,       // Not connected
    FRIEND,     // Already friends
    SENT,       // Friend request sent (you initiated)
    RECEIVED    // Friend request received (you need to accept)
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
