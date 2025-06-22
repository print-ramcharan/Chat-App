package com.codewithram.secretchat.ui.home

import Conversation
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithram.secretchat.data.Repository
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: Repository) : ViewModel() {

    private val _chats = MutableLiveData<List<Conversation>>()
    val chats: LiveData<List<Conversation>> = _chats

    fun loadChats() {
        viewModelScope.launch {
            try {
                val conversationList = repository.getConversationsForCurrentUser()
                _chats.value = conversationList
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error fetching conversations", e)
            }
        }
    }
}
