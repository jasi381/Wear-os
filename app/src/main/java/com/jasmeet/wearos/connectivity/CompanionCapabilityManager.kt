package com.jasmeet.wearos.connectivity

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the paired Wear OS peer from the phone side using the kc-android pattern:
 *   - [deviceConnected] — watch is paired & reachable via BT/Wi-Fi (NodeClient)
 *   - [appAlive]        — wear companion app responded to our PING within timeout
 *   - [wearNodeId]      — node id of the currently reachable watch, if any
 *
 * `null` = not yet determined. Values flip to true/false after the first heartbeat.
 *
 * Separating device vs. app presence is important: a watch can be connected over
 * Bluetooth (official companion app shows "connected") while our wear APK isn't
 * installed — CapabilityClient alone cannot tell these two apart.
 */
class CompanionCapabilityManager(
    context: Context,
) : MessageClient.OnMessageReceivedListener {

    private val nodeClient = Wearable.getNodeClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())

    private val _deviceConnected = MutableStateFlow<Boolean?>(null)
    val deviceConnected: StateFlow<Boolean?> = _deviceConnected.asStateFlow()

    private val _appAlive = MutableStateFlow<Boolean?>(null)
    val appAlive: StateFlow<Boolean?> = _appAlive.asStateFlow()

    private val _wearNodeId = MutableStateFlow<String?>(null)
    val wearNodeId: StateFlow<String?> = _wearNodeId.asStateFlow()

    private var waitingForPong = false

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            checkConnection()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    private val pongTimeoutRunnable = Runnable {
        if (waitingForPong) {
            waitingForPong = false
            _appAlive.value = false
            Log.w(TAG, "Pong timeout — watch connected but wear app not responding")
        }
    }

    fun start() {
        Log.d(TAG, "start()")
        messageClient.addListener(this)
        checkConnection()
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    fun stop() {
        Log.d(TAG, "stop()")
        messageClient.removeListener(this)
        handler.removeCallbacks(heartbeatRunnable)
        handler.removeCallbacks(pongTimeoutRunnable)
    }

    fun refresh() {
        checkConnection()
    }

    private fun checkConnection() {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes: List<Node> ->
                val best = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                Log.d(TAG, "connectedNodes=$nodes best=${best?.id}")

                if (best == null) {
                    _wearNodeId.value = null
                    _deviceConnected.value = false
                    _appAlive.value = false
                    return@addOnSuccessListener
                }

                _wearNodeId.value = best.id
                _deviceConnected.value = true

                waitingForPong = true
                handler.removeCallbacks(pongTimeoutRunnable)
                handler.postDelayed(pongTimeoutRunnable, PONG_TIMEOUT_MS)

                messageClient.sendMessage(best.id, PING_PATH, byteArrayOf())
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send ping", e)
                        waitingForPong = false
                        handler.removeCallbacks(pongTimeoutRunnable)
                        _appAlive.value = false
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get connected nodes", e)
                _deviceConnected.value = false
                _appAlive.value = false
            }
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${event.path}")

        when (event.path) {
            PONG_PATH -> {
                waitingForPong = false
                handler.removeCallbacks(pongTimeoutRunnable)
                _wearNodeId.value = event.sourceNodeId
                _deviceConnected.value = true
                _appAlive.value = true
                Log.i(TAG, "Pong from ${event.sourceNodeId} — wear app alive")
            }
            PING_PATH -> {
                _wearNodeId.value = event.sourceNodeId
                _deviceConnected.value = true
                _appAlive.value = true
                messageClient.sendMessage(event.sourceNodeId, PONG_PATH, byteArrayOf())
                Log.i(TAG, "Ping from ${event.sourceNodeId}, sent pong")
            }
        }
    }

    companion object {
        private const val TAG = "CompanionCapabilityMgr"
        const val PING_PATH = "/jasmeet/ping"
        const val PONG_PATH = "/jasmeet/pong"
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val PONG_TIMEOUT_MS = 5_000L
    }
}
