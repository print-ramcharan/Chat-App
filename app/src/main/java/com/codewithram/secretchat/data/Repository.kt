package com.codewithram.secretchat.data

import AddMemberRequest
import AvatarUpdateRequest
import Conversation
import ConversationDetailsResponse
import ConversationMemberResponse
import ConversationRequest
import ConversationUpdateRequest
import Friend
import FriendActionRequest
import FriendsResponse
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
//import com.codewithram.secretchat.ui.gallery.User
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
            Log.d("Repository", "Auth token updated")
        }

    public var userId: String?
        get() = sharedPrefs.getString("user_id", null)
        set(value) {
            sharedPrefs.edit().putString("user_id", value).apply()
            Log.d("Repository", "User ID updated")
        }

    internal fun getToken(): String? = sharedPrefs.getString("auth_token", null)

    // Fetch conversations for current user using bearer token
    suspend fun getConversationsForCurrentUser(): List<Conversation> {
        val token = getToken()
        Log.d("Repository", "Fetching conversations with token: $token")
        if (token == null) throw Exception("Unauthorized: No auth token found")

        val response = ApiClient.apiService.listConversationsForCurrentUser("Bearer $token")
        return if (response.isSuccessful) {
            val conversationsResponse = response.body()
            Log.d("Repository", "Fetched conversations count: ${conversationsResponse?.data?.size ?: 0}")
            conversationsResponse?.data ?: emptyList()
        } else {
            Log.e("Repository", "Failed to fetch conversations: ${response.code()} ${response.message()}")
            throw Exception("Failed to fetch conversations: ${response.code()}")
        }
    }

    suspend fun getMessages(conversationId: String): List<Message> = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw Exception("No auth token found")
        val response = api.getMessages("Bearer $token", conversationId)

        if (response.isSuccessful) {
            val body = response.body()
            Log.d("API", "📥 Raw API response: $body")

            val messages = body?.messages ?: emptyList()
            messages.forEach { Log.d("API", "✅ Message: ${it.id}, Statuses: ${it.status_entries}") }

            messages
        } else {
            throw Exception("Failed to get messages: ${response.code()} ${response.message()}")
        }
    }



    // Login user and store token and userId on success
    suspend fun login(email: String, password: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(LoginRequest(email, password))
            return@withContext if (response.isSuccessful) {
                response.body()?.let { loginResponse ->
                    authToken = "Bearer ${loginResponse.token}"
                    userId = loginResponse.user.id
                    Log.d("Repository", "Login successful for user ID: ${loginResponse.user.id}")
                    Result.success(loginResponse)
                } ?: run {
                    Log.e("Repository", "Empty login response")
                    Result.failure(Exception("Empty login response"))
                }
            } else {
                Log.e("Repository", "Login failed: ${response.code()} ${response.message()}")
                Result.failure(Exception("Login failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "Login exception", e)
            Result.failure(e)
        }
    }
    suspend fun updateFcmToken(fcmToken: String): Unit = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrEmpty()) {
            Log.w("Repository", "No auth token found. Skipping FCM token update.")
            return@withContext
        }

        val payload = mapOf("fcm_token" to fcmToken)
        val response = api.updateFcmToken("Bearer $token", payload)

        if (!response.isSuccessful) {
            Log.e("Repository", "Failed to update FCM token: ${response.code()} ${response.message()}")
            // Optional: handle retry or show UI feedback
        }
    }

    suspend fun updateMessageStatus(messageId: String, request: MessageStatusUpdateRequest): Unit =
        withContext(Dispatchers.IO) {
            val token = getToken() ?: throw Exception("No auth token found")

            val response = api.updateMessageStatus("Bearer $token", messageId, request)

            if (!response.isSuccessful) {
                Log.e("Repository", "❌ Failed to update message status: ${response.code()} ${response.message()}")
                throw Exception("Failed to update message status: ${response.code()}")
            } else {
                Log.d("Repository", "✅ Message marked as ${request.status_code}")
            }
        }
    suspend fun replyToMessage(messageId: String, request: ReplyRequest): Unit =
        withContext(Dispatchers.IO) {
            val token = getToken() ?: throw Exception("No auth token found")

            val response = api.replyToMessage("Bearer $token", messageId, request)

            if (!response.isSuccessful) {
                Log.e("Repository", "❌ Failed to send reply: ${response.code()} ${response.message()}")
                throw Exception("Failed to send reply: ${response.code()}")
            } else {
                Log.d("Repository", "✅ Reply sent successfully")
            }
        }



    suspend fun makeUserAdmin(conversationId: String, userId: String): Conversation = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw Exception("No auth token found")

        // Create request with admins_to_add list containing the userId
        val request = UpdateAdminsRequest(adminsToAdd = listOf(userId))

        val response = api.updateConversationAdmins("Bearer $token", conversationId, request)

        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response from admin update")
        } else {
            Log.e("Repository", "Failed to update admins: ${response.code()} ${response.message()}")
            throw Exception("Failed to make user admin: ${response.code()}")
        }
    }

    suspend fun removeUserAdmin(conversationId: String, userId: String): Conversation = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw Exception("No auth token found")

        // Create request with admins_to_remove list containing the userId
        val request = UpdateAdminsRequest(adminsToRemove = listOf(userId))

        val response = api.updateConversationAdmins("Bearer $token", conversationId, request)

        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response from admin update")
        } else {
            Log.e("Repository", "Failed to update admins: ${response.code()} ${response.message()}")
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
            Log.e("Repository", "Failed to add member: ${response.code()} ${response.message()}")
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
            Log.e("Repository", "Failed to remove member: ${response.code()} ${response.message()}")
            throw Exception("Failed to remove member: ${response.code()}")
        }
    }

    suspend fun updateAvatarUrl(avatarUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        try {
            val request = AvatarUpdateRequest(avatarUrl)
            val response = api.updateUserAvatar("Bearer $token", request)

            return@withContext if (response.isSuccessful) {
                // Store new URL in shared preferences
                sharedPrefs.edit().putString("avatar_url", avatarUrl).apply()
                Log.d("Repository", "Avatar updated successfully")
                Result.success(Unit)
            } else {
                Log.e("Repository", "Failed to update avatar: ${response.code()} ${response.message()}")
                Result.failure(Exception("Failed to update avatar: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "Exception while updating avatar", e)
            Result.failure(e)
        }
    }





    suspend fun getGroupMembers(groupId: String): List<ConversationMemberResponse> = withContext(Dispatchers.IO) {
        val token =  getToken() ?: throw Exception("No auth token found")

        val response = api.getConversationMembers("Bearer $token", groupId)
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            Log.e("Repository", "Failed to fetch group members: ${response.code()} ${response.message()}")
            throw Exception("Failed to fetch group members: ${response.code()}")
        }
    }
    suspend fun getConversationDetails(conversationId: String): ConversationDetailsResponse? {
        // Calls your API endpoint for conversation details
        val token = getToken() ?: throw Exception("No auth token found") // Implement token retrieval accordingly
        val response = api.getConversationDetails("Bearer $token", conversationId)
        return if (response.isSuccessful) response.body() else null
    }

    suspend fun loadGroupAvatar(conversationId: String): String? {
        return try {
            val token = getToken() ?: throw Exception("No auth token found")
            val response = api.getGroupAvatar("Bearer $token", conversationId)

            if (response.isSuccessful) {
                Log.d("loadGroupAvatar", "Avatar loaded successfully ${response.body()}")
                response.body()?.groupAvatar
            } else {
                Log.e("loadGroupAvatar", "API error: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e("loadGroupAvatar", "Exception loading avatar", e)
            null
        }
    }

    suspend fun updateGroupAvatar(conversationId: String, base64Avatar: String) {
        val token = getToken() ?: throw Exception("No auth token found")

        Log.d("avatar before", base64Avatar)
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




    // Alternative method to fetch conversations (if needed)
    suspend fun getConversations(): Result<List<Conversation>> = withContext(Dispatchers.IO) {
        try {
            val token =  getToken() ?: throw Exception("No auth token found")
            val id = userId ?: return@withContext Result.failure(Exception("User ID not found"))

            Log.d("Repository", "Fetching user conversations for user ID: $id")

            val response = api.getUserConversations(token, id)
            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d("Repository", "Successfully fetched user conversations: ${it.size}")
                    Result.success(it)
                } ?: run {
                    Log.e("Repository", "Empty conversations response")
                    Result.failure(Exception("Empty conversations response"))
                }
            } else {
                Log.e("Repository", "Failed to fetch conversations: ${response.code()} ${response.message()}")
                Result.failure(Exception("Failed to fetch conversations: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "Exception fetching conversations", e)
            Result.failure(e)
        }
    }


    // Send a friend request
    suspend fun sendFriendRequest(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        return@withContext try {
            val response = api.sendFriendRequest("Bearer $token", FriendActionRequest( userId))
            if (response.isSuccessful) {
                Log.d("Repository", "Friend request sent to $userId")
                Result.success(Unit)
            } else {
                Log.e("Repository", "Send friend request failed: ${response.code()} ${response.message()}")
                Result.failure(Exception("Failed to send friend request: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "Exception in sendFriendRequest", e)
            Result.failure(e)
        }
    }

    // Accept a friend request
    suspend fun acceptFriendRequest(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        return@withContext try {
            val response = api.acceptFriendRequest("Bearer $token", FriendActionRequest(userId))
            if (response.isSuccessful) {
                Log.d("Repository", "Friend request from $userId accepted")
                Result.success(Unit)
            } else {
                Log.e("Repository", "Accept friend request failed: ${response.code()} ${response.message()}")
                Result.failure(Exception("Failed to accept friend request: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "Exception in acceptFriendRequest", e)
            Result.failure(e)
        }
    }


    // Unfriend a user
    suspend fun unfriendUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        return@withContext try {
            val response = api.removeFriend("Bearer $token", FriendActionRequest(userId))
            if (response.isSuccessful) {
                Log.d("Repository", "Unfriended user $userId successfully")
                Result.success(Unit)
            } else {
                Log.e("Repository", "Unfriend failed: ${response.code()} ${response.message()}")
                Result.failure(Exception("Failed to unfriend user: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "Exception in unfriendUser", e)
            Result.failure(e)
        }
    }

    // Get current user's friends list
    suspend fun getFriends(): Result<List<com.codewithram.secretchat.ui.gallery.User>> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        return@withContext try {
            val response = api.getFriendsList("Bearer $token")
            if (response.isSuccessful) {
                val friendsResponse = response.body()
                val friends = friendsResponse?.friends ?: emptyList()
                Log.d("Repository", "Fetched ${friends.size} friends")
                Result.success(friends)
            } else {
                Log.e("Repository", "Get friends failed: ${response.code()} ${response.message()}")
                Result.failure(Exception("Failed to fetch friends: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "Exception in getFriends", e)
            Result.failure(e)
        }
    }

    // Get pending friend requests
    suspend fun getPendingRequests(): Result<List<com.codewithram.secretchat.ui.gallery.User>> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        return@withContext try {
            val response = api.getPendingFriendRequests("Bearer $token")
            if (response.isSuccessful) {
                val pending = response.body()?.pending_requests ?: emptyList()
                Log.d("Repository", "Fetched ${pending.size} pending friend requests")
                Result.success(pending)
            } else {
                Log.e("Repository", "Get pending requests failed: ${response.code()} ${response.message()}")
                Result.failure(Exception("Failed to fetch pending requests: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "Exception in getPendingRequests", e)
            Result.failure(e)
        }
    }

    // Fetch users that current user might want to befriend
    suspend fun getDiscoverableUsers(): Result<List<com.codewithram.secretchat.ui.gallery.User>> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        return@withContext try {
            val response = api.getDiscoverableUsers("Bearer $token")
            if (response.isSuccessful) {
                val users = response.body() ?: emptyList()
                Log.d("Repository", "Fetched ${users.size} discoverable users")
                Result.success(users)
            } else {
                Log.e("Repository", "Get discoverable users failed: ${response.code()} ${response.message()}")
                Result.failure(Exception("Failed to fetch discoverable users: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "Exception in getDiscoverableUsers", e)
            Result.failure(e)
        }
    }


    // Get mutual friends with another user
    suspend fun getMutualFriends(otherUserId: String): Result<List<com.codewithram.secretchat.ui.gallery.User>> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("No auth token found"))

        return@withContext try {
            val response = api.getMutualFriends("Bearer $token", otherUserId)
            Log.d("response", response.toString())
            if (response.isSuccessful) {
                val mutual = response.body()?.mutualFriends ?: emptyList()
                Log.d("Repository", "Fetched ${mutual.size} mutual friends with $otherUserId")
                Result.success(mutual)
            } else {
                Log.e("Repository", "Get mutual friends failed: ${response.code()} ${response.message()}")
                Result.failure(Exception("Failed to fetch mutual friends: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "Exception in getMutualFriends", e)
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


    // Clear stored data and logout user
    fun logout() {
        sharedPrefs.edit().clear().apply()
        Log.d("Repository", "User logged out, preferences cleared")
    }
}
