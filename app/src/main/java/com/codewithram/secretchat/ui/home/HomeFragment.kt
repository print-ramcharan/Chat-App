package com.codewithram.secretchat.ui.home

import Chat
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.codewithram.secretchat.R
import com.codewithram.secretchat.data.Repository
import com.codewithram.secretchat.databinding.FragmentHomeBinding
import java.time.OffsetDateTime

class HomeFragment : Fragment() {


    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var userAdapter: UserAdapter
    private lateinit var repository: Repository
    private lateinit var sharedViewModel: SharedViewModel


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("HomeFragment", "onCreateView started")
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val sharedPrefs = requireContext().getSharedPreferences("secret_chat_prefs", Context.MODE_PRIVATE)
        repository = Repository(sharedPrefs)

        val factory = HomeViewModelFactory(repository)
        homeViewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]

        sharedViewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]
        sharedViewModel.refreshTrigger.observe(viewLifecycleOwner) {
            Log.d("HomeFragment", "SharedViewModel triggered refresh")
            homeViewModel.loadChats()
        }
        setupRecyclerView()
        Log.d("HomeFragment", "Calling loadChats()")
        homeViewModel.loadChats()

        homeViewModel.chats.observe(viewLifecycleOwner) { conversations ->
            Log.d("HomeFragment", "Conversations received: ${conversations.size}")

            val chats = conversations.map { conversation ->
                Log.d("HomeFragment", "Mapping conversation id: ${conversation.id}")

                val timestamp = try {
                    OffsetDateTime.parse(conversation.lastMessage?.insertedAt
                        ?: conversation.updatedAt
                        ?: conversation.insertedAt
                    ).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Date parsing failed: ${e.message}")
                    0L
                }

                Chat(
                    id = conversation.id,
                    name = if (conversation.isGroup) {
                        conversation.groupName ?: "Group"
                    } else {
                        "Private Chat"
                    },
                    lastMessage = conversation.lastMessage?.encryptedBody ?: "",
                    lastTimestamp = timestamp,
                    unreadCount = 0
                )
            }


            Log.d("HomeFragment", "Updating adapter with ${chats.size} chats")
            userAdapter.updateData(chats)
        }

        return binding.root
    }

    private fun setupRecyclerView() {
        Log.d("HomeFragment", "Setting up RecyclerView and Adapter")
        userAdapter = UserAdapter(emptyList()) { chat ->
            Log.d("HomeFragment", "Chat clicked: ${chat.name} (${chat.id})")
            val bundle = Bundle().apply {
                putString("conversationId", chat.id.toString())
                putString("chatName", chat.name)
            }
            findNavController().navigate(R.id.action_nav_home_to_chatFragment, bundle)
        }
        binding.usersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.usersRecyclerView.adapter = userAdapter
    }

    override fun onResume() {
        super.onResume()
        Log.d("HomeFragment", "onResume: Reloading chats")
        homeViewModel.loadChats()
    }

    fun refreshChats() {
        Log.d("HomeFragment", "refreshChats called from MainActivity")
        homeViewModel.loadChats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("HomeFragment", "onDestroyView called, clearing binding")
        _binding = null
    }
}
