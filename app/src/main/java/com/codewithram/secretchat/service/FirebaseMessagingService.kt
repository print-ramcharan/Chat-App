package com.codewithram.secretchat.service

import MessageStatusUpdateRequest
import StatusEnumRequest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.edit
import com.codewithram.secretchat.MainActivity
import com.codewithram.secretchat.R
import com.codewithram.secretchat.data.Repository
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.concurrent.thread


class MyFirebaseService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseService"
    private lateinit var repository: Repository
    private lateinit var sharedPrefs: SharedPreferences
    override fun onCreate() {
        super.onCreate()
        sharedPrefs = getSharedPreferences("secret_chat_prefs", Context.MODE_PRIVATE)
        repository = Repository(sharedPrefs)

        Log.d(TAG, "🔥 Service created")
        refreshTokenPeriodically()
    }

    private fun refreshTokenPeriodically() {
        thread(start = true, isDaemon = true) {
            while (true) {
                try {
                    Thread.sleep((6 * 60 * 60 * 1000).toLong()) // Sleep for 6 hours
                    FirebaseMessaging.getInstance().getToken()
                        .addOnCompleteListener(OnCompleteListener { task: Task<String?>? ->
                            if (task!!.isSuccessful()) {
                                val newToken = task.getResult()
                                Log.d(TAG, "Refreshed token: " + newToken)
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        repository.updateFcmToken(newToken.toString())
                                        Log.d(TAG, "✅ FCM token updated on backend")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Failed to update FCM token", e)
                                    }
                                }
                            } else {
                                Log.e(TAG, "Failed to refresh token", task.getException())
                            }
                        })
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Token refresh interrupted", e)
                }
            }
        }
    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔥 New FCM token: $token")

//        val sharedPrefs = getSharedPreferences("secret_chat_prefs", Context.MODE_PRIVATE)
//        val repository = Repository(sharedPrefs)

        val auth_token = sharedPrefs.getString("auth_token", null)
        Log.d(TAG, "auth_token: $auth_token")
        if (auth_token == null) {
            Log.d(TAG, "❌ No auth token found, not updating FCM token")
            sharedPrefs.edit { putString("fcm_token_pending", token) }
            return
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.updateFcmToken(token)
                    Log.d(TAG, "✅ FCM token updated on backend")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to update FCM token", e)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "📨 Message received: ${remoteMessage.data}")

        val data = remoteMessage.data
        val messageId = data["message_id"] ?: return
        val senderName = data["sender_name"] ?: "Someone"
        val messageContent = data["encrypted_body"] ?: "[New message]"
        val conversationId = data["conversation_id"]
        val senderId = data["sender_id"]
        val clientRef = data["client_ref"]

        val title = "New message from $senderName"
        val body = messageContent
        val channelId = "messages_channel"
        val notificationId = conversationId.hashCode()

        // ✅ Reply Action
        val replyLabel = "Reply"
        val keyTextReply = "key_text_reply"

        val remoteInput = RemoteInput.Builder(keyTextReply).setLabel(replyLabel).build()

        val replyIntent = Intent(this, NotificationReplyReceiver::class.java).apply {
            action = "com.codewithram.secretchat.REPLY_ACTION"
            putExtra("message_id", messageId)
            putExtra("conversation_id", conversationId)
            putExtra("sender_id", senderId)
            putExtra("client_ref", clientRef)
            putExtra("notification_id", notificationId)
        }


        val replyPendingIntent = PendingIntent.getBroadcast(
            this, notificationId,
            replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_edit_name, replyLabel, replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        // ✅ Mark as Read Action
        val markReadIntent = Intent(this, NotificationReplyReceiver::class.java).apply {
            action = "com.codewithram.secretchat.MARK_READ_ACTION"
            putExtra("message_id", messageId)
            putExtra("action_type", "mark_read")
            putExtra("notification_id", notificationId)
        }



        val markReadPendingIntent = PendingIntent.getBroadcast(
            this, notificationId + 1,
            markReadIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_done, "Mark as Read", markReadPendingIntent
        ).build()

        // ✅ Tapping notification opens app
//        val intent = Intent(this, MainActivity::class.java)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to_chat", true)
            putExtra("conversation_id", conversationId)
            putExtra("sender_id", senderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, "Chat Messages", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val messagingStyle = NotificationCompat.MessagingStyle("You")
            .setConversationTitle(senderName)
            .addMessage(messageContent, System.currentTimeMillis(), senderName)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_message)
            .setStyle(messagingStyle)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()


        notificationManager.notify(notificationId, notification)

        // ✅ Immediately mark message as DELIVERED on receipt
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sharedPrefs = getSharedPreferences("secret_chat_prefs", Context.MODE_PRIVATE)
                val repository = Repository(sharedPrefs)

                repository.updateMessageStatus(
                    messageId,
                    MessageStatusUpdateRequest(
                        status_code = StatusEnumRequest.DELIVERED
                    )
                )
                Log.d(TAG, "✅ Message marked as DELIVERED on arrival")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to mark as delivered", e)
            }
        }
    }
}
