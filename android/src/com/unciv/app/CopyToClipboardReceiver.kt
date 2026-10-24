package com.unciv.app

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.badlogic.gdx.backends.android.AndroidApplication

/**
 * This Receiver can be called from an Action on the error Notification shown by MultiplayerTurnCheckWorker.
 * It copies the text given to it to clipboard and then shows a Toast.
 * It's intended to help find out why the Turn Notifier failed.
 */
class CopyToClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val clipboard: ClipboardManager = context.getSystemService(AndroidApplication.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("exception", intent.getStringExtra(MultiplayerTurnCheckWorker.CLIPBOARD_EXTRA))
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.resources.getString(R.string.Notify_Error_StackTrace_Toast), Toast.LENGTH_SHORT).show()
    }
}
