package com.codewithram.secretchat.ui.home

import ConversationDetailsResponse
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codewithram.secretchat.R
import com.codewithram.secretchat.data.Repository
import com.codewithram.secretchat.databinding.FragmentGroupInfoSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.codewithram.secretchat.MainActivity


class GroupInfoBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentGroupInfoSheetBinding
    private val groupId by lazy { arguments?.getString("group_id") ?: "" }

    private lateinit var repo: Repository

    private lateinit var currentUserId: String
    private var isCurrentUserAdmin: Boolean = false
    private lateinit var imagePicker: ActivityResultLauncher<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentGroupInfoSheetBinding.inflate(inflater, container, false)
        repo = Repository(requireContext().getSharedPreferences("secret_chat_prefs", 0))
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

         imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                Log.d("AvatarUpload", "Selected URI: $uri")

                Glide.with(this).load(uri).circleCrop().into(binding.groupImageView)

                try {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    Log.d("AvatarUpload", "InputStream is null: ${inputStream == null}")

                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    Log.d("AvatarUpload", "Bitmap is null: ${originalBitmap == null}")

                    // ✅ Resize the bitmap to max 512x512 to reduce size
                    val resizedBitmap = resizeBitmap(originalBitmap, 512)

                    // ✅ Compress the resized bitmap (80% quality for JPEG)
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                    val byteArray = byteArrayOutputStream.toByteArray()
                    Log.d("AvatarUpload", "Compressed ByteArray size: ${byteArray.size}")

                    val base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                    Log.d("AvatarUpload", "Base64 (first 100 chars): ${base64Image.take(100)}...")

                    lifecycleScope.launch {
                         repo.updateGroupAvatar(groupId, base64Image)
                        Toast.makeText(requireContext(), "Group avatar updated", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.setFragmentResult("group_update", Bundle().apply {
                            putString("updated_avatar_base64", base64Image)
                        })
                        binding.groupImageView.setImageBitmap(resizedBitmap)
//                        loadConversationDetails()
                    }
                } catch (e: Exception) {
                    Log.e("AvatarUpload", "Exception while encoding image", e)
                }
            } else {
                Log.w("AvatarUpload", "No URI selected")
            }
        }
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

    private fun pickImageFromGallery() {
        imagePicker.launch("image/*")
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
            Log.d("dataConva", details.toString())

            val users = details.members.map { member ->
                User(
                    id = member.id,
                    username = member.username,
                    avatarUrl = member.avatar_url
                )
            }

            val adminIds = details.members.filter { it.is_admin }.map { it.id }
            isCurrentUserAdmin = adminIds.contains(currentUserId)

            binding.addMemberButton.visibility = if (isCurrentUserAdmin) View.VISIBLE else View.GONE
            binding.editGroupAvatarIcon.visibility = if (isCurrentUserAdmin) View.VISIBLE else View.GONE

            binding.editGroupNameIcon.visibility = if(isCurrentUserAdmin) View.VISIBLE else View.GONE
            binding.editGroupAvatarIcon.setOnClickListener {
                pickImageFromGallery()
            }
            binding.editGroupNameIcon.setOnClickListener {
                if (!isCurrentUserAdmin) return@setOnClickListener

                val editText = EditText(requireContext()).apply {
                    hint = "Enter new group name"
                    setText(binding.groupNameTextView.text.toString())
                    setPadding(32, 24, 32, 24)
                    inputType = InputType.TYPE_CLASS_TEXT
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Edit Group Name")
                    .setView(editText)
                    .setPositiveButton("Update") { _, _ ->
                        val newName = editText.text.toString().trim()
                        if (newName.isNotEmpty()) {
                            updateGroupName(newName)
                        } else {
                            Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }



            if (details.is_group) {
                binding.membersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                binding.membersRecyclerView.adapter = GroupMemberAdapter(
                    members = users,
                    adminIds = adminIds,
                    groupCreatorId = details.created_by,
                    currentUserId = currentUserId,
                    isCurrentUserAdmin = isCurrentUserAdmin,
                    onMakeAdmin = { user -> makeUserAdminApiCall(user.id) },
                    onRemoveAdmin = { user -> removeUserAdminApiCall(user.id)},
                    onDeleteMember = { user -> showDeleteMemberConfirmation(user) },
                    onAvatarClicked = { bitmap -> showPreviewPopup(requireContext(), bitmap) }

                     )
                Log.d("avatarData", details.group_avatar_url.toString())
                Log.d("avatarData", details.members.toString())

                binding.membersRecyclerView.visibility = View.VISIBLE
            } else {
                binding.membersRecyclerView.visibility = View.GONE
            }


//            binding.groupNameTextView.text = details.group_name ?: "Group Members"
            if (details.is_group) {
                binding.groupNameTextView.text = details.group_name ?: "Group Members"
                binding.groupNameTextView.isClickable = isCurrentUserAdmin
            } else {
                val other = details.members.firstOrNull { it.id != currentUserId }
                binding.groupNameTextView.text = other?.username ?: "Private Chat"
                binding.groupNameTextView.isClickable = false
            }


            if (!details.is_group) {
                binding.addMemberButton.visibility = View.GONE
                }
            if (!details.group_avatar_url.isNullOrEmpty()) {
                val bitmap = decodeBase64ToBitmap(details.group_avatar_url)
                if (bitmap != null) {
                    Log.d("AvatarDecode", "Bitmap decoding successful")
                    val drawable = RoundedBitmapDrawableFactory.create(resources, bitmap).apply {
                        isCircular = true
                    }
                    binding.groupImageView.setImageDrawable(drawable)

                    binding.groupImageView.setOnClickListener {
                        showPreviewPopup(requireContext(), bitmap)
                    }
                } else {
                    Log.e("AvatarDecode", "Bitmap decoding returned null")
                    binding.groupImageView.setImageResource(R.drawable.ic_default_profile)
                }
            } else {
                binding.groupImageView.setImageResource(R.drawable.ic_default_profile)
            }





        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Failed to load conversation details", Toast.LENGTH_SHORT).show()
        }
    }
}

    private fun updateGroupName(newName: String) {
        lifecycleScope.launch {
            try {
                repo.updateGroupName(groupId, newName)
                Toast.makeText(requireContext(), "Group name updated", Toast.LENGTH_SHORT).show()
//                loadConversationDetails()
                binding.groupNameTextView.text = newName
                parentFragmentManager.setFragmentResult("group_update", Bundle().apply {
                    putString("updated_name", newName)
                })
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to update name", Toast.LENGTH_SHORT).show()
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
