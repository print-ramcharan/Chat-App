package com.codewithram.secretchat.data

import AddMemberRequest
import AvatarUpdateRequest
import Conversation
import ConversationDetailsResponse
import ConversationMemberResponse
import ConversationRequest
import ConversationUpdateRequest
import FriendActionRequest
import Message
import MessageStatusUpdateRequest
import RemoveMemberRequest
import ReplyRequest
import UpdateAdminsRequest
import android.content.SharedPreferences
import android.util.Log
import com.codewithram.secretchat.data.model.LoginRequest
import com.codewithram.secretchat.data.model.LoginResponse
import com.codewithram.secretchat.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repository(
    private val sharedPrefs: SharedPreferences
) {

    private val api = ApiClient.apiService

    private var authToken: String?
        get() = sharedPrefs.getString("auth_token", null)
        set(value) {
            sharedPrefs.edit().putString("auth_token", value).apply()
        }

    public var userId: String?
        get() = sharedPrefs.getString("user_id", null)
        set(value) {
            sharedPrefs.edit().putString("user_id", value).apply()
        }

    internal fun getToken(): String? = sharedPrefs.getString("auth_token", null)

    suspend fun getConversationsForCurrentUser(): List<Conversation> {
        val token = getToken()
       if (token == null) throw Exception("Unauthorized: No auth token found")

        val response = ApiClient.apiService.listConversationsForCurrentUser("Bearer $token")
        return if (response.isSuccessful) {
            val conversationsResponse = response.body()
             conversationsResponse?.data ?: emptyList()
        } else {
            throw Exception("Failed to fetch conversations: ${response.code()}")
        }
    }

    suspend fun getMessages(conversationId: String): List<Message> = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw Exception("No auth token found")
        val response = api.getMessages("Bearer $token", conversationId)

        if (response.isSuccessful) {
            val body = response.body()
            val messages = body?.messages ?: emptyList()
            messages
        } else {
            throw Exception("Failed to get messages: ${response.code()} ${response.message()}")
        }
    }

    suspend fun login(email: String, password: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(LoginRequest(email, password))
            return@withContext if (response.isSuccessful) {
                response.body()?.let { loginResponse ->
                    authToken = "Bearer ${loginResponse.token}"
                    userId = loginResponse.user.id
                    Result.success(loginResponse)
                } ?: run {
                   Result.failure(Exception("Empty login response"))
                }
            } else {
                Result.failure(Exception("Login failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
             Result.failure(e)
        }
    }
    suspend fun updateFcmToken(fcmToken: String): Unit = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrEmpty()) {
            return@withContext
        }

        val payload = mapOf("fcm_token" to fcmToken)
        val response = api.updateFcmToken("Bearer $token", payload)

        if (!response.isSuccessful) {
            Log.e("Repository", "Update failed FCM token: ${response.code()} ${response.message()}")
        }
    }

    suspend fun updateMessageStatus(messageId: String, request: MessageStatusUpdateRequest): Unit =
        withContext(Dispatchers.IO) {
            val token = getToken() ?: throw Exception("No auth token found")
            val response = api.updateMessageStatus("Bearer $token", messageId, request)
            if (!response.isSuccessful) {
                 throw Exception("Failed to update message status: ${response.code()}")
            }
        }
    suspend fun replyToMessage(messageId: String, request: ReplyRequest): Unit =
        withContext(Dispatchers.IO) {
            val token = getToken() ?: throw Exception("No auth token found")
            val response = api.replyToMessage("Bearer $token", messageId, request)
            if (!response.isSuccessful) {
                 throw Exception("Failed to send reply: ${response.code()}")
            }
        }
    suspend fun makeUserAdmin(conversationId: String, userId: String): Conversation = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw Exception("No auth token found")
        val request = UpdateAdminsRequest(adminsToAdd = listOf(userId))
        val response = api.updateConversationAdmins("Bearer $token", conversationId, request)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response from admin update")
        } else {
            throw Exception("Failed to make user admin: ${response.code()}")
        }
    }

    suspend fun removeUserAdmin(conversationId: String, userId: String): Conversation = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw Exception("No auth token found")
        val request = UpdateAdminsRequest(adminsToRemove = listOf(userId))
        val response = api.updateConversationAdmins("Bearer $token", conversationId, request)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response from admin update")
        } else {
            throw Exception("Failed to remove user admin: ${response.code()}")
        }
    }
    suspend fun addMemberToConversation(conversationId: String, newUserId: String): Conversation = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw Exception("No auth token found")
        val request = AddMemberRequest(userId = newUserId)
        val response = api.addMemberToConversation("Bearer $token", conversationId, request)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response from add member")
        } else {
            throw Exception("Failed to add member: ${response.code()}")
        }
    }
    suspend fun removeMemberFromConversation(conversationId: String, userId: String): Conversation = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw Exception("No auth token found")
        val request = RemoveMemberRequest(userId = userId)
        val response = api.removeMemberFromConversation("Bearer $token", conversationId, request)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response from remove member")
        } else {
            throw Exception("Failed to remove member: ${response.code()}")
        }
    }
    suspend fun updateAvatarUrl(avatarUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))
        try {
            val request = AvatarUpdateRequest(avatarUrl)
            val response = api.updateUserAvatar("Bearer $token", request)
            return@withContext if (response.isSuccessful) {
                sharedPrefs.edit().putString("avatar_url", avatarUrl).apply()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update avatar: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupMembers(groupId: String): List<ConversationMemberResponse> = withContext(Dispatchers.IO) {
        val token =  getToken() ?: throw Exception("No auth token found")
        val response = api.getConversationMembers("Bearer $token", groupId)
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("Failed to fetch group members: ${response.code()}")
        }
    }
    suspend fun getConversationDetails(conversationId: String): ConversationDetailsResponse? {
        val token = getToken() ?: throw Exception("No auth token found")
        val response = api.getConversationDetails("Bearer $token", conversationId)
        return if (response.isSuccessful) response.body() else null
    }

    suspend fun loadGroupAvatar(conversationId: String): String? {
        return try {
            val token = getToken() ?: throw Exception("No auth token found")
            val response = api.getGroupAvatar("Bearer $token", conversationId)
            if (response.isSuccessful) {
                response.body()?.groupAvatar
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("loadGroupAvatar", "Exception loading avatar", e)
            null
        }
    }
    suspend fun updateGroupAvatar(conversationId: String, base64Avatar: String) {
        val token = getToken() ?: throw Exception("No auth token found")
         val request = ConversationUpdateRequest(group_avatar_url = base64Avatar)
        val response = api.updateConversation("Bearer $token", conversationId, request)
        if (!response.isSuccessful) {
            throw Exception("Failed to update avatar: ${response.errorBody()?.string()}")
        }
    }
    suspend fun updateGroupName(conversationId: String, newName: String) {
        val token = getToken() ?: throw Exception("No auth token found")
        val request = ConversationUpdateRequest(group_name = newName)
        val response = api.updateConversation("Bearer $token", conversationId, request)
        if (!response.isSuccessful) {
            throw Exception("Failed to update group name: ${response.errorBody()?.string()}")
        }
    }
    suspend fun getConversations(): Result<List<Conversation>> = withContext(Dispatchers.IO) {
        try {
            val token =  getToken() ?: throw Exception("No auth token found")
            val id = userId ?: return@withContext Result.failure(Exception("User ID not found"))
             val response = api.getUserConversations(token, id)
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: run {
                    Result.failure(Exception("Empty conversations response"))
                }
            } else {
                Result.failure(Exception("Failed to fetch conversations: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun sendFriendRequest(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))
        return@withContext try {
            val response = api.sendFriendRequest("Bearer $token", FriendActionRequest( userId))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to send friend request: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun acceptFriendRequest(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))
        return@withContext try {
            val response = api.acceptFriendRequest("Bearer $token", FriendActionRequest(userId))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to accept friend request: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun unfriendUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))
        return@withContext try {
            val response = api.removeFriend("Bearer $token", FriendActionRequest(userId))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to unfriend user: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getFriends(): Result<List<com.codewithram.secretchat.ui.gallery.User>> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        return@withContext try {
            val response = api.getFriendsList("Bearer $token")
            if (response.isSuccessful) {
                val friendsResponse = response.body()
                val friends = friendsResponse?.friends ?: emptyList()
                Result.success(friends)
            } else {
                Result.failure(Exception("Failed to fetch friends: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getPendingRequests(): Result<List<com.codewithram.secretchat.ui.gallery.User>> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))
        return@withContext try {
            val response = api.getPendingFriendRequests("Bearer $token")
            if (response.isSuccessful) {
                val pending = response.body()?.pending_requests ?: emptyList()
                Result.success(pending)
            } else {
                Result.failure(Exception("Failed to fetch pending requests: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getDiscoverableUsers(): Result<List<com.codewithram.secretchat.ui.gallery.User>> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        return@withContext try {
            val response = api.getDiscoverableUsers("Bearer $token")
            if (response.isSuccessful) {
                val users = response.body() ?: emptyList()
                Result.success(users)
            } else {
                Result.failure(Exception("Failed to fetch discoverable users: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun getMutualFriends(otherUserId: String): Result<List<com.codewithram.secretchat.ui.gallery.User>> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        return@withContext try {
            val response = api.getMutualFriends("Bearer $token", otherUserId)
            if (response.isSuccessful) {
                val mutual = response.body()?.mutualFriends ?: emptyList()
                Result.success(mutual)
            } else {
                Result.failure(Exception("Failed to fetch mutual friends: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun createConversation(groupName: String?, memberIds: List<String>, isGroup: Boolean): Result<Conversation> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))
        val creatorId = userId ?: return@withContext Result.failure(Exception("User ID not found"))
        val request = ConversationRequest(
            isGroup = isGroup,
            groupName = groupName,
            groupAvatarUrl = null,
            createdBy = creatorId,
            memberIds = listOf(creatorId) + memberIds
        )
        try {
            val response = api.createConversation("Bearer $token", request)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response from conversation creation"))
            } else {
                Result.failure(Exception("Create conversation failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    fun logout() {
        sharedPrefs.edit().clear().apply()
    }
}
