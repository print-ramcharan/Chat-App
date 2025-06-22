package com.codewithram.secretchat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.codewithram.secretchat.data.Repository
import com.codewithram.secretchat.databinding.ActivityMainBinding
import com.codewithram.secretchat.ui.home.HomeViewModel
import com.codewithram.secretchat.ui.home.HomeViewModelFactory
import com.google.android.material.navigation.NavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var homeViewModel: HomeViewModel

    private lateinit var repository: Repository

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportActionBar?.hide()
    FirebaseApp.initializeApp(this);
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    handleIntent(intent)
    repository = Repository(this.getSharedPreferences("secret_chat_prefs", 0))

    FirebaseMessaging.getInstance().token
        .addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                return@addOnCompleteListener
            }
            val token = task.result
            lifecycleScope.launch {
                 repository.updateFcmToken(token)
            }
        }
    val factory = HomeViewModelFactory(repository)
    homeViewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
    setSupportActionBar(binding.appBarMain.toolbar)
    val fab = binding.appBarMain.fab
    fab.setOnClickListener {
        lifecycleScope.launch {
            val result = repository.getFriends()
            result.onSuccess { friends ->
                if (friends.isEmpty()) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("No Friends Found")
                        .setMessage("You need to add friends first.\n\nGo to the 'Friends' tab to send requests.")
                        .setPositiveButton("Go to Friends") { _, _ ->
                            findNavController(R.id.nav_host_fragment_content_main)
                                .navigate(R.id.nav_gallery)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    showCreateConversationDialog(friends)
                }
            }.onFailure {
                Toast.makeText(this@MainActivity, "Failed to fetch friends", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val drawerLayout: DrawerLayout = binding.drawerLayout

    val navView: NavigationView = binding.navView
    val prefs = getSharedPreferences("secret_chat_prefs", MODE_PRIVATE)
    val username = prefs.getString("username", "Guest")
    val avatar_url = prefs.getString("avatar_url", "")
    val displayName = prefs.getString("display_name", "User")
    val phoneNumber = prefs.getString("phone_number", "N/A")

    val navHeader = binding.navView.getHeaderView(0)
    val usernameTextView = navHeader.findViewById<TextView>(R.id.username_text)
    val emailTextView = navHeader.findViewById<TextView>(R.id.mail)
    val avatarImageView = navHeader.findViewById<ImageView>(R.id.avatar)

    requestNotificationPermissionIfNeeded()
    if (!avatar_url.isNullOrEmpty()) {
        try {
            Log.d("pure Avatar", avatar_url)
            val pureBase64 = avatar_url.substringAfter("base64,")
            val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

            Glide.with(this)
                .load(bitmap)
                .placeholder(R.drawable.ic_default_profile)
                .circleCrop()
                .into(avatarImageView)
        } catch (e: Exception) {
            Log.e("AvatarLoad", "Failed to decode avatar", e)
        }
    }
    usernameTextView.text = displayName ?: "Guest"
    emailTextView.text = phoneNumber ?: "N/A"

    val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Glide.with(this).load(uri).circleCrop().into(avatarImageView)

            try {
                val inputStream = contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)

                val resizedBitmap = resizeBitmap(originalBitmap, 512)

                val byteArrayOutputStream = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                val byteArray = byteArrayOutputStream.toByteArray()

                val base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                lifecycleScope.launch {
                    val result = repository.updateAvatarUrl(base64Image)
                    if (result.isSuccess) {
                        Toast.makeText(this@MainActivity, "Avatar updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to update avatar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("AvatarUpload", "Exception while encoding image", e)
            }
        } else {
            Log.w("AvatarUpload", "No URI selected")
        }
    }

    avatarImageView.setOnClickListener {
        imagePicker.launch("image/*")
    }


    val navController = findNavController(R.id.nav_host_fragment_content_main)

    navController.addOnDestinationChangedListener { _, destination, _ ->
        val showToolbar = destination.id in setOf(
            R.id.nav_home,
            R.id.nav_gallery,
            R.id.nav_slideshow
        )

        if (showToolbar) {
            supportActionBar?.show()
        } else {
            supportActionBar?.hide()
        }
        if (destination.id == R.id.nav_home) {
            binding.appBarMain.fab.show()
        } else {
            binding.appBarMain.fab.hide()
        }
    }

    appBarConfiguration = AppBarConfiguration(
        setOf(R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow), drawerLayout
    )

    setupActionBarWithNavController(navController, appBarConfiguration)
    navView.setupWithNavController(navController)
}
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra("navigate_to_chat", false)) {
            val conversationId = intent.getStringExtra("conversation_id")
            val senderId = intent.getStringExtra("sender_id")

            val navController = findNavController(R.id.nav_host_fragment_content_main)
            navController.navigate(
                R.id.action_nav_home_to_chatFragment,
                bundleOf(
                    "conversation_id" to conversationId,
                    "sender_id" to senderId
                )
            )
        }
    }


    fun showCreateConversationDialog(friends: List<com.codewithram.secretchat.ui.gallery.User>) {
        val selectedFriends = mutableListOf<String>()
        val friendNames = friends.map { it.displayName }.toTypedArray()
        val friendIds = friends.map { it.id }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val isGroupSwitch = Switch(this).apply {
            text = "Group Chat"
            isChecked = false
        }

        val groupNameInput = EditText(this).apply {
            hint = "Enter group name"
            inputType = InputType.TYPE_CLASS_TEXT
            visibility = View.GONE
        }

        layout.addView(isGroupSwitch)
        layout.addView(groupNameInput)

        isGroupSwitch.setOnCheckedChangeListener { _, isChecked ->
            groupNameInput.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("New Conversation")
            .setView(layout)
            .setMultiChoiceItems(friendNames, null) { _, which, isChecked ->
                val id = friendIds[which]
                if (isChecked) selectedFriends.add(id) else selectedFriends.remove(id)
            }
            .setPositiveButton("Create") { _, _ ->
                val isGroup = isGroupSwitch.isChecked
                val groupName = groupNameInput.text.toString().trim()

                when {
                    isGroup && (groupName.isEmpty() || selectedFriends.size < 1) -> {
                        Toast.makeText(this, "Enter group name and select at least 1 member", Toast.LENGTH_SHORT).show()
                    }
                    !isGroup && selectedFriends.size != 1 -> {
                        Toast.makeText(this, "Select exactly 1 person for 1-on-1 chat", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val finalName = if (isGroup) groupName else null
                        lifecycleScope.launch {
                            val result = repository.createConversation(finalName, selectedFriends, isGroup)
                            result.onSuccess { conversation ->
                                val message = if (conversation.reused) "Conversation already exists" else "Conversation created"
                                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()

                                val navController = findNavController(R.id.nav_host_fragment_content_main)
                                navController.popBackStack(R.id.nav_home, true)
                                navController.navigate(R.id.nav_home)
                            }.onFailure {
                                Toast.makeText(this@MainActivity, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                    }
                }
            }
            .setNegativeButton("Cancel", null)
        builder.show()
    }
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                repository.logout()
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.loginFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}