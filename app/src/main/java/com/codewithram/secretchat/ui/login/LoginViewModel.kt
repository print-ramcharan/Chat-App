package com.codewithram.secretchat.ui.login

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithram.secretchat.data.Repository
import com.codewithram.secretchat.data.model.LoginResponse
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: Repository) : ViewModel() {

    val loginResult = MutableLiveData<Result<LoginResponse>>()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            val result = repository.login(email, password)
            loginResult.postValue(result)
        }
    }
}