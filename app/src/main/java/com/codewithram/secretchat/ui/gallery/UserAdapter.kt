//package com.codewithram.secretchat.ui.gallery
//
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.util.Base64
//import android.util.Log
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Button
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.ListAdapter
//import androidx.recyclerview.widget.RecyclerView
//import com.bumptech.glide.Glide
//import com.codewithram.secretchat.R
//
//enum class Section {
//    FRIENDS,
//    DISCOVER,
//    PENDING,
//    MUTUAL
//}
//
//class UserAdapter(
//    private val section: Section,
//    private val onActionClick: (User, String) -> Unit
//) : ListAdapter<User, UserAdapter.UserViewHolder>(DiffCallback()) {
//
//    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        private val nameText: TextView = view.findViewById(R.id.text_name)
//        private val avatar: ImageView = view.findViewById(R.id.avatar)
//        private val actionButton: Button = view.findViewById(R.id.button_action)
//
//        fun bind(user: User) {
//            nameText.text = user.displayName
//
//            if (!user.avatarData.isNullOrEmpty()) {
//                try {
//                    val pureBase64 = user.avatarData.substringAfter("base64,", user.avatarData)
//                    val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
//                    val originalBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
//
//                    val resizedBitmap = resizeBitmap(originalBitmap, 128)
//
//                    Glide.with(itemView.context)
//                        .load(resizedBitmap)
//                        .placeholder(R.drawable.ic_default_profile)
//                        .circleCrop()
//                        .into(avatar)
//
//                } catch (e: Exception) {
//                    Log.e("AvatarLoad", "Failed to decode avatar", e)
//                    avatar.setImageResource(R.drawable.ic_default_profile)
//                }
//            } else {
//                avatar.setImageResource(R.drawable.ic_default_profile)
//            }
//
//            when (section) {
//                Section.FRIENDS -> {
//                    actionButton.text = "Unfriend"
//                    actionButton.visibility = View.VISIBLE
//                    actionButton.setOnClickListener { onActionClick(user, "unfriend") }
//                }
//                Section.DISCOVER -> {
//                    actionButton.text = "Add"
//                    actionButton.visibility = View.VISIBLE
//                    actionButton.setOnClickListener { onActionClick(user, "add") }
//                }
//                Section.PENDING -> {
//                    actionButton.text = "Accept"
//                    actionButton.visibility = View.VISIBLE
//                    actionButton.setOnClickListener { onActionClick(user, "accept") }
//                }
//                Section.MUTUAL -> {
//                    actionButton.text = "Add"
//                    actionButton.visibility = View.VISIBLE
//                    actionButton.setOnClickListener { onActionClick(user, "add") }
//                }
//
//            }
//        }
//    }
//
//    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
//        val width = bitmap.width
//        val height = bitmap.height
//
//        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
//        val scaledWidth = (width * scale).toInt()
//        val scaledHeight = (height * scale).toInt()
//
//        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_friend, parent, false)
//        return UserViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
//        holder.bind(getItem(position))
//
//    }
//
//    class DiffCallback : DiffUtil.ItemCallback<User>() {
//        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean = oldItem.id == newItem.id
//        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = oldItem == newItem
//    }
//}
//
//package com.codewithram.secretchat.ui.gallery
//
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.util.Base64
//import android.util.Log
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Button
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.ListAdapter
//import androidx.recyclerview.widget.RecyclerView
//import com.bumptech.glide.Glide
//import com.codewithram.secretchat.R
//
//enum class Section {
//    FRIENDS,
//    DISCOVER,
//    PENDING,
//    MUTUAL
//}
//
//class UserAdapter(
//    private val section: Section,
//    private val onActionClick: (User, String) -> Unit
//) : ListAdapter<User, UserAdapter.UserViewHolder>(DiffCallback()) {
//
//    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        private val nameText: TextView = view.findViewById(R.id.text_name)
//        private val avatar: ImageView = view.findViewById(R.id.avatar)
//        private val actionButton: Button = view.findViewById(R.id.button_action)
//
//        fun bind(user: User) {
//            nameText.text = user.displayName
//
//            // Handle avatar
//            if (!user.avatarData.isNullOrEmpty()) {
//                try {
//                    val pureBase64 = user.avatarData.substringAfter("base64,", user.avatarData)
//                    val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
//                    val originalBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
//                    val resizedBitmap = resizeBitmap(originalBitmap, 128)
//
//                    Glide.with(itemView.context)
//                        .load(resizedBitmap)
//                        .placeholder(R.drawable.ic_default_profile)
//                        .circleCrop()
//                        .into(avatar)
//
//                } catch (e: Exception) {
//                    Log.e("AvatarLoad", "Failed to decode avatar", e)
//                    avatar.setImageResource(R.drawable.ic_default_profile)
//                }
//            } else {
//                avatar.setImageResource(R.drawable.ic_default_profile)
//            }
//
//            // Set action button based on section or friendshipStatus
//            when (section) {
//                Section.FRIENDS -> {
//                    actionButton.text = "Unfriend"
//                    actionButton.isEnabled = true
//                    actionButton.setOnClickListener { onActionClick(user, "unfriend") }
//                }
//                Section.PENDING -> {
//                    actionButton.text = "Accept"
//                    actionButton.isEnabled = true
//                    actionButton.setOnClickListener { onActionClick(user, "accept") }
//                }
//                Section.DISCOVER, Section.MUTUAL -> {
//                    when (user.friendshipStatus ?: FriendshipStatus.NONE) {
//                        FriendshipStatus.FRIEND -> {
//                            actionButton.text = "Unfriend"
//                            actionButton.isEnabled = true
//                            actionButton.setOnClickListener { onActionClick(user, "unfriend") }
//                        }
//                        FriendshipStatus.SENT -> {
//                            actionButton.text = "Pending"
//                            actionButton.isEnabled = false
//                            actionButton.setOnClickListener(null)
//                        }
//                        FriendshipStatus.RECEIVED -> {
//                            actionButton.text = "Accept"
//                            actionButton.isEnabled = true
//                            actionButton.setOnClickListener { onActionClick(user, "accept") }
//                        }
//                        FriendshipStatus.NONE -> {
//                            actionButton.text = "Add"
//                            actionButton.isEnabled = true
//                            actionButton.setOnClickListener { onActionClick(user, "add") }
//                        }
//                        null -> {
//                            actionButton.text = "Add"
//                            actionButton.isEnabled = true
//                            actionButton.setOnClickListener { onActionClick(user, "add") }
//                        }
//                    }
//
//                }
//            }
//        }
//
//        private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
//            val width = bitmap.width
//            val height = bitmap.height
//            val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
//            val scaledWidth = (width * scale).toInt()
//            val scaledHeight = (height * scale).toInt()
//            return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
//        }
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
//        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
//        return UserViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
//        holder.bind(getItem(position))
//    }
//
//    class DiffCallback : DiffUtil.ItemCallback<User>() {
//        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean = oldItem.id == newItem.id
//        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = oldItem == newItem
//    }
//}

package com.codewithram.secretchat.ui.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        private val actionButton: Button = view.findViewById(R.id.button_action)

        fun bind(user: User) {
            nameText.text = user.displayName

            // Handle avatar
//            if (!user.avatarData.isNullOrEmpty()) {
//                try {
//                    val pureBase64 = user.avatarData.substringAfter("base64,", user.avatarData)
//                    val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
//                    val originalBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
//                    val resizedBitmap = resizeBitmap(originalBitmap, 128)
//
//                    Glide.with(itemView.context)
//                        .load(resizedBitmap)
//                        .placeholder(R.drawable.ic_default_profile)
//                        .circleCrop()
//                        .into(avatar)
//
//                } catch (e: Exception) {
//                    Log.e("AvatarLoad", "Failed to decode avatar", e)
//                    avatar.setImageResource(R.drawable.ic_default_profile)
//                }
//            } else {
//                avatar.setImageResource(R.drawable.ic_default_profile)
//            }

            if (!user.avatarData.isNullOrEmpty()) {
                try {
                    val base64Prefix = "base64,"
                    val pureBase64 = if (user.avatarData.contains(base64Prefix)) {
                        user.avatarData.substringAfter(base64Prefix)
                    } else {
                        user.avatarData // fallback, maybe already raw base64
                    }

                    val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)

                    if (decodedBytes.isNotEmpty()) {
                        val originalBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                        if (originalBitmap != null) {
                            val resizedBitmap = resizeBitmap(originalBitmap, 128)
                            Glide.with(itemView.context)
                                .load(resizedBitmap)
                                .placeholder(R.drawable.ic_default_profile)
                                .circleCrop()
                                .into(avatar)
                        } else {
                            Log.e("AvatarLoad", "Decoded bytes but bitmap is null")
                            avatar.setImageResource(R.drawable.ic_default_profile)
                        }
                    } else {
                        Log.e("AvatarLoad", "Base64 decode returned empty byte array")
                        avatar.setImageResource(R.drawable.ic_default_profile)
                    }
                } catch (e: Exception) {
                    Log.e("AvatarLoad", "Failed to decode avatar", e)
                    avatar.setImageResource(R.drawable.ic_default_profile)
                }
            } else {
                avatar.setImageResource(R.drawable.ic_default_profile)
            }


            // Set button strictly by section (backend decides user list)
            when (section) {
                Section.FRIENDS -> {
                    actionButton.text = "Unfriend"
                    actionButton.isEnabled = true
                    actionButton.setOnClickListener { onActionClick(user, "unfriend") }
                }
                Section.PENDING -> {
                    actionButton.text = "Accept"
                    actionButton.isEnabled = true
                    actionButton.setOnClickListener { onActionClick(user, "accept") }
                }
                Section.DISCOVER -> {
                    actionButton.text = "Add"
                    actionButton.isEnabled = true
                    actionButton.setOnClickListener { onActionClick(user, "add") }
                }
                Section.MUTUAL -> {
                    when (user.friendshipStatus ?: FriendshipStatus.NONE) {
                        FriendshipStatus.FRIEND -> {
                            actionButton.text = "Unfriend"
                            actionButton.isEnabled = true
                            actionButton.setOnClickListener { onActionClick(user, "unfriend") }
                        }
                        FriendshipStatus.SENT -> {
                            actionButton.text = "Pending"
                            actionButton.isEnabled = false
                            actionButton.setOnClickListener(null)
                        }
                        FriendshipStatus.RECEIVED -> {
                            actionButton.text = "Accept"
                            actionButton.isEnabled = true
                            actionButton.setOnClickListener { onActionClick(user, "accept") }
                        }
                        FriendshipStatus.NONE, null -> { // Handle null explicitly!
                            actionButton.text = "Add"
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
            val scaledWidth = (width * scale).toInt()
            val scaledHeight = (height * scale).toInt()
            return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
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
