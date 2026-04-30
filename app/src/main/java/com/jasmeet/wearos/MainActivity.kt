package com.jasmeet.wearos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jasmeet.wearos.connectivity.CompanionCapabilityManager
import com.jasmeet.wearos.data.DataLayerManager
import com.jasmeet.wearos.ui.theme.WearOsTheme

class MainActivity : ComponentActivity() {

    private lateinit var capabilityManager: CompanionCapabilityManager
    private lateinit var dataLayerManager: DataLayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        capabilityManager = CompanionCapabilityManager(applicationContext)
        dataLayerManager = DataLayerManager(applicationContext)
        setContent {
            WearOsTheme {
                CompanionScreen(
                    capabilityManager = capabilityManager,
                    dataLayerManager = dataLayerManager,
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanionScreen(
    capabilityManager: CompanionCapabilityManager,
    dataLayerManager: DataLayerManager,
) {
    val deviceConnected by capabilityManager.deviceConnected.collectAsState()
    val appAlive by capabilityManager.appAlive.collectAsState()
    val nodeId by capabilityManager.wearNodeId.collectAsState()

    val lastReceived by dataLayerManager.lastReceivedMessage.collectAsState()
    val myCounter by dataLayerManager.myCounter.collectAsState()
    val peerCounter by dataLayerManager.peerCounter.collectAsState()

    var messageText by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Companion · WearOs") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            // ── Peer Status ──────────────────────────────────────────
            item { SectionHeader("Peer Status") }
            item {
                StatusCard(
                    title = "Watch connected",
                    value = deviceConnected.label(onText = "Yes", offText = "No"),
                    detail = deviceConnected.describeDevice(),
                    state = deviceConnected.toState(),
                )
            }
            item {
                StatusCard(
                    title = "Wear app alive",
                    value = appAlive.label(onText = "Yes", offText = "No"),
                    detail = appAlive.describeApp(),
                    state = appAlive.toState(),
                )
            }
            item {
                StatusCard(
                    title = "Watch node id",
                    value = nodeId ?: "—",
                    detail = if (nodeId != null) "Active reachable node" else "No reachable node",
                    state = if (nodeId != null) StatusState.On else StatusState.Off,
                )
            }
            item {
                OutlinedButton(
                    onClick = capabilityManager::refresh,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Refresh") }
            }

            // ── Messages (MessageClient) ─────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader("Messages  ·  MessageClient") }
            item {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Message to watch") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                Button(
                    onClick = {
                        val id = nodeId
                        if (id != null && messageText.isNotBlank()) {
                            dataLayerManager.sendMessage(id, messageText.trim())
                            messageText = ""
                        }
                    },
                    enabled = nodeId != null && messageText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send to Watch") }
            }
            item {
                StatusCard(
                    title = "Last received from Watch",
                    value = lastReceived ?: "Nothing yet",
                    detail = if (lastReceived != null) "Received via MessageClient" else "Waiting…",
                    state = if (lastReceived != null) StatusState.On else StatusState.Unknown,
                )
            }

            // ── Data Sync (DataClient) ───────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader("Tap Counter  ·  DataClient") }
            item {
                CounterCard(
                    myLabel = "Phone taps",
                    peerLabel = "Watch taps",
                    myCount = myCounter,
                    peerCount = peerCounter,
                    onIncrement = dataLayerManager::incrementMyCounter,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CounterCard(
    myLabel: String,
    peerLabel: String,
    myCount: Int,
    peerCount: Int?,
    onIncrement: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = myLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$myCount",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(onClick = onIncrement) { Text("Tap  +1") }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = peerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = peerCount?.toString() ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (peerCount != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "synced via DataClient",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    detail: String,
    state: StatusState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusDot(state)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(state: StatusState) {
    val color = when (state) {
        StatusState.On -> Color(0xFF2E7D32)
        StatusState.Off -> Color(0xFFC62828)
        StatusState.Unknown -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .size(14.dp)
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

private fun Boolean?.label(onText: String, offText: String): String = when (this) {
    true -> onText
    false -> offText
    null -> "Checking…"
}

private fun Boolean?.describeDevice(): String = when (this) {
    true -> "Watch paired & reachable over BT/Wi-Fi"
    false -> "No watch reachable (BT off, out of range, or unpaired)"
    null -> "Querying node client…"
}

private fun Boolean?.describeApp(): String = when (this) {
    true -> "Wear app responded to heartbeat ping"
    false -> "No pong — wear app not installed or not responding"
    null -> "Waiting for first heartbeat…"
}
