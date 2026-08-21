package ru.cherepokivan.standalonevoicechat.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import ru.cherepokivan.standalonevoicechat.data.SavedServer
import ru.cherepokivan.standalonevoicechat.network.DiagnosticCheck
import ru.cherepokivan.standalonevoicechat.protocol.ConnectionState
import ru.cherepokivan.standalonevoicechat.viewmodel.VoiceChatUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatApp(
    state: VoiceChatUiState,
    onHostChanged: (String) -> Unit,
    onBootstrapRelayUrlChanged: (String) -> Unit,
    onPairingCodeChanged: (String) -> Unit,
    onMinecraftPortChanged: (String) -> Unit,
    onVoicePortChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onSaveServer: () -> Unit,
    onSelectServer: (SavedServer) -> Unit,
    onRemoveServer: (String) -> Unit,
    onImportShareCode: (String) -> Unit,
    onForegroundAudio: () -> Unit,
    onToggleMute: () -> Unit,
    onPttPressed: (Boolean) -> Unit,
    onPttToggle: () -> Unit,
    onInputVolumeChanged: (Float) -> Unit,
    onOutputVolumeChanged: (Float) -> Unit
) {
    var shareCode by remember { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onImportShareCode)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Simple Voice Chat") })
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Standalone Android client", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SectionCard("Server") {
                    OutlinedTextField(value = state.host, onValueChange = onHostChanged, label = { Text("Server host") }, placeholder = { Text("play.example.com") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = state.minecraftPort, onValueChange = onMinecraftPortChanged, label = { Text("Minecraft") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = state.voicePort, onValueChange = onVoicePortChanged, label = { Text("Voice UDP") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = state.bootstrapRelayUrl, onValueChange = onBootstrapRelayUrlChanged, label = { Text("Bootstrap relay (HTTPS)") }, placeholder = { Text("https://relay.example.com") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = state.pairingCode, onValueChange = onPairingCodeChanged, label = { Text("Одноразовый код из Minecraft") }, placeholder = { Text("ABCD-EFGH-IJKL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConnect, modifier = Modifier.weight(1f)) { Text("Connect") }
                        FilledTonalButton(onClick = onSaveServer, modifier = Modifier.weight(1f)) { Text("Save server") }
                    }
                }
            }
            item {
                SectionCard("Connection") {
                    Text(connectionLabel(state.connectionState), color = connectionColor(state.connectionState), style = MaterialTheme.typography.titleMedium)
                    Text(state.statusMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                SectionCard("Voice groups") {
                    Text("Groups, members and join/leave actions appear only after a verified server bootstrap and normal SVC authorisation.")
                }
            }
            if (state.diagnostics.isNotEmpty()) {
                item { DiagnosticsCard(state.diagnostics) }
            }
            item {
                SectionCard("Push-to-talk") {
                    Text(if (state.pushToTalkToggleMode) "Toggle mode" else "Hold mode")
                    FilledTonalButton(onClick = onPttToggle) { Text("Switch PTT mode") }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(118.dp)
                            .pointerInput(state.pushToTalkToggleMode) {
                                detectTapGestures(
                                    onPress = {
                                        if (state.pushToTalkToggleMode) {
                                            onPttPressed(!state.isPushToTalkPressed)
                                        } else {
                                            onPttPressed(true)
                                            tryAwaitRelease()
                                            onPttPressed(false)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Mic, null, modifier = Modifier.size(44.dp))
                            Text(if (state.isPushToTalkPressed) "TRANSMIT REQUESTED" else "HOLD TO TALK")
                        }
                    }
                    Text("Audio is retained locally until a supported SVC bootstrap and protocol adapter are verified.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                SectionCard("Audio & background") {
                    Text("Input volume: ${state.inputVolume.toInt()}%")
                    Slider(value = state.inputVolume, onValueChange = onInputVolumeChanged, valueRange = 0f..150f)
                    Text("Output volume: ${state.outputVolume.toInt()}%")
                    Slider(value = state.outputVolume, onValueChange = onOutputVolumeChanged, valueRange = 0f..150f)
                    Text(if (state.muted) "Microphone muted" else "Microphone enabled")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onToggleMute, modifier = Modifier.weight(1f)) { Text(if (state.muted) "Unmute" else "Mute") }
                        FilledTonalButton(onClick = onForegroundAudio, modifier = Modifier.weight(1f)) { Text(if (state.foregroundAudioActive) "Service active" else "Enable background audio") }
                    }
                }
            }
            item {
                SectionCard("QR configuration") {
                    Text("Only public host and port data are accepted. Secrets, passwords and UUIDs are rejected by design.")
                    OutlinedTextField(value = shareCode, onValueChange = { shareCode = it }, label = { Text("Paste svc:// share code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onImportShareCode(shareCode) }, modifier = Modifier.weight(1f)) { Text("Import") }
                        FilledTonalButton(onClick = {
                            scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan Simple Voice Chat server configuration"))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.QrCodeScanner, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Scan")
                        }
                    }
                }
            }
            item { SavedServersCard(state.savedServers, onSelectServer, onRemoveServer) }
            item {
                Spacer(Modifier.height(16.dp))
                Text("No session credentials are stored, shown in notifications or included in QR codes.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        })
    }
}

@Composable
private fun DiagnosticsCard(checks: List<DiagnosticCheck>) = SectionCard("Connection diagnostics") {
    checks.forEach { check ->
        Text("${check.name}: ${check.status} — ${check.detail}", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
    }
}

@Composable
private fun SavedServersCard(servers: List<SavedServer>, onSelect: (SavedServer) -> Unit, onRemove: (String) -> Unit) = SectionCard("Saved servers") {
    if (servers.isEmpty()) Text("No saved servers yet.")
    servers.forEach { server ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(server.name)
                Text("${server.host}:${server.voicePort}", style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = { onSelect(server) }) { Text("Use") }
            IconButton(onClick = { onRemove(server.id) }) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> "● Disconnected"
    ConnectionState.Connecting -> "● Connecting…"
    ConnectionState.Authenticating -> "● Authenticating…"
    ConnectionState.Connected -> "● Connected"
    ConnectionState.JoiningGroup -> "● Joining group…"
    ConnectionState.ConnectedToGroup -> "● Connected to group"
    ConnectionState.Disconnecting -> "● Disconnecting…"
    ConnectionState.Error -> "● Connection blocked safely"
}

@Composable
private fun connectionColor(state: ConnectionState) = when (state) {
    ConnectionState.Error -> MaterialTheme.colorScheme.error
    ConnectionState.Connected, ConnectionState.ConnectedToGroup -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}
