package com.codewithram.secretchat.ui.home

import Attachment
import Message
import StatusEntry
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codewithram.secretchat.IpAddressType
import com.codewithram.secretchat.R
import com.codewithram.secretchat.ServerConfig
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

//    private var isGroupChat: Boolean = true


    private fun getTokenFromPrefs(): String {
        val prefs = requireContext().getSharedPreferences("secret_chat_prefs", 0)
        return prefs.getString("auth_token", "") ?: ""
    }
    private val conversationUUID by lazy {
        UUID.fromString(requireArguments().getString("conversationId")
            ?: error("conversationId missing"))
    }
    private var chatName: String = "Group"

//    private val chatName by lazy {
//        requireArguments().getString("chatName") ?: "Group"
//    }
//    private val avatarBase64 by lazy {
//        requireArguments().getString("group_avatar_url") ?: ""
//    }
    private var avatarBase64: String = ""
    private val isGroupChat by lazy { arguments?.getBoolean("isGroup") ?: true }
//    private val chatName by lazy { arguments?.getString("chatName") ?: "Chat" }

    private  lateinit var groupName: TextView
    private lateinit var groupImage: ImageView

    private val currentUserUUID: UUID
        get() = UUID.fromString(
            requireContext()
                .getSharedPreferences("secret_chat_prefs", 0)
                .getString("user_id", "") ?: ""
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        chatName = requireArguments().getString("chatName") ?: "Group"
        avatarBase64 = requireArguments().getString("group_avatar_url") ?: ""

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
         groupName = toolbarView.findViewById<TextView>(R.id.groupName)
         groupImage = toolbarView.findViewById<ImageView>(R.id.groupImage)
        val editGroup = toolbarView.findViewById<ImageButton>(R.id.editGroup)
        val backButton = toolbarView.findViewById<ImageView>(R.id.backButton)

        parentFragmentManager.setFragmentResultListener("group_update", viewLifecycleOwner) { _, bundle ->
            val newName = bundle.getString("updated_name")
            val newAvatar = bundle.getString("updated_avatar_base64")

            adapter.isPrivate = isGroupChat
            // Handle group name update
            newName?.let {
                groupName.text = it
                val payload = JSONObject().apply {
                    put("group_id", conversationUUID.toString())
                    put("group_name", it)
                }
                phoenixChannel.push("group_info_updated", payload)
            }

            // Handle avatar update
            newAvatar?.let {
                try {
                    val imageBytes = Base64.decode(it, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    groupImage.setImageBitmap(bitmap)

                    val payload = JSONObject().apply {
                        put("group_id", conversationUUID.toString())
                        put("group_avatar_url", it) // assuming backend handles Base64 string
                    }
                    groupImage.setOnClickListener {
                        showImagePreviewDialog(requireContext(), bitmap)
                    }
                    phoenixChannel.push("group_info_updated", payload)
                } catch (e: Exception) {
                    groupImage.setImageResource(R.drawable.account_circle)
                }
            }
        }
        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        groupName.text = chatName
//        groupImage.setImageResource(R.drawable.ic_default_profile)

        if (avatarBase64.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                groupImage.setImageBitmap(bitmap)

                // Set up click listener to open in floating dialog
                groupImage.setOnClickListener {
                    showImagePreviewDialog(requireContext(), bitmap)
                }

            } catch (e: Exception) {
                groupImage.setImageResource(R.drawable.ic_default_profile)
            }
        } else {
            groupImage.setImageResource(R.drawable.ic_default_profile)
        }

        if (isGroupChat) {
            toolbarView.setOnClickListener { showGroupInfoBottomSheet() }
            editGroup.visibility = View.VISIBLE
            editGroup.setOnClickListener {
                Toast.makeText(requireContext(), "Edit clicked", Toast.LENGTH_SHORT).show()
            }
        } else {
            editGroup.visibility = View.GONE
        }


        loadMessages()
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    }
    private fun showImagePreviewDialog(context: Context, bitmap: Bitmap) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val imageView = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(800, 800, Gravity.CENTER)
            background = ContextCompat.getDrawable(context, R.drawable.circle_mask) // optional
            clipToOutline = true
            setPadding(24, 24, 24, 24)
        }

        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#AA000000")) // dim background
            addView(imageView)
            setOnClickListener { dialog.dismiss() }
        }

        imageView.setOnClickListener {
            dialog.dismiss()
            showFullScreenImageDialog(context, bitmap)
        }

        dialog.setContentView(container)
        dialog.show()
    }

private fun showFullScreenImageDialog(context: Context, bitmap: Bitmap) {
    val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    val imageView = ImageView(context).apply {
        setImageBitmap(bitmap)
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        setBackgroundColor(Color.BLACK)
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setOnClickListener { dialog.dismiss() }
    }

    dialog.setContentView(imageView)
    dialog.show()
}

    private fun showGroupInfoBottomSheet() {
        val bottomSheet = GroupInfoBottomSheet().apply {
            arguments = Bundle().apply {
                putString("group_id", conversationUUID.toString())
            }
        }
        bottomSheet.show(parentFragmentManager, "GroupInfoBottomSheet")
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

        val scheme = if (ServerConfig.ipAddress == IpAddressType.DOMAIN) "wss" else "ws"
        val url = "$scheme://${ServerConfig.ipAddress.address}/socket/websocket?token=$token"

        phoenixChannel = PhoenixChannel(
//                socketUrl = url,
//            socketUrl = "ws://${ServerConfig.ipAddress.address}/socket/websocket?token=$token",
//            socketUrl = "ws://192.168.0.190:4000/socket/websocket?token=$token",
            socketUrl = "wss://social-application-backend-hwrx.onrender.com/socket/websocket?token=$token",
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
                    "group_info_updated" -> updateGroupInfo(payload)
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
    private fun updateGroupInfo(payload: JSONObject) {
        val groupId = payload.optString("group_id") ?: return

        val newName = payload.optString("group_name", null)
        val newAvatarBase64 = payload.optString("group_avatar_url", null)

        // Update name only if present
        newName?.let {
            if (it.isNotBlank()) {
                groupName.text = it
                chatName = it
            }
        }

        // Update avatar only if present
        newAvatarBase64?.let {
            try {
                val imageBytes = Base64.decode(it, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                groupImage.setImageBitmap(bitmap)
                avatarBase64 = it
                groupImage.setOnClickListener {
                    showImagePreviewDialog(requireContext(), bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                groupImage.setImageResource(R.drawable.ic_default_profile)
            }
        }
    }



    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleNewMessage(payload: Any) {
        try {
            val msg = parseMessageFromJson(payload as JSONObject)

            Log.d("status-check", "Statuses in msg: ${msg.status_entries}")
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
        Log.d(TAG, "📥 Raw phx_reply payload: $payload")

        val response = payload.optJSONObject("response") ?: run {
            Log.w(TAG, "⚠️ No response object in payload")
            return
        }

        val messageJson = response.optJSONObject("message") ?: run {
            Log.w(TAG, "⚠️ No message object in response")
            return
        }

        val msg = try {
            Message(
                id = UUID.fromString(messageJson.getString("id")),
                client_ref = messageJson.optString("client_ref", ""),
                sender_display_name = messageJson.optString("sender_display_name", null),
                sender_avatar_data = messageJson.optString("sender_avatar_data", null),
                encrypted_body = messageJson.getString("encrypted_body"),
                message_type = messageJson.getString("message_type"),
                sender_id = UUID.fromString(messageJson.getString("sender_id")),
                conversation_id = null,
                inserted_at = messageJson.getString("inserted_at"),
                updated_at = messageJson.optString("updated_at", messageJson.getString("inserted_at")),
                attachments = emptyList(),
                status_entries = emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse message: ${e.message}")
            return
        }

        // Parse statuses
        response.optJSONArray("statuses")?.let { statusesArray ->
            val statuses = mutableListOf<StatusEntry>()
            for (i in 0 until statusesArray.length()) {
                try {
                    val s = statusesArray.getJSONObject(i)
                    statuses.add(
                        StatusEntry(
                            id = UUID.fromString(s.getString("id")),
                            message_id = UUID.fromString(s.getString("message_id")),
                            user_id = UUID.fromString(s.getString("user_id")),
                            status = s.getString("status"),
                            status_ts = s.getString("status_ts"),
                            inserted_at = s.getString("inserted_at"),
                            updated_at = s.getString("updated_at"),
                            display_name = s.optString("display_name", null),
                            avatar_data = s.optString("avatar_data", null)
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to parse status at index $i: ${e.message}")
                }
            }
            msg.status_entries = statuses
        }

        val clientRef = msg.client_ref
        val messageId = msg.id

        if (clientRef.isNotBlank() && seenClientRefs.contains(clientRef)) {
            Log.d(TAG, "⏩ Skipping duplicate reply: $clientRef")
            return
        }
        if (clientRef.isNotBlank()) seenClientRefs.add(clientRef)

        requireActivity().runOnUiThread {
            val index = adapter.indexOfFirst {
                it.client_ref == clientRef || it.id == messageId
            }

            if (index != -1) {
                Log.d(TAG, "🔄 Updating message at index $index for clientRef=$clientRef or id=$messageId")
                adapter.updateMessageAt(index, msg)
            } else {
                Log.d(TAG, "➕ Appending new message: $messageId")
                adapter.addMessage(msg)
                binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            }

            pendingMessagesByClientRef.remove(clientRef)
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
                onOk = { reply ->
                    Log.d("PhoenixChannel", "✅ Message sent successfully: $reply")
                    val json = JSONObject(reply.toString()) // Make sure it's a JSONObject
                    handleReply(json)
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

//        val id = UUID.fromString(messageObject.getString("id"))
        val id = try {
            val idString = messageObject.optString("id", null)
            UUID.fromString(idString ?: UUID.randomUUID().toString()) // fallback
        } catch (e: IllegalArgumentException) {
            Log.e("ChatFragment", "Invalid UUID format: ${e.message}")
            UUID.randomUUID() // fallback on error
        }




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
//                        id = UUID.randomUUID(),
                        id = UUID.fromString(getString("id")),
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


