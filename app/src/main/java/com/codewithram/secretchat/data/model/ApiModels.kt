import java.util.UUID
import java.time.OffsetDateTime
import com.google.gson.annotations.SerializedName

// User.kt
data class User(
    val id: UUID,
    val username: String,
    val phoneNumber: String,
    val displayName: String,
    val avatarUrl: String?,
    val publicKey: String,
    val insertedAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val hashedPassword: String? = null // nullable for security reasons
)

// Contact.kt
data class Contact(
    val id: UUID,
    val userId: UUID,
    val contactUserId: UUID,
    val insertedAt: OffsetDateTime
)


//data class Conversation(
//    val id: UUID,
//
//    @SerializedName("is_group")
//    val isGroup: Boolean,
//
//    @SerializedName("group_name")
//    val groupName: String?,
//
//    @SerializedName("group_avatar_url")
//    val groupAvatarUrl: String?,
//
//    @SerializedName("created_by")
//    val createdBy: UUID,
//
//    @SerializedName("inserted_at")
//    val insertedAt: String,
////    val insertedAt: OffsetDateTime,
//
//    @SerializedName("updated_at")
//    val updatedAt: String
//)
data class Conversation(
    val id: UUID,

    @SerializedName("is_group")
    val isGroup: Boolean,

    @SerializedName("group_name")
    val groupName: String?,

    @SerializedName("group_avatar_url")
    val groupAvatarUrl: String?,

    @SerializedName("created_by")
    val createdBy: UUID,

    @SerializedName("inserted_at")
    val insertedAt: String,

    @SerializedName("updated_at")
    val updatedAt: String?,

    @SerializedName("last_message")
    val lastMessage: LastMessage?,

    @SerializedName("members")
    val members: List<Member>
)

data class LastMessage(
    val id: UUID,

    @SerializedName("inserted_at")
    val insertedAt: String,

    @SerializedName("encrypted_body")
    val encryptedBody: String,

    @SerializedName("message_type")
    val messageType: String,

    @SerializedName("sender_id")
    val senderId: UUID
)


data class MutualFriendsResponse(
    @SerializedName("mutual_friends")
    val mutualFriends: List<com.codewithram.secretchat.ui.gallery.User>
)

// ConversationMember.kt
data class ConversationMember(
    val id: UUID,
    val conversationId: UUID,
    val userId: UUID,
    val joinedAt: OffsetDateTime,
    val isAdmin: Boolean,
    val insertedAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

// Device.kt
data class Device(
    val id: UUID,
    val userId: UUID,
    val deviceName: String,
    val publicKey: String,
    val insertedAt: OffsetDateTime,
    val deviceToken: String,
    val platform: String,
    val updatedAt: OffsetDateTime
)
data class MessagesResponse(
    val messages: List<Message>
)

//data class Message(
//    val id: UUID,
//    val client_ref: String,
//    val sender_display_name: String? = null,
//    val sender_avatar_data: String? = null,
//    val encrypted_body: String,
//    val message_type: String,
//    val sender_id: UUID,
//    val conversation_id: UUID,
//    val inserted_at: String,  // use String or ZonedDateTime with adapters if you use kotlinx.serialization or Gson
//    val updated_at: String,
//    val attachments: List<Attachment>,
//    var status_entries: List<StatusEntry>,
//
//)
data class Message(
    val id: UUID,
    val client_ref: String,
    val sender_display_name: String? = null,
    val sender_avatar_data: String? = null,
    val encrypted_body: String,
    val message_type: String,
    val sender_id: UUID,
    val conversation_id: UUID? = null,
    val inserted_at: String,
    val updated_at: String? = null,
    val attachments: List<Attachment> = emptyList(),
    var status_entries: List<StatusEntry> = emptyList()
)


data class Attachment(
    val id: UUID,
    val file_url: String,
    val mime_type: String,
    val message_id: UUID,
    val inserted_at: String,
    val updated_at: String
)

data class StatusEntry(
    val id: UUID,
    val message_id: UUID,
    val user_id: UUID,
    val status: String,        // e.g. "sent", "delivered", "read"
    val status_ts: String,
    val inserted_at: String,
    val updated_at: String,
    val display_name: String,
    val avatar_data : String
)

// No request body class needed since ID is in the URL

// Response member data class
data class ConversationMemberResponse(
    val user_id: String,
    val username: String,
    val avatar_url: String?,  // nullable if your DB allows null
    val is_admin: Boolean
)

// If you want to wrap the list in a container, e.g.:
// data class MembersResponse(val members: List<ConversationMemberResponse>)

data class RegisterRequest(
    val username: String,

    @SerializedName("phone_number")
    val phone_number: String,

    @SerializedName("display_name")
    val display_name: String,

    val password: String,

    @SerializedName("public_key")
    val public_key: String
)

data class FriendActionRequest(
    @SerializedName("friend_id")
    val friend_id: String
)

data class FriendsResponse(
    val friends: List<com.codewithram.secretchat.ui.gallery.User>
)


data class PendingRequestsResponse(
    val pending_requests: List<com.codewithram.secretchat.ui.gallery.User>
)

data class Friend(
    val id: String,
    val username: String,
    val display_name: String
)

data class UserResponse(
    val id: UUID,
    val username: String,
    val phoneNumber: String,
    val displayName: String,
    val avatarUrl: String?,
    val publicKey: String,
    val insertedAt: String,  // ISO8601 string or use OffsetDateTime with proper deserialization
    val updatedAt: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val token: String,   // JWT token or similar
    val user: UserResponse
)

// Device related requests/responses

data class DeviceRequest(
    val deviceName: String,
    val publicKey: String,
    val deviceToken: String,
    val platform: String
)

data class DeviceResponse(
    val id: UUID,
    val userId: UUID,
    val deviceName: String,
    val publicKey: String,
    val insertedAt: String,
    val deviceToken: String,
    val platform: String,
    val updatedAt: String
)

// Conversation related requests
//abcdedf
data class ConversationsResponse(
    val data: List<Conversation>
)


data class Chat(
    val id: UUID,
    val name: String,          // groupName or username or conversation display name
    val lastMessage: String,   // last message preview (can be empty if none)
    val lastTimestamp: Long,   // epoch millis of last message or conversation updatedAt
    val unreadCount: Int       // number of unread messages for this chat
)

data class ConversationRequest(
    @SerializedName("is_group")
    val isGroup: Boolean,

    @SerializedName("group_name")
    val groupName: String? = null,

    @SerializedName("group_avatar_url")
    val groupAvatarUrl: String? = null,

    @SerializedName("created_by")
    val createdBy: String,

    @SerializedName("members")
    val memberIds: List<String> = emptyList()
)
data class AddMemberRequest(
    @SerializedName("user_id")
    val userId: String
)
data class RemoveMemberRequest(
    @SerializedName("user_id")
    val userId: String
)

data class AvatarUpdateRequest(

    @SerializedName("avatar_url")
    val avatarUrl: String
)


data class ConversationUpdateRequest(
    val groupName: String?,
    val groupAvatarUrl: String?
)

data class UpdateAdminsRequest(
    @SerializedName("admins_to_add")
    val adminsToAdd: List<String>? = null,

    @SerializedName("admins_to_remove")
    val adminsToRemove: List<String>? = null
)


// Message related requests

enum class MessageTypeRequest {
    TEXT, IMAGE, VIDEO, AUDIO, OTHER
}

data class MessageRequest(
    val encryptedBody: String,
    val messageType: MessageTypeRequest,
    val mediaUrl: String? = null
)

// Message status update request

enum class StatusEnumRequest {
    SENT, DELIVERED, READ
}

data class MessageStatusUpdateRequest(
    val status: StatusEnumRequest,
    val statusTs: String  // ISO8601 timestamp string
)


data class ConversationDetailsResponse(
    val id: String,
    val group_name: String?,
    val group_avatar_url: String?,
    val created_by: String,
    val creator: Creator,
    val members: List<Member>,
    val created_at: String
)

data class Creator(
    val id: String,
    val username: String,
    val avatar_url: String?
)

data class Member(
    val id: String,
    val username: String,
    val avatar_url: String?,
    val is_admin: Boolean
)

data class ConversationDetails(
    val id: String,
    val group_name: String?,
    val group_avatar_url: String?,
    val created_by: String,
    val creator: Creator,
    val members: List<Member>,
    val created_at: String
)


