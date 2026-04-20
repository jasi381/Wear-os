package com.jasmeet.wear.connectivity

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
 * Tracks the paired phone peer from the watch side:
 *   - [peerReachable]  — phone is connected AND the companion app is running/available
 *   - [peerInstalled]  — companion app is installed on at least one paired node (may be offline)
 *   - [phoneNodeId]    — node id of the currently reachable phone, if any
 */
class WearCapabilityManager(
    context: Context,
) : CapabilityClient.OnCapabilityChangedListener {

    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)

    private val _peerInstalled = MutableStateFlow<Boolean?>(null)
    val peerInstalled: StateFlow<Boolean?> = _peerInstalled.asStateFlow()

    private val _peerReachable = MutableStateFlow<Boolean?>(null)
    val peerReachable: StateFlow<Boolean?> = _peerReachable.asStateFlow()

    private val _phoneNodeId = MutableStateFlow<String?>(null)
    val phoneNodeId: StateFlow<String?> = _phoneNodeId.asStateFlow()

    fun start() {
        Log.d(TAG, "start()")

        capabilityClient.addListener(
            this,
            Uri.parse("wear://*/$MOBILE_CAPABILITY"),
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
            .getCapability(MOBILE_CAPABILITY, CapabilityClient.FILTER_ALL)
            .addOnSuccessListener { info ->
                _peerInstalled.value = info.nodes.isNotEmpty()
                Log.d(TAG, "FILTER_ALL installed=${info.nodes.isNotEmpty()} nodes=${info.nodes}")
            }
            .addOnFailureListener { e -> Log.e(TAG, "FILTER_ALL failed", e) }

        capabilityClient
            .getCapability(MOBILE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener(::applyReachable)
            .addOnFailureListener { e -> Log.e(TAG, "FILTER_REACHABLE failed", e) }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name != MOBILE_CAPABILITY) return
        Log.d(TAG, "onCapabilityChanged nodes=${info.nodes}")
        applyReachable(info)
    }

    private fun applyReachable(info: CapabilityInfo) {
        val reachable = info.nodes.isNotEmpty()
        _peerReachable.value = reachable
        _phoneNodeId.value = info.nodes.firstOrNull()?.id

        if (reachable) {
            _peerInstalled.value = true
            return
        }

        capabilityClient
            .getCapability(MOBILE_CAPABILITY, CapabilityClient.FILTER_ALL)
            .addOnSuccessListener { all ->
                _peerInstalled.value = all.nodes.isNotEmpty()
                Log.d(TAG, "recheck FILTER_ALL installed=${all.nodes.isNotEmpty()}")
            }
    }

    companion object {
        private const val TAG = "WearCapabilityMgr"
        private const val MOBILE_CAPABILITY = "jasmeet_wearos_mobile"
    }
}
