package com.codewithram.secretchat.ui.gallery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.codewithram.secretchat.data.Repository
import com.codewithram.secretchat.databinding.FragmentFriendsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FriendsFragment : Fragment() {

    private val sharedPrefs by lazy {
        requireContext().getSharedPreferences("secret_chat_prefs", Context.MODE_PRIVATE)
    }

    private val repository by lazy {
        Repository(sharedPrefs)
    }

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!

    private lateinit var friendsAdapter: UserAdapter
    private lateinit var discoverAdapter: UserAdapter
    private lateinit var mutualAdapter: UserAdapter
    private lateinit var pendingAdapter: UserAdapter

    private var allFriends = emptyList<User>()
    private var allPendingRequests = emptyList<User>()
    private var allDiscoverableUsers = emptyList<User>()
    private var allMutualFriends = emptyList<User>()

    private var currentUserId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        currentUserId = sharedPrefs.getString("user_id", null)

        setupRecyclerViews()
        registerReceiver()
        loadAllData()

        return binding.root
    }

    private fun setupRecyclerViews() {
        friendsAdapter = UserAdapter(Section.FRIENDS) { user, action ->
            if (action == "unfriend") {
                lifecycleScope.launch {
                    repository.unfriendUser(user.id)
                        .onSuccess { loadFriends() }
                }
            }
        }

        discoverAdapter = UserAdapter(Section.DISCOVER) { user, action ->
            if (action == "add") {
                lifecycleScope.launch {
                    repository.sendFriendRequest(user.id)
                        .onSuccess {
                            loadPendingRequests()
                            loadFriends()
                            updateDiscoverableStatuses()
                            Toast.makeText(requireContext(), "Friend request sent", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(requireContext(), "Failed to send request", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }

        mutualAdapter = UserAdapter(Section.MUTUAL) { user, action ->
            if (action == "add") {
                lifecycleScope.launch {
                    repository.sendFriendRequest(user.id)
                        .onSuccess {
                            loadPendingRequests()
                            updateDiscoverableStatuses()
                            Toast.makeText(requireContext(), "Friend request sent", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(requireContext(), "Failed to send request", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }

        pendingAdapter = UserAdapter(Section.PENDING) { user, action ->
            if (action == "accept") {
                lifecycleScope.launch {
                    repository.acceptFriendRequest(user.id)
                        .onSuccess {
                            loadPendingRequests()
                            loadFriends()
                            Toast.makeText(requireContext(), "Friend request accepted", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(requireContext(), "Failed to accept request", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = friendsAdapter

        binding.suggestionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.suggestionsRecyclerView.adapter = discoverAdapter

        binding.mutualRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.mutualRecyclerView.adapter = mutualAdapter

        binding.pendingRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.pendingRecyclerView.adapter = pendingAdapter
    }

    private fun loadAllData() {
        loadFriends()
        loadPendingRequests()
        loadDiscoverableUsers()
        loadMutualFriends()
    }

    private fun loadFriends() {
        lifecycleScope.launch {
            repository.getFriends()
                .onSuccess { users ->
                    allFriends = users
                    friendsAdapter.submitList(users)
                    updateDiscoverableStatuses()
                }
                .onFailure { e -> e.printStackTrace() }
        }
    }

    private fun loadPendingRequests() {
        lifecycleScope.launch(Dispatchers.Main) {
            repository.getPendingRequests()
                .onSuccess { users ->
                    allPendingRequests = users
                    val visible = users.isNotEmpty()
                    binding.pendingTitle.visibility = if (visible) View.VISIBLE else View.GONE
                    binding.pendingRecyclerView.visibility = if (visible) View.VISIBLE else View.GONE
                    pendingAdapter.submitList(users)
                    updateDiscoverableStatuses()
                }
                .onFailure {
                    binding.pendingTitle.visibility = View.GONE
                    binding.pendingRecyclerView.visibility = View.GONE
                }
        }
    }

    private fun loadDiscoverableUsers() {
        lifecycleScope.launch(Dispatchers.Main) {
            repository.getDiscoverableUsers()
                .onSuccess { users ->
                    allDiscoverableUsers = users
                    updateDiscoverableStatuses()
                }
                .onFailure {
                    binding.suggestionsTitle.visibility = View.GONE
                    binding.suggestionsRecyclerView.visibility = View.GONE
                }
        }
    }

    private fun loadMutualFriends() {
        val userId = currentUserId ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            repository.getMutualFriends(userId)
                .onSuccess { users ->
                    allMutualFriends = users
                    val visible = users.isNotEmpty()
                    binding.mutualTitle.visibility = if (visible) View.VISIBLE else View.GONE
                    binding.mutualRecyclerView.visibility = if (visible) View.VISIBLE else View.GONE
                    mutualAdapter.submitList(users)
                }
                .onFailure {
                    binding.mutualTitle.visibility = View.GONE
                    binding.mutualRecyclerView.visibility = View.GONE
                }
        }
    }

    private fun updateDiscoverableStatuses() {
        val friendIds = allFriends.map { it.id }.toSet()
        val receivedIds = allPendingRequests.map { it.id }.toSet()

        val updated = allDiscoverableUsers.map { user ->
            user.friendshipStatus = when (user.id) {
                in friendIds -> FriendshipStatus.FRIEND
                in receivedIds -> FriendshipStatus.RECEIVED
                else -> FriendshipStatus.SENT // fallback
            }
            user
        }

        val visible = updated.isNotEmpty()
        binding.suggestionsTitle.visibility = if (visible) View.VISIBLE else View.GONE
        binding.suggestionsRecyclerView.visibility = if (visible) View.VISIBLE else View.GONE
        discoverAdapter.submitList(updated)
    }

    private val phoenixReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = intent?.getStringExtra("event")
            val payload = intent?.getStringExtra("payload")

            when (event) {
                "friend_request_sent" -> {
                    loadPendingRequests()
                    updateDiscoverableStatuses()
                }
                "friend_request_received" -> {
                    loadPendingRequests()
                }
                "friend_request_accepted" -> {
                    loadFriends()
                    loadPendingRequests()
                }
                "friend_removed" -> {
                    loadFriends()
                    updateDiscoverableStatuses()
                    Toast.makeText(requireContext(), "You were removed as a friend", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter("PHOENIX_GLOBAL_EVENT")
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(phoenixReceiver, filter)
    }

    override fun onDestroyView() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(phoenixReceiver)
        _binding = null
        super.onDestroyView()
    }
}
