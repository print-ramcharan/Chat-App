package com.codewithram.secretchat.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.codewithram.secretchat.R
import com.codewithram.secretchat.ui.home.PhoenixChannel
import org.json.JSONObject

class PhoenixService : Service() {

    private var phoenixChannel: PhoenixChannel? = null
    private val TAG = "PhoenixService"
    private val CHANNEL_ID = "phoenix_channel_id"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildPersistentNotification())
        Log.d(TAG, "Foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra("token")
        val userId = intent?.getStringExtra("user_id")

        Log.d(TAG, "Service started with token=$token, userId=$userId")

        if (token != null && userId != null) {
//            val url = "ws://social-application-backend-hwrx.onrender.com/socket/websocket?token=$token"

            val url = "ws://192.168.0.190:4000/socket/websocket?token=$token"
            phoenixChannel = PhoenixChannel(
                socketUrl = url,
                topic = "user:$userId",
                params = mapOf("token" to token)
            )

            phoenixChannel?.onMessageReceived = { event, payload ->
                Log.d(TAG, "Global event received: $event - $payload")
                handleGlobalEvent(event, payload)
            }

            phoenixChannel?.onJoinSuccess = {
                Log.d(TAG, "Successfully joined global channel: $it")
            }

            phoenixChannel?.onJoinError = {
                Log.e(TAG, "Failed to join global channel: $it")
            }

            phoenixChannel?.onError = {
                Log.e(TAG, "WebSocket error", it)
            }

            phoenixChannel?.onClose = {
                Log.w(TAG, "WebSocket closed or disconnected")
            }

            phoenixChannel?.connect()
            Log.d(TAG, "PhoenixChannel connect called")
        } else {
            Log.e(TAG, "Token or userId missing in intent extras")
        }

        return START_STICKY
    }

    private fun handleGlobalEvent(event: String, payload: JSONObject) {
        when (event) {
            "new_message" -> {
                val msg = payload.optString("encrypted_body", "You have a new message")
                showNotification("New Message", msg)
                broadcastEvent("new_message", payload)
            }
            "friend_request" -> {
                showNotification("Friend Request", "You have a new friend request")
                broadcastEvent("friend_request", payload)
            }
            else -> {
                Log.d(TAG, "Unhandled event: $event")
            }
        }
    }

    private fun broadcastEvent(event: String, payload: JSONObject) {
        val intent = Intent("PHOENIX_GLOBAL_EVENT")
        intent.putExtra("event", event)
        intent.putExtra("payload", payload.toString())
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun showNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    private fun buildPersistentNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SecretChat Background Service")
            .setContentText("Listening for new messages...")
            .setSmallIcon(R.drawable.ic_message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Phoenix Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Listens for real-time updates from SecretChat"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        phoenixChannel?.disconnect()
        phoenixChannel = null
        Log.d(TAG, "Service destroyed and PhoenixChannel disconnected")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
