package com.codewithram.secretchat.ui.home

import android.content.Context
import androidx.core.content.edit

class ReadStatusManager(context: Context) {

    private val prefs = context.getSharedPreferences("read_status_prefs", Context.MODE_PRIVATE)

    fun setLastReadTimestamp(conversationId: String, timestamp: Long) {
        prefs.edit { putLong("last_read_$conversationId", timestamp) }
    }
}
