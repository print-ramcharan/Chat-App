package com.codewithram.secretchat.ui.home

import ConversationDetailsResponse
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codewithram.secretchat.data.Repository
import com.codewithram.secretchat.databinding.FragmentGroupInfoSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch


class GroupInfoBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentGroupInfoSheetBinding
    private val groupId by lazy { arguments?.getString("group_id") ?: "" }

    private lateinit var repo: Repository

    private lateinit var currentUserId: String
    private var isCurrentUserAdmin: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentGroupInfoSheetBinding.inflate(inflater, container, false)
        repo = Repository(requireContext().getSharedPreferences("secret_chat_prefs", 0))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentUserId = repo.userId.toString() // assuming repo has this property
        binding.addMemberButton.setOnClickListener {
            if (isCurrentUserAdmin) {
                // Show UI to add a member (e.g., open dialog to enter user ID or pick user)
                showAddMemberDialog()
            } else {
                Toast.makeText(requireContext(), "Only admins can add members", Toast.LENGTH_SHORT).show()
            }
        }
        loadConversationDetails()
    }

private fun loadConversationDetails() {
    binding.progressBar.visibility = View.VISIBLE

    lifecycleScope.launch {
        try {
            val details: ConversationDetailsResponse? = repo.getConversationDetails(groupId)

            binding.progressBar.visibility = View.GONE

            if (details == null) {
                Toast.makeText(requireContext(), "Conversation details not found", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val users = details.members.map { member ->
                User(
                    id = member.id,
                    username = member.username,
                    avatarUrl = member.avatar_url
                )
            }

            val adminIds = details.members.filter { it.is_admin }.map { it.id }
            isCurrentUserAdmin = adminIds.contains(currentUserId)

            // Show or hide "Add Member" button based on admin status
            binding.addMemberButton.visibility = if (isCurrentUserAdmin) View.VISIBLE else View.GONE

            binding.membersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.membersRecyclerView.adapter = GroupMemberAdapter(
                members = users,
                adminIds = adminIds,
                groupCreatorId = details.created_by,
                currentUserId = currentUserId,
                isCurrentUserAdmin = isCurrentUserAdmin,
                onMakeAdmin = { user -> makeUserAdminApiCall(user.id) },
                onRemoveAdmin = { user -> removeUserAdminApiCall(user.id)},
                    onDeleteMember = { user -> showDeleteMemberConfirmation(user) }
            )

            binding.groupNameTextView.text = details.group_name ?: "Group Members"
            if (!details.group_avatar_url.isNullOrEmpty()) {
                // Load avatar if needed
            }

        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Failed to load conversation details", Toast.LENGTH_SHORT).show()
        }
    }
}

    private fun showAddMemberDialog() {
        val inputEditText = EditText(requireContext()).apply {
            hint = "Enter username or user ID"
            setPadding(32, 24, 32, 24)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Member")
            .setView(inputEditText)
            .setPositiveButton("Add") { _, _ ->
                val userInput = inputEditText.text.toString().trim()
                if (userInput.isNotEmpty()) {
                    addMemberApiCall(userInput)
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid username or ID", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun showDeleteMemberConfirmation(user: User) {
        Log.d("userId", user.toString())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Member")
            .setMessage("Are you sure you want to remove ${user.username}?")
            .setPositiveButton("Remove") { _, _ ->
                deleteMemberApiCall(user.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMemberApiCall(userId: String) {
        lifecycleScope.launch {
            try {
                repo.removeMemberFromConversation(groupId, userId)
                Toast.makeText(requireContext(), "Member removed", Toast.LENGTH_SHORT).show()
                loadConversationDetails()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to remove member", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addMemberApiCall(userIdOrUsername: String) {
        lifecycleScope.launch {
            try {
                // Assuming your repo function expects userId, adapt if username needs resolving first
                repo.addMemberToConversation(groupId, userIdOrUsername)
                Toast.makeText(requireContext(), "Member added successfully", Toast.LENGTH_SHORT).show()
                loadConversationDetails() // Refresh members list
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to add member: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun removeUserAdminApiCall(userId: String) {
        lifecycleScope.launch {
            try {
                repo.removeUserAdmin(groupId, userId)
                Toast.makeText(requireContext(), "User removed as admin", Toast.LENGTH_SHORT).show()
                loadConversationDetails() // Refresh list after change
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to remove admin", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun makeUserAdminApiCall(userId: String) {
        lifecycleScope.launch {
            try {
                repo.makeUserAdmin(groupId, userId)
                Toast.makeText(requireContext(), "User made admin", Toast.LENGTH_SHORT).show()
                loadConversationDetails() // Refresh members after admin change
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to make admin", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
