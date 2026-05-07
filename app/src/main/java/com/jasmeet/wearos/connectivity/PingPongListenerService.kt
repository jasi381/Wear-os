package com.jasmeet.wearos.connectivity

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * Replies to PING with PONG even when [MainActivity] is not in the foreground.
 * Without this, the watch-side heartbeat would only see "app alive" while our
 * UI is actually open — here we want the phone app to look alive whenever it's
 * installed on the device.
 */
class PingPongListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${event.path} source=${event.sourceNodeId}")
        if (event.path == CompanionCapabilityManager.PING_PATH) {
            Wearable.getMessageClient(this)
                .sendMessage(event.sourceNodeId, CompanionCapabilityManager.PONG_PATH, byteArrayOf())
                .addOnSuccessListener {
                    Log.d(TAG, "Replied pong to ${event.sourceNodeId}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to reply pong to ${event.sourceNodeId}", e)
                }
        }
    }

    companion object {
        private const val TAG = "PingPongListener"
    }
}
