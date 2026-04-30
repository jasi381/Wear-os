package com.jasmeet.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.jasmeet.wear.connectivity.WearCapabilityManager
import com.jasmeet.wear.data.DataLayerManager
import com.jasmeet.wear.presentation.theme.WearOsTheme

class WatchActivity : ComponentActivity() {

    private lateinit var capabilityManager: WearCapabilityManager
    private lateinit var dataLayerManager: DataLayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        capabilityManager = WearCapabilityManager(applicationContext)
        dataLayerManager = DataLayerManager(applicationContext)
        setContent {
            WearApp(
                capabilityManager = capabilityManager,
                dataLayerManager = dataLayerManager,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        capabilityManager.start()
        dataLayerManager.start()
    }

    override fun onStop() {
        super.onStop()
        capabilityManager.stop()
        dataLayerManager.stop()
    }
}

@Composable
fun WearApp(
    capabilityManager: WearCapabilityManager,
    dataLayerManager: DataLayerManager,
) {
    val deviceConnected by capabilityManager.deviceConnected.collectAsState()
    val appAlive by capabilityManager.appAlive.collectAsState()
    val phoneNodeId by capabilityManager.phoneNodeId.collectAsState()

    val lastReceived by dataLayerManager.lastReceivedMessage.collectAsState()
    val myCounter by dataLayerManager.myCounter.collectAsState()
    val peerCounter by dataLayerManager.peerCounter.collectAsState()

    WearOsTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    EdgeButton(onClick = capabilityManager::refresh) {
                        Text("Refresh")
                    }
                },
            ) { contentPadding ->
                TransformingLazyColumn(
                    contentPadding = contentPadding,
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // ── Peer Status ──────────────────────────────────
                    item {
                        ListHeader(modifier = Modifier.fillMaxWidth()) {
                            Text("WearOs · Peer")
                        }
                    }
                    item {
                        StatusRow(
                            label = "Phone",
                            value = deviceConnected.label(),
                            state = deviceConnected.toState(),
                        )
                    }
                    item {
                        StatusRow(
                            label = "Phone app",
                            value = appAlive.label(),
                            state = appAlive.toState(),
                        )
                    }
                    item {
                        StatusRow(
                            label = "Node",
                            value = phoneNodeId?.take(6) ?: "—",
                            state = if (phoneNodeId != null) StatusState.On else StatusState.Off,
                        )
                    }

                    // ── Messages ─────────────────────────────────────
                    item {
                        ListHeader(modifier = Modifier.fillMaxWidth()) {
                            Text("Messages")
                        }
                    }
                    item {
                        StatusRow(
                            label = "Received",
                            value = lastReceived ?: "—",
                            state = if (lastReceived != null) StatusState.On else StatusState.Unknown,
                        )
                    }
                    item {
                        Card(
                            onClick = {
                                val id = phoneNodeId
                                if (id != null) {
                                    dataLayerManager.sendMessage(id, "Hi from Watch!")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatusDot(
                                    if (phoneNodeId != null) StatusState.On else StatusState.Off
                                )
                                Text(
                                    text = "Say Hi!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // ── Tap Counter ───────────────────────────────────
                    item {
                        ListHeader(modifier = Modifier.fillMaxWidth()) {
                            Text("Tap Counter")
                        }
                    }
                    item {
                        StatusRow(
                            label = "My taps",
                            value = "$myCounter",
                            state = StatusState.On,
                        )
                    }
                    item {
                        StatusRow(
                            label = "Phone taps",
                            value = peerCounter?.toString() ?: "—",
                            state = if (peerCounter != null) StatusState.On else StatusState.Unknown,
                        )
                    }
                    item {
                        Card(
                            onClick = dataLayerManager::incrementMyCounter,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatusDot(StatusState.On)
                                Text(
                                    text = "Tap  +1",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, state: StatusState) {
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusDot(state)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(state: StatusState) {
    val color = when (state) {
        StatusState.On -> Color(0xFF4CAF50)
        StatusState.Off -> Color(0xFFE53935)
        StatusState.Unknown -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private enum class StatusState { On, Off, Unknown }

private fun Boolean?.toState(): StatusState = when (this) {
    true -> StatusState.On
    false -> StatusState.Off
    null -> StatusState.Unknown
}

private fun Boolean?.label(): String = when (this) {
    true -> "Yes"
    false -> "No"
    null -> "…"
}
