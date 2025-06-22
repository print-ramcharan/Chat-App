package com.codewithram.secretchat.ui.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codewithram.secretchat.R

enum class Section {
    FRIENDS,
    DISCOVER,
    PENDING,
    MUTUAL
}

class UserAdapter(
    private val section: Section,
    private val onActionClick: (User, String) -> Unit
) : ListAdapter<User, UserAdapter.UserViewHolder>(DiffCallback()) {

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.text_name)
        private val avatar: ImageView = view.findViewById(R.id.avatar)
        private val actionButton: ImageButton = view.findViewById(R.id.button_action)

        private fun icon(code: String): Int = when (code) {
            "add" -> R.drawable.ic_add_friend
            "accept" -> R.drawable.ic_approve
            "unfriend" -> R.drawable.ic_delete
            "pending" -> R.drawable.ic_pending_updated
            else -> R.drawable.ic_pending
        }

        private fun setButtonIcon(button: ImageButton, drawableRes: Int) {
            button.setImageResource(drawableRes)
            button.background = null
        }

        fun bind(user: User) {
            nameText.text = user.displayName
            if (!user.avatarData.isNullOrEmpty()) {
                try {
                    val base64Prefix = "base64,"
                    val pureBase64 = if (user.avatarData.contains(base64Prefix)) {
                        user.avatarData.substringAfter(base64Prefix)
                    } else {
                        user.avatarData
                    }

                    val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)

                    if (decodedBytes.isNotEmpty()) {
                        val originalBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        originalBitmap?.let {
                            val resizedBitmap = resizeBitmap(it, 128)
                            Glide.with(itemView.context)
                                .load(resizedBitmap)
                                .placeholder(R.drawable.account_circle)
                                .circleCrop()
                                .into(avatar)
                        } ?: run {
                            avatar.setImageResource(R.drawable.account_circle)
                        }
                    } else {
                        avatar.setImageResource(R.drawable.account_circle)
                    }
                } catch (e: Exception) {
                   avatar.setImageResource(R.drawable.account_circle)
                }
            } else {
                avatar.setImageResource(R.drawable.account_circle)
            }

            // Set icon and behavior
            when (section) {
                Section.FRIENDS -> {
                    setButtonIcon(actionButton, icon("unfriend"))
                    actionButton.isEnabled = true
                    actionButton.setOnClickListener { onActionClick(user, "unfriend") }
                }
                Section.PENDING -> {
                    setButtonIcon(actionButton, icon("accept"))
                    actionButton.isEnabled = true
                    actionButton.setOnClickListener { onActionClick(user, "accept") }
                }
                Section.DISCOVER -> {
                    setButtonIcon(actionButton, icon("add"))
                    actionButton.isEnabled = true
                    actionButton.setOnClickListener { onActionClick(user, "add") }
                }
                Section.MUTUAL -> {
                    when (user.friendshipStatus ?: FriendshipStatus.NONE) {
                        FriendshipStatus.FRIEND -> {
                            setButtonIcon(actionButton, icon("unfriend"))
                            actionButton.isEnabled = true
                            actionButton.setOnClickListener { onActionClick(user, "unfriend") }
                        }
                        FriendshipStatus.SENT -> {
                            setButtonIcon(actionButton, icon("pending"))
                            actionButton.isEnabled = false
                            actionButton.setOnClickListener(null)
                        }
                        FriendshipStatus.RECEIVED -> {
                            setButtonIcon(actionButton, icon("accept"))
                            actionButton.isEnabled = true
                            actionButton.setOnClickListener { onActionClick(user, "accept") }
                        }
                        FriendshipStatus.NONE, null -> {
                            setButtonIcon(actionButton, icon("add"))
                            actionButton.isEnabled = true
                            actionButton.setOnClickListener { onActionClick(user, "add") }
                        }
                    }
                }
            }
        }

        private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
            return Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = oldItem == newItem
    }
}
