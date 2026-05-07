package com.jasmeet.wearos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
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

private enum class Screen { Home, Part1, Part2, Part3 }

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
                AppNav(
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

@Composable
private fun AppNav(
    capabilityManager: CompanionCapabilityManager,
    dataLayerManager: DataLayerManager,
) {
    var screen by remember { mutableStateOf(Screen.Home) }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (screen) {
        Screen.Home  -> HomeScreen(onNavigate = { screen = it })
        Screen.Part1 -> Part1Screen(capabilityManager, onBack = { screen = Screen.Home })
        Screen.Part2 -> Part2Screen(capabilityManager, dataLayerManager, onBack = { screen = Screen.Home })
        Screen.Part3 -> Part3Screen(capabilityManager, onBack = { screen = Screen.Home })
    }
}

// ── Home ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onNavigate: (Screen) -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("WearOs Demo", fontWeight = FontWeight.Bold) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Wearable Data Layer API — a step-by-step guide to phone ↔ watch communication.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            PartCard(
                number = "1",
                title = "Peer Detection",
                description = "Detect if the watch is physically reachable (BT/Wi-Fi) and whether the companion app is installed — independently.",
                onClick = { onNavigate(Screen.Part1) },
            )
            PartCard(
                number = "2",
                title = "Data Messaging",
                description = "Send real-time text messages with MessageClient and sync a persistent tap counter with DataClient.",
                onClick = { onNavigate(Screen.Part2) },
            )
            PartCard(
                number = "3",
                title = "Remote Activity",
                description = "Launch activities on the watch from the phone and vice versa — full bidirectional remote control.",
                onClick = { onNavigate(Screen.Part3) },
                enabled = true,
            )
        }
    }
}

@Composable
private fun PartCard(
    number: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Part 1 — Peer Detection ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Part1Screen(
    manager: CompanionCapabilityManager,
    onBack: () -> Unit,
) {
    val deviceConnected by manager.deviceConnected.collectAsState()
    val appAlive by manager.appAlive.collectAsState()
    val nodeId by manager.wearNodeId.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Part 1 · Peer Detection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Text(
                    text = "NodeClient checks physical BT/Wi-Fi reachability. A separate ping/pong heartbeat confirms the companion app is installed and responding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                    onClick = manager::refresh,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Refresh") }
            }
        }
    }
}

// ── Part 2 — Data Messaging ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Part2Screen(
    capabilityManager: CompanionCapabilityManager,
    dataLayerManager: DataLayerManager,
    onBack: () -> Unit,
) {
    val nodeId by capabilityManager.wearNodeId.collectAsState()
    val lastReceived by dataLayerManager.lastReceivedMessage.collectAsState()
    val myCounter by dataLayerManager.myCounter.collectAsState()
    val peerCounter by dataLayerManager.peerCounter.collectAsState()

    var messageText by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Part 2 · Data Messaging") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            // ── MessageClient ────────────────────────────────────────
            item { SectionHeader("MessageClient  —  real-time messages") }
            item {
                Text(
                    text = "Fire-and-forget delivery to a specific node. Message is dropped if the peer app is not in foreground.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

            // ── DataClient ───────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader("DataClient  —  synced tap counter") }
            item {
                Text(
                    text = "DataItems persist on the source node and sync to all connected nodes. Counter survives app restarts and reconnects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

// ── Part 3 — Remote Activity ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Part3Screen(
    manager: CompanionCapabilityManager,
    onBack: () -> Unit,
) {
    val deviceConnected by manager.deviceConnected.collectAsState()
    val appAlive by manager.appAlive.collectAsState()
    val nodeId by manager.wearNodeId.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Part 3 · Remote Activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Text(
                    text = "RemoteActivityHelper allows you to launch any activity on a remote node via a deep link. It requires the target activity to have a matching intent-filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                StatusCard(
                    title = "Target Node",
                    value = nodeId ?: "No watch",
                    detail = if (nodeId != null) "Watch app reachable" else "Connect a watch first",
                    state = if (nodeId != null) StatusState.On else StatusState.Off,
                )
            }

            item {
                Button(
                    onClick = manager::launchWatchActivity,
                    enabled = nodeId != null && appAlive == true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Launch Activity on Watch")
                }
            }

            item { Spacer(Modifier.height(10.dp)) }

            item { SectionHeader("How it works") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "1. Sender uses RemoteActivityHelper to send an ACTION_VIEW intent.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "2. Google Play Services routes the intent to the target node.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "3. Target system finds a matching <intent-filter> and starts the activity.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

// ── Shared UI components ──────────────────────────────────────────────────────

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
private fun StatusCard(title: String, value: String, detail: String, state: StatusState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
        StatusState.On      -> Color(0xFF2E7D32)
        StatusState.Off     -> Color(0xFFC62828)
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

private fun Boolean?.toState() = when (this) {
    true  -> StatusState.On
    false -> StatusState.Off
    null  -> StatusState.Unknown
}

private fun Boolean?.label(onText: String, offText: String) = when (this) {
    true  -> onText
    false -> offText
    null  -> "Checking…"
}

private fun Boolean?.describeDevice() = when (this) {
    true  -> "Watch paired & reachable over BT/Wi-Fi"
    false -> "No watch reachable (BT off, out of range, or unpaired)"
    null  -> "Querying node client…"
}

private fun Boolean?.describeApp() = when (this) {
    true  -> "Wear app responded to heartbeat ping"
    false -> "No pong — wear app not installed or not responding"
    null  -> "Waiting for first heartbeat…"
}