package com.jasmeet.wearos.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles real-time communication with the paired watch using two Data Layer APIs:
 *   - [MessageClient] for fire-and-forget text messages on [MESSAGE_PATH]
 *   - [DataClient] for a persistent tap counter that survives reconnects
 *
 * Counter paths are asymmetric so each side only reacts to the peer's writes:
 *   phone writes /jasmeet/counter/phone → watch reads it as peerCounter
 *   watch writes /jasmeet/counter/wear  → phone reads it as peerCounter
 */
class DataLayerManager(context: Context) :
    MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener {

    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    private val _lastReceivedMessage = MutableStateFlow<String?>(null)
    val lastReceivedMessage: StateFlow<String?> = _lastReceivedMessage.asStateFlow()

    private val _myCounter = MutableStateFlow(0)
    val myCounter: StateFlow<Int> = _myCounter.asStateFlow()

    private val _peerCounter = MutableStateFlow<Int?>(null)
    val peerCounter: StateFlow<Int?> = _peerCounter.asStateFlow()

    fun start() {
        messageClient.addListener(this)
        dataClient.addListener(this)
        // Restore peer counter from the last known DataItem (survives app restarts)
        dataClient.getDataItems(Uri.parse("wear://*$PEER_COUNTER_PATH"))
            .addOnSuccessListener { items ->
                for (item in items) {
                    val map = DataMapItem.fromDataItem(item).dataMap
                    _peerCounter.value = map.getInt(KEY_VALUE, 0)
                }
                items.release()
            }
    }

    fun stop() {
        messageClient.removeListener(this)
        dataClient.removeListener(this)
    }

    fun sendMessage(nodeId: String, text: String) {
        messageClient.sendMessage(nodeId, MESSAGE_PATH, text.toByteArray(Charsets.UTF_8))
            .addOnFailureListener { e -> Log.e(TAG, "sendMessage failed", e) }
    }

    fun incrementMyCounter() {
        val next = _myCounter.value + 1
        _myCounter.value = next
        // Timestamp forces the DataItem to be treated as changed even if the value is the same
        val request = PutDataMapRequest.create(MY_COUNTER_PATH).apply {
            dataMap.putInt(KEY_VALUE, next)
            dataMap.putLong("ts", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request)
            .addOnFailureListener { e -> Log.e(TAG, "putDataItem failed", e) }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == MESSAGE_PATH) {
            _lastReceivedMessage.value = String(event.data, Charsets.UTF_8)
            Log.d(TAG, "Message from ${event.sourceNodeId}: ${_lastReceivedMessage.value}")
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == PEER_COUNTER_PATH
            ) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                _peerCounter.value = map.getInt(KEY_VALUE, 0)
                Log.d(TAG, "Peer counter updated to ${_peerCounter.value}")
            }
        }
        dataEvents.release()
    }

    companion object {
        private const val TAG = "DataLayerMgr"
        const val MESSAGE_PATH = "/jasmeet/message"
        private const val MY_COUNTER_PATH = "/jasmeet/counter/phone"
        private const val PEER_COUNTER_PATH = "/jasmeet/counter/wear"
        private const val KEY_VALUE = "value"
    }
}
