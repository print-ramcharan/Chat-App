//package com.codewithram.secretchat.ui.home
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Button
//import android.widget.TextView
//import androidx.recyclerview.widget.RecyclerView
//import com.codewithram.secretchat.R
//
//data class User(
//    val  id: String,
//    val username: String,
//    val avatarUrl : String?,
//)
//class GroupMemberAdapter(
//    private val members: List<User>,
//    private val adminIds: List<String>,
//    private val groupCreatorId: String,
//    private val currentUserId: String,
//    private val isCurrentUserAdmin: Boolean,
//    private val onMakeAdmin: (User) -> Unit,
//    private val onRemoveAdmin: (User) -> Unit,
//    private val onDeleteMember: (User) -> Unit
//) : RecyclerView.Adapter<GroupMemberAdapter.ViewHolder>() {
//
//    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val nameText = view.findViewById<TextView>(R.id.memberName)
//        val roleText = view.findViewById<TextView>(R.id.memberRole)
//        val makeAdminBtn = view.findViewById<Button>(R.id.makeAdminBtn)
//    }
//
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_group_member, parent, false)
//        return ViewHolder(view)
//    }
//
//    override fun getItemCount() = members.size
//
//
//override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//    val user = members[position]
//    val isAdmin = adminIds.contains(user.id)
//    val isCreator = user.id == groupCreatorId
//    val isCurrentUser = user.id == currentUserId
//
//    // Display name with "You"
//    var displayName = user.username
//    if (isCurrentUser) displayName += " (You)"
//    holder.nameText.text = displayName
//
//    // Role label
//    holder.roleText.text = when {
//        isCreator -> "Creator"
//        isAdmin -> "Admin"
//        else -> "Member"
//    }
//
//    if (isCurrentUserAdmin && !isCreator && !isCurrentUser) {
//        holder.makeAdminBtn.visibility = View.VISIBLE
//        if (isAdmin) {
//            holder.makeAdminBtn.text = "Remove Admin"
//            holder.makeAdminBtn.setOnClickListener { onRemoveAdmin(user) }
//        } else {
//            holder.makeAdminBtn.text = "Make Admin"
//            holder.makeAdminBtn.setOnClickListener { onMakeAdmin(user) }
//        }
//    } else {
//        holder.makeAdminBtn.visibility = View.GONE
//    }
//}
//
//}

package com.codewithram.secretchat.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.codewithram.secretchat.R

data class User(
    val id: String,
    val username: String,
    val avatarUrl: String?,
)

class GroupMemberAdapter(
    private val members: List<User>,
    private val adminIds: List<String>,
    private val groupCreatorId: String,
    private val currentUserId: String,
    private val isCurrentUserAdmin: Boolean,
    private val onMakeAdmin: (User) -> Unit,
    private val onRemoveAdmin: (User) -> Unit,
    private val onDeleteMember: (User) -> Unit
) : RecyclerView.Adapter<GroupMemberAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.memberName)
        val roleText: TextView = view.findViewById(R.id.memberRole)
        val adminBtn: Button = view.findViewById(R.id.makeAdminBtn)
        val deleteBtn: Button = view.findViewById(R.id.deleteBtn)
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

        holder.nameText.text = if (isMe) "${user.username} (You)" else user.username

        holder.roleText.text = when {
            isCreator -> "Creator"
            isAdmin -> "Admin"
            else -> "Member"
        }

        // Admin button logic
        if (isCurrentUserAdmin && !isCreator && !isMe) {
            holder.adminBtn.visibility = View.VISIBLE
            holder.adminBtn.text = if (isAdmin) "Remove Admin" else "Make Admin"
            holder.adminBtn.setOnClickListener {
                if (isAdmin) onRemoveAdmin(user) else onMakeAdmin(user)
            }
        } else {
            holder.adminBtn.visibility = View.GONE
        }

        // Delete button logic
        if (isCurrentUserAdmin && !isMe && !isCreator) {
            holder.deleteBtn.visibility = View.VISIBLE
            holder.deleteBtn.setOnClickListener {
                onDeleteMember(user)
            }
        } else {
            holder.deleteBtn.visibility = View.GONE
        }
    }
}
