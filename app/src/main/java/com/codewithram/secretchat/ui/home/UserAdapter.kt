package com.codewithram.secretchat.ui.home

import Chat
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codewithram.secretchat.R
import com.codewithram.secretchat.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserAdapter(
    internal var chats: MutableList<Chat>,
    private val onClick: (Chat) -> Unit
) : RecyclerView.Adapter<UserAdapter.ChatViewHolder>() {

    fun updateData(newChats: List<Chat>) {
        chats = newChats.toMutableList()
        notifyDataSetChanged()
    }
    fun updateChatMessage(conversationId: String, newLastMessage: String) {
        val index = chats.indexOfFirst { it.id.toString() == conversationId }
        if (index != -1) {
            val oldChat = chats[index]
            val updatedChat = oldChat.copy(
                lastMessage = newLastMessage,
                unreadCount = oldChat.unreadCount + 1
            )
            chats[index] = updatedChat
            notifyItemChanged(index)
        } else {
            // Optional: If chat not found, you could add it or ignore
            Log.w("UserAdapter", "Chat with id $conversationId not found to update")
        }
    }

    // Reset unread count to 0 when user opens chat (optional)
    fun resetUnreadCount(conversationId: String) {
        val index = chats.indexOfFirst { it.id.toString() == conversationId }
        if (index != -1) {
            val oldChat = chats[index]
            if (oldChat.unreadCount != 0) {
                val updatedChat = oldChat.copy(unreadCount = 0)
                chats[index] = updatedChat
                notifyItemChanged(index)
            }
        }
    }

    inner class ChatViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            binding.chatName.text = chat.name
            binding.lastMessage.text = chat.lastMessage

            if (chat.isSentByCurrentUser) {
                binding.messageStatusIcon.visibility = View.VISIBLE

                val (iconRes, tintColorRes, contentDescription) = when (chat.messageStatus) {
                    "read" -> Triple(R.drawable.ic_done_all, R.color.purple_500, "Read")
                    "delivered" -> Triple(R.drawable.ic_done, R.color.gray_500, "Delivered")
                    "sent" -> Triple(R.drawable.check_40px, R.color.gray_400, "Sent")
                    else -> Triple(R.drawable.check_40px, R.color.gray_400, "Sent")
                }
//                val (iconRes, tintColorRes, contentDescription) = when (chat.messageStatus) {
//                    "read" -> Triple(R.drawable.ic_done_all, R.color.purple_500, "Read")
//                    "delivered" -> Triple(R.drawable.ic_done, R.color.gray_500, "Delivered")
//                    "sent" -> Triple(R.drawable.ic_sent, R.color.gray_400, "Sent")
//                    else -> Triple(R.drawable.ic_sent, R.color.gray_400, "Sent")
//                }

// Set icon and color
                binding.messageStatusIcon.setImageResource(iconRes)
                binding.messageStatusIcon.setColorFilter(
                    ContextCompat.getColor(binding.root.context, tintColorRes),
                    PorterDuff.Mode.SRC_IN
                )
                binding.messageStatusIcon.contentDescription = contentDescription

// Adjust icon size if status is "sent"
//                val sizeInDp = if (chat.messageStatus == "sent") 28 else 24
//                val density = binding.root.context.resources.displayMetrics.density
//                val sizeInPx = (sizeInDp * density).toInt()

//                binding.messageStatusIcon.layoutParams = binding.messageStatusIcon.layoutParams.apply {
//                    width = sizeInPx
//                    height = sizeInPx
//                }


//                binding.messageStatusIcon.setImageResource(iconRes)
//                binding.messageStatusIcon.setColorFilter(
//                    ContextCompat.getColor(binding.root.context, tintColorRes),
//                    PorterDuff.Mode.SRC_IN
//                )
//                binding.messageStatusIcon.contentDescription = contentDescription
            } else {
                binding.messageStatusIcon.visibility = View.GONE
            }


            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            binding.timestamp.text = sdf.format(Date(chat.lastTimestamp))

            binding.chatAvatar.visibility = View.VISIBLE

            var avatar_url = chat.avatarBase64
            avatar_url = avatar_url?.substringAfter("base64,", avatar_url.toString())

            if (!avatar_url.isNullOrEmpty()) {
                val bitmap = decodeBase64ToBitmap(avatar_url)

                if (!chat.avatarBase64.isNullOrBlank()) {
                    val base64Image = "data:image/png;base64,${chat.avatarBase64}"

                    Glide.with(binding.root.context)
                        .load(base64Image)
                        .placeholder(R.drawable.account_circle)
                        .error(R.drawable.account_circle_off)
                        .into(binding.chatAvatar)
                } else {
                    binding.chatAvatar.setImageResource(R.drawable.ic_exit_group)
                }

                if (bitmap != null) {
                    val drawable =
                        RoundedBitmapDrawableFactory.create(binding.root.resources, bitmap)
                            .apply {
                                isCircular = true
                            }
                    binding.chatAvatar.setImageDrawable(drawable)

                    binding.chatAvatar.setOnClickListener {
                        showPreviewPopup(binding.root.context, bitmap)
                    }
                } else {
                    binding.chatAvatar.setImageResource(R.drawable.account_circle_off)
                }

            } else {
                binding.chatAvatar.setImageResource(R.drawable.account_circle)
            }

//            binding.unreadCount.apply {
//                visibility = if (chat.unreadCount > 0) View.VISIBLE else View.GONE
//                text = chat.unreadCount.toString()
//            }
            binding.unreadCountContainer.visibility = if (chat.unreadCount > 0) View.VISIBLE else View.GONE
            binding.unreadCount.text = chat.unreadCount.toString()


            binding.root.setOnClickListener { onClick(chat) }
        }
    }

    private fun showPreviewPopup(context: Context, bitmap: Bitmap?) {
        if (bitmap == null) {
            Toast.makeText(context, "Unable to load image", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(context)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val imageView = ImageView(context).apply {
            setImageBitmap(bitmap)
            layoutParams = FrameLayout.LayoutParams(600, 600, Gravity.CENTER)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(context, R.drawable.avatar_background)
            clipToOutline = true
            setOnClickListener {
                dialog.dismiss()
                showFullImagePopup(context, bitmap)
            }
        }

        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#80000000"))
            setOnClickListener { dialog.dismiss() }
            addView(imageView)
        }

        dialog.setContentView(container)
        dialog.show()
    }

    private fun showFullImagePopup(context: Context, bitmap: Bitmap) {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val imageView = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }

        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            addView(imageView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ))
            setOnClickListener { dialog.dismiss() }
        }

        dialog.setContentView(container)
        dialog.show()
    }

    private fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            val normalized = base64String.trim()
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            val decodedBytes = Base64.decode(padded, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e("AvatarDecode", "Failed to decode avatar", e)
            null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount() = chats.size
}
