package com.codewithram.secretchat.ui.home

import Conversation
import android.util.Log
import androidx.lifecycle.*
import com.codewithram.secretchat.data.Repository
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: Repository) : ViewModel() {

    private val _chats = MutableLiveData<List<Conversation>>()
    val chats: LiveData<List<Conversation>> = _chats

    fun loadChats() {
        Log.d("HomeViewModel", "loadChats called")
        viewModelScope.launch {
            try {
                val conversationList = repository.getConversationsForCurrentUser()
                Log.d("HomeViewModel", "Conversations fetched: ${conversationList.size}")
                _chats.value = conversationList
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error fetching conversations", e)
            }
        }
    }
}
