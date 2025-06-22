package com.codewithram.secretchat.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.recyclerview.widget.RecyclerView
import com.codewithram.secretchat.R
import com.google.gson.annotations.SerializedName
//
data class User(
    val id: String,
    val username: String,
    @SerializedName("avatar_data")
    val avatarUrl: String?,
)
//
//class GroupMemberAdapter(
//    private val members: List<User>,
//    private val adminIds: List<String>,
//    private val groupCreatorId: String,
//    private val currentUserId: String,
//    private val isCurrentUserAdmin: Boolean,
//    private val onMakeAdmin: (User) -> Unit,
//    private val onRemoveAdmin: (User) -> Unit,
//    private val onDeleteMember: (User) -> Unit,
//    private val onAvatarClicked: ((Bitmap) -> Unit)? = null
//
//) : RecyclerView.Adapter<GroupMemberAdapter.ViewHolder>() {
//
//    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val nameText: TextView = view.findViewById(R.id.memberName)
//        val roleText: TextView = view.findViewById(R.id.memberRole)
//        val adminBtn: Button = view.findViewById(R.id.makeAdminBtn)
//        val deleteBtn: Button = view.findViewById(R.id.deleteBtn)
//        val avatarImage: ImageView = view.findViewById(R.id.memberProfile)
//
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_group_member, parent, false)
//        return ViewHolder(view)
//    }
//
//    override fun getItemCount(): Int = members.size
//
//    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//        val user = members[position]
//        val isAdmin = adminIds.contains(user.id)
//        val isCreator = user.id == groupCreatorId
//        val isMe = user.id == currentUserId
//
//        holder.avatarImage.setImageResource(R.drawable.ic_default_profile)
//
//        if (!user.avatarUrl.isNullOrEmpty()) {
//            try {
//                val imageBytes = Base64.decode(user.avatarUrl, Base64.DEFAULT)
//                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
//
//                val drawable = RoundedBitmapDrawableFactory.create(holder.itemView.resources, bitmap).apply {
//                    isCircular = true
//                }
//
//                holder.avatarImage.setImageDrawable(drawable)
//
//                holder.avatarImage.setOnClickListener {
//                    onAvatarClicked?.invoke(bitmap)
//                }
//
//            } catch (e: Exception) {
//                holder.avatarImage.setImageResource(R.drawable.ic_default_profile)
//            }
//        }
//
//        holder.nameText.text = if (isMe) "${user.username} (You)" else user.username
//
//        holder.roleText.text = when {
//            isCreator -> "Creator"
//            isAdmin -> "Admin"
//            else -> "Member"
//        }
//
//        // Admin button logic
//        if (isCurrentUserAdmin && !isCreator && !isMe) {
//            holder.adminBtn.visibility = View.VISIBLE
//            holder.adminBtn.text = if (isAdmin) "Remove Admin" else "Make Admin"
//            holder.adminBtn.setOnClickListener {
//                if (isAdmin) onRemoveAdmin(user) else onMakeAdmin(user)
//            }
//        } else {
//            holder.adminBtn.visibility = View.GONE
//        }
//
//        // Delete button logic
//        if (isCurrentUserAdmin && !isMe && !isCreator) {
//            holder.deleteBtn.visibility = View.VISIBLE
//            holder.deleteBtn.setOnClickListener {
//                onDeleteMember(user)
//            }
//        } else {
//            holder.deleteBtn.visibility = View.GONE
//        }
//    }
//}

class GroupMemberAdapter(
    private val members: List<User>,
    private val adminIds: List<String>,
    private val groupCreatorId: String,
    private val currentUserId: String,
    private val isCurrentUserAdmin: Boolean,
    private val onMakeAdmin: (User) -> Unit,
    private val onRemoveAdmin: (User) -> Unit,
    private val onDeleteMember: (User) -> Unit,
    private val onAvatarClicked: ((Bitmap) -> Unit)? = null
) : RecyclerView.Adapter<GroupMemberAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.memberName)
        val roleText: TextView = view.findViewById(R.id.memberRole)
        val avatarImage: ImageView = view.findViewById(R.id.memberProfile)
        val adminIcon: ImageView = view.findViewById(R.id.makeAdminIcon)
        val deleteIcon: ImageView = view.findViewById(R.id.deleteIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = members.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = members[position]
        val isAdmin = adminIds.contains(user.id)
        val isCreator = user.id == groupCreatorId
        val isMe = user.id == currentUserId

        holder.avatarImage.setImageResource(R.drawable.ic_default_profile)

        if (!user.avatarUrl.isNullOrEmpty()) {
            try {
                val imageBytes = Base64.decode(user.avatarUrl, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                val drawable = RoundedBitmapDrawableFactory.create(holder.itemView.resources, bitmap).apply {
                    isCircular = true
                }

                holder.avatarImage.setImageDrawable(drawable)
                holder.avatarImage.setOnClickListener { onAvatarClicked?.invoke(bitmap) }
            } catch (e: Exception) {
                holder.avatarImage.setImageResource(R.drawable.ic_default_profile)
            }
        }

        holder.nameText.text = if (isMe) "${user.username} (You)" else user.username
        holder.roleText.text = when {
            isCreator -> "Creator"
            isAdmin -> "Admin"
            else -> "Member"
        }

        // Admin Icon logic
        if (isCurrentUserAdmin && !isCreator && !isMe) {
            holder.adminIcon.visibility = View.VISIBLE
            holder.adminIcon.setImageResource(
                if (isAdmin) R.drawable.ic_remove_admin else R.drawable.ic_admin
            )
            holder.adminIcon.setOnClickListener {
                if (isAdmin) onRemoveAdmin(user) else onMakeAdmin(user)
            }
        } else {
            holder.adminIcon.visibility = View.GONE
        }

        // Delete Icon logic
        if (isCurrentUserAdmin && !isCreator && !isMe) {
            holder.deleteIcon.visibility = View.VISIBLE
            holder.deleteIcon.setOnClickListener { onDeleteMember(user) }
        } else {
            holder.deleteIcon.visibility = View.GONE
        }
    }
}

