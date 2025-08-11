package com.d4rk.cleaner.app.clean.clipboard.services

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import com.d4rk.cleaner.core.utils.extensions.clearClipboardCompat

class ClipboardNotificationListenerService : NotificationListenerService() {
    private val screenReceiver = ScreenReceiver()

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            migrateNotificationFilter(0, null)
        }
    }

    private class ScreenReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val manager = context?.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                manager?.clearClipboardCompat()
            }
        }
    }
}