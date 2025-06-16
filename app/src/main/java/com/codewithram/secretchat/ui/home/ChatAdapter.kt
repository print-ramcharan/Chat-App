//package com.codewithram.secretchat.ui.home
//
//import Message
//import StatusEntry
//import android.content.Intent
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.graphics.Paint
//import android.net.Uri
//import android.os.Build
//import android.util.Base64
//import android.util.Log
//import android.view.LayoutInflater
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.LinearLayout
//import android.widget.TextView
//import androidx.annotation.RequiresApi
//import androidx.appcompat.app.AlertDialog
//import androidx.recyclerview.widget.RecyclerView
//import com.bumptech.glide.Glide
//import com.codewithram.secretchat.R
//import com.codewithram.secretchat.databinding.ItemMessageReceivedBinding
//import com.codewithram.secretchat.databinding.ItemMessageSentBinding
//import java.text.SimpleDateFormat
//import java.time.Instant
//import java.util.Locale
//import java.util.TimeZone
//import java.util.UUID
//
//data class UserInfo(val name: String, val photoUrl: String)
//
//internal val userMap = mutableMapOf<UUID, UserInfo>()
//
//fun setUserMap(map: Map<UUID, UserInfo>) {
//    userMap.clear()
//    userMap.putAll(map)
//}
//
//class ChatAdapter(private val currentUserId: UUID,
//                  private val userMap: MutableMap<UUID, UserInfo>
//) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
//
//    companion object {
//        private const val VIEW_TYPE_SENT = 1
//        private const val VIEW_TYPE_RECEIVED = 2
//    }
//
//    var onMessageRead: ((UUID) -> Unit)? = null
//
//    internal val messages = mutableListOf<Message>()
//
//    fun setAll(newMessages: List<Message>) {
//        messages.clear()
//        messages.addAll(newMessages)
//        notifyDataSetChanged()
//    }
//
//    fun addMessage(msg: Message) {
//        messages.add(msg)
//        notifyItemInserted(messages.size - 1)
//    }
//
//    fun updateMessageAt(index: Int, newMessage: Message) {
//        messages[index] = newMessage
//        notifyItemChanged(index)
//    }
//
//    fun findMessageIndexById(messageId: UUID): Int {
//        return messages.indexOfFirst { it.id == messageId }
//    }
//
//    fun containsMessageId(messageId: UUID): Boolean {
//        return messages.any { it.id == messageId }
//    }
//
//    fun findPendingMessageIndex(encryptedBody: String, senderId: UUID): Int {
//        return messages.indexOfFirst {
//            it.encrypted_body == encryptedBody &&
//                    it.sender_id == senderId &&
//                    it.status_entries.any { status -> status.status == "pending" }
//        }
//    }
//
//    override fun getItemViewType(position: Int): Int {
//        return if (messages[position].sender_id == currentUserId) VIEW_TYPE_SENT
//        else VIEW_TYPE_RECEIVED
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
//        if (viewType == VIEW_TYPE_SENT) {
//            val binding = ItemMessageSentBinding.inflate(
//                LayoutInflater.from(parent.context), parent, false
//            )
//            SentMessageViewHolder(binding)
//        } else {
//            val binding = ItemMessageReceivedBinding.inflate(
//                LayoutInflater.from(parent.context), parent, false
//            )
//            ReceivedMessageViewHolder(binding)
//        }
//
//    override fun getItemCount(): Int = messages.size
//
//    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
//        val msg = messages[position]
//        when (holder) {
//            is SentMessageViewHolder     -> holder.bind(msg)
//            is ReceivedMessageViewHolder -> holder.bind(msg)
//        }
//    }
//
//
//    @RequiresApi(Build.VERSION_CODES.O)
//    fun updateMessageStatus(messageId: String, userId: String, newStatus: String) {
//        val msgIndex = messages.indexOfFirst { it.id.toString() == messageId }
//        if (msgIndex == -1) return
//
//        val entryIndex = messages[msgIndex].status_entries.indexOfFirst {
//            it.user_id.toString() == userId
//        }
//
//        val updatedEntry = StatusEntry(
//            id = UUID.randomUUID(),
//            message_id = UUID.fromString(messageId),
//            user_id = UUID.fromString(userId),
//            status = newStatus,
//            status_ts = Instant.now().toString(),
//            inserted_at = Instant.now().toString(),
//            updated_at = Instant.now().toString(),
//            display_name = "",
//            avatar_data = ""
//        )
//
//        if (entryIndex != -1) {
//            messages[msgIndex].status_entries = messages[msgIndex].status_entries.toMutableList().apply {
//                set(entryIndex, updatedEntry)
//            }
//        } else {
//            messages[msgIndex].status_entries = messages[msgIndex].status_entries + updatedEntry
//        }
//
//        notifyItemChanged(msgIndex)
//    }
//
//    fun getUnreadMessagesForUser(userId: UUID): List<Message> {
//        return messages.filter {
//            it.sender_id != userId &&
//                    it.status_entries.none { entry -> entry.user_id == userId && entry.status == "read" }
//        }
//    }
//    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
//        super.onViewAttachedToWindow(holder)
//        val pos = holder.adapterPosition
//        if (pos != RecyclerView.NO_POSITION) {
//            val msg = messages[pos]
//            if (msg.sender_id != currentUserId &&
//                msg.status_entries.none { it.user_id == currentUserId && it.status == "read" }
//            ) {
//                onMessageRead?.invoke(msg.id)
//            }
//        }
//    }
//
//
//    inner class SentMessageViewHolder(private val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root) {
//
//        fun bind(msg: Message) {
//            Log.d("messagex", msg.toString())
//            binding.textMessageBody.text = msg.encrypted_body
//            binding.attachmentContainer.removeAllViews()
//
//            msg.attachments.forEach { a ->
//                binding.attachmentContainer.addView(
//                    TextView(itemView.context).apply {
//                        text = "Attachment: ${a.file_url}"
//                        setTextColor(0xFF0000FF.toInt())
//                        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
//                        setOnClickListener {
//                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(a.file_url))
//                            itemView.context.startActivity(intent)
//                        }
//                    }
//                )
//            }
//
//            val relevantStatuses = msg.status_entries
//                .filter { it.user_id != currentUserId }
//                .map { it.status }
//
//            val statusText = when {
//                relevantStatuses.isEmpty() || relevantStatuses.any { it == "pending" } -> "✓"
//                relevantStatuses.all { it == "read" } -> "✓✓ (blue)"
//                relevantStatuses.all { it == "delivered" } -> "✓✓"
//                relevantStatuses.all { it == "delivered" || it == "read" } -> "✓✓"
//                else -> "✓"
//            }
//
//            binding.textMessageStatus.text = statusText
//
//            itemView.setOnLongClickListener {
//                val context = itemView.context
//
//                val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_message_status, null)
//                val container = dialogView.findViewById<LinearLayout>(R.id.statusContainer)
//
//                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
//                    timeZone = TimeZone.getTimeZone("UTC")
//                }
//                val outputFormat = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
//
//                msg.status_entries.orEmpty().forEach { entry ->
//                    if (entry.user_id == currentUserId) return@forEach
//
//                    val name = entry.display_name?.takeIf { it.isNotBlank() }
//                        ?: "Unknown"
//
//                    val formattedTime = try {
//                        entry.status_ts?.let {
//                            val parsedDate = inputFormat.parse(it)
//                            outputFormat.format(parsedDate!!)
//                        } ?: "Unknown time"
//                    } catch (e: Exception) {
//                        entry.status_ts ?: "Unknown time"
//                    }
//
//                    val avatarBitmap = entry.avatar_data?.takeIf { it.isNotBlank() }?.let {
//                        try {
//                            base64ToBitmap(it)
//                        } catch (e: Exception) {
//                            null
//                        }
//                    }
//
//                    val itemLayout = LinearLayout(context).apply {
//                        orientation = LinearLayout.HORIZONTAL
//                        setPadding(0, 12, 0, 12)
//                    }
//
//                    val avatarView = ImageView(context).apply {
//                        layoutParams = LinearLayout.LayoutParams(100, 100)
//                        setImageResource(R.drawable.ic_default_profile)
//                        avatarBitmap?.let { setImageBitmap(it) }
//                    }
//
//                    val infoView = TextView(context).apply {
//                        text = "👤 $name\n📍 ${entry.status}\n🕒 $formattedTime"
//                        setPadding(20, 0, 0, 0)
//                    }
//
//                    itemLayout.addView(avatarView)
//                    itemLayout.addView(infoView)
//                    container.addView(itemLayout)
//                }
//
//                AlertDialog.Builder(context)
//                    .setTitle("Message Status")
//                    .setView(dialogView)
//                    .setPositiveButton("OK", null)
//                    .show()
//
//                true
//            }
//        }
//    }
//
//    fun base64ToBitmap(base64Str: String): Bitmap? {
//        return try {
//            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
//            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
//        } catch (e: Exception) {
//            null
//        }
//    }
//
//inner class ReceivedMessageViewHolder(private val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root) {
//
//    fun bind(msg: Message) {
//        binding.textMessageBody.text = msg.encrypted_body
//        binding.attachmentContainer.removeAllViews()
//
//        msg.attachments.forEach { a ->
//            binding.attachmentContainer.addView(
//                TextView(itemView.context).apply {
//                    text = "Attachment: ${a.file_url}"
//                    setTextColor(0xFF0000FF.toInt())
//                    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
//                    setOnClickListener {
//                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(a.file_url))
//                        itemView.context.startActivity(intent)
//                    }
//                }
//            )
//        }
//
//        // ✅ Use only msg.sender_display_name and msg.sender_avatar_data
//        val name = msg.sender_display_name ?: "Unknown"
//        binding.senderName.text = name
//
//        val avatarBitmap = msg.sender_avatar_data?.takeIf { it.isNotBlank() }?.let {
//            try {
//                base64ToBitmap(it)
//            } catch (e: Exception) {
//                Log.e("AvatarDecode", "Invalid base64 avatar: ${e.message}")
//                null
//            }
//        }
//
//        avatarBitmap?.let {
//            Glide.with(itemView.context)
//                .load(it)
//                .circleCrop()
//                .into(binding.ivSenderAvatar)
//        } ?: run {
//            binding.ivSenderAvatar.setImageResource(R.drawable.ic_default_profile)  // fallback image
//        }
//    }
//}
//
//}
//

package com.codewithram.secretchat.ui.home

import Message
import StatusEntry
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codewithram.secretchat.R
import com.codewithram.secretchat.databinding.ItemMessageReceivedBinding
import com.codewithram.secretchat.databinding.ItemMessageSentBinding
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class ChatAdapter(private val currentUserId: UUID) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    var onMessageRead: ((UUID) -> Unit)? = null
    private val messages = mutableListOf<Message>()

    fun setAll(newMessages: List<Message>) {
        messages.apply {
            clear()
            addAll(newMessages)
        }
        notifyDataSetChanged()
    }

    fun addMessage(msg: Message) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun containsMessageId(messageId: UUID): Boolean =
        messages.any { it.id == messageId }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateMessageStatus(messageId: String, userId: String, newStatus: String) {
        // find the message
        val idx = messages.indexOfFirst { it.id.toString() == messageId }
        if (idx == -1) return

        // find if there's an existing status entry for that user
//        val oldList = messages[idx].status_entries
//        val entryIndex = oldList.indexOfFirst { it.user_id.toString() == userId }

        val oldList = messages[idx].status_entries.orEmpty()
        val entryIndex = oldList.indexOfFirst { it.user_id.toString() == userId }

        // build a fresh StatusEntry
        val updatedEntry = StatusEntry(
            id          = UUID.randomUUID(),
            message_id  = UUID.fromString(messageId),
            user_id     = UUID.fromString(userId),
            status      = newStatus,
            status_ts   = Instant.now().toString(),
            inserted_at = Instant.now().toString(),
            updated_at  = Instant.now().toString(),
            display_name = "",    // or carry over if you have it
            avatar_data  = ""
        )

        // swap it in or append
        val newList = if (entryIndex >= 0) {
            oldList.toMutableList().apply { set(entryIndex, updatedEntry) }
        } else {
            oldList + updatedEntry
        }

        // update and redraw that row
        messages[idx].status_entries = newList
        notifyItemChanged(idx)
    }
    fun updateMessageAt(index: Int, newMessage: Message) {
        messages[index] = newMessage
        notifyItemChanged(index)
    }

    fun findMessageIndexById(messageId: UUID) =
        messages.indexOfFirst { it.id == messageId }

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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SentMessageViewHolder     -> holder.bind(messages[position])
            is ReceivedMessageViewHolder -> holder.bind(messages[position])
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return
        val msg = messages[pos]
        if (msg.sender_id != currentUserId &&
            msg.status_entries?.none { it.user_id == currentUserId && it.status == "read" } ?: true
        ) {
            onMessageRead?.invoke(msg.id)
        }
    }

    inner class SentMessageViewHolder(private val binding: ItemMessageSentBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message) {
            Log.d("messagex", msg.toString())
            binding.textMessageBody.text = msg.encrypted_body

            binding.attachmentContainer.removeAllViews()
            msg.attachments.forEach { a ->
                binding.attachmentContainer.addView(TextView(itemView.context).apply {
                    text = "Attachment: ${a.file_url}"
                    setTextColor(0xFF0000FF.toInt())
                    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    setOnClickListener {
                        itemView.context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(a.file_url))
                        )
                    }
                })
            }

//            val otherStatuses = msg.status_entries
//                .filter { it.user_id != currentUserId }
//                .map { it.status }
            val otherStatuses = msg.status_entries.orEmpty()
                .filter { it.user_id != currentUserId }
                .map { it.status }


            binding.textMessageStatus.text = when {
                otherStatuses.isEmpty() || otherStatuses.any { it == "pending" } -> "✓"
                otherStatuses.all { it == "read" }                              -> "✓✓ (blue)"
                otherStatuses.all { it == "delivered" }                         -> "✓✓"
                otherStatuses.all { it in setOf("delivered","read") }           -> "✓✓"
                else                                                            -> "✓"
            }

            itemView.setOnLongClickListener {
                showStatusDialog(msg)
                true
            }
        }

        private fun showStatusDialog(msg: Message) {
            val ctx = itemView.context
            val dialogView = LayoutInflater.from(ctx)
                .inflate(R.layout.dialog_message_status, null)
            val container = dialogView.findViewById<LinearLayout>(R.id.statusContainer)

            val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
            val outFmt = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())

            msg.status_entries.orEmpty().forEach { e ->

            if (e.user_id == currentUserId) return@forEach

                val name = e.display_name?.takeIf(String::isNotBlank) ?: "Unknown"
                val time = runCatching {
                    e.status_ts?.let { outFmt.format(inFmt.parse(it)!!) }
                }.getOrNull() ?: "Unknown time"

                val avatarBmp = e.avatar_data?.takeIf(String::isNotBlank)?.let(::base64ToBitmap)

                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0,12,0,12)
                }
                val iv = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(100,100)
                    setImageResource(R.drawable.ic_default_profile)
                    avatarBmp?.let(::setImageBitmap)
                }
                val tv = TextView(ctx).apply {
                    text = "👤 $name\n📍 ${e.status}\n🕒 $time"
                    setPadding(20,0,0,0)
                }
                row.addView(iv)
                row.addView(tv)
                container.addView(row)
            }

            AlertDialog.Builder(ctx)
                .setTitle("Message Status")
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    inner class ReceivedMessageViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message) {
            binding.textMessageBody.text = msg.encrypted_body

            binding.attachmentContainer.removeAllViews()
            msg.attachments.forEach { a ->
                binding.attachmentContainer.addView(TextView(itemView.context).apply {
                    text = "Attachment: ${a.file_url}"
                    setTextColor(0xFF0000FF.toInt())
                    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    setOnClickListener {
                        itemView.context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(a.file_url))
                        )
                    }
                })
            }

            // show sender name & avatar directly from Message
            binding.senderName.text = msg.sender_display_name ?: "Unknown"
            val bmp = msg.sender_avatar_data
                ?.takeIf(String::isNotBlank)
                ?.let(::base64ToBitmap)

            if (bmp != null) {
                Glide.with(itemView.context)
                    .load(bmp)
                    .circleCrop()
                    .into(binding.ivSenderAvatar)
            } else {
                binding.ivSenderAvatar.setImageResource(R.drawable.ic_default_profile)
            }
        }
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
