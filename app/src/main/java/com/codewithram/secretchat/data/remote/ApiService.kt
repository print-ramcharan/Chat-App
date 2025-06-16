package com.codewithram.secretchat.data.remote

import AddMemberRequest
import AvatarUpdateRequest
import Conversation
import ConversationDetailsResponse
import ConversationMemberResponse
import ConversationRequest
import ConversationUpdateRequest
import ConversationsResponse
import DeviceRequest
import DeviceResponse
import FriendActionRequest
import FriendsResponse
import Message
import MessageRequest
//import MessageStatus
import MessageStatusUpdateRequest
import MessagesResponse
import MutualFriendsResponse
import PendingRequestsResponse
import RegisterRequest
import RemoveMemberRequest
import StatusEntry
import UpdateAdminsRequest
import UserResponse
import com.codewithram.secretchat.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth (public)
    @POST("/api/register")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<UserResponse>

    @POST("/api/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>

    // Users (protected)
    @GET("/api/users/search")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("query") query: String
    ): Response<List<User>>

    @GET("/api/users/{id}")
    suspend fun getUserById(
        @Header("Authorization") token: String,
        @Path("id") userId: String
    ): Response<User>

    @PATCH("/api/users/avatar")
    suspend fun updateUserAvatar(
        @Header("Authorization") token: String,
        @Body avatarUpdate: AvatarUpdateRequest
    ): Response<Unit>

    @GET("/api/users/{id}/conversations")
    suspend fun getUserConversations(
        @Header("Authorization") token: String,
        @Path("id") userId: String
    ): Response<List<Conversation>>

    // Devices (protected)
    @POST("/api/devices")
    suspend fun createDevice(
        @Header("Authorization") token: String,
        @Body deviceRequest: DeviceRequest
    ): Response<DeviceResponse>

    @DELETE("/api/devices/{id}")
    suspend fun deleteDevice(
        @Header("Authorization") token: String,
        @Path("id") deviceId: String
    ): Response<Unit>

    // Conversations (protected)
    @POST("/api/conversations")
    suspend fun createConversation(
        @Header("Authorization") token: String,
        @Body conversationRequest: ConversationRequest
    ): Response<Conversation>

    @PATCH("/api/conversations/{id}/add_member")
    suspend fun addMemberToConversation(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Body memberRequest: AddMemberRequest
    ): Response<Conversation>

    @POST("/api/conversations/{conversationId}/remove_member")
    suspend fun removeMemberFromConversation(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: String,
        @Body request: RemoveMemberRequest
    ): Response<Conversation>

    @GET("/api/conversations")
    suspend fun listConversationsForCurrentUser(
        @Header("Authorization") token: String
    ): Response<ConversationsResponse>

    @GET("/api/conversations/{id}/members")
    suspend fun getConversationMembers(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String
    ): Response<List<ConversationMemberResponse>>

    @GET("/api/conversations/{id}/details")
    suspend fun getConversationDetails(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String
    ): Response<ConversationDetailsResponse>

    @PATCH("/api/conversations/{id}")
    suspend fun updateConversation(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Body updateRequest: ConversationUpdateRequest
    ): Response<Conversation>

    @PATCH("/api/conversations/{id}/admins")
    suspend fun updateConversationAdmins(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Body adminsRequest: UpdateAdminsRequest
    ): Response<Conversation>

    @DELETE("/api/conversations/{id}")
    suspend fun deleteConversation(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String
    ): Response<Unit>

    // Messages (protected)
    @POST("/api/conversations/{conversation_id}/messages")
    suspend fun createMessage(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String,
        @Body messageRequest: MessageRequest
    ): Response<Message>

    @GET("/api/conversations/{conversation_id}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String
    ): Response<MessagesResponse>

    // Message statuses (protected)
    @PATCH("/api/messages/{message_id}/status")
    suspend fun updateMessageStatus(
        @Header("Authorization") token: String,
        @Path("message_id") messageId: String,
        @Body statusRequest: MessageStatusUpdateRequest
    ): Response<StatusEntry>

    //Friends and Discovery


    // Friends and Discovery (protected)

    @GET("/api/friendships/friends")
    suspend fun getFriendsList(
        @Header("Authorization") token: String
    ): Response<FriendsResponse>

    @GET("/api/friendships/pending")
    suspend fun getPendingFriendRequests(
        @Header("Authorization") token: String
    ): Response<PendingRequestsResponse>

    @GET("/api/friendships/discover")
    suspend fun getDiscoverableUsers(
        @Header("Authorization") token: String
    ): Response<List<com.codewithram.secretchat.ui.gallery.User>> // Ensure Phoenix route exists

    @POST("/api/friendships/send")
    suspend fun sendFriendRequest(
        @Header("Authorization") token: String,
        @Body request: FriendActionRequest
    ): Response<Unit>

    @POST("/api/friendships/accept")
    suspend fun acceptFriendRequest(
        @Header("Authorization") token: String,
        @Body request: FriendActionRequest
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "/api/friendships", hasBody = true)
    suspend fun removeFriend(
        @Header("Authorization") token: String,
        @Body request: FriendActionRequest
    ): Response<Unit>

    @GET("/api/friendships/mutual/{user_id}")
    suspend fun getMutualFriends(
        @Header("Authorization") token: String,
        @Path("user_id") userId: String
    ): Response<MutualFriendsResponse>


}
