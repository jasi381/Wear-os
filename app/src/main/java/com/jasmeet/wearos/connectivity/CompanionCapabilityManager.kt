package com.jasmeet.wearos.connectivity

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the paired Wear OS peer from the phone side:
 *   - [peerReachable]  — watch is connected AND the wear app is running/available
 *   - [peerInstalled]  — wear app is installed on at least one paired node (may be offline)
 *   - [wearNodeId]     — node id of the currently reachable watch, if any
 *
 * `null` states mean "not yet determined". After [start] resolves the first query,
 * values flip to concrete true/false.
 *
 * Reachable going false triggers a FILTER_ALL re-check so we can distinguish
 * "watch went out of range / BT off" from "wear app was uninstalled".
 */
class CompanionCapabilityManager(
    context: Context,
) : CapabilityClient.OnCapabilityChangedListener {

    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)

    private val _peerInstalled = MutableStateFlow<Boolean?>(null)
    val peerInstalled: StateFlow<Boolean?> = _peerInstalled.asStateFlow()

    private val _peerReachable = MutableStateFlow<Boolean?>(null)
    val peerReachable: StateFlow<Boolean?> = _peerReachable.asStateFlow()

    private val _wearNodeId = MutableStateFlow<String?>(null)
    val wearNodeId: StateFlow<String?> = _wearNodeId.asStateFlow()

    fun start() {
        Log.d(TAG, "start()")

        capabilityClient.addListener(
            this,
            Uri.parse("wear://*/$WEAR_CAPABILITY"),
            CapabilityClient.FILTER_REACHABLE,
        )

        refresh()
    }

    fun stop() {
        Log.d(TAG, "stop()")
        capabilityClient.removeListener(this)
    }

    fun refresh() {
        capabilityClient
            .getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_ALL)
            .addOnSuccessListener { info ->
                _peerInstalled.value = info.nodes.isNotEmpty()
                Log.d(TAG, "FILTER_ALL installed=${info.nodes.isNotEmpty()} nodes=${info.nodes}")
            }
            .addOnFailureListener { e -> Log.e(TAG, "FILTER_ALL failed", e) }

        capabilityClient
            .getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener(::applyReachable)
            .addOnFailureListener { e -> Log.e(TAG, "FILTER_REACHABLE failed", e) }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name != WEAR_CAPABILITY) return
        Log.d(TAG, "onCapabilityChanged nodes=${info.nodes}")
        applyReachable(info)
    }

    private fun applyReachable(info: CapabilityInfo) {
        val reachable = info.nodes.isNotEmpty()
        _peerReachable.value = reachable
        _wearNodeId.value = info.nodes.firstOrNull()?.id

        if (reachable) {
            _peerInstalled.value = true
            return
        }

        // Reachable went false — was the app uninstalled, or is the watch just disconnected?
        capabilityClient
            .getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_ALL)
            .addOnSuccessListener { all ->
                _peerInstalled.value = all.nodes.isNotEmpty()
                Log.d(TAG, "recheck FILTER_ALL installed=${all.nodes.isNotEmpty()}")
            }
    }

    companion object {
        private const val TAG = "CompanionCapabilityMgr"
        private const val WEAR_CAPABILITY = "jasmeet_wearos_wear"
    }
}
