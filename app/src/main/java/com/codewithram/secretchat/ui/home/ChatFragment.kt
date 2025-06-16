package com.codewithram.secretchat.ui.home

import Attachment
import Message
import StatusEntry
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codewithram.secretchat.R
import com.codewithram.secretchat.data.Repository
import com.codewithram.secretchat.databinding.FragmentChatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.toString

class ChatFragment : Fragment() {
    private val TAG = "ChatFragment"
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ChatAdapter
    private lateinit var repo: Repository
    private lateinit var phoenixChannel: PhoenixChannel
    private var initialHistoryLoaded = false
    private val pendingMessagesByClientRef = mutableMapOf<String, Int>()
    private val seenClientRefs = mutableSetOf<String>()
    private var heartbeatJob: Job? = null
    private val topic by lazy { phoenixChannel.topic }



    private fun getTokenFromPrefs(): String {
        val prefs = requireContext().getSharedPreferences("secret_chat_prefs", 0)
        return prefs.getString("auth_token", "") ?: ""
    }
    private val conversationUUID by lazy {
        UUID.fromString(requireArguments().getString("conversationId")
            ?: error("conversationId missing"))
    }
    private val chatName by lazy {
        requireArguments().getString("chatName") ?: "Group"
    }
    private val currentUserUUID: UUID
        get() = UUID.fromString(
            requireContext()
                .getSharedPreferences("secret_chat_prefs", 0)
                .getString("user_id", "") ?: ""
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        repo = Repository(requireContext().getSharedPreferences("secret_chat_prefs", 0))

        binding.sendButton.isEnabled = true
        setupRecycler()
        connectPhoenixChannel()
        setupSendButton()

        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbarView = view.findViewById<View>(R.id.custom_chat_toolbar)
        val groupName = toolbarView.findViewById<TextView>(R.id.groupName)
        val groupImage = toolbarView.findViewById<ImageView>(R.id.groupImage)
        val editGroup = toolbarView.findViewById<ImageButton>(R.id.editGroup)
        val backButton = toolbarView.findViewById<ImageView>(R.id.backButton)

        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        groupName.text = chatName
        groupImage.setImageResource(R.drawable.ic_default_profile)

        toolbarView.setOnClickListener { showGroupInfoBottomSheet() }
        editGroup.setOnClickListener {
            Toast.makeText(requireContext(), "Edit clicked", Toast.LENGTH_SHORT).show()
        }

        loadMessages()
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    }

    private fun showGroupInfoBottomSheet() {
        val bottomSheet = GroupInfoBottomSheet().apply {
            arguments = Bundle().apply {
                putString("group_id", conversationUUID.toString())
            }
        }
        bottomSheet.show(parentFragmentManager, "GroupInfoBottomSheet")
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.chat_toolbar_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_group_info -> {
                showGroupInfoBottomSheet(); true
            }
            R.id.action_exit_group -> {
                Toast.makeText(requireContext(), "Exited group", Toast.LENGTH_SHORT).show(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupRecycler() {
        adapter = ChatAdapter(currentUserUUID).apply {
            onMessageRead = { id ->
                if (initialHistoryLoaded) sendReadReceipt(id)
            }
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatFragment.adapter
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val msgs = withContext(Dispatchers.IO) {
                repo.getMessages(conversationUUID.toString())
            }
            adapter.setAll(msgs)
            Log.d("msgs", msgs.toString())
            if (msgs.isNotEmpty()) {
                binding.recyclerView.scrollToPosition(msgs.lastIndex)
            }
            initialHistoryLoaded = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun connectPhoenixChannel() {
        val token = requireContext().getSharedPreferences("secret_chat_prefs", 0)
            .getString("auth_token", "") ?: ""

        phoenixChannel = PhoenixChannel(
            socketUrl = "ws://192.168.0.190:4000/socket/websocket?token=$token",
            topic = "chat:$conversationUUID",
            params = mapOf("token" to token)
        )

        phoenixChannel.onJoinSuccess = {
            Log.d(TAG, "Joined $it")
        }

        phoenixChannel.onMessageReceived = { event, payload ->
            activity?.runOnUiThread {
                when (event) {
                    "new_message" -> handleNewMessage(payload)
                    "phx_reply" -> handleReply(payload)
                    "message_status_updated" -> updateMessage(payload)
                    "message_status_update" -> updateStatusEntry(payload)
                }
            }
        }

        phoenixChannel.connect()
        startHeartbeat()

        phoenixChannel.onClose = {
            Log.w(TAG, "Channel closed. Attempting to reconnect...")
            reconnectWithBackoff()
        }

        phoenixChannel.onError = { error ->
            Log.e(TAG, "Socket error: $error")
            reconnectWithBackoff()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleNewMessage(payload: Any) {
        try {
            val msg = parseMessageFromJson(payload as JSONObject)

            Log.d("message Data", msg.toString())
            val existingIndex = adapter.findMessageIndexById(msg.id)

            if (existingIndex != -1) {
                adapter.updateMessageAt(existingIndex, msg)
                adapter.notifyItemChanged(existingIndex)
            } else {
                adapter.addMessage(msg)
                adapter.notifyItemInserted(adapter.itemCount - 1)//remove if problem
                binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            }

            if (msg.sender_id != currentUserUUID) {
                sendReadAcknowledgment(msg.id.toString())
            }

        } catch (e: Exception) {
            Log.e(TAG, "handleNewMessage parse error: $e")
        }
    }



    private fun handleReply(payload: JSONObject) {
        val responseObject = payload.optJSONObject("payload")
            ?.optJSONObject("response")

        responseObject?.let {
            val msg = parseMessageFromJson(it)
            val clientRef = msg.client_ref

            if (clientRef.isNotEmpty() && seenClientRefs.contains(clientRef)) {
                Log.d(TAG, "Duplicate phx_reply ignored: $clientRef")
                return
            }

            seenClientRefs.add(clientRef)

            val index = pendingMessagesByClientRef[clientRef]
            if (index != null) {
                adapter.updateMessageAt(index, msg)
                adapter.notifyItemChanged(index)
                pendingMessagesByClientRef.remove(clientRef)
            } else {
                if (!adapter.containsMessageId(msg.id)) {
                    adapter.addMessage(msg)
                    binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }



    private fun updateMessage(payload: Any) {
        val msg = parseMessageFromJson(payload as JSONObject)
        val index = adapter.findMessageIndexById(msg.id)
        if (index != -1) {
            adapter.updateMessageAt(index, msg)
            adapter.notifyItemChanged(index)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateStatusEntry(payload: JSONObject) {
        val messageId = payload.optString("message_id")
        val userId = payload.optString("user_id")
        val newStatus = payload.optString("status")
        adapter.updateMessageStatus(messageId, userId, newStatus)
    }

    private fun reconnectWithBackoff(retries: Int = 5) {
        if (retries == 0) return

        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Reconnecting to Phoenix Channel...")
            phoenixChannel.connect()
        }, 3000L)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()  // ✅ This line works now
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(25_000)
                try {
                    val heartbeatJson = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", JSONObject.NULL)
                    }
                    phoenixChannel.getSocket().send(heartbeatJson.toString())
                    Log.d(TAG, "✅ Heartbeat sent")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Heartbeat error: ${e.message}")
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendReadAcknowledgment(messageId: String) {
        val ackPayload = JSONObject().apply {
            put("message_id", messageId)
            put("status", "read")
            put("user_id", currentUserUUID.toString())
            put("status_ts", Instant.now().toString())
        }

        phoenixChannel.push("update_message_status", ackPayload)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        phoenixChannel.disconnect()
        _binding = null

        (requireActivity() as? AppCompatActivity)?.supportActionBar?.apply {
            show()
            setDisplayShowCustomEnabled(false)
            setDisplayShowTitleEnabled(true)
            title = "Default Title"
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendReadReceipt(messageId: UUID) {
        val now = Instant.now().toString()
        val payload = JSONObject().apply {
            put("message_id", messageId.toString())
            put("user_id", currentUserUUID.toString())
            put("status", "read")
            put("status_ts", now)
        }
        phoenixChannel.push("update_message_status", payload) {
            Log.d(TAG, "ReadReceipt ack: $it")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupSendButton() {
        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val now = Instant.now().toString()
            val messageId = UUID.randomUUID()
            val clientRef = UUID.randomUUID().toString()

            val pendingMsg = Message(
                id = messageId,
                client_ref = clientRef,
                encrypted_body = text,
                message_type = "text",
                sender_id = currentUserUUID,
                conversation_id = conversationUUID,
                inserted_at = now,
                updated_at = now,
                attachments = emptyList(),
                sender_display_name = "",
                sender_avatar_data = "",
                status_entries = listOf(
                    StatusEntry(
                        id = UUID.randomUUID(),
                        message_id = messageId,
                        user_id = currentUserUUID,
                        status = "pending",
                        status_ts = now,
                        inserted_at = now,
                        updated_at = now,
                        display_name = "",
                        avatar_data = ""
                    )
                )
            )

//            adapter.addMessage(pendingMsg)
            val pendingIndex = adapter.itemCount - 1
            pendingMessagesByClientRef[clientRef] = pendingIndex

            binding.recyclerView.scrollToPosition(pendingIndex)
            binding.messageInput.text?.clear()

            val payload = JSONObject().apply {
                put("client_ref", clientRef)
                put("encrypted_body", text)
                put("message_type", "text")
            }

            phoenixChannel.pushWithReply(
                event = "send_message",
                payload = payload,
                onOk = {
                    Log.d("PhoenixChannel", "✅ Message sent successfully: $it")
                },
                onError = {
                    Log.e("PhoenixChannel", "❌ Message send error: $it")
                },
                onTimeout = {
                    Log.e("PhoenixChannel", "⏱️ Message send timed out")
                }
            )

        }
    }

    private fun parseMessageFromJson(jsonObj: JSONObject): Message {
        Log.d("MessageParser", "Raw JSON: $jsonObj")

        val messageObject = if (jsonObj.has("payload") && jsonObj.getJSONObject("payload").has("response")) {
            jsonObj.getJSONObject("payload").getJSONObject("response")
        } else {
            jsonObj
        }
        Log.d("MessageParser", "Parsed messageObject: $messageObject")


        val conversationId = topic.substringAfter("chat:").trim()

        val id = UUID.fromString(messageObject.getString("id"))
        val body = messageObject.getString("encrypted_body")
        val sender = UUID.fromString(messageObject.getString("sender_id"))
        val conv = UUID.fromString(messageObject.optString("conversation_id", conversationId))
        val ins = messageObject.getString("inserted_at")
        val upd = messageObject.optString("updated_at", ins)
        val clientRef = messageObject.optString("client_ref", "")
        val senderDisplayName = messageObject.optString("sender_display_name", "")
        val senderAvatarData = messageObject.optString("sender_avatar_data", "")


        val attachments = JSONArray(messageObject.optString("attachments", "[]")).let { arr ->
            List(arr.length()) { i ->
                arr.getJSONObject(i).run {
                    Attachment(
                        UUID.fromString(getString("id")),
                        getString("file_url"),
                        getString("mime_type"),
                        UUID.fromString(getString("message_id")),
                        getString("inserted_at"),
                        getString("updated_at")
                    )
                }
            }
        }

        val statuses = JSONArray(messageObject.optString("statuses", "[]")).let { arr ->
            List(arr.length()) { i ->
                arr.getJSONObject(i).run {
                    StatusEntry(
                        id = UUID.randomUUID(),
                        message_id = UUID.fromString(getString("message_id")),
                        user_id = UUID.fromString(getString("user_id")),
                        status = getString("status"),
                        status_ts = getString("status_ts"),
                        inserted_at = getString("inserted_at"),
                        updated_at = getString("updated_at"),
                        display_name = optString("display_name", ""),  // 👈 extra fields
                        avatar_data = optString("avatar_data", "")
                    )
                }
            }
        }

        return Message(
            id = id,
            client_ref = clientRef,
            encrypted_body = body,
            message_type = messageObject.getString("message_type"),
            sender_id = sender,
            conversation_id = conv,
            inserted_at = ins,
            updated_at = upd,
            attachments = attachments,
            status_entries = statuses,
            sender_display_name = senderDisplayName,
            sender_avatar_data = senderAvatarData
        )
    }


}


