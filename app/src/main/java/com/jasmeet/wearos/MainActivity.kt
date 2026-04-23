package com.jasmeet.wearos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jasmeet.wearos.connectivity.CompanionCapabilityManager
import com.jasmeet.wearos.ui.theme.WearOsTheme

class MainActivity : ComponentActivity() {

    private lateinit var capabilityManager: CompanionCapabilityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        capabilityManager = CompanionCapabilityManager(applicationContext)

        setContent {
            WearOsTheme {
                CompanionScreen(manager = capabilityManager)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        capabilityManager.start()
    }

    override fun onStop() {
        super.onStop()
        capabilityManager.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanionScreen(manager: CompanionCapabilityManager) {
    val deviceConnected by manager.deviceConnected.collectAsState()
    val appAlive by manager.appAlive.collectAsState()
    val nodeId by manager.wearNodeId.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("Companion · WearOs") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(
                title = "Watch connected",
                value = deviceConnected.label(onText = "Yes", offText = "No"),
                detail = deviceConnected.describeDevice(),
                state = deviceConnected.toState(),
            )
            StatusCard(
                title = "Wear app alive",
                value = appAlive.label(onText = "Yes", offText = "No"),
                detail = appAlive.describeApp(),
                state = appAlive.toState(),
            )
            StatusCard(
                title = "Watch node id",
                value = nodeId ?: "—",
                detail = if (nodeId != null) "Active reachable node" else "No reachable node",
                state = if (nodeId != null) StatusState.On else StatusState.Off,
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = manager::refresh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Refresh")
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
