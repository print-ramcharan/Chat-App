package com.codewithram.secretchat.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _refreshTrigger = MutableLiveData<Unit>()
    val refreshTrigger: LiveData<Unit> = _refreshTrigger

    fun triggerRefresh() {
        _refreshTrigger.value = Unit
    }
}
