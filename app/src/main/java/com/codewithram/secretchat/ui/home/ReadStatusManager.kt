package com.codewithram.secretchat.ui.home

import android.content.Context

class ReadStatusManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("read_status_prefs", Context.MODE_PRIVATE)

    fun getLastReadTimestamp(conversationId: String): Long {
        return prefs.getLong("last_read_$conversationId", 0L)
    }

    fun setLastReadTimestamp(conversationId: String, timestamp: Long) {
        prefs.edit().putLong("last_read_$conversationId", timestamp).apply()
    }
}
