package com.codewithram.secretchat.service

import MessageStatusUpdateRequest
import ReplyRequest
import StatusEnumRequest
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.codewithram.secretchat.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReplyReceiver : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra("message_id") ?: return
        val actionType = intent.getStringExtra("action_type")
        val notificationId = intent.getIntExtra("notification_id", -1)

        val sharedPrefs = context.getSharedPreferences("secret_chat_prefs", Context.MODE_PRIVATE)
        val repository = Repository(sharedPrefs)
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence("key_text_reply")
            ?.toString()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when {
                    actionType == "mark_read" -> {
                        repository.updateMessageStatus(
                            messageId,
                            MessageStatusUpdateRequest(
                                status_code = StatusEnumRequest.READ,
                            )
                        )
                    }
                    !replyText.isNullOrBlank() -> {
                        repository.replyToMessage(
                            messageId,
                            ReplyRequest(content = replyText)
                        )
                    }
                }

                if (notificationId != -1) {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(notificationId)
                }

            } catch (e: Exception) {
                Log.e("NotificationReply", "Error handling notification action", e)
            }
        }
    }
}
