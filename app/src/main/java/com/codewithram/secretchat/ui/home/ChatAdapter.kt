package com.codewithram.secretchat.ui.home

import Message
import StatusEntry
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codewithram.secretchat.R
import com.codewithram.secretchat.databinding.ItemMessageReceivedBinding
import com.codewithram.secretchat.databinding.ItemMessageSentBinding
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class ChatAdapter(private val currentUserId: UUID) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    internal var isPrivate: Boolean = false
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
//    fun updateMessageAt(index: Int, newMessage: Message) {
//        messages[index] = newMessage
//        notifyItemChanged(index)
//    }
    fun updateMessageAt(index: Int, newMessage: Message) {
        if (index in messages.indices) {
            messages[index] = newMessage
            notifyItemChanged(index)
        }
    }
    fun indexOfFirst(predicate: (Message) -> Boolean): Int {
        return messages.indexOfFirst(predicate)
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

    inner class SentMessageViewHolder(private val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message) {
            Log.d("messagex", msg.status_entries.toString())
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

            val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            inFmt.timeZone = TimeZone.getTimeZone("UTC")

            val outFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

            val formattedTime = runCatching {
                outFmt.format(inFmt.parse(msg.inserted_at)!!)
            }.getOrElse {
                "??:??"
            }

            binding.textMessageTime.text = formattedTime


            binding.textMessageTime.text = formattedTime
            val otherStatuses = msg.status_entries.orEmpty()
                .filter { it.user_id != currentUserId }
                .map { it.status }


            val statusPriority = listOf("pending", "sent", "delivered", "read")
            val priority = otherStatuses
                .mapNotNull { it.lowercase() }
                .minByOrNull { statusPriority.indexOf(it) }

//            binding.textMessageStatus.text = when (priority) {
//                "pending" -> ".."            // Not yet processed by backend / not sent
//                "sent" -> "✓"               // Sent but not delivered
//                "delivered" -> "✓✓"         // Delivered to all, but not necessarily read
//                "read" -> "✓✓ (blue)"       // All read
//                else -> "something wrong"                 // Fallback
//            }
            val context = itemView.context
            val iconView = binding.imageMessageStatus

            when (priority) {
                "pending" -> {
                    iconView.setImageResource(R.drawable.pending) // Clock or hourglass icon
                    iconView.setColorFilter(ContextCompat.getColor(context, R.color.gray))
                }
                "sent" -> {
                    iconView.setImageResource(R.drawable.check_40px)
                    iconView.setColorFilter(ContextCompat.getColor(context, R.color.gray))

//                    // Increase size for "sent"
//                    val sizeInDp = 28
//                    val density = context.resources.displayMetrics.density
//                    val sizeInPx = (sizeInDp * density).toInt()
//                    iconView.layoutParams = iconView.layoutParams.apply {
//                        width = sizeInPx
//                        height = sizeInPx
//                    }
                }
                "delivered" -> {
                    iconView.setImageResource(R.drawable.ic_done_all) // ✓✓ double tick
                    iconView.setColorFilter(ContextCompat.getColor(context, R.color.gray))
                }
                "read" -> {
                    iconView.setImageResource(R.drawable.ic_done_all) // same ✓✓ icon
                    iconView.setColorFilter(ContextCompat.getColor(context, R.color.purple_500)) // blue tint
                }
                else -> {
                    iconView.setImageDrawable(null) // or fallback icon
                }
            }


            itemView.setOnLongClickListener {
                showStatusDialog(msg)
                true
            }
        }

        private fun showStatusDialog(msg: Message) {
            val ctx = itemView.context
            val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_message_status, null)
            val container = dialogView.findViewById<LinearLayout>(R.id.statusContainer)

            val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val outFmt = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())

            val otherStatuses = msg.status_entries.orEmpty().filter { it.user_id != currentUserId }

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
                        e.status_ts?.let { outFmt.format(inFmt.parse(it)!!) }
                    }.getOrNull() ?: "Unknown time"

                    val avatarBmp = e.avatar_data?.takeIf { it.isNotBlank() }?.let { base64 ->
                        try {
                            val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        } catch (ex: Exception) {
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

                    val (iconRes, tintColorRes, label) = when (e.status?.lowercase()) {
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
                .show()
        }

        private fun makeRow(context: Context, iconRes: Int, text: String, colorRes: Int = R.color.gray): LinearLayout {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
                gravity = Gravity.START
            }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(32, 32)
                if (iconRes != 0) {
                    setImageResource(iconRes)
                    setColorFilter(ContextCompat.getColor(context, colorRes))
                } else {
                    visibility = View.GONE
                }
            }

            val label = TextView(context).apply {
                this.text = text
                textSize = 14f
                setPadding(12, 0, 0, 0)
            }

            row.addView(icon)
            row.addView(label)
            return row
        }


    }
    inner class ReceivedMessageViewHolder(private val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message) {
            binding.textMessageBody.text = msg.encrypted_body

            // Handle attachments
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

            val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            inFmt.timeZone = TimeZone.getTimeZone("UTC")

            val outFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

            val formattedTime = runCatching {
                outFmt.format(inFmt.parse(msg.inserted_at)!!)
            }.getOrElse {
                "??:??"
            }

            binding.textMessageTime.text = formattedTime
            // Check if this is a private conversation
            val isPrivateChat = isPrivate != true // adjust logic if using a different field

            if (isPrivateChat) {
                // Hide avatar and name
                binding.ivSenderAvatar.visibility = View.GONE
                binding.senderName.visibility = View.GONE
            } else {
                // Show avatar and name
                binding.senderName.text = msg.sender_display_name ?: "Unknown"
                binding.senderName.visibility = View.VISIBLE
                binding.ivSenderAvatar.visibility = View.VISIBLE

                val bmp = msg.sender_avatar_data
                    ?.takeIf(String::isNotBlank)
                    ?.let(::base64ToBitmap)

                if (bmp != null) {
                    Glide.with(itemView.context)
                        .load(bmp)
                        .circleCrop()
                        .into(binding.ivSenderAvatar)
                } else {
                    binding.ivSenderAvatar.setImageResource(R.drawable.account_circle)
                }
            }
        }
    }

    fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("avatar", "Decoding error", e)

            null
        }
    }

}
