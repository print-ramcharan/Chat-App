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
    private val TAG = "SplashFragment"
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
            delay(1500) // Optional splash delay

            val token = getTokenFromStorage()
            Log.d(TAG, "Retrieved token from SharedPreferences: $token")

            if (token != null) {
                val isValid = withContext(Dispatchers.IO) {
                    try {
                        val response = ApiClient.apiService.listConversationsForCurrentUser("Bearer $token")
                        Log.d(TAG, "Token validation response: ${response.code()}")
                        response.isSuccessful
                    } catch (e: Exception) {
                        Log.e(TAG, "Error validating token", e)
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
//                    requireContext().startService(serviceIntent)
                        ContextCompat.startForegroundService(requireContext(), serviceIntent)

                    } else {
                        Log.w("Splash", "App in background. Skipping PhoenixService start.")
                    }
//                    Log.d(TAG, "Token is valid. Navigating to home.")
//                    val intent = Intent(requireContext(), PhoenixService::class.java).apply {
//                        putExtra("token", token)
//                        putExtra("user_id", userId)
//                    }
//                    Log.d(TAG, "Starting PhoenixService with token=$token and user_id=$userId")
//                    requireContext().startService(intent)

                    findNavController().navigate(
                        SplashFragmentDirections.actionSplashFragmentToNavHome()
                    )
                } else {
                    Log.w(TAG, "Token invalid or expired. Clearing token and navigating to login.")
                    clearToken()
                    findNavController().navigate(
                        SplashFragmentDirections.actionSplashFragmentToLoginFragment()
                    )
                }
            } else {
                Log.d(TAG, "No token found. Navigating to login.")
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
//    private fun getUserIdFromStorage(): String? {
//        val shared
//    }

    private fun clearToken() {
        val sharedPref = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
        sharedPref.edit().remove("auth_token").apply()
        Log.d(TAG, "Token cleared from SharedPreferences.")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
