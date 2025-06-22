package com.codewithram.secretchat.service

import android.app.ActivityManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.codewithram.secretchat.IpAddressType
import com.codewithram.secretchat.R
import com.codewithram.secretchat.ServerConfig
import com.codewithram.secretchat.ui.home.PhoenixChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class PhoenixService : Service() {

    private var phoenixChannel: PhoenixChannel? = null
    private val TAG = "PhoenixService"
    private var userId: String = ""
    private var heartbeatJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createSilentNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra("token")
        userId = intent?.getStringExtra("user_id").orEmpty()

        if (!(applicationContext as Application).isAppInForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!token.isNullOrEmpty() && userId.isNotEmpty()) {
//            val url = "ws://${ServerConfig.ipAddress.address}/socket/websocket?token=$token"

            val scheme = if (ServerConfig.ipAddress == IpAddressType.DOMAIN) "wss" else "ws"
            val url = "$scheme://${ServerConfig.ipAddress.address}/socket/websocket?token=$token"

            val socketUrl = "wss://social-application-backend-hwrx.onrender.com/socket/websocket?token=$token"
//            val socketUrl = "ws://192.168.0.190:4000/socket/websocket?token=$token"

            phoenixChannel = PhoenixChannel(
                socketUrl = socketUrl,
                topic = "user:$userId",
                params = mapOf("token" to token)
            ).apply {
                onMessageReceived = { event, payload ->
                    handleGlobalEvent(event, payload)
                }
                onJoinSuccess = {
                    startHeartbeat()
                }
                onJoinError = { Log.e(TAG, "Join failed: $it") }
                onError = { Log.e(TAG, "WebSocket error", it) }
                onClose = { Log.w(TAG, "Socket closed") }

                connect()
            }
        } else {
            stopSelf()
        }

        return START_STICKY
    }
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive && phoenixChannel?.isConnected() == true) {
                try {
                    phoenixChannel?.push("heartbeat", JSONObject())
                    Log.d(TAG, "❤Sent phx_heartbeat")
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed", e)
                }
                delay(30_000)
            }
        }
    }


    private fun createSilentNotification(): Notification {
        val channelId = "phoenix_silent_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Silent Phoenix Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_2_0_round)
            .setContentText("checking for new messages")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSound(null)
            .setOngoing(true)
            .build()
    }

    private fun handleGlobalEvent(event: String, payload: JSONObject) {
        when (event) {
            "presence_state",
            "new_message",
            "friend_request_received",
            "friend_request_sent",
            "message_status_updated",
            "unread_count_updated",
            "friend_request_accepted" -> broadcastEvent(event, payload)
            else -> Log.d(TAG, "PhoenixService: Unhandled event: $event")
        }
    }

    private fun broadcastEvent(event: String, payload: JSONObject) {
        val intent = Intent("PHOENIX_GLOBAL_EVENT").apply {
            putExtra("event", event)
            putExtra("payload", payload.toString())
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onDestroy() {
        phoenixChannel?.disconnect()
        phoenixChannel = null
        heartbeatJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun Application.isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        return appProcesses.any {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    it.processName == packageName
        }
    }
}
