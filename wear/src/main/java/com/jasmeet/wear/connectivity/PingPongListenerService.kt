package com.jasmeet.wear.connectivity

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * Replies to PING with PONG even when [WatchActivity] is not in the foreground,
 * so the phone's heartbeat sees "wear app alive" whenever the APK is installed.
 */
class PingPongListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${event.path}")
        if (event.path == WearCapabilityManager.PING_PATH) {
            Wearable.getMessageClient(this)
                .sendMessage(event.sourceNodeId, WearCapabilityManager.PONG_PATH, byteArrayOf())
            Log.d(TAG, "Replied pong to ${event.sourceNodeId}")
        }
    }

    companion object {
        private const val TAG = "PingPongListener"
    }
}
