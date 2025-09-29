package com.codewithram.secretchat.ui.home

import Attachment
import Message
import StatusEntry
import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codewithram.secretchat.R
import com.codewithram.secretchat.data.Repository
import com.codewithram.secretchat.databinding.FragmentChatBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID

class ChatFragment : Fragment() {
    private val TAG = "ChatFragment"
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ChatAdapter
    private lateinit var repo: Repository
    private lateinit var phoenixChannel: PhoenixChannel
    private var initialHistoryLoaded = false
//    private val pendingMessagesByClientRef = mutableMapOf<String, Int>()
private val pendingMessagesByClientRef = mutableMapOf<String, Message>()
    private val seenClientRefs = mutableSetOf<String>()
    private var heartbeatJob: Job? = null
    private val topic by lazy { phoenixChannel.topic }
    private val PICK_ATTACHMENT_REQUEST = 1

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null


    private lateinit var mediaPickerLauncher: ActivityResultLauncher<String>

    private lateinit var audioRecorderLauncher: ActivityResultLauncher<Intent>
    private var isAttachmentInProgress = false

    private var replyingTo: Message? = null
    private lateinit var replyPreviewLayout: LinearLayout
    private lateinit var replySenderTextView: TextView
    private lateinit var replyMessageTextView: TextView
    private lateinit var replyCancelButton: ImageView

    private fun mergeStatusEntries(
        oldList: List<StatusEntry>,
        newList: List<StatusEntry>
    ): List<StatusEntry> {
        val map = oldList.associateBy { it.user_id }.toMutableMap()
        for (newEntry in newList) {
            val oldEntry = map[newEntry.user_id]
            if (oldEntry == null || oldEntry.status_ts < newEntry.status_ts) {
                // Update with newer status entry
                map[newEntry.user_id] = newEntry
            }
        }
        return map.values.toList()
    }

    private val currentAttachments = mutableListOf<Attachment>()

//    @RequiresApi(Build.VERSION_CODES.O)
//    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
//        bitmap?.let {
//            val stream = ByteArrayOutputStream()
//            it.compress(Bitmap.CompressFormat.JPEG, 90, stream)
//            val encoded = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
//
//            val attachmentData = JSONObject().apply {
//                put("file_data", encoded)
//                put("mime_type", "image/jpeg")
//                put("file_size", stream.size())
//            }
//            sendMessageWithAttachment(attachmentData)
//        }
//    }
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>


    private companion object {
        const val REQUEST_PHOTO = 10
        const val REQUEST_VIDEO = 11
        const val REQUEST_AUDIO = 12
        const val REQUEST_RECORD_AUDIO = 13
    }

    private val conversationUUID by lazy {
        UUID.fromString(requireArguments().getString("conversationId")
            ?: error("conversationId missing"))
    }
    private var chatName: String = "Group"

    private var avatarBase64: String = ""
    private val isGroupChat by lazy { arguments?.getBoolean("isGroup") ?: true }

    private  lateinit var groupName: TextView
    private lateinit var groupImage: ImageView

    private val currentUserUUID: UUID
        get() = UUID.fromString(
            requireContext()
                .getSharedPreferences("secret_chat_prefs", 0)
                .getString("user_id", "") ?: ""
        )

    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: FloatingActionButton
    private lateinit var recordAudioButton: ImageButton
    private lateinit var attachmentButton: ImageButton
    private lateinit var cameraButton: ImageButton

    private var initialX = 0f
    private var isRecording = false
    private val cancelThreshold = 150 // in pixels

    private val MIC_PERMISSION_REQUEST_CODE = 101

    private var photoUri: Uri? = null


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        chatName = requireArguments().getString("chatName") ?: "Group"
        avatarBase64 = requireArguments().getString("group_avatar_url") ?: ""

        mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            isAttachmentInProgress = false
            if (uri != null) {
                sendAttachment(uri)
            } else {
                Toast.makeText(requireContext(), "No file selected", Toast.LENGTH_SHORT).show()
            }
        }
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            isAttachmentInProgress = false
            if (success && photoUri != null) {
                sendAttachment(photoUri!!)
            } else {
                Toast.makeText(requireContext(), "Photo capture failed", Toast.LENGTH_SHORT).show()
            }
        }



    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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

        replyPreviewLayout = view.findViewById(R.id.replyPreviewLayout)
        replySenderTextView = view.findViewById(R.id.replySenderTextView)
        replyMessageTextView = view.findViewById(R.id.replyMessageTextView)
        replyCancelButton = view.findViewById(R.id.replyCancelButton)

        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
        recordAudioButton = view.findViewById(R.id.recordAudioButton)
        attachmentButton = view.findViewById(R.id.attachmentButton)
        cameraButton = view.findViewById(R.id.cameraButton)

        cameraButton.setOnClickListener {
            openCamera()
        }
        recordAudioButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX
                    checkAndRequestMicPermission {
                        isRecording = true
                        startRecording()
                    }

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = initialX - event.rawX
                    if (deltaX > cancelThreshold && isRecording) {
                        cancelRecording()
                        isRecording = false
                        Toast.makeText(requireContext(), "Recording canceled", Toast.LENGTH_SHORT).show()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) {
                        stopRecordingAndSend()
                        isRecording = false
                    }
                    v.performClick()
                    true
                }

                else -> false
            }
        }


        mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            isAttachmentInProgress = false
            if (uri != null) {
                sendAttachment(uri)
            } else {
                Toast.makeText(requireContext(), "No file selected", Toast.LENGTH_SHORT).show()
            }
        }

        // Toggle send/mic button based on input


        // Register the audio recorder launcher
        audioRecorderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isAttachmentInProgress = false
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let { uri -> sendAttachment(uri) }
            }
        }
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val position = vh.adapterPosition
                val msg = adapter.getMessageAt(position)
                onReplyClicked(msg) // your method in ChatFragment
                adapter.notifyItemChanged(position) // reset swipe
            }

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                return if (vh is ChatAdapter.SentMessageViewHolder || vh is ChatAdapter.ReceivedMessageViewHolder) {
                    ItemTouchHelper.LEFT
                } else 0
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.recyclerView)

        replyCancelButton.setOnClickListener {
            replyingTo = null
            replyPreviewLayout.visibility = View.GONE
        }

        if (toolbarView != null) {
            // Now you can access the attachmentButton from the included layout
            val attachmentButton: ImageButton = view.findViewById(R.id.attachmentButton)

            // Set an onClickListener to open the file picker
            attachmentButton.setOnClickListener {
                showAttachmentOptions() // Function to pick the file
            }
        } else {
            Log.e("ChatFragment", "custom_chat_toolbar not found in the layout!")
        }


        parentFragmentManager.setFragmentResultListener("group_update", viewLifecycleOwner) { _, bundle ->
            val newName = bundle.getString("updated_name")
            val newAvatar = bundle.getString("updated_avatar_base64")

            adapter.isPrivate = isGroupChat
            newName?.let {
                groupName.text = it
                val payload = JSONObject().apply {
                    put("group_id", conversationUUID.toString())
                    put("group_name", it)
                }
                phoenixChannel.push("group_info_updated", payload)
            }

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
                } catch (_: Exception) {
                    groupImage.setImageResource(R.drawable.account_circle)
                }
            }
        }
        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        groupName.text = chatName

        if (avatarBase64.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                groupImage.setImageBitmap(bitmap)

                groupImage.setOnClickListener {
                    showImagePreviewDialog(requireContext(), bitmap)
                }

            } catch (_: Exception) {
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
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                binding.sendButton.visibility = if (hasText) View.VISIBLE else View.GONE
                binding.recordAudioButton.visibility = if (hasText) View.GONE else View.VISIBLE
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startRecording() {
        try {
            val fileName = "AUD_${System.currentTimeMillis()}.m4a"
            val outputDir = requireContext().cacheDir
            audioFile = File(outputDir, fileName)

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.d("Audio", "Recording started at ${audioFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("Audio", "Failed to start recording: ${e.message}")
            Toast.makeText(requireContext(), "Unable to start recording", Toast.LENGTH_SHORT).show()
            recorder?.release()
            recorder = null
            isRecording = false
        }
    }

    private fun checkAndRequestMicPermission(onGranted: () -> Unit) {
        val permission = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(permission), MIC_PERMISSION_REQUEST_CODE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == MIC_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(requireContext(), "Microphone permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cancelRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null

        audioFile?.let { file ->
            if (file.exists()) file.delete()
            audioFile = null
        }

        Toast.makeText(requireContext(), "Recording discarded", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun stopRecordingAndSend() {
        try {
            recorder?.apply {
                stop() // ❗ can throw if not recording
                release()
            }
            recorder = null

            audioFile?.let { file ->
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    file
                )
                sendAttachment(uri)
                Log.d("Audio", "Recording sent: ${file.name}")
            }
        } catch (e: RuntimeException) {
            Log.e("Audio", "Failed to stop recorder: ${e.message}")
            Toast.makeText(requireContext(), "Recording failed or was too short", Toast.LENGTH_SHORT).show()
            audioFile?.delete() // cleanup
            recorder = null
        }
    }

    fun onReplyClicked(message: Message) {
        replyingTo = message
        replySenderTextView.text = message.sender_display_name ?: "Unknown"
        replyMessageTextView.text = message.encrypted_body ?: "[Attachment]"
        replyPreviewLayout.visibility = View.VISIBLE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showAttachmentOptions() {
        if (isAttachmentInProgress) return

        val options = arrayOf("Choose Photo", "Choose Video", "Choose Audio", "Take Photo", "Record Audio")
        AlertDialog.Builder(requireContext())
            .setTitle("Attach Media")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> mediaPickerLauncher.launch("image/*")
                    1 -> mediaPickerLauncher.launch("video/*")
                    2 -> mediaPickerLauncher.launch("audio/*")
                    3 -> openCamera()
                    4 -> openAudioRecorder()
                }
                isAttachmentInProgress = true
            }
            .show()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun openCamera() {
        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
        val photoFile = File(requireContext().cacheDir, fileName)
        photoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            photoFile
        )
        isAttachmentInProgress = true
        cameraLauncher.launch(photoUri)
    }


    private fun openAudioRecorder() {
        val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)

        if (intent.resolveActivity(requireContext().packageManager) != null) {
            audioRecorderLauncher.launch(intent)
        } else {
            Toast.makeText(requireContext(), "No audio recorder app found on this device", Toast.LENGTH_SHORT).show()
        }
    }


    // Remove the following
    @Deprecated("Use ActivityResultContracts instead.")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // You no longer need this method
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
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

    private fun showImagePreviewDialog(context: Context, bitmap: Bitmap) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val imageView = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(800, 800, Gravity.CENTER)
            background = ContextCompat.getDrawable(context, R.drawable.circle_mask) // optional
            clipToOutline = true
            setPadding(24, 24, 24, 24)
        }

        val container = FrameLayout(context).apply {
            setBackgroundColor("#AA000000".toColorInt()) // dim background
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

        // Check if the dialog already exists, avoid showing duplicate
        if (dialog.isShowing) {
            dialog.dismiss()
        }

        dialog.setContentView(imageView)
        dialog.show()
    }

    private fun showGroupInfoBottomSheet() {
        // Check if the bottom sheet is already shown, prevent opening a new one
        val existingBottomSheet = parentFragmentManager.findFragmentByTag("GroupInfoBottomSheet")
        if (existingBottomSheet == null) {
            val bottomSheet = GroupInfoBottomSheet().apply {
                arguments = Bundle().apply {
                    putString("group_id", conversationUUID.toString())
                }
            }
            bottomSheet.show(parentFragmentManager, "GroupInfoBottomSheet")
        } else {
            Log.d(TAG, "Group info bottom sheet is already open")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupRecycler() {
        adapter = ChatAdapter(currentUserUUID, childFragmentManager).apply {
            onMessageRead = { id ->
                if (initialHistoryLoaded) sendReadReceipt(id)
            }

            // Deduplicate messages by 'id' before updating the adapter
            val uniqueMessages = messages.distinctBy { it.id }

            // Update the adapter with the unique messages
            updateMessages(uniqueMessages) // Use the method directly
        }

        adapter.scrollListener = { repliedMessageId ->
            scrollToRepliedMessage(repliedMessageId)
        }

        adapter.onHighlight = { id -> highlightMessage(id) }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatFragment.adapter
        }
    }


    fun highlightMessage(id: UUID) {
        val index = adapter.findMessageIndexById(id)
        if (index != -1) {
            binding.recyclerView.scrollToPosition(index)

            Handler(Looper.getMainLooper()).postDelayed({
                val vh = binding.recyclerView.findViewHolderForAdapterPosition(index)
                vh?.itemView?.let { view ->
                    val context = view.context

                    // Dynamically fetch theme's colorPrimary (no R usage)
                    val typedValue = TypedValue()
                    val theme = context.theme
                    theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
                    val highlightColor = typedValue.data

                    val originalColor = (view.background as? ColorDrawable)?.color ?: Color.TRANSPARENT
                    view.setBackgroundColor(highlightColor)

                    ValueAnimator.ofObject(ArgbEvaluator(), highlightColor, originalColor).apply {
                        duration = 1000
                        addUpdateListener { animator ->
                            view.setBackgroundColor(animator.animatedValue as Int)
                        }
                        start()
                    }
                }
            }, 300)
        }
    }
    private fun scrollToRepliedMessage(repliedId: UUID) {
        val index = adapter.findMessageIndexById(repliedId)

        // If the message is pending, find it by client_ref
        if (index == -1) {
            val pendingIndex = adapter.findMessageIndexByClientRef(repliedId.toString())
            if (pendingIndex != -1) {
                Log.d("ChatFragment", "Replied message is pending, scrolling to pending message: $repliedId")
                binding.recyclerView.scrollToPosition(pendingIndex)
            } else {
                Log.d("ChatFragment", "Original message not found for reply: $repliedId")
            }
        } else {
            binding.recyclerView.scrollToPosition(index)
        }
    }

    private fun updateGroupInfo(payload: JSONObject) {
        payload.optString("group_id") ?: return

        val newName = payload.optString("group_name", "")
        val newAvatarBase64 = payload.optString("group_avatar_url", "")

        newName.let {
            if (it.isNotBlank()) {
                groupName.text = it
                chatName = it
            }
        }

        newAvatarBase64.let {
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
    private fun connectPhoenixChannel() {
        val token = requireContext().getSharedPreferences("secret_chat_prefs", 0)
            .getString("auth_token", "") ?: ""
        Log.d(TAG, "Auth token: $token")

        phoenixChannel = PhoenixChannel(
//            socketUrl = "wss://social-application-backend-hwrx.onrender.com/socket/websocket?token=$token",
//            socketUrl = "ws://192.168.0.190:4000/socket",
            socketUrl = "ws://192.168.0.169:4000/socket/websocket?token=$token",
            topic = "chat:$conversationUUID",
            params = mapOf("token" to token)
        )
        Log.d(TAG, "Connecting to topic: chat:$conversationUUID")

        phoenixChannel.onJoinSuccess = {
            Log.d(TAG, "✅ Successfully joined channel: $it")

        }

        phoenixChannel.onMessageReceived = { event, payload ->
            Log.d(TAG, "📩 Received event: $event, payload: $payload")
            activity?.runOnUiThread {
                when (event) {
                    "new_message" -> {
                        Log.d(TAG, "Handling new_message event")
                        handleNewMessage(payload)
                    }
                    "phx_reply" -> {
                        Log.d(TAG, "Handling phx_reply event")
                        handleReply(payload)
                    }
                    "message_status_updated" -> {
                        Log.d(TAG, "Handling message_status_updated event")
                        updateMessage(payload)
                    }
                    "message_status_update" -> {
                        Log.d(TAG, "Handling message_status_update event")
                        updateStatusEntry(payload)
                    }
                    "group_info_updated" -> {
                        Log.d(TAG, "Handling group_info_updated event")
                        updateGroupInfo(payload)
                    }
                    else -> {
                        Log.w(TAG, "⚠️ Unhandled event: $event")
                    }
                }
            }
        }

        phoenixChannel.connect()
        Log.d(TAG, "🔌 Socket connect initiated")

        startHeartbeat()
        Log.d(TAG, "💓 Heartbeat started")

        phoenixChannel.onClose = {
            Log.w(TAG, "❌ Channel closed, reconnecting...")
            reconnectWithBackoff()
        }

        phoenixChannel.onError = { error ->
            Log.e(TAG, "🔥 Error occurred in channel: $error")
            reconnectWithBackoff()
        }
    }

    private fun reconnectWithBackoff(retries: Int = 5) {
        if (retries == 0) return

        Handler(Looper.getMainLooper()).postDelayed({
            phoenixChannel.connect()
        }, 3000L)
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

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
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
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat error: ${e.message}")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendAttachment(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val fileBytes = inputStream?.readBytes()
            inputStream?.close()

            if (fileBytes == null) {
                Log.w("Attachment", "File content is null")
                return
            }

            val encodedFile = Base64.encodeToString(fileBytes, Base64.DEFAULT)
            val mimeType = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"

            Log.d("Attachment", "MIME type from ContentResolver: $mimeType")
            Log.d("Attachment", "File size: ${fileBytes.size} bytes")

            val attachmentData = JSONObject().apply {
                put("file_data", encodedFile)
                put("mime_type", mimeType)
                put("file_size", fileBytes.size)
            }

            sendMessageWithAttachment(attachmentData)
        } catch (e: Exception) {
            Log.e("Attachment", "Error reading file", e)
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendMessageWithAttachment(attachmentData: JSONObject) {
        val now = Instant.now().toString()
        val messageId = UUID.randomUUID()
        val clientRef = UUID.randomUUID().toString()

        // Create the new attachment to be sent
        val newAttachment = Attachment(
            id = UUID.randomUUID(), // Temporary ID
            file_url = attachmentData.getString("file_data"),
            mime_type = attachmentData.getString("mime_type"),
            message_id = messageId,
            inserted_at = now,
            updated_at = now,
        )

        // Prepare replyTo with only one attachment (not all original ones)
        val trimmedReplyTo = replyingTo?.let { original ->
            original.copy(
                attachments = original.attachments.take(1) // Show only the first one (or use your logic here)
            )
        }

        val pendingMsg = Message(
            id = messageId,
            client_ref = clientRef,
            encrypted_body = "",
            message_type = "media",
            sender_id = currentUserUUID,
            conversation_id = conversationUUID,
            inserted_at = now,
            updated_at = now,
            attachments = listOf(newAttachment),
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
                    avatar_data = "",
                )
            ),
            reply_to = trimmedReplyTo
        )

        // Add to pending tracking and UI
        pendingMessagesByClientRef[clientRef] = pendingMsg
        adapter.safeUpsertMessage(pendingMsg)
        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)

        Log.d("Attachment", "Sending attachment message")
        Log.d("Attachment", "Attachment payload: $attachmentData")

        // Construct payload to send over Phoenix
        val payload = JSONObject().apply {
            put("client_ref", clientRef)
            put("message_type", "media")
            put("encrypted_body", "")
            put("attachment", attachmentData)

            // Include reply_to_id if replying
            replyingTo?.let { replyMsg ->
                put("reply_to_id", replyMsg.id.toString())
            }
        }

        // Send over Phoenix
        phoenixChannel.pushWithReply(
            event = "send_message",
            payload = payload,
            onOk = { reply ->
                activity?.runOnUiThread {
                    try {
                        val json = JSONObject(reply.toString())

                        // Remove pending message from tracking and UI
                        pendingMessagesByClientRef.remove(clientRef)
                        adapter.removeMessageByClientRef(clientRef)

                        // Handle the server-confirmed message
                        handleReply(json)

                        replyingTo = null
                        replyPreviewLayout.visibility = View.GONE
                    } catch (e: Exception) {
                        Log.e("PhoenixChannel", "Error handling reply: ${e.message}")
                    }
                }
            },
            onError = {
                activity?.runOnUiThread {
                    Log.e("PhoenixChannel", "Message send error: $it")
                    Toast.makeText(context, "Failed to send attachment", Toast.LENGTH_SHORT).show()
                    pendingMessagesByClientRef.remove(clientRef)
                }
            },
            onTimeout = {
                activity?.runOnUiThread {
                    Log.e("PhoenixChannel", "Message send timed out")
                    Toast.makeText(context, "Attachment send timed out", Toast.LENGTH_SHORT).show()
                    pendingMessagesByClientRef.remove(clientRef)
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadMessages() {
        lifecycleScope.launch {
            val msgs = withContext(Dispatchers.IO) {
                repo.getMessages(conversationUUID.toString())
            }

            adapter.setAll(msgs)
            if (msgs.isNotEmpty()) {
                binding.recyclerView.scrollToPosition(msgs.lastIndex)
            }

            initialHistoryLoaded = true


            // ✅ Defer sending read acknowledgments until channel is joined
            phoenixChannel.onJoinSuccess = {
                Log.d(TAG, "✅ Channel joined, sending read receipts")

                val unreadFromOthers = msgs.filter {
                    it.sender_id != currentUserUUID &&
                            it.status_entries.none { s ->
                                s.user_id == currentUserUUID && s.status == "read"
                            }
                }

                unreadFromOthers.forEach { msg ->
                    sendReadAcknowledgment(msg.id.toString())
                }
            }
        }
    }




    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleNewMessage(payload: Any) {
        try {
            val msg = parseMessageFromJson(payload as JSONObject)

            requireActivity().runOnUiThread {
                val indexById = adapter.findMessageIndexById(msg.id)
                val indexByClientRef = adapter.findMessageIndexByClientRef(msg.client_ref)

                if (indexById != -1) {
                    // 🔁 Message already exists (real message ID), just merge statuses
                    val existing = adapter.getMessageAt(indexById)
                    val mergedStatuses = mergeStatusEntries(existing.status_entries, msg.status_entries)
                    val updated = existing.copy(status_entries = mergedStatuses)
                    adapter.updateMessageAt(indexById, updated)

                } else if (indexByClientRef != -1) {
                    // 🔁 Message exists by client_ref (probably pending), update instead of remove
                    val existing = adapter.getMessageAt(indexByClientRef)
                    val mergedStatuses = mergeStatusEntries(existing.status_entries, msg.status_entries)
                    val updated = msg.copy(status_entries = mergedStatuses)
                    adapter.updateMessageAt(indexByClientRef, updated)

                } else {
                    // ✅ New message: insert it
                    adapter.safeUpsertMessage(msg)
                    binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                }

                if (msg.client_ref.isNotBlank()) {
                    seenClientRefs.add(msg.client_ref)
                    pendingMessagesByClientRef.remove(msg.client_ref)
                }

                if (msg.sender_id != currentUserUUID) {
                    sendReadAcknowledgment(msg.id.toString())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleNewMessage error: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleReply(payload: JSONObject) {
        val response = payload.optJSONObject("response") ?: return
        val messageJson = response.optJSONObject("message") ?: return

        val msg = try {
            parseMessageFromJson(messageJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse replied message: ${e.message}")
            return
        }

        val statuses = mutableListOf<StatusEntry>()
        response.optJSONArray("statuses")?.let { array ->
            for (i in 0 until array.length()) {
                try {
                    val s = array.getJSONObject(i)
                    statuses.add(
                        StatusEntry(
                            id = UUID.fromString(s.getString("id")),
                            message_id = UUID.fromString(s.getString("message_id")),
                            user_id = UUID.fromString(s.getString("user_id")),
                            status = s.getString("status"),
                            status_ts = s.getString("status_ts"),
                            inserted_at = s.getString("inserted_at"),
                            updated_at = s.getString("updated_at"),
                            display_name = s.optString("display_name", ""),
                            avatar_data = s.optString("avatar_data", "")
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Status parse error: ${e.message}")
                }
            }
        }

        requireActivity().runOnUiThread {
            val existingIndex = adapter.findMessageIndexById(msg.id)
            if (existingIndex != -1) {
                val existing = adapter.getMessageAt(existingIndex)
                val mergedStatuses = mergeStatusEntries(existing.status_entries, statuses)
                val updated = existing.copy(status_entries = mergedStatuses)
                adapter.updateMessageAt(existingIndex, updated)
            } else {
                // Remove pending message with same client_ref before inserting real one
                val clientRefIndex = adapter.findMessageIndexByClientRef(msg.client_ref)
                if (clientRefIndex != -1) {
                    adapter.removeMessageAt(clientRefIndex)
                }

                val msgWithStatuses = msg.copy(status_entries = statuses)
                adapter.safeUpsertMessage(msgWithStatuses)
                binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            }


            if (msg.client_ref.isNotBlank()) {
                seenClientRefs.add(msg.client_ref)
                pendingMessagesByClientRef.remove(msg.client_ref)
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
        try {
            // Defensive check for required keys
            if (!payload.has("message_id") || !payload.has("user_id") || !payload.has("status")) {
                Log.w(TAG, "Status update missing keys, ignoring: $payload")
                return
            }

            val messageId = payload.getString("message_id")
            val userId = payload.getString("user_id")
            val newStatus = payload.getString("status")

            // Ensure this runs on UI thread
            activity?.runOnUiThread {
                adapter.updateMessageStatus(messageId, userId, newStatus)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status", e)
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

            // Create payload with proper reply_to structure (only if replying to a message)
            val payload = JSONObject().apply {
                put("client_ref", clientRef)
                put("encrypted_body", text)
                put("message_type", "text")

                // Only add 'reply_to' if there's an active reply (i.e., replyingTo != null)
                replyingTo?.let { replyMsg ->
                    put("reply_to", replyMsg.id.toString())
//                    put("encrypted_body", replyMsg.encrypted_body ?: "")
//                    put("message_type", replyMsg.message_type)
//                    put("sender_id", replyMsg.sender_id.toString())
                }

            }

            // Create pending message object (with reply_to info if present)
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
                        avatar_data = "",
                    )
                ),
                reply_to = replyingTo?.let { original ->
                    original.copy(
                        client_ref = original.client_ref ?: UUID.randomUUID().toString(), // 👈 important fix
                        encrypted_body = original.encrypted_body ?: "[Attachment]",
                        sender_display_name = original.sender_display_name ?: "You",
                        attachments = original.attachments ?: emptyList()
                    )
                }

            )


            // Store the full message object instead of just index
            pendingMessagesByClientRef[clientRef] = pendingMsg

            // Add to UI
            adapter.safeUpsertMessage(pendingMsg)
            binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            binding.messageInput.text?.clear()

            // Push message via Phoenix channel
            phoenixChannel.pushWithReply(
                event = "send_message",
                payload = payload,
                onOk = { reply ->
                    activity?.runOnUiThread {
                        try {
                            val json = JSONObject(reply.toString())
                            handleReply(json)
                            pendingMessagesByClientRef.remove(clientRef)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling reply", e)
                        }
                        replyingTo = null  // Reset the reply state after sending
                        replyPreviewLayout.visibility = View.GONE
                    }
                },
                onError = {
                    activity?.runOnUiThread {
                        Log.e("PhoenixChannel", "Message send error: $it")
                        // Optionally remove the pending message if send fails
                        pendingMessagesByClientRef.remove(clientRef)
                        // Optionally show error to user
                        Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
                },
                onTimeout = {
                    activity?.runOnUiThread {
                        Log.e("PhoenixChannel", "Message send timed out")
                        pendingMessagesByClientRef.remove(clientRef)
                        Toast.makeText(context, "Message timed out", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun parseMessageFromJson(jsonObj: JSONObject): Message {
        val messageObject = when {
            jsonObj.has("payload") && jsonObj.getJSONObject("payload").has("response") ->
                jsonObj.getJSONObject("payload").getJSONObject("response")
            else -> jsonObj
        }

        val conversationId = try {
            UUID.fromString(messageObject.optString("conversation_id"))
        } catch (_: Exception) {
            UUID.fromString(topic.substringAfter("chat:").trim())
        }

        val id = try {
            UUID.fromString(messageObject.optString("id"))
        } catch (_: Exception) {
            UUID.randomUUID()
        }

        val senderId = try {
            UUID.fromString(messageObject.getString("sender_id"))
        } catch (_: Exception) {
            throw IllegalArgumentException("Missing or invalid sender_id")
        }

        val attachments = messageObject.optJSONArray("attachments")?.let { arr ->
            List(arr.length()) { i ->
                arr.getJSONObject(i).let {
                    Attachment(
                        id = UUID.fromString(it.getString("id")),
//                        file_url = it.optString("file_url", null),
                        file_url = it.optString("file_data", null),
                        mime_type = it.getString("mime_type"),
                        message_id = UUID.fromString(it.getString("message_id")),
                        inserted_at = it.getString("inserted_at"),
                        updated_at = it.getString("updated_at")
                    )
                }
            }
        } ?: emptyList()

        val statuses = messageObject.optJSONArray("statuses")?.let { arr ->
            List(arr.length()) { i ->
                arr.getJSONObject(i).let {
                    StatusEntry(
                        id = UUID.fromString(it.getString("id")),
                        message_id = UUID.fromString(it.getString("message_id")),
                        user_id = UUID.fromString(it.getString("user_id")),
                        status = it.getString("status"),
                        status_ts = it.getString("status_ts"),
                        inserted_at = it.getString("inserted_at"),
                        updated_at = it.getString("updated_at"),
                        display_name = it.optString("display_name", ""),
                        avatar_data = it.optString("avatar_data", "")
                    )
                }
            }
        } ?: emptyList()

        // ✅ ADD THIS: Declare replyToObj
        val replyToObj = messageObject.optJSONObject("reply_to")
        val replyTo: Message? = replyToObj?.let {
            try {
                val replyAttachments = it.optJSONArray("attachments")?.let { arr ->
                    List(arr.length()) { i ->
                        arr.getJSONObject(i).let { att ->
                            Attachment(
                                id = UUID.fromString(att.getString("id")),
//                                file_url = att.optString("file_url", null),
                                file_url = att.optString("file_data", null),
                                mime_type = att.getString("mime_type"),
                                message_id = UUID.fromString(att.getString("message_id")),
                                inserted_at = att.getString("inserted_at"),
                                updated_at = att.getString("updated_at")
                            )
                        }
                    }
                } ?: emptyList()

                Message(
                    id = UUID.fromString(it.getString("id")),
                    encrypted_body = it.optString("encrypted_body", ""),
                    message_type = it.optString("message_type", "text"),
                    sender_id = UUID.fromString(it.getString("sender_id")),
                    sender_display_name = it.optString("sender_display_name", ""),
                    client_ref = "",
                    sender_avatar_data = "",
                    reply_to = null,
                    conversation_id = null,
                    inserted_at = "",
                    updated_at = "",
                    attachments = replyAttachments,
                    status_entries = emptyList()
                )
            } catch (_: Exception) {
                null
            }
        }

        return Message(
            id = id,
            client_ref = messageObject.optString("client_ref", ""),
            encrypted_body = messageObject.getString("encrypted_body"),
            message_type = messageObject.getString("message_type"),
            sender_id = senderId,
            conversation_id = conversationId,
            inserted_at = messageObject.getString("inserted_at"),
            updated_at = messageObject.optString("updated_at", messageObject.getString("inserted_at")),
            attachments = attachments,
            status_entries = statuses,
            sender_display_name = messageObject.optString("sender_display_name", ""),
            sender_avatar_data = messageObject.optString("sender_avatar_data", ""),
            reply_to = replyTo
        )
    }

}


