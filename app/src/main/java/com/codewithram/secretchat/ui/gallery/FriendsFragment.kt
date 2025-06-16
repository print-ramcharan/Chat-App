package com.codewithram.secretchat.ui.gallery

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        currentUserId = sharedPrefs.getString("user_id", null)

        setupRecyclerViews()
        loadPendingRequests()
        loadFriends()
        loadDiscoverableUsers()
        loadMutualFriends()

        return binding.root
    }

    private fun setupRecyclerViews() {
        friendsAdapter = UserAdapter(
            section = Section.FRIENDS,
            onActionClick = { user, action ->
                if (action == "unfriend") {
                    lifecycleScope.launch {
                        repository.unfriendUser(user.id)
                            .onSuccess { loadFriends() }
                    }
                }
            }
        )

        discoverAdapter = UserAdapter(
            section = Section.DISCOVER,
            onActionClick = { user, action ->
                if (action == "add") {
                    lifecycleScope.launch {
                        repository.sendFriendRequest(user.id)
                            .onSuccess {
                                loadDiscoverableUsers()
                                loadMutualFriends()
                                Toast.makeText(requireContext(), "Friend request sent", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(requireContext(), "Failed to send request", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
        )

        mutualAdapter = UserAdapter(
            section = Section.MUTUAL,
            onActionClick = { user, action ->
                if (action == "add") {
                    lifecycleScope.launch {
                        repository.sendFriendRequest(user.id)
                            .onSuccess {
                                loadMutualFriends()
                                Toast.makeText(requireContext(), "Friend request sent", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(requireContext(), "Failed to send request", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
        )

        pendingAdapter = UserAdapter(
            section = Section.PENDING,
            onActionClick = { user, action ->
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
        )


        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = friendsAdapter

        binding.suggestionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.suggestionsRecyclerView.adapter = discoverAdapter

        binding.mutualRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.mutualRecyclerView.adapter = mutualAdapter

        binding.pendingRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.pendingRecyclerView.adapter = pendingAdapter

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
                    if (users.isNotEmpty()) {
                        binding.pendingTitle.visibility = View.VISIBLE
                        binding.pendingRecyclerView.visibility = View.VISIBLE
                        pendingAdapter.submitList(users)
                    } else {
                        binding.pendingTitle.visibility = View.GONE
                        binding.pendingRecyclerView.visibility = View.GONE
                    }
                    updateDiscoverableStatuses()
                }
                .onFailure { e ->
                    Log.e("FriendsFragment", "Failed to load pending requests", e)
                    binding.pendingTitle.visibility = View.GONE
                    binding.pendingRecyclerView.visibility = View.GONE
                }
        }
    }


    private fun updateDiscoverableStatuses() {
        val friendIds = allFriends.map { it.id }.toSet()
        val receivedIds = allPendingRequests.map { it.id }.toSet()
        val sentIds = allDiscoverableUsers
            .filter { it.id !in friendIds && it.id !in receivedIds }
            .filterNot { it in allMutualFriends } // optional
            .map { it.id } // assuming these are available to send requests

        val updated = allDiscoverableUsers.map { user ->
            user.friendshipStatus = when (user.id) {
                in friendIds -> FriendshipStatus.FRIEND
                in receivedIds -> FriendshipStatus.RECEIVED
                in sentIds -> FriendshipStatus.SENT
                else -> FriendshipStatus.NONE
            }
            user
        }

        binding.suggestionsTitle.visibility =
            if (updated.isNotEmpty()) View.VISIBLE else View.GONE
        binding.suggestionsRecyclerView.visibility =
            if (updated.isNotEmpty()) View.VISIBLE else View.GONE

        discoverAdapter.submitList(updated)
    }

    private fun loadDiscoverableUsers() {
        lifecycleScope.launch(Dispatchers.Main) {
            repository.getDiscoverableUsers()
                .onSuccess { users ->
                    allDiscoverableUsers = users
                    updateDiscoverableStatuses()
                    if (users.isNotEmpty()) {
                        binding.suggestionsTitle.visibility = View.VISIBLE
                        binding.suggestionsRecyclerView.visibility = View.VISIBLE
                        discoverAdapter.submitList(users)
                    } else {
                        binding.suggestionsTitle.visibility = View.GONE
                        binding.suggestionsRecyclerView.visibility = View.GONE
                    }
                }
                .onFailure { e ->
                    Log.e("FriendsFragment", "Failed to load discoverable users", e)
                    binding.suggestionsTitle.visibility = View.GONE
                    binding.suggestionsRecyclerView.visibility = View.GONE
                }
        }
    }

    private fun loadMutualFriends() {
        val userId = currentUserId
        if (userId == null) {
            Log.e("FriendsFragment", "No user_id found in SharedPreferences.")
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            repository.getMutualFriends(userId)
                .onSuccess { users ->
                    if (users.isNotEmpty()) {
                        binding.mutualTitle.visibility = View.VISIBLE
                        binding.mutualRecyclerView.visibility = View.VISIBLE
                        mutualAdapter.submitList(users)
                    } else {
                        binding.mutualTitle.visibility = View.GONE
                        binding.mutualRecyclerView.visibility = View.GONE
                    }
                }
                .onFailure { e ->
                    Log.e("FriendsFragment", "Failed to load mutual friends", e)
                    binding.mutualTitle.visibility = View.GONE
                    binding.mutualRecyclerView.visibility = View.GONE
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

