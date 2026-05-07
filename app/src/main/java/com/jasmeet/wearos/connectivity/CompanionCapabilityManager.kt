package com.jasmeet.wearos.connectivity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the paired Wear OS peer from the phone side using the kc-android pattern:
 *   - [deviceConnected] — watch is paired & reachable via BT/Wi-Fi (CapabilityClient)
 *   - [appAlive]        — wear companion app responded to our PING within timeout
 *   - [wearNodeId]      — node id of the currently reachable watch, if any
 *
 * `null` = not yet determined. Values flip to true/false after the first heartbeat.
 *
 * Separating device vs. app presence is important: a watch can be connected over
 * Bluetooth (official companion app shows "connected") while our wear APK isn't
 * installed — CapabilityClient tells us which nodes have our app.
 */
class CompanionCapabilityManager(
    private val context: Context,
) : MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {

    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)
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
        capabilityClient.addListener(this, WEAR_CAPABILITY)
        checkConnection()
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    fun stop() {
        Log.d(TAG, "stop()")
        messageClient.removeListener(this)
        capabilityClient.removeListener(this)
        handler.removeCallbacks(heartbeatRunnable)
        handler.removeCallbacks(pongTimeoutRunnable)
    }

    fun refresh() {
        checkConnection()
    }

    fun launchWatchActivity() {
        val nodeId = _wearNodeId.value ?: return
        Log.d(TAG, "launchWatchActivity() nodeId=$nodeId")

        val remoteActivityHelper = RemoteActivityHelper(context, ContextCompat.getMainExecutor(context))
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("wearos://launch"))

        remoteActivityHelper.startRemoteActivity(intent, nodeId)
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        Log.d(TAG, "onCapabilityChanged: $info")
        updateNodes(info.nodes)
    }

    private fun checkConnection() {
        capabilityClient.getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capabilityInfo ->
                Log.d(TAG, "getCapability success: ${capabilityInfo.nodes}")
                updateNodes(capabilityInfo.nodes)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get capabilities", e)
                _deviceConnected.value = false
                _appAlive.value = false
            }
    }

    private fun updateNodes(nodes: Set<Node>) {
        val best = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
        Log.d(TAG, "Updating nodes: $nodes best=${best?.id}")

        if (best == null) {
            _wearNodeId.value = null
            _deviceConnected.value = false
            _appAlive.value = false
            return
        }

        _wearNodeId.value = best.id
        _deviceConnected.value = true

        // Only ping if we are not already waiting for a pong
        // or if we haven't confirmed app is alive yet
        if (!waitingForPong || _appAlive.value != true) {
            waitingForPong = true
            handler.removeCallbacks(pongTimeoutRunnable)
            handler.postDelayed(pongTimeoutRunnable, PONG_TIMEOUT_MS)

            messageClient.sendMessage(best.id, PING_PATH, byteArrayOf())
                .addOnSuccessListener {
                    Log.d(TAG, "Ping sent successfully to ${best.id}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send ping to ${best.id}", e)
                    waitingForPong = false
                    handler.removeCallbacks(pongTimeoutRunnable)
                    _appAlive.value = false
                }
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
        private const val WEAR_CAPABILITY = "jasmeet_wearos_wear"
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val PONG_TIMEOUT_MS = 5_000L
    }
}
