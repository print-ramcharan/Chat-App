package com.codewithram.secretchat.ui.splash

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.codewithram.secretchat.data.remote.ApiClient
import com.codewithram.secretchat.databinding.FragmentSplashBinding
import com.codewithram.secretchat.service.PhoenixService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!
    private var userId:String? = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            delay(1500)

            val token = getTokenFromStorage()
            if (token != null) {
                val isValid = withContext(Dispatchers.IO) {
                    try {
                        val response = ApiClient.apiService.listConversationsForCurrentUser("Bearer $token")
                        response.isSuccessful
                    } catch (_: Exception) {
                        false
                    }
                }

                if (isValid) {
                    val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val appProcesses = activityManager.runningAppProcesses

                    val isForeground = appProcesses?.any {
                        it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                                it.processName == requireContext().packageName
                    } ?: false

                    if (isForeground) {
                        val serviceIntent = Intent(requireContext(), PhoenixService::class.java).apply {
                            putExtra("token", token)
                            putExtra("user_id", userId)
                        }
                        ContextCompat.startForegroundService(requireContext(), serviceIntent)

                    } else {
                        Log.w("Splash", "App in background. Skipping PhoenixService start.")
                    }

                    findNavController().navigate(
                        SplashFragmentDirections.actionSplashFragmentToNavHome()
                    )
                } else {
                    clearToken()
                    findNavController().navigate(
                        SplashFragmentDirections.actionSplashFragmentToLoginFragment()
                    )
                }
            } else {
                findNavController().navigate(
                    SplashFragmentDirections.actionSplashFragmentToLoginFragment()
                )
            }
        }
    }

    private fun getTokenFromStorage(): String? {
        val sharedPref = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
        userId = sharedPref.getString("user_id", null)
        return sharedPref.getString("auth_token", null)

    }

    private fun clearToken() {
        val sharedPref = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
        sharedPref.edit { remove("auth_token") }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
