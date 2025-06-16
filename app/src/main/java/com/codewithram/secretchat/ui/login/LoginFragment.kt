//package com.codewithram.secretchat.ui.login
//
//import android.os.Bundle
//import android.text.TextUtils
//import android.util.Log
//import androidx.fragment.app.Fragment
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Toast
//import androidx.lifecycle.lifecycleScope
//import androidx.navigation.fragment.findNavController
//import com.codewithram.secretchat.data.model.LoginRequest
//import com.codewithram.secretchat.data.remote.ApiClient
//import com.codewithram.secretchat.databinding.FragmentLoginBinding
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
//class LoginFragment : Fragment() {
//
//    private var _binding: FragmentLoginBinding? = null
//    private val binding get() = _binding!!
//    private val TAG = "LoginFragment"
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentLoginBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        binding.loginButton.setOnClickListener {
//            val email = binding.usernameEditText.text.toString().trim()
//            val password = binding.passwordEditText.text.toString()
//
//            if (validateInput(email, password)) {
//                performLogin(email, password)
//            }
//        }
//    }
//
//    private fun validateInput(phone_number: String, password: String): Boolean {
//        if (TextUtils.isEmpty(phone_number)) {
//            binding.usernameEditText.error = "Phone Number required"
//            return false
//        }
//        if (TextUtils.isEmpty(password)) {
//            binding.passwordEditText.error = "Password required"
//            return false
//        }
//        return true
//    }
//
//    private fun performLogin(phoneNumber: String, password: String) {
//        lifecycleScope.launch {
//            val response = withContext(Dispatchers.IO) {
//                try {
//                    ApiClient.apiService.login(LoginRequest(phoneNumber, password))
//                } catch (e: Exception) {
//                    Log.e(TAG, "Login API call failed", e)
//                    null
//                }
//            }
//
//            if (response != null && response.isSuccessful && response.body() != null) {
//                val loginResponse = response.body()!!
//                Log.d(TAG, "Login successful, token received: ${loginResponse.token}")
//                saveToken(loginResponse.token)
//
//                Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()
//                findNavController().navigate(
//                    LoginFragmentDirections.actionLoginFragmentToNavHome()
//                )
//            } else {
//                Log.w(TAG, "Login failed: ${response?.code()} - ${response?.message()}")
//                Toast.makeText(requireContext(), "Login failed. Check credentials.", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    private fun saveToken(token: String) {
//        val sharedPref = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
//        sharedPref.edit().putString("auth_token", token).apply()
//        Log.d(TAG, "Token saved to SharedPreferences: $token")
//
//        // Read immediately and print to verify
//        val savedToken = sharedPref.getString("auth_token", null)
//        Log.d(TAG, "Token read back from SharedPreferences: $savedToken")
//    }
//
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}
//
//package com.codewithram.secretchat.ui.login
//
//import android.os.Bundle
//import android.text.TextUtils
//import android.util.Log
//import androidx.fragment.app.Fragment
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Toast
//import androidx.lifecycle.lifecycleScope
//import androidx.navigation.fragment.findNavController
//import com.codewithram.secretchat.data.model.LoginRequest
//import com.codewithram.secretchat.data.remote.ApiClient
//import com.codewithram.secretchat.databinding.FragmentLoginBinding
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
//class LoginFragment : Fragment() {
//
//    private var _binding: FragmentLoginBinding? = null
//    private val binding get() = _binding!!
//    private val TAG = "LoginFragment"
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentLoginBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        binding.loginButton.setOnClickListener {
//            val phone = binding.usernameEditText.text.toString().trim()
//            val password = binding.passwordEditText.text.toString()
//
//            if (validateInput(phone, password)) {
//                performLogin(phone, password)
//            }
//        }
//    }
//
//    private fun validateInput(phone: String, password: String): Boolean {
//        if (TextUtils.isEmpty(phone)) {
//            binding.usernameEditText.error = "Phone Number required"
//            return false
//        }
//        if (TextUtils.isEmpty(password)) {
//            binding.passwordEditText.error = "Password required"
//            return false
//        }
//        return true
//    }
//
//    private fun performLogin(phoneNumber: String, password: String) {
//        lifecycleScope.launch {
//            val response = withContext(Dispatchers.IO) {
//                try {
//                    ApiClient.apiService.login(LoginRequest(phoneNumber, password))
//                } catch (e: Exception) {
//                    Log.e(TAG, "Login API call failed", e)
//                    null
//                }
//            }
//
//            if (response?.isSuccessful == true && response.body() != null) {
//                val loginResponse = response.body()!!
//                Log.d(TAG, "Login successful, token: ${loginResponse.token}")
//                saveAuthData(loginResponse.token, loginResponse.user.id,
//                    loginResponse.user.username,
//                    loginResponse.user.display_name,
//                    loginResponse.user.phone_number)
//
//                Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()
//                findNavController().navigate(
//                    LoginFragmentDirections.actionLoginFragmentToNavHome()
//                )
//            } else {
//                Log.w(TAG, "Login failed: ${response?.code()} - ${response?.message()}")
//                Toast.makeText(requireContext(), "Login failed. Check credentials.", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    private fun saveAuthData(
//        token: String,
//        userId: String,
//        username: String,
//        displayName: String,
//        phoneNumber: String
//    ) {
//        val prefs = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
//        prefs.edit()
//            .putString("auth_token", token)
//            .putString("user_id", userId)
//            .putString("username", username)
//            .putString("display_name", displayName)
//            .putString("phone_number", phoneNumber)
//            .apply()
//
//        Log.d(TAG, "Saved to prefs: auth_token=$token, user_id=$userId, username=$username, display_name=$displayName, phone_number=$phoneNumber")
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}

package com.codewithram.secretchat.ui.login

import RegisterRequest
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.codewithram.secretchat.data.model.LoginRequest
//import com.codewithram.secretchat.data.model.RegisterRequest
import com.codewithram.secretchat.data.remote.ApiClient
import com.codewithram.secretchat.databinding.FragmentLoginBinding
import com.codewithram.secretchat.service.PhoenixService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val TAG = "LoginFragment"
    private var isLoginMode = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateUiForMode()

        binding.loginButton.setOnClickListener {
            if (isLoginMode) {
                handleLogin()
            } else {
                handleRegister()
            }
        }

        binding.switchModeTextView.setOnClickListener {
            toggleModeWithAnimation()
        }
    }

    private fun toggleModeWithAnimation() {
        val fadeOut = AlphaAnimation(1f, 0f).apply { duration = 300 }
        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 300 }

        // Fade out the entire form container (LinearLayout root)
        binding.loginRoot.startAnimation(fadeOut)
        fadeOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}

            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                isLoginMode = !isLoginMode
                updateUiForMode()
                binding.loginRoot.startAnimation(fadeIn)
            }

            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
    }

    private fun updateUiForMode() {
        if (isLoginMode) {
            binding.titleTextView.text = "Welcome Back"
            binding.loginButton.text = "Login"
            binding.switchModeTextView.text = "Don't have an account? Register"

            binding.usernameEditText.visibility = View.GONE
            binding.displayNameEditText.visibility = View.GONE

            binding.phoneEditText.hint = "Phone Number"

        } else {
            binding.titleTextView.text = "Create Account"
            binding.loginButton.text = "Register"
            binding.switchModeTextView.text = "Already have an account? Login"

            binding.usernameEditText.visibility = View.VISIBLE
            binding.displayNameEditText.visibility = View.VISIBLE

            binding.phoneEditText.hint = "Phone Number"
        }

        // Clear all input fields on toggle for better UX
        binding.usernameEditText.text?.clear()
        binding.displayNameEditText.text?.clear()
        binding.phoneEditText.text?.clear()
        binding.passwordEditText.text?.clear()
    }

    private fun handleLogin() {
        val phone = binding.phoneEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()

        if (!validateLoginInput(phone, password)) return

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                try {
                    ApiClient.apiService.login(LoginRequest(phone, password))
                } catch (e: Exception) {
                    Log.e(TAG, "Login API failed", e)
                    null
                }
            }

            if (response?.isSuccessful == true && response.body() != null) {
                val loginResponse = response.body()!!
                saveAuthData(
                    loginResponse.token,
                    loginResponse.user.id,
                    loginResponse.user.username,
                    loginResponse.user.display_name,
                    loginResponse.user.phone_number,
                    loginResponse.user.avatar_url.toString(),
                )
                Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), PhoenixService::class.java).apply {
                    putExtra("token", loginResponse.token)
                    putExtra("user_id", loginResponse.user.id)
                }
                requireContext().startService(intent)

                findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToNavHome())
            } else {
                Toast.makeText(requireContext(), "Login failed. Check credentials.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleRegister() {
        val username = binding.usernameEditText.text.toString().trim()
        val displayName = binding.displayNameEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()

        if (!validateRegisterInput(username, displayName, phone, password)) return

        // Generate key pair
        val keyPair = generateRSAKeyPair()
        if (keyPair == null) {
            Toast.makeText(requireContext(), "Failed to generate keys", Toast.LENGTH_SHORT).show()
            return
        }

        val publicKeyEncoded = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val privateKeyEncoded = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)

        // Save private key securely (here, SharedPreferences - consider Android Keystore for production)
        savePrivateKey(privateKeyEncoded)

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                try {
                    ApiClient.apiService.register(
                        RegisterRequest(
                            username = username,
                            phone_number = phone,
                            display_name = displayName,
                            password = password,
                            public_key = publicKeyEncoded
                        )

                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Register API failed", e)
                    null
                }
            }

            if (response?.isSuccessful == true) {
                Toast.makeText(requireContext(), "Registration successful! Please login.", Toast.LENGTH_SHORT).show()
                // Automatically switch to login mode after registration
                isLoginMode = true
                updateUiForMode()
            } else {
                Toast.makeText(requireContext(), "Registration failed. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePrivateKey(privateKeyEncoded: String) {
        val prefs = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
        prefs.edit().putString("private_key", privateKeyEncoded).apply()
        Log.d(TAG, "Private key saved locally.")
    }

    private fun generateRSAKeyPair(): java.security.KeyPair? {
        return try {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            keyGen.generateKeyPair()
        } catch (e: Exception) {
            Log.e(TAG, "Key pair generation failed", e)
            null
        }
    }

    private fun validateLoginInput(phone: String, password: String): Boolean {
        if (TextUtils.isEmpty(phone)) {
            binding.phoneEditText.error = "Phone number required"
            return false
        }
        if (TextUtils.isEmpty(password)) {
            binding.passwordEditText.error = "Password required"
            return false
        }
        return true
    }

    private fun validateRegisterInput(
        username: String,
        displayName: String,
        phone: String,
        password: String,
    ): Boolean {
        if (TextUtils.isEmpty(username)) {
            binding.usernameEditText.error = "Username required"
            return false
        }
        if (TextUtils.isEmpty(displayName)) {
            binding.displayNameEditText.error = "Display name required"
            return false
        }
        if (TextUtils.isEmpty(phone)) {
            binding.phoneEditText.error = "Phone number required"
            return false
        }
        if (TextUtils.isEmpty(password)) {
            binding.passwordEditText.error = "Password required"
            return false
        }
        return true
    }

    private fun saveAuthData(
        token: String,
        userId: String,
        username: String,
        displayName: String,
        phoneNumber: String,
        avatarUrl: String,
    ) {
        val prefs = requireActivity().getSharedPreferences("secret_chat_prefs", 0)
        prefs.edit()
            .putString("auth_token", token)
            .putString("user_id", userId)
            .putString("username", username)
            .putString("display_name", displayName)
            .putString("phone_number", phoneNumber)
            .putString("avatar_url", avatarUrl)
            .apply()

        Log.d(TAG, "Saved auth data to prefs")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
