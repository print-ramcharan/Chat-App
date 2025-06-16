package com.codewithram.secretchat.ui.home

import Chat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codewithram.secretchat.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.*

class UserAdapter(
    private var chats: List<Chat>,
    private val onClick: (Chat) -> Unit
) : RecyclerView.Adapter<UserAdapter.ChatViewHolder>() {

    fun updateData(newChats: List<Chat>) {
        chats = newChats
        notifyDataSetChanged()
    }

    inner class ChatViewHolder(private val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chat: Chat) {
            binding.chatName.text = chat.name
            binding.lastMessage.text = chat.lastMessage

            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            binding.timestamp.text = sdf.format(Date(chat.lastTimestamp))

            binding.unreadCount.visibility = if (chat.unreadCount > 0) View.VISIBLE else View.GONE
            binding.unreadCount.text = chat.unreadCount.toString()

            binding.root.setOnClickListener { onClick(chat) }
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
