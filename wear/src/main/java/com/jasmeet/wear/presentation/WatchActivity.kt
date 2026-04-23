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
import androidx.compose.runtime.remember
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
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.jasmeet.wear.connectivity.WearCapabilityManager
import com.jasmeet.wear.presentation.theme.WearOsTheme

class WatchActivity : ComponentActivity() {

    private lateinit var capabilityManager: WearCapabilityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        capabilityManager = WearCapabilityManager(applicationContext)
        setContent {
            WearApp(manager = capabilityManager)
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

@Composable
fun WearApp(manager: WearCapabilityManager) {
    val deviceConnected by manager.deviceConnected.collectAsState()
    val appAlive by manager.appAlive.collectAsState()
    val phoneNodeId by manager.phoneNodeId.collectAsState()

    WearOsTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    EdgeButton(onClick = manager::refresh) {
                        Text("Refresh")
                    }
                },
            ) { contentPadding ->
                TransformingLazyColumn(
                    contentPadding = contentPadding,
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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

@WearPreviewDevices
@Composable
fun DefaultPreview() {
    WearOsTheme {
        AppScaffold {
            ScreenScaffold { _ ->
                Column {
                    StatusRow("Phone", "Yes", StatusState.On)
                    StatusRow("Phone app", "No", StatusState.Off)
                }
            }
        }
    }
}
