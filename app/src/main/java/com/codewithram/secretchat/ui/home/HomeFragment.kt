package com.codewithram.secretchat.ui.home

import Chat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.codewithram.secretchat.R
import com.codewithram.secretchat.data.Repository
import com.codewithram.secretchat.databinding.FragmentHomeBinding
import org.json.JSONObject
import java.time.OffsetDateTime

class HomeFragment : Fragment() {

    private val localUnreadCounts = mutableMapOf<String, Int>()
    private lateinit var readStatusManager: ReadStatusManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var userAdapter: UserAdapter
    private lateinit var repository: Repository

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("HomeFragment", "onCreateView started")
        _binding = FragmentHomeBinding.inflate(inflater, container, false)


        val sharedPrefs = requireContext().getSharedPreferences("secret_chat_prefs", Context.MODE_PRIVATE)
        repository = Repository(sharedPrefs)

        readStatusManager = ReadStatusManager(requireContext())

        val factory = HomeViewModelFactory(repository)
        homeViewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]

        setupRecyclerView()

        Log.d("HomeFragment", "Calling loadChats()")
        homeViewModel.loadChats()
        homeViewModel.chats.observe(viewLifecycleOwner) { conversations ->
            binding.emptyStateMessage.visibility =
                if (conversations.isEmpty()) View.VISIBLE else View.GONE

            val chats = conversations.map { conversation ->
                val lastMsgTime = try {
                    OffsetDateTime.parse(
                        conversation.lastMessage?.insertedAt
                            ?: conversation.updatedAt
                            ?: conversation.insertedAt
                    ).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    0L
                }

                val backendUnreadCount = conversation.unreadCount ?: 0
                val currentStored = localUnreadCounts.getOrPut(conversation.id.toString()) { backendUnreadCount }
                localUnreadCounts[conversation.id.toString()] = currentStored
                val unreadCount = currentStored


                Chat(
                    id = conversation.id,
                    name = if (conversation.isGroup) conversation.groupName ?: "Group"
                    else conversation.members.firstOrNull { it.id != repository.userId }?.username ?: "Private Chat",
                    lastMessage = conversation.lastMessage?.encryptedBody ?: "",
                    lastTimestamp = lastMsgTime,
                    unreadCount = unreadCount,
                    is_group = conversation.isGroup,
                    lastMessageId = conversation.lastMessage?.id.toString(),
                    isSentByCurrentUser = if (conversation.lastMessage?.senderId.toString() == repository.userId) true else false,
                    messageStatus= conversation.lastMessage?.message_status,
                    avatarBase64 = if (conversation.isGroup) conversation.groupAvatarUrl
                    else conversation.members.firstOrNull { it.id != repository.userId }?.avatar_url
                )
            }
            userAdapter.updateData(chats)
        }

        return binding.root
    }

    private val phoenixEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = intent?.getStringExtra("event")
            val payloadString = intent?.getStringExtra("payload")

            try {
                val payload = JSONObject(payloadString ?: return)

                when (event) {
                    "unread_count_updated" ->{
                        val conversationId = payload.optString("conversation_id")
                        val unreadCount = payload.optInt("unread_count")
                        localUnreadCounts[conversationId] = unreadCount
                        Log.d("HomeFragment", "🔄 Updated unread count for $conversationId to $unreadCount")
                    }
                    "new_message" -> {
                        val conversationId = payload.optString("conversation_id")
                        val encryptedBody = payload.optString("encrypted_body")
                        val senderId = payload.optString("sender_id")
                        val messageStatus = payload.optString("message_status", null)

                        val isSentByMe = senderId == repository.userId
                        val statusToShow = if (isSentByMe) messageStatus else null

                        Log.d("HomeFragment", "📥 new_message received. Conv: $conversationId, SentByMe: $isSentByMe, Status: $statusToShow")

                        updateChatWithNewMessage(
                            conversationId = conversationId,
                            newMessage = encryptedBody,
                            senderId = senderId,
                            messageStatus = statusToShow
                        )
                    }

                    "message_status_updated" -> {
                        val conversationId = payload.optString("conversation_id")
                        val messageId = payload.optString("message_id")
                        val newStatus = payload.optString("new_status")

                        Log.d("HomeFragment", "✅ Status update for message $messageId in $conversationId → $newStatus")

                        updateMessageStatusInChatList(
                            conversationId = conversationId,
                            messageId = messageId,
                            newStatus = newStatus
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Failed to parse event payload for event: $event", e)
            }
        }
    }


    private fun setupRecyclerView() {
        userAdapter = UserAdapter(mutableListOf()) { chat ->
            val lastReadTimestamp = System.currentTimeMillis()
            readStatusManager.setLastReadTimestamp(chat.id.toString(), lastReadTimestamp)
            localUnreadCounts[chat.id.toString()] = 0

            val updatedChats = userAdapter.chats.map {
                if (it.id == chat.id) it.copy(unreadCount = 0) else it
            }
            userAdapter.updateData(updatedChats)

            val bundle = Bundle().apply {
                putString("conversationId", chat.id.toString())
                putString("chatName", chat.name)
                putBoolean("isGroup", chat.is_group)
                putString("group_avatar_url", chat.avatarBase64 ?: "")
            }
            findNavController().navigate(R.id.action_nav_home_to_chatFragment, bundle)
        }

        binding.usersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.usersRecyclerView.adapter = userAdapter
    }

    private fun updateMessageStatusInChatList(
        conversationId: String,
        messageId: String,
        newStatus: String
    ) {
        val index = userAdapter.chats.indexOfFirst { it.id.toString() == conversationId }
        if (index != -1) {
            val oldChat = userAdapter.chats[index]

            // Update only if the message is the latest one shown
            if (oldChat.lastMessageId == messageId) {
                val updatedChat = oldChat.copy(messageStatus = newStatus)
                userAdapter.chats[index] = updatedChat
                userAdapter.notifyItemChanged(index)

                Log.d("HomeFragment", "🔄 Updated chat status to $newStatus for conversation $conversationId")
            } else {
                Log.d("HomeFragment", "⚠ Skipped status update: message ID $messageId not latest in chat")
            }
        } else {
            Log.w("HomeFragment", "❓ Chat not found for conversation ID: $conversationId")
        }
    }


    private fun updateChatWithNewMessage(
        conversationId: String?,
        newMessage: String,
        senderId: String?,
        messageStatus: String? = null // optional from payload
    ) {
        if (conversationId == null) return

        val currentChats = userAdapter.chats.toMutableList()
        val index = currentChats.indexOfFirst { it.id.toString() == conversationId }

        if (index != -1) {
            val oldChat = currentChats[index]
            val isSentByMe = senderId == repository.userId

            if (!isSentByMe) {
                val oldCount = localUnreadCounts[conversationId] ?: 0
                localUnreadCounts[conversationId] = oldCount + 1
            }

            val updatedChat = oldChat.copy(
                lastMessage = newMessage,
                lastTimestamp = System.currentTimeMillis(),
                unreadCount = localUnreadCounts[conversationId] ?: 0,
                isSentByCurrentUser = isSentByMe,
                messageStatus = if (isSentByMe) messageStatus else null
            )

            currentChats[index] = updatedChat
            userAdapter.updateData(currentChats)
        }
    }


    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(phoenixEventReceiver, IntentFilter("PHOENIX_GLOBAL_EVENT"))
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(phoenixEventReceiver)
    }

    override fun onResume() {
        super.onResume()
        Log.d("HomeFragment", "onResume: Reloading chats")
        homeViewModel.loadChats()

    }

    fun refreshChats() {
        Log.d("HomeFragment", "refreshChats called from MainActivity")
        homeViewModel.loadChats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("HomeFragment", "onDestroyView called, clearing binding")
        _binding = null
    }
}
