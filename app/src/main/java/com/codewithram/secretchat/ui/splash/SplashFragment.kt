package com.codewithram.secretchat.ui.splash

import RefreshRequest
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
import retrofit2.HttpException

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!
    private var userId: String? = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.retryButton.setOnClickListener {
            startVerificationFlow()
        }

        startVerificationFlow()
    }

    private fun startVerificationFlow() {
        binding.retryButton.visibility = View.GONE
        binding.statusText.text = "Checking server..."

        lifecycleScope.launch {
            try {
                delay(500)

                val accessToken = getTokenFromStorage()
                val refreshToken = getRefreshTokenFromStorage()

                if (accessToken == null) {
                    Log.d("SplashFragment", "No access token stored.")
                    navigateToLogin()
                    return@launch
                }

                val verifyResult = verifyTokenUntilServerResponds(accessToken)

                when (verifyResult) {
                    VerifyResult.VALID -> {
                        Log.d("SplashFragment", "Token valid.")
                        proceedToHome(accessToken)
                    }
                    VerifyResult.INVALID -> {
                        Log.d("SplashFragment", "Token invalid, trying refresh.")
                        if (refreshToken.isNullOrEmpty()) {
                            clearTokens()
                            navigateToLogin()
                        } else {
                            binding.statusText.text = "Refreshing session..."
                            val refreshedToken = refreshTokenUntilServerResponds(refreshToken)
                            if (refreshedToken != null) {
                                saveNewAccessToken(refreshedToken)
                                proceedToHome(refreshedToken)
                            } else {
                                Log.w("SplashFragment", "Server unreachable during refresh. Showing retry.")
                                binding.statusText.text = "Server unreachable."
                                binding.retryButton.visibility = View.VISIBLE
                                // DO NOT navigate to login
                            }
                        }
                    }
                    VerifyResult.UNREACHABLE -> {
                        Log.w("SplashFragment", "Server unreachable during verify. Showing retry.")
                        binding.statusText.text = "Server unreachable."
                        binding.retryButton.visibility = View.VISIBLE
                        // DO NOT navigate to login
                    }
                }
            } catch (e: Exception) {
                Log.e("SplashFragment", "Unhandled error", e)
                binding.statusText.text = "Unexpected error occurred."
                binding.retryButton.visibility = View.VISIBLE
            }
        }
    }

    private enum class VerifyResult {
        VALID,
        INVALID,
        UNREACHABLE
    }

    private suspend fun verifyTokenUntilServerResponds(token: String): VerifyResult {
        var retries = 0
        val maxRetries = 3

        while (retries < maxRetries) {
            val result = withContext(Dispatchers.IO) {
                try {
                    val response = ApiClient.apiService.verifyToken("Bearer $token")
                    Log.d("SplashFragment", "verifyToken HTTP ${response.code()}")
                    if (response.isSuccessful) {
                        VerifyResult.VALID
                    } else {
                        VerifyResult.INVALID
                    }
                } catch (e: Exception) {
                    Log.w("SplashFragment", "Server unreachable attempt ${retries + 1}", e)
                    VerifyResult.UNREACHABLE
                }
            }

            if (result == VerifyResult.VALID) return VerifyResult.VALID
            if (result == VerifyResult.INVALID) return VerifyResult.INVALID

            retries++
            binding.statusText.text = "Server not responding... (Attempt $retries/$maxRetries)"
            delay(3000)
        }

        return VerifyResult.UNREACHABLE
    }

    private suspend fun refreshTokenUntilServerResponds(refreshToken: String): String? {
        var retries = 0
        val maxRetries = 3

        while (retries < maxRetries) {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = RefreshRequest(refresh_token = refreshToken)
                    val response = ApiClient.apiService.refreshToken(request)
                    Log.d("SplashFragment", "refreshToken HTTP ${response.code()}")
                    if (response.isSuccessful) {
                        response.body()?.access_token
                    } else {
                        // invalid refresh token
                        ""
                    }
                } catch (e: Exception) {
                    Log.w("SplashFragment", "Server unreachable refreshing attempt ${retries + 1}", e)
                    null
                }
            }

            if (result != null && result.isNotEmpty()) {
                return result
            }
            if (result == "") {
                // refresh token invalid
                return null
            }

            retries++
            binding.statusText.text = "Server not responding... (Attempt $retries/$maxRetries)"
            delay(3000)
        }

        return null
    }

    private fun proceedToHome(token: String) {
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
            Log.w("SplashFragment", "App in background. Skipping PhoenixService start.")
        }

        findNavController().navigate(
            SplashFragmentDirections.actionSplashFragmentToNavHome()
        )
    }

    private fun navigateToLogin() {
        Log.d("SplashFragment", "Navigating to login")
        findNavController().navigate(
            SplashFragmentDirections.actionSplashFragmentToLoginFragment()
        )
    }

    private fun getTokenFromStorage(): String? {
        val sharedPref = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
        userId = sharedPref.getString("user_id", null)
        return sharedPref.getString("auth_token", null)
    }

    private fun getRefreshTokenFromStorage(): String? {
        val sharedPref = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
        return sharedPref.getString("refresh_token", null)
    }

    private fun saveNewAccessToken(newToken: String) {
        val sharedPref = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
        sharedPref.edit {
            putString("auth_token", newToken)
        }
    }

    private fun clearTokens() {
        val sharedPref = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
        sharedPref.edit {
            remove("auth_token")
            remove("refresh_token")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
