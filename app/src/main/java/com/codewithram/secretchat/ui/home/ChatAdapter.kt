package com.codewithram.secretchat.ui.home

import ImageSliderDialogFragment
import Message
import StatusEntry
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.marginBottom
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codewithram.secretchat.R
import com.codewithram.secretchat.databinding.ItemMessageReceivedBinding
import com.codewithram.secretchat.databinding.ItemMessageSentBinding
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import java.util.UUID


class ChatAdapter(private val currentUserId: UUID, private val fragmentManager: FragmentManager, ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val lock = Any()
    internal var isPrivate: Boolean = false

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    var onHighlight: ((UUID) -> Unit)? = null
    var onReplyPreviewClick: ((UUID) -> Unit)? = null
    var onReply: ((Message) -> Unit)? = null
    fun getMessageAt(position: Int): Message = messages[position]

    var onMessageRead: ((UUID) -> Unit)? = null
    internal var messages = mutableListOf<Message>()

    @RequiresApi(Build.VERSION_CODES.O)
    fun safeAddMessage(msg: Message) {
        synchronized(lock) {
            // Prevent duplicates based on id or client_ref
            if (messages.any { it.id == msg.id || it.client_ref == msg.client_ref }) return

            val insertIndex = calculateInsertIndex(msg)
            messages.add(insertIndex, msg)
            notifyItemInserted(insertIndex)
        }
    }

    fun removeMessageAt(index: Int) {
        synchronized(lock) {
            if (index in messages.indices) {
                messages.removeAt(index)
                notifyItemRemoved(index)
            }
        }
    }

    fun removeMessageByClientRef(clientRef: String) {
        val index = messages.indexOfFirst { it.client_ref == clientRef }
        if (index != -1) {
            messages.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages.sortedBy {
            try { Instant.parse(it.inserted_at) } catch (e: Exception) { Instant.MIN }
        })
        notifyDataSetChanged()
    }

    fun mergeStatusEntries(old: List<StatusEntry>, incoming: List<StatusEntry>): List<StatusEntry> {
        val latestByUser = mutableMapOf<String, StatusEntry>()

        (old + incoming).forEach { entry ->
            val key = entry.user_id.toString()
            val existing = latestByUser[key]

            if (existing == null || entry.status_ts > existing.status_ts) {
                latestByUser[key] = entry
            }
        }

        return latestByUser.values.toList()
    }

    fun safeUpsertMessage(msg: Message) {
        val existingById = findMessageIndexById(msg.id)
        val existingByClientRef = findMessageIndexByClientRef(msg.client_ref)

        when {
            existingById != -1 -> {
                // Merge status if same ID exists
                val existing = messages[existingById]
                val merged = existing.copy(status_entries = mergeStatusEntries(existing.status_entries, msg.status_entries))
                messages[existingById] = merged
                notifyItemChanged(existingById)
            }

            existingByClientRef != -1 -> {
                // Remove pending duplicate by client_ref and insert real one
                messages.removeAt(existingByClientRef)
                notifyItemRemoved(existingByClientRef)
                messages.add(msg)
                notifyItemInserted(messages.size - 1)
            }

            else -> {
                // Brand new message
                messages.add(msg)
                notifyItemInserted(messages.size - 1)
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateInsertIndex(msg: Message): Int {
        return try {
            val newTime = Instant.parse(msg.inserted_at)
            messages.indexOfFirst {
                try {
                    Instant.parse(it.inserted_at).isAfter(newTime)
                } catch (e: Exception) {
                    false
                }
            }.takeIf { it != -1 } ?: messages.size
        } catch (e: Exception) {
            messages.size // fallback to append
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateMessageStatus(messageId: String, userId: String, newStatus: String) {
        synchronized(lock) {
            val idx = messages.indexOfFirst { it.id.toString() == messageId }
            if (idx == -1) return

            val message = messages[idx].copy()
            val oldList = message.status_entries
            val entryIndex = oldList.indexOfFirst { it.user_id.toString() == userId }

            val updatedEntry = StatusEntry(
                id = UUID.randomUUID(),
                message_id = UUID.fromString(messageId),
                user_id = UUID.fromString(userId),
                status = newStatus,
                status_ts = Instant.now().toString(),
                inserted_at = Instant.now().toString(),
                updated_at = Instant.now().toString(),
                display_name = "",
                avatar_data = ""
            )

            val newList = if (entryIndex >= 0) {
                oldList.toMutableList().apply { set(entryIndex, updatedEntry) }
            } else {
                oldList + updatedEntry
            }

            message.status_entries = newList
            messages[idx] = message
            notifyItemChanged(idx)
        }
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].sender_id == currentUserId) VIEW_TYPE_SENT
        else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        if (viewType == VIEW_TYPE_SENT) {
            SentMessageViewHolder(
                ItemMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else {
            ReceivedMessageViewHolder(
                ItemMessageReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

    override fun getItemCount() = messages.size

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SentMessageViewHolder -> holder.bind(messages[position])
            is ReceivedMessageViewHolder -> holder.bind(messages[position])
        }
    }

    var scrollListener: ((UUID) -> Unit)? = null

    fun findMessageIndexById(id: UUID): Int {
        return messages.indexOfFirst { it.id == id }
    }

    fun findMessageIndexByClientRef(clientRef: String): Int {
        return messages.indexOfFirst { it.client_ref == clientRef }
    }

    fun updateMessageAt(index: Int, newMsg: Message) {
        if (index in messages.indices) {
            messages[index] = newMsg
            notifyItemChanged(index)
        }
    }

    fun setAll(newMessages: List<Message>) {
        synchronized(lock) {
            messages.clear()
            messages.addAll(newMessages)
            notifyDataSetChanged()
        }
    }

    inner class SentMessageViewHolder(private val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root) {

    private val screenW = Resources.getSystem().displayMetrics.widthPixels
    private val maxAttachW = (screenW * 0.5f).toInt()
    private val Int.dp get() = (this * Resources.getSystem().displayMetrics.density).toInt()

fun bind(msg: Message) {
    // === Reset previous views ===
    binding.attachmentContainer.removeAllViews()
    binding.replyImageContainer.removeAllViews()

    val ctx = binding.root.context
    val screenWidth = ctx.resources.displayMetrics.widthPixels
    val maxWidth = (screenWidth * 0.60f).toInt()  // Maximum width based on screen width

    val hasText = !msg.encrypted_body.isNullOrBlank()
    val hasAttachments = msg.attachments.isNotEmpty()

    // Minimum width for the reply layout (200dp when the message is small)

    // === ✅ REPLY PREVIEW ===
    val minWidthForReply = 160.dp        // Minimum: 200dp
    val maxWidthForReply = (screenWidth * 0.60f).toInt()   // Maximum: 55% of screen

    if (msg.reply_to != null) {
        val reply = msg.reply_to!!
        binding.replyLayout.visibility = View.VISIBLE
        binding.replySenderTextView.text = reply.sender_display_name ?: "Unknown"

        // Display reply preview text
        val replyPreviewText = when {
            reply.attachments.isNotEmpty() -> {
                val firstMime = reply.attachments.first().mime_type?.lowercase() ?: ""
                when {
                    firstMime.startsWith("image") -> "[Image]"
                    firstMime.startsWith("video") -> "[Video]"
                    firstMime.startsWith("audio") -> "[Audio]"
                    else -> "[Attachment]"
                }
            }
            !reply.encrypted_body.isNullOrBlank() -> reply.encrypted_body
            else -> "[Reply]"
        }
        binding.replyMessageTextView.text = replyPreviewText

        val paint = binding.replyMessageTextView.paint

        // Measure reply text width (or fallback for attachments)
        val replyTextBody = reply.encrypted_body ?: ""
        val isReplyAttachment = reply.attachments.isNotEmpty()

        // Base minimal width for attachments (fix value)
        val minWidthAttachment = 160.dp

        // Width needed for reply preview text + padding (for text replies)
        val replyTextWidth = if (isReplyAttachment || replyTextBody.isBlank()) {
            minWidthAttachment
        } else {
            // Text width + some horizontal padding
            (paint.measureText(replyTextBody).toInt() + 56.dp).coerceAtLeast(minWidthAttachment)
        }

        // Measure current/sending message text width
        val currentMsgText = msg.encrypted_body ?: ""
        val currentMsgTextWidth = if (currentMsgText.isBlank()) {
            0 // no text, so width zero for this
        } else {
            paint.measureText(currentMsgText).toInt() + 56.dp
        }

        // Cap max width for reply preview layout: 55% of screen width
        val maxReplyWidth = (screenWidth * 0.55f).toInt()

        // !!! Take the maximum width between reply preview and current message text, capped at maxReplyWidth !!!
        val adjustedWidth = maxOf(replyTextWidth, currentMsgTextWidth).coerceAtMost(maxReplyWidth)

        // Assign size to replyLayout
        binding.replyLayout.layoutParams = binding.replyLayout.layoutParams.apply {
            width = adjustedWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        // Limit reply message TextView width with left/right padding considered
        binding.replyMessageTextView.maxWidth = adjustedWidth - 48.dp

        // Setup reply images preview container as before
        binding.replyImageContainer.apply {
            removeAllViews()
            visibility = if (isReplyAttachment) View.VISIBLE else View.GONE
            reply.attachments.forEach { attachment ->
                when {
                    attachment.mime_type?.startsWith("image") == true -> {
                        val imageView = ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(32.dp, 32.dp).apply { marginEnd = 3.dp }
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                        Glide.with(context)
                            .load(Base64.decode(attachment.file_url, Base64.DEFAULT))
                            .into(imageView)
                        addView(imageView)
                    }
                    attachment.mime_type?.startsWith("video") == true -> {
                        addView(ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(32.dp, 32.dp).apply { marginEnd = 3.dp }
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setImageResource(R.drawable.ic_play)
                        })
                    }
                    attachment.mime_type?.startsWith("audio") == true -> {
                        addView(ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(32.dp, 32.dp).apply { marginEnd = 3.dp }
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setImageResource(R.drawable.ic_mic)
                        })
                    }
                    else -> {
                        addView(ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(32.dp, 32.dp).apply { marginEnd = 3.dp }
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setImageResource(R.drawable.ic_close)
                        })
                    }
                }
            }
        }

        // Set purple bar on left
        if (binding.replyLayout.childCount == 0 || binding.replyLayout.getChildAt(0).layoutParams.width != 4.dp) {
            val purpleBarLeft = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(4.dp, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(ContextCompat.getColor(context, R.color.purple_700))
            }
            binding.replyLayout.addView(purpleBarLeft, 0)
        }

        binding.replyLayout.setBackgroundColor(ContextCompat.getColor(ctx, R.color.call_card_background))
        binding.replyLayout.setOnClickListener {
            onReplyPreviewClick?.invoke(reply.id)
        }
        binding.replyLayout.invalidate()
    } else {
        binding.replyLayout.visibility = View.GONE
        binding.replyImageContainer.removeAllViews()
    }


    // === ✅ TEXT BUBBLE (Time+Status only if hasText) ===
    if (hasText) {
        binding.textMessageBody.visibility = View.VISIBLE
        binding.textMessageBody.text = msg.encrypted_body

        // Adjust message width based on reply layout width
        binding.textMessageBody.maxWidth = if (binding.replyLayout.visibility == View.VISIBLE) {
            binding.replyLayout.layoutParams.width
        } else {
            maxWidth
        }

        binding.textMessageTime.text = formatTime(msg.inserted_at)
        val (iconRes, tintRes) = getStatusIconAndColor(msg)
        binding.imageMessageStatus.setImageResource(iconRes)
        binding.imageMessageStatus.setColorFilter(ContextCompat.getColor(ctx, tintRes))

        val params = binding.textMessageBody.layoutParams as LinearLayout.LayoutParams
        params.height = LinearLayout.LayoutParams.WRAP_CONTENT
        binding.textMessageBody.layoutParams = params
    } else {
        binding.textMessageBody.visibility = View.GONE
    }

    // === ✅ ATTACHMENTS (main message, not reply bar) ===
    if (hasAttachments) {
        binding.attachmentBubbleLayout.visibility = View.VISIBLE

        val onlyFiles = msg.attachments.all {
            val mime = it.mime_type.lowercase()
            !mime.startsWith("image") && !mime.startsWith("video") && !mime.startsWith("audio")
        }

        msg.attachments.forEach { a ->
            val decoded = try {
                Base64.decode(a.file_url ?: "", Base64.DEFAULT)
            } catch (_: Exception) {
                null
            }

            val mime = a.mime_type.lowercase()
            val view = when {
                mime.startsWith("image") && decoded != null ->
                    createImageView(decoded, msg, !hasText)
                mime.startsWith("video") && decoded != null ->
                    createVideoView(decoded, msg, !hasText)
                mime.startsWith("audio") && decoded != null ->
                    createAudioView(decoded, msg)
                else ->
                    createFileLinkView("📎 File", a.file_url ?: "")
            }

            view.setOnLongClickListener {
                showStatusDialog(msg)
                true
            }
            // Make main attachments also thin & neat
            val layoutParams = view.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(32.dp, 32.dp)
            layoutParams.bottomMargin = 3.dp  // slimmer margin
            layoutParams.topMargin = 3.dp
            layoutParams.marginEnd = 3.dp
            view.layoutParams = layoutParams
            binding.attachmentContainer.addView(view)
        }

        // Show bottom row ONLY for non-media files
        if (!hasText && onlyFiles) {
            binding.attachmentTimeStatusRow.visibility = View.VISIBLE
            binding.attachmentTimeOverlay.text = formatTime(msg.inserted_at)
            val (aIcon, aTint) = getStatusIconAndColor(msg)
            binding.attachmentStatusOverlay.setImageResource(aIcon)
            binding.attachmentStatusOverlay.setColorFilter(ContextCompat.getColor(ctx, aTint))
        } else {
            binding.attachmentTimeStatusRow.visibility = View.GONE
        }
    } else {
        binding.attachmentBubbleLayout.visibility = View.GONE
    }

    // === ✅ Long Press Handler ===
    binding.root.setOnLongClickListener {
        showStatusDialog(msg)
        true
    }

    // === ✅ Highlight Listener ===
    binding.replyLayout.setOnClickListener {
        onHighlight?.invoke(msg.reply_to?.id ?: msg.id)
    }

    // === ✅ onReply Action ===
    binding.root.setOnClickListener {
        onReply?.invoke(msg)
    }
}

    private fun getTextWidth(text: String): Int {
        val paint = Paint()
        paint.textSize = 14f // Adjust text size as needed
        return paint.measureText(text).toInt()
    }

    private fun showStatusDialog(msg: Message) {
            val ctx = itemView.context
            val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_message_status, null)
            val container = dialogView.findViewById<LinearLayout>(R.id.statusContainer)

            val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val outFmt = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())

            val otherStatuses = msg.status_entries.filter { it.user_id != currentUserId }

            if (otherStatuses.isEmpty()) {
                val tv = TextView(ctx).apply {
                    text = "No status info available for other users yet."
                    setPadding(40, 60, 40, 60)
                    textSize = 16f
                    gravity = Gravity.CENTER
                }
                container.addView(tv)
            } else {
                otherStatuses.forEach { e ->
                    val name = e.display_name?.takeIf { it.isNotBlank() } ?: "Unknown"
                    val time = runCatching {
                        e.status_ts.let { outFmt.format(inFmt.parse(it)!!) }
                    }.getOrNull() ?: "Unknown time"

                    val avatarBmp = e.avatar_data?.takeIf { it.isNotBlank() }?.let { base64 ->
                        try {
                            val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    val itemView = LayoutInflater.from(ctx).inflate(R.layout.item_status_row, container, false)

                    val avatarView = itemView.findViewById<ImageView>(R.id.avatarImageView)
                    avatarView.setImageBitmap(avatarBmp ?: BitmapFactory.decodeResource(ctx.resources, R.drawable.account_circle))

                    itemView.findViewById<TextView>(R.id.nameTextView).text = name
                    itemView.findViewById<TextView>(R.id.timeTextView).text = time

                    val statusIconView = itemView.findViewById<ImageView>(R.id.statusIcon)
                    val statusTextView = itemView.findViewById<TextView>(R.id.statusTextView)

                    val (iconRes, tintColorRes, label) = when (e.status.lowercase()) {
                        "pending" -> Triple(R.drawable.pending, R.color.gray, "Pending")
                        "sent" -> Triple(R.drawable.ic_sent, R.color.gray, "Sent")
                        "delivered" -> Triple(R.drawable.ic_done_all, R.color.gray, "Delivered")
                        "read" -> Triple(R.drawable.ic_done_all, R.color.purple_500, "Read")
                        else -> Triple(0, R.color.gray, "Unknown")
                    }

                    if (iconRes != 0) {
                        statusIconView.setImageResource(iconRes)
                        statusIconView.setColorFilter(ContextCompat.getColor(ctx, tintColorRes))
                    } else {
                        statusIconView.setImageDrawable(null)
                    }

                    statusTextView.text = label
                    container.addView(itemView)
                }
            }

            AlertDialog.Builder(ctx)
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .show() }


        private fun createImageView(bytes: ByteArray, msg: Message, showOverlay: Boolean): View {
            val ctx = itemView.context
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val ar = bmp.height.toFloat() / bmp.width
            val hPx = (maxAttachW * ar).toInt()

            val imageView = ShapeableImageView(ctx).apply {
                setImageBitmap(bmp)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, hPx
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = null
                shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setTopLeftCorner(CornerFamily.ROUNDED, 8.dp.toFloat())
                    .setTopRightCorner(CornerFamily.ROUNDED, 16.dp.toFloat())
                    .setBottomLeftCorner(CornerFamily.ROUNDED, 16.dp.toFloat())
                    .setBottomRightCorner(CornerFamily.ROUNDED, 2.dp.toFloat())
                    .build()
                setOnClickListener { onImageClick(bmp) }
                setOnLongClickListener { showStatusDialog(msg); true }
            }

            // Overlay: subtle diagonal white fade from bottom right to top left (across corner, NOT full image)
            val fadeWidth = (0.52f * maxAttachW).toInt()    // covers a generous corner area for all devices
            val fadeHeight = (0.23f * hPx).toInt()

            val subtleDiagonalOverlay = object : View(ctx) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val w = width.toFloat()
                    val h = height.toFloat()
                    // Key tweak: 0x33FFFFFF (20% white) for a truly subtle highlight
                    paint.shader = LinearGradient(
                        w, h,
                        0f, 0f,
                        intArrayOf(0x33FFFFFF, Color.TRANSPARENT),  // 20% opaque to transparent
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
            }.apply {
                layoutParams = FrameLayout.LayoutParams(
                    fadeWidth, fadeHeight, Gravity.END or Gravity.BOTTOM
                ).apply {
                    marginEnd = 0
                    bottomMargin = 0
                }
                isClickable = false
                isFocusable = false
            }

            val container = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    maxAttachW, FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 0
                    bottomMargin = 4.dp
                }
                setPadding(0, 0, 0, 0)
            }

            container.addView(imageView)
            container.addView(subtleDiagonalOverlay) // Super-subtle, only-observable-on-close-look highlight

            if (showOverlay) {
                val overlay = createTimeOverlay(msg)
                val overlayParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.BOTTOM
                ).apply {
                    marginEnd = 0  // Set to zero for no extra gap
                    bottomMargin = 4
                    // If you want just a tiny gap from the edge, use 2.dp or 4.dp
                }
                container.addView(overlay, overlayParams)
            }

            return container
        }

        private fun createVideoView(bytes: ByteArray, msg: Message, showOverlay: Boolean): View {
        val tmp = File.createTempFile("vid_", ".mp4", itemView.context.cacheDir).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(itemView.context, "${itemView.context.packageName}.provider", tmp)

        return FrameLayout(itemView.context).apply {
            layoutParams = LinearLayout.LayoutParams(maxAttachW, (maxAttachW * 0.66f).toInt())
            addView(VideoView(context).apply {
                setVideoURI(uri)
                setOnClickListener { if (isPlaying) pause() else start() }
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })

            if (showOverlay) {
                val overlayParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.BOTTOM
                ).apply {
                    marginEnd = 10.dp
                    bottomMargin = 8.dp
                }
                addView(createTimeOverlay(msg), overlayParams)
            }
        }
    }
        private fun createAudioView(bytes: ByteArray, msg: Message): View {
            val tmp = File.createTempFile("aud_", ".mp3", itemView.context.cacheDir).apply {
                writeBytes(bytes)
            }
            val ctx = itemView.context

            val isDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val bgColor = ContextCompat.getColor(ctx, R.color.card_purple_bg)

            val timeColor = if (isDark)
                ContextCompat.getColor(ctx, android.R.color.darker_gray)
            else
                ContextCompat.getColor(ctx, R.color.purple_500)

            val player = MediaPlayer().apply {
                setDataSource(ctx, FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", tmp))
                prepare()
            }

            val totalMs = player.duration
            val fullDur = formatMillis(totalMs)

            val btnPlay = ImageButton(ctx).apply {
                setImageResource(android.R.drawable.ic_media_play)
                background = null
                layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp).apply {  // Reduced from 28.dp to 24.dp
                   marginStart = 4.dp
                    marginEnd = 4.dp  // Reduced from 6.dp to 4.dp
                }
            }

            val seek = SeekBar(ctx).apply {
                max = totalMs
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                thumb.setTint(ContextCompat.getColor(ctx, R.color.purple_500))
                progressDrawable.setTint(ContextCompat.getColor(ctx, R.color.purple_500))
            }

            val timer = TextView(ctx).apply {
                text = "00:00 / $fullDur"
                setTextColor(timeColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 2.dp  // Reduced from 2.dp to 1.dp
                }
            }

            val handler = Handler(Looper.getMainLooper())
            var tick: Runnable? = null
            fun stopTick() = tick?.let { handler.removeCallbacks(it) }

            btnPlay.setOnClickListener {
                if (player.isPlaying) {
                    player.pause()
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    stopTick()
                } else {
                    player.start()
                    btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                    tick = object : Runnable {
                        override fun run() {
                            if (player.isPlaying) {
                                val current = player.currentPosition
                                seek.progress = current
                                timer.text = "${formatMillis(current)} / $fullDur"
                                handler.postDelayed(this, 16)
                            }
                        }
                    }
                    handler.post(tick!!)
                }
            }

            player.setOnCompletionListener {
                btnPlay.setImageResource(android.R.drawable.ic_media_play)
                seek.progress = 0
                timer.text = "00:00 / $fullDur"
                stopTick()
            }

            val controlsRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(btnPlay)
                addView(seek)
            }

            val audioContentInner = FrameLayout(ctx).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 12.dp.toFloat()
                    setColor(bgColor)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(4.dp, 4.dp, 4.dp, 4.dp)  // Reduced from 6dp to 4dp padding

                val innerColumn = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    addView(controlsRow)
                    addView(timer)
                }

                addView(innerColumn)

                val timeOverlay = createTimeOverlay(msg).apply {
                    findViewById<TextView>(R.id.attachmentTimeOverlay)?.setTextColor(timeColor)
                }

                val overlayParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.BOTTOM
                ).apply {
                    marginEnd = 2.dp  // Reduced from 4.dp to 2.dp
                    bottomMargin = 1.dp  // Reduced from 2.dp to 1.dp
                }

                addView(timeOverlay, overlayParams)
            }

            val audioContentCard = FrameLayout(ctx).apply {
                addView(audioContentInner)
                layoutParams = LinearLayout.LayoutParams(
                    (screenW * 0.55f).toInt(),  // Reduced from 0.6f to 0.55f for ~30dp less width
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.TRANSPARENT)
                ViewCompat.setElevation(this, 4.dp.toFloat())
            }

            return FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 1.dp  // Reduced from 2.dp
                    bottomMargin = 1.dp // Reduced from 2.dp
                }
                addView(audioContentCard)
            }
        }


    private fun createFileLinkView(label: String, url: String): View =
        TextView(itemView.context).apply {
            text = label
            paint.isUnderlineText = true
            setTextColor(Color.BLUE)
            setOnClickListener { itemView.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }

        private fun createTimeOverlay(msg: Message): View {
            val (iconRes, _) = getStatusIconAndColor(msg)
            val ctx = itemView.context

            val purple = ContextCompat.getColor(ctx, R.color.text_status) // Use your branded purple

            val timeView = TextView(ctx).apply {
                text = formatTime(msg.inserted_at)
                setTextColor(purple)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                // Use a little bold: best for WhatsApp/Telegram style
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                // Alternative for slightly less bold, more modern:
                // typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val statusView = ImageView(ctx).apply {
                setImageResource(iconRes)
                setColorFilter(purple, PorterDuff.Mode.SRC_ATOP)
                layoutParams = LinearLayout.LayoutParams(14.dp, 14.dp).apply {
                    leftMargin = 4.dp
                    gravity = Gravity.CENTER_VERTICAL
                }
            }

            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                background = null
                setPadding(0, 0, 0, 0)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.BOTTOM
                ).apply {
                    marginEnd = 10.dp  // Adjust to bring status close to image corner
                    bottomMargin = 8.dp
                }
                addView(timeView)
                addView(statusView)
            }
        }

        private fun getStatusIconAndColor(msg: Message): Pair<Int, Int> {
        val status = getOverallStatus(msg.status_entries, currentUserId.toString())
        return when (status) {
            "read" -> R.drawable.ic_done_all to R.color.purple_500
            "delivered" -> R.drawable.ic_done_all to R.color.gray
            "sent" -> R.drawable.ic_sent to R.color.gray
            else -> R.drawable.pending to R.color.gray
        }
    }

    private fun getOverallStatus(entries: List<StatusEntry>, currentUserId: String): String {
        if (entries.isEmpty()) return "pending"
        val others = entries.filter { it.user_id.toString() != currentUserId }
        if (others.isEmpty()) return "sent"
        return when {
            others.all { it.status.equals("read", true) } -> "read"
            others.any { it.status.equals("delivered", true) } -> "delivered"
            others.any { it.status.equals("sent", true) } -> "sent"
            else -> "pending"
        }
    }

    private fun formatMillis(ms: Int): String = String.format("%02d:%02d", (ms / 1000) / 60, (ms / 1000) % 60)
}

    inner class ReceivedMessageViewHolder(val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root) {

        private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        private val screenHeight = Resources.getSystem().displayMetrics.heightPixels

        private val maxAttachmentWidth = (screenWidth * 0.5f).toInt()
        private val maxAttachmentHeight = (screenHeight * 0.33f).toInt()
        private val maxBubbleWidthPx: Int
            get() = (Resources.getSystem().displayMetrics.widthPixels * 0.6f).toInt()

        @RequiresApi(Build.VERSION_CODES.O)
        fun bind(msg: Message) {
            val context = itemView.context
            val screenWidth = context.resources.displayMetrics.widthPixels

            // Set max bubble width based on private/group
            val maxBubbleWidth = if (isPrivate) {
                (screenWidth * 0.6f).toInt()
            } else {
                (screenWidth * 0.55f).toInt()
            }

            // ✅ TEXT bubble
            if (!msg.encrypted_body.isNullOrBlank() || msg.reply_to != null) {
                binding.textBubble.visibility = View.VISIBLE
                binding.textMessageBody.text = msg.encrypted_body ?: ""
                bindReplyPreview(context, msg)

                // Set max width for text bubble
                binding.textMessageBody.maxWidth = maxBubbleWidth

                // Show time if no attachments
                binding.textMessageTime.visibility =
                    if (msg.attachments.isEmpty()) View.VISIBLE else View.GONE
                if (msg.attachments.isEmpty())
                    binding.textMessageTime.text = formatTime(msg.inserted_at)
            } else {
                binding.textBubble.visibility = View.GONE
            }

            // ✅ ATTACHMENT bubble
            if (msg.attachments.isNotEmpty()) {
                binding.attachmentBubble.visibility = View.VISIBLE
                binding.attachmentContainer.removeAllViews()

                msg.attachments.forEach { attachment ->
                    val mimeType = attachment.mime_type.orEmpty().lowercase()
                    val decodedBytes = try {
                        Base64.decode(attachment.file_url ?: "", Base64.DEFAULT)
                    } catch (_: Exception) {
                        null
                    }

                    val view = when {
                        mimeType.startsWith("image") && decodedBytes != null ->
                            createImageAttachmentView(context, decodedBytes, msg)

                        mimeType.startsWith("audio") && decodedBytes != null ->
                            createAudioPlayerView(context, decodedBytes, msg)

                        mimeType.startsWith("video") && decodedBytes != null ->
                            createVideoPlayerView(context, decodedBytes, msg)

                        else -> createClickableTextView(context, "📎 File", attachment.file_url ?: "")
                    }
                    binding.attachmentContainer.addView(view)
                }
            } else {
                binding.attachmentBubble.visibility = View.GONE
            }

            // ✅ Sender Info for TEXT bubble
            if (!isPrivate) {
                binding.senderName.text = msg.sender_display_name ?: "Unknown"
                binding.senderName.visibility = View.VISIBLE
                binding.ivSenderAvatar.visibility = View.VISIBLE

                val bmp = msg.sender_avatar_data?.let { base64ToBitmap(it) }
                if (bmp != null) {
                    Glide.with(context).load(bmp).circleCrop().into(binding.ivSenderAvatar)
                } else {
                    binding.ivSenderAvatar.setImageResource(R.drawable.account_circle)
                }

                // ✅ Sender Info for ATTACHMENT bubble
                if (msg.attachments.isNotEmpty()) {
                    binding.ivAttachmentSenderAvatar.visibility = View.VISIBLE
                    if (bmp != null) {
                        Glide.with(context).load(bmp).circleCrop()
                            .into(binding.ivAttachmentSenderAvatar)
                    } else {
                        binding.ivAttachmentSenderAvatar.setImageResource(R.drawable.account_circle)
                    }
                } else {
                    binding.ivAttachmentSenderAvatar.visibility = View.GONE
                }
            } else {
                binding.senderName.visibility = View.GONE
                binding.ivSenderAvatar.visibility = View.GONE
                binding.ivAttachmentSenderAvatar.visibility = View.GONE
            }
        }


        private fun bindReplyPreview(context: Context, msg: Message) {
            val reply = msg.reply_to
            if (reply != null) {
                binding.replyLayout.visibility = View.VISIBLE
                binding.replyLayout.removeAllViews()

                val attachment = reply.attachments.firstOrNull()

                val screenWidth = context.resources.displayMetrics.widthPixels
                val minBubbleWidth = (screenWidth * 0.55f).toInt() // ✅ Minimum width like WhatsApp

                // ✅ Parent bubble layout
                val replyView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundResource(R.drawable.reply_background)
                    setPadding(8.dp, 6.dp, 8.dp, 6.dp)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        this.width = LinearLayout.LayoutParams.WRAP_CONTENT
                    }
                    minimumWidth = minBubbleWidth
                    minimumHeight = 48.dp
                }

                // ✅ Left vertical colored bar
                val verticalBar = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(4.dp, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                        marginEnd = 6.dp
                    }
                    setBackgroundColor(ContextCompat.getColor(context, R.color.purple_700))
                }
                replyView.addView(verticalBar)

                // ✅ Right content (fills width properly)
                val rightContent = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                // ✅ Text column (Sender + Message/Type)
                val textColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                // Sender Name
                textColumn.addView(TextView(context).apply {
                    text = reply.sender_display_name ?: "Unknown"
                    setTextColor(ContextCompat.getColor(context, R.color.purple_700))
                    setTypeface(typeface, Typeface.BOLD)
                    textSize = 13f
                })

                if (attachment != null) {
                    textColumn.addView(TextView(context).apply {
                        text = when {
                            attachment.mime_type?.startsWith("image") == true -> "Photo"
                            attachment.mime_type?.startsWith("video") == true -> "Video"
                            attachment.mime_type?.startsWith("audio") == true -> "Audio"
                            else -> "File"
                        }
                        setTextColor(Color.DKGRAY)
                        textSize = 12f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                } else {
                    val preview = reply.encrypted_body?.takeIf { it.isNotBlank() } ?: "[Message]"
                    textColumn.addView(TextView(context).apply {
                        text = if (preview.length > 55) preview.take(55) + "..." else preview
                        setTextColor(Color.DKGRAY)
                        textSize = 12f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                }

                rightContent.addView(textColumn)

                // ✅ Thumbnail/Icon (if any)
                if (attachment != null) {
                    val thumbView = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(45.dp, 45.dp).apply {
                            marginStart = 6.dp
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    when {
                        attachment.mime_type?.startsWith("image") == true -> {
                            Glide.with(context)
                                .load(Base64.decode(attachment.file_url, Base64.DEFAULT))
                                .placeholder(R.drawable.ic_message)
                                .centerCrop()
                                .into(thumbView)
                        }
                        attachment.mime_type?.startsWith("video") == true -> {
                            thumbView.setImageResource(R.drawable.ic_play)
                            thumbView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                        attachment.mime_type?.startsWith("audio") == true -> {
                            thumbView.setImageResource(R.drawable.ic_mic)
                            thumbView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                        else -> {
                            thumbView.setImageResource(R.drawable.ic_message)
                        }
                    }
                    rightContent.addView(thumbView)
                }

                replyView.addView(rightContent)
                binding.replyLayout.addView(replyView)

                // ✅ Scroll-to-original click
                binding.replyLayout.setOnClickListener {
                    reply.id?.let {
                        onReplyPreviewClick?.invoke(it)
                        onHighlight?.invoke(it)
                    }
                }
            } else {
                binding.replyLayout.visibility = View.GONE
            }
        }


        @RequiresApi(Build.VERSION_CODES.O)
        private fun createImageAttachmentView(context: Context, data: ByteArray, msg: Message): View {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)

            val fixedWidth = (screenWidth * 0.55f).toInt()  // ~55% of screen
            val fixedHeight = (screenHeight * 0.33f).toInt() // ~1/3 of screen

            val imageView = ImageView(context).apply {
                setImageBitmap(bitmap)
                layoutParams = FrameLayout.LayoutParams(fixedWidth, fixedHeight).apply {
                    gravity = Gravity.CENTER
                }
                scaleType = ImageView.ScaleType.CENTER_CROP // ✅ Fills the box properly
                adjustViewBounds = false
                clipToOutline = true
                setOnClickListener { onImageClick(bitmap) }
            }

            return FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(fixedWidth, fixedHeight).apply {
                    topMargin = 8.dp
                    bottomMargin = 4.dp
                }
                addView(imageView)
                addView(createOverlayLayout(context, msg)) // ✅ Time overlay (already implemented)
            }
        }

        // ✅ Audio Attachment View
        @RequiresApi(Build.VERSION_CODES.O)
        private fun createAudioPlayerView(context: Context, data: ByteArray, msg: Message): View {
            val tmp = File.createTempFile("aud_", ".mp3", context.cacheDir).apply {
                writeBytes(data)
            }

            // === Colors consistent with received bubble ===
            val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val bubbleColor = ContextCompat.getColor(context, R.color.card_purple_bg)
            val timeColor = if (isDark)
                ContextCompat.getColor(context, android.R.color.darker_gray)
            else
                ContextCompat.getColor(context, R.color.purple_500)

            // === Media Player Setup ===
            val player = MediaPlayer().apply {
                setDataSource(context, FileProvider.getUriForFile(context, "${context.packageName}.provider", tmp))
                prepare()
            }

            val totalMs = player.duration
            val fullDur = formatMillis(totalMs)

            val btnPlay = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_media_play)
                background = null
                layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 6.dp }
            }

            val seek = SeekBar(context).apply {
                max = totalMs
                progress = 0
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                thumb.setTint(ContextCompat.getColor(context, R.color.purple_500))
                progressDrawable.setTint(ContextCompat.getColor(context, R.color.purple_500))
            }

            val timer = TextView(context).apply {
                text = "00:00 / $fullDur"
                setTextColor(timeColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2.dp }
            }

            // === Playback Handling ===
            val handler = Handler(Looper.getMainLooper())
            var tick: Runnable? = null
            fun stopTick() = tick?.let { handler.removeCallbacks(it) }

            btnPlay.setOnClickListener {
                if (player.isPlaying) {
                    player.pause()
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    stopTick()
                } else {
                    player.start()
                    btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                    tick = object : Runnable {
                        override fun run() {
                            if (player.isPlaying) {
                                seek.progress = player.currentPosition
                                timer.text = "${formatMillis(player.currentPosition)} / $fullDur"
                                handler.postDelayed(this, 50)
                            }
                        }
                    }
                    handler.post(tick!!)
                }
            }

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        player.seekTo(progress)
                        timer.text = "${formatMillis(progress)} / $fullDur"
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            player.setOnCompletionListener {
                btnPlay.setImageResource(android.R.drawable.ic_media_play)
                seek.progress = 0
                timer.text = "00:00 / $fullDur"
                stopTick()
            }

            // === Controls Row (Play + SeekBar) ===
            val controlsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(btnPlay)
                addView(seek)
            }

            // === Inner Content with Rounded Background ===
            val audioContentInner = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 12.dp.toFloat()
                    setColor(bubbleColor)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(8.dp, 8.dp, 8.dp, 8.dp)

                val innerColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    addView(controlsRow)
                    addView(timer)
                }
                addView(innerColumn)

                // === Time Overlay (bottom-right)
                val timeOverlay = FrameLayout(context).apply {
                    val timeText = TextView(context).apply {
                        text = formatTime(msg.inserted_at)
                        setTextColor(timeColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        typeface = Typeface.MONOSPACE
                    }
                    addView(timeText)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.END or Gravity.BOTTOM
                    ).apply {
                        marginEnd = 4.dp
                        bottomMargin = 2.dp
                    }
                }
                addView(timeOverlay)
            }

            // === Outer Card (adds elevation + max width) ===
            val audioContentCard = FrameLayout(context).apply {
                addView(audioContentInner)
                layoutParams = LinearLayout.LayoutParams(
                    (screenWidth * 0.6f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 2.dp
                    bottomMargin = 2.dp
                }
                setBackgroundColor(Color.TRANSPARENT)
                ViewCompat.setElevation(this, 2.dp.toFloat())
            }

            return FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(audioContentCard)
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun createAudioView(bytes: ByteArray, msg: Message): View {
            val ctx = itemView.context
            val tmp = File.createTempFile("aud_", ".mp3", ctx.cacheDir).apply {
                writeBytes(bytes)
            }

            // === Colors consistent with received bubble ===
            val isDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val bubbleColor = ContextCompat.getColor(ctx, R.color.card_purple_bg) // receiver bubble color
            val timeColor = if (isDark)
                ContextCompat.getColor(ctx, android.R.color.darker_gray)
            else
                ContextCompat.getColor(ctx, R.color.purple_500)

            // === Media Player Setup ===
            val player = MediaPlayer().apply {
                setDataSource(ctx, FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", tmp))
                prepare()
            }

            val totalMs = player.duration
            val fullDur = formatMillis(totalMs)

            val btnPlay = ImageButton(ctx).apply {
                setImageResource(android.R.drawable.ic_media_play)
                background = null
                layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 6.dp }
            }

            val seek = SeekBar(ctx).apply {
                max = totalMs
                progress = 0
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                thumb.setTint(ContextCompat.getColor(ctx, R.color.purple_500))
                progressDrawable.setTint(ContextCompat.getColor(ctx, R.color.purple_500))
            }

            val timer = TextView(ctx).apply {
                text = "00:00 / $fullDur"
                setTextColor(timeColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2.dp }
            }

            // === Playback Handling ===
            val handler = Handler(Looper.getMainLooper())
            var tick: Runnable? = null
            fun stopTick() = tick?.let { handler.removeCallbacks(it) }

            btnPlay.setOnClickListener {
                if (player.isPlaying) {
                    player.pause()
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    stopTick()
                } else {
                    player.start()
                    btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                    tick = object : Runnable {
                        override fun run() {
                            if (player.isPlaying) {
                                seek.progress = player.currentPosition
                                timer.text = "${formatMillis(player.currentPosition)} / $fullDur"
                                handler.postDelayed(this, 50)
                            }
                        }
                    }
                    handler.post(tick!!)
                }
            }

            player.setOnCompletionListener {
                btnPlay.setImageResource(android.R.drawable.ic_media_play)
                seek.progress = 0
                timer.text = "00:00 / $fullDur"
                stopTick()
            }

            // === Controls Row (Play + SeekBar) ===
            val controlsRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(btnPlay)
                addView(seek)
            }

            // === Inner Content with Rounded Background ===
            val audioContentInner = FrameLayout(ctx).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 12.dp.toFloat()
                    setColor(bubbleColor)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(8.dp, 8.dp, 8.dp, 8.dp)

                val innerColumn = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    addView(controlsRow)
                    addView(timer)
                }
                addView(innerColumn)

                // === Time Overlay (only time, no status) ===
                val timeOverlay = FrameLayout(ctx).apply {
                    val timeText = TextView(ctx).apply {
                        text = formatTime(msg.inserted_at)
                        setTextColor(timeColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        typeface = Typeface.MONOSPACE
                    }
                    addView(timeText)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.END or Gravity.BOTTOM
                    ).apply {
                        marginEnd = 4.dp
                        bottomMargin = 2.dp
                    }
                }
                addView(timeOverlay)
            }

            // === Outer Card (adds elevation + max width) ===
            val audioContentCard = FrameLayout(ctx).apply {
                addView(audioContentInner)
                layoutParams = LinearLayout.LayoutParams(
                    (screenWidth * 0.6f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 2.dp
                    bottomMargin = 2.dp
                }
                setBackgroundColor(Color.TRANSPARENT)
                ViewCompat.setElevation(this, 2.dp.toFloat())
            }

            return FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(audioContentCard)
            }
        }

        // ✅ Video Attachment View
        @RequiresApi(Build.VERSION_CODES.O)
        private fun createVideoPlayerView(context: Context, data: ByteArray, msg: Message): View {
            val tempFile = File.createTempFile("video_", ".mp4", context.cacheDir)
            tempFile.writeBytes(data)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", tempFile)

            val videoView = VideoView(context).apply {
                setVideoURI(uri)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setOnClickListener { if (isPlaying) pause() else start() }
            }

            return FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    maxAttachmentWidth,
                    maxAttachmentHeight
                ).apply {
                    topMargin = 6.dp
                    bottomMargin = 6.dp
                }
                addView(videoView)
                addView(createOverlayLayout(context, msg))
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun createOverlayLayout(context: Context, msg: Message): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(8.dp, 4.dp, 8.dp, 4.dp)
                background = ContextCompat.getDrawable(context, R.drawable.overlay_bg)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                ).apply {
                    setMargins(0, 0, 8.dp, 8.dp)
                }
                addView(TextView(context).apply {
                    text = formatTime(msg.inserted_at)
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setShadowLayer(4f, 0f, 0f, Color.BLACK)
                })
            }
        }

        private fun createClickableTextView(context: Context, label: String, fileUrl: String): TextView {
            return TextView(context).apply {
                text = label
                setTextColor(0xFF0000FF.toInt())
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                setOnClickListener {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl)))
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("SimpleDateFormat")
        private fun formatTime(timestamp: String): String {
            return try {
                val instant = Instant.parse(timestamp) // Parse ISO format
                val formatter = DateTimeFormatter.ofPattern("hh:mm a")
                    .withZone(ZoneId.systemDefault()) // Local timezone
                formatter.format(instant)
            } catch (e: Exception) {
                timestamp // fallback to raw if parsing fails
            }
        }

        val Int.dp: Int
            get() = (this * Resources.getSystem().displayMetrics.density).toInt()
    }

    fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    fun bindReplyPreview(context: Context, layout: LinearLayout, reply: Message, onClick: () -> Unit) {
        layout.visibility = View.VISIBLE
        layout.removeAllViews()

        val sender = TextView(context).apply {
            text = reply.sender_display_name ?: "Unknown"
            setTypeface(null, Typeface.BOLD)
        }
        val message = TextView(context).apply {
            text = reply.encrypted_body ?: "[Attachment]"
        }

        layout.addView(sender)
        layout.addView(message)

        reply.attachments.firstOrNull { it.mime_type?.startsWith("image") == true }?.let {
            val imageView = ImageView(context).apply {
                try {
                    val bytes = Base64.decode(it.file_url, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    setImageBitmap(bmp)
                    layoutParams = LinearLayout.LayoutParams(150, 150).apply {
                        gravity = Gravity.START
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setOnClickListener { onImageClick(bmp) }
                } catch (e: Exception) {
                    visibility = View.GONE
                }
            }
            layout.addView(imageView)
        }

        layout.setOnClickListener { onClick() }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return
        val msg = messages[pos]
        if (msg.sender_id != currentUserId &&
            msg.status_entries.none { it.user_id == currentUserId && it.status == "read" }
        ) {
            onMessageRead?.invoke(msg.id)
        }
    }

    val Int.dp: Int
        get() = (this * Resources.getSystem().displayMetrics.density).toInt()

    private fun formatMillis(millis: Int): String {
        val totalSeconds = millis / 1000
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    private fun createAudioPlayerView(context: Context, data: ByteArray, msg: Message): View {
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels

        val tempFile = File.createTempFile("audio_", ".mp3", context.cacheDir).apply {
            writeBytes(data)
        }

        val isDark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val bubbleColor = ContextCompat.getColor(context, R.color.card_purple_bg)
        val timeColor = if (isDark)
            ContextCompat.getColor(context, android.R.color.darker_gray)
        else
            ContextCompat.getColor(context, R.color.purple_500)

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            tempFile
        )

        val mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            prepare()
        }

        val totalMs = mediaPlayer.duration
        val fullDur = formatMillis(totalMs)

        val btnPlay = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_media_play)
            background = null
            layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 6.dp }
        }

        val seek = SeekBar(context).apply {
            max = totalMs
            progress = 0
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            thumb.setTint(ContextCompat.getColor(context, R.color.purple_500))
            progressDrawable.setTint(ContextCompat.getColor(context, R.color.purple_500))
        }

        val timer = TextView(context).apply {
            text = "00:00 / $fullDur"
            setTextColor(timeColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 2.dp }
        }

        val handler = Handler(Looper.getMainLooper())
        var tick: Runnable? = null
        fun stopTick() = tick?.let { handler.removeCallbacks(it) }

        btnPlay.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                btnPlay.setImageResource(android.R.drawable.ic_media_play)
                stopTick()
            } else {
                mediaPlayer.start()
                btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                tick = object : Runnable {
                    override fun run() {
                        if (mediaPlayer.isPlaying) {
                            seek.progress = mediaPlayer.currentPosition
                            timer.text = "${formatMillis(mediaPlayer.currentPosition)} / $fullDur"
                            handler.postDelayed(this, 16)
                        }
                    }
                }
                handler.post(tick!!)
            }
        }

        mediaPlayer.setOnCompletionListener {
            btnPlay.setImageResource(android.R.drawable.ic_media_play)
            seek.progress = 0
            timer.text = "00:00 / $fullDur"
            stopTick()
        }

        // === Controls Row (Play + SeekBar) ===
        val controlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(btnPlay)
            addView(seek)
        }

        // ✅ Message Time Overlay (bottom-right)
        val timeOverlay = TextView(context).apply {
            text = formatTime(msg.inserted_at)
            setTextColor(timeColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
        }

        val overlayParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.BOTTOM
        ).apply {
            marginEnd = 4.dp
            bottomMargin = 2.dp
        }

        // === Inner Bubble with Rounded Background ===
        val audioContentInner = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 12.dp.toFloat()
                setColor(bubbleColor)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(6.dp, 6.dp, 6.dp, 6.dp)

            val innerColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(controlsRow)
                addView(timer)
            }

            addView(innerColumn)
            addView(timeOverlay, overlayParams) // ✅ added message time
        }

        // === Outer Card with Elevation (max width ~60%) ===
        val audioContentCard = FrameLayout(context).apply {
            addView(audioContentInner)
            layoutParams = LinearLayout.LayoutParams(
                (screenWidth * 0.6f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            ViewCompat.setElevation(this, 4.dp.toFloat())
        }

        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.dp
                bottomMargin = 2.dp
            }
            addView(audioContentCard)
        }
    }

    private fun createVideoPlayerView(context: Context, data: ByteArray): View {
        val tempFile = File.createTempFile("video_", ".mp4", context.cacheDir)
        tempFile.writeBytes(data)

        return Button(context).apply {
            text = "▶️ Play Video"
            setOnClickListener {
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    tempFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            }
        }
    }

    private fun createClickableTextView(context: Context, label: String, fileUrl: String): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(0xFF0000FF.toInt())
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl)))
            }
        }
    }

    private fun formatTime(utcTimestamp: String): String {
        return try {
            val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val outFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            outFmt.format(inFmt.parse(utcTimestamp)!!)
        } catch (e: Exception) {
            "??:??"
        }
    }

    private fun onImageClick(clickedBitmap: Bitmap) {
        val allBitmaps = messages.flatMap { it.attachments }
            .filter { it.mime_type?.startsWith("image") == true }
            .mapNotNull { a ->
                a.file_url?.let {
                    try {
                        val bytes = Base64.decode(it, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) {
                        null
                    }
                }
            }

        val index = allBitmaps.indexOfFirst { it.sameAs(clickedBitmap) }
        if (index != -1) {
            ImageSliderDialogFragment(allBitmaps, index)
                .show(fragmentManager, "ImageSlider")
        }
    }

//    private fun showStatusDialog(msg: Message, msgView: View) {
//        val ctx = msgView.context
//        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_message_status, null)
//        val container = dialogView.findViewById<LinearLayout>(R.id.statusContainer)
//
//        val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
//            timeZone = TimeZone.getTimeZone("UTC")
//        }
//        val outFmt = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
//
//        val otherStatuses = msg.status_entries.filter { it.user_id != currentUserId }
//
//        if (otherStatuses.isEmpty()) {
//            val tv = TextView(ctx).apply {
//                text = "No status info available for other users yet."
//                setPadding(40, 60, 40, 60)
//                textSize = 16f
//                gravity = Gravity.CENTER
//            }
//            container.addView(tv)
//        } else {
//            otherStatuses.forEach { e ->
//                val name = e.display_name?.takeIf { it.isNotBlank() } ?: "Unknown"
//                val time = runCatching {
//                    e.status_ts.let { outFmt.format(inFmt.parse(it)!!) }
//                }.getOrNull() ?: "Unknown time"
//
//                val avatarBmp = e.avatar_data?.takeIf { it.isNotBlank() }?.let { base64 ->
//                    try {
//                        val imageBytes = Base64.decode(base64, Base64.DEFAULT)
//                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
//                    } catch (_: Exception) {
//                        null
//                    }
//                }
//
//                val itemView = LayoutInflater.from(ctx).inflate(R.layout.item_status_row, container, false)
//
//                val avatarView = itemView.findViewById<ImageView>(R.id.avatarImageView)
//                avatarView.setImageBitmap(avatarBmp ?: BitmapFactory.decodeResource(ctx.resources, R.drawable.account_circle))
//
//                itemView.findViewById<TextView>(R.id.nameTextView).text = name
//                itemView.findViewById<TextView>(R.id.timeTextView).text = time
//
//                val statusIconView = itemView.findViewById<ImageView>(R.id.statusIcon)
//                val statusTextView = itemView.findViewById<TextView>(R.id.statusTextView)
//
//                val (iconRes, tintColorRes, label) = when (e.status.lowercase()) {
//                    "pending" -> Triple(R.drawable.pending, R.color.gray, "Pending")
//                    "sent" -> Triple(R.drawable.ic_sent, R.color.gray, "Sent")
//                    "delivered" -> Triple(R.drawable.ic_done_all, R.color.gray, "Delivered")
//                    "read" -> Triple(R.drawable.ic_done_all, R.color.purple_500, "Read")
//                    else -> Triple(0, R.color.gray, "Unknown")
//                }
//
//                if (iconRes != 0) {
//                    statusIconView.setImageResource(iconRes)
//                    statusIconView.setColorFilter(ContextCompat.getColor(ctx, tintColorRes))
//                } else {
//                    statusIconView.setImageDrawable(null)
//                }
//
//                statusTextView.text = label
//                container.addView(itemView)
//            }
//        }
//
//        AlertDialog.Builder(ctx)
//            .setView(dialogView)
//            .setPositiveButton("OK", null)
//            .show()
//    }
}
