package com.jasmeet.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.jasmeet.wear.connectivity.WearCapabilityManager
import com.jasmeet.wear.data.DataLayerManager
import com.jasmeet.wear.presentation.theme.WearOsTheme

private enum class WearScreen { Home, Part1, Part2, Part3 }

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
    var screen by remember { mutableStateOf(WearScreen.Home) }

    BackHandler(enabled = screen != WearScreen.Home) { screen = WearScreen.Home }

    WearOsTheme {
        AppScaffold {
            when (screen) {
                WearScreen.Home  -> WearHomeScreen(onNavigate = { screen = it })
                WearScreen.Part1 -> WearPart1Screen(capabilityManager)
                WearScreen.Part2 -> WearPart2Screen(capabilityManager, dataLayerManager)
                WearScreen.Part3 -> WearPart3Screen(capabilityManager)
            }
        }
    }
}

// ── Home ──────────────────────────────────────────────────────────────────────

@Composable
private fun WearHomeScreen(onNavigate: (WearScreen) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            contentPadding = contentPadding,
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("WearOs Demo", fontWeight = FontWeight.Bold)
                }
            }
            item {
                Text(
                    text = "Wearable Data Layer step-by-step",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                )
            }
            item {
                WearPartCard(
                    number = "1",
                    title = "Peer Detection",
                    description = "NodeClient + ping/pong",
                    onClick = { onNavigate(WearScreen.Part1) },
                )
            }
            item {
                WearPartCard(
                    number = "2",
                    title = "Data Messaging",
                    description = "MessageClient + DataClient",
                    onClick = { onNavigate(WearScreen.Part2) },
                )
            }
            item {
                WearPartCard(
                    number = "3",
                    title = "Remote Activity",
                    description = "Launch activities across devices",
                    onClick = { onNavigate(WearScreen.Part3) },
                    enabled = true,
                )
            }
        }
    }
}

@Composable
private fun WearPartCard(
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
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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

@Composable
private fun WearPart1Screen(manager: WearCapabilityManager) {
    val deviceConnected by manager.deviceConnected.collectAsState()
    val appAlive by manager.appAlive.collectAsState()
    val phoneNodeId by manager.phoneNodeId.collectAsState()

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = manager::refresh) { Text("Refresh") }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            contentPadding = contentPadding,
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("Part 1 · Peer Detection")
                }
            }
            item {
                StatusRow("Phone", deviceConnected.label(), deviceConnected.toState())
            }
            item {
                StatusRow("Phone app", appAlive.label(), appAlive.toState())
            }
            item {
                StatusRow(
                    label = "Node",
                    value = phoneNodeId?.take(6) ?: "—",
                    state = if (phoneNodeId != null) StatusState.On else StatusState.Off,
                )
            }
        }
    }
}

// ── Part 2 — Data Messaging ───────────────────────────────────────────────────

@Composable
private fun WearPart2Screen(
    capabilityManager: WearCapabilityManager,
    dataLayerManager: DataLayerManager,
) {
    val phoneNodeId by capabilityManager.phoneNodeId.collectAsState()
    val lastReceived by dataLayerManager.lastReceivedMessage.collectAsState()
    val myCounter by dataLayerManager.myCounter.collectAsState()
    val peerCounter by dataLayerManager.peerCounter.collectAsState()

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            contentPadding = contentPadding,
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("Part 2 · Data Messaging")
                }
            }

            // MessageClient
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
                ActionCard(
                    label = "Say Hi!",
                    detail = "Send via MessageClient",
                    enabled = phoneNodeId != null,
                    onClick = {
                        phoneNodeId?.let { dataLayerManager.sendMessage(it, "Hi from Watch!") }
                    },
                )
            }

            // DataClient
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("Tap Counter")
                }
            }
            item {
                StatusRow("My taps", "$myCounter", StatusState.On)
            }
            item {
                StatusRow(
                    label = "Phone taps",
                    value = peerCounter?.toString() ?: "—",
                    state = if (peerCounter != null) StatusState.On else StatusState.Unknown,
                )
            }
            item {
                ActionCard(
                    label = "Tap  +1",
                    detail = "Sync via DataClient",
                    enabled = true,
                    onClick = dataLayerManager::incrementMyCounter,
                )
            }
        }
    }
}

// ── Part 3 — Remote Activity ──────────────────────────────────────────────────

@Composable
private fun WearPart3Screen(manager: WearCapabilityManager) {
    val phoneNodeId by manager.phoneNodeId.collectAsState()
    val appAlive by manager.appAlive.collectAsState()

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = manager::launchPhoneActivity,
                enabled = phoneNodeId != null && appAlive == true,
            ) { Text("Launch Phone App") }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            contentPadding = contentPadding,
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("Part 3 · Remote Activity")
                }
            }
            item {
                StatusRow(
                    label = "Target Phone",
                    value = phoneNodeId?.take(6) ?: "No phone",
                    state = if (phoneNodeId != null) StatusState.On else StatusState.Off,
                )
            }
            item {
                Text(
                    text = "Launch an activity on your phone via RemoteActivityHelper.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                )
            }
        }
    }
}

// ── Shared UI components ──────────────────────────────────────────────────────

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
private fun ActionCard(
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(if (enabled) StatusState.On else StatusState.Off)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(state: StatusState) {
    val color = when (state) {
        StatusState.On      -> Color(0xFF4CAF50)
        StatusState.Off     -> Color(0xFFE53935)
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

private fun Boolean?.toState() = when (this) {
    true  -> StatusState.On
    false -> StatusState.Off
    null  -> StatusState.Unknown
}

private fun Boolean?.label() = when (this) {
    true  -> "Yes"
    false -> "No"
    null  -> "…"
}
