package ru.cherepokivan.standalonevoicechat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.cherepokivan.standalonevoicechat.data.SavedServer
import ru.cherepokivan.standalonevoicechat.data.SavedServersRepository
import ru.cherepokivan.standalonevoicechat.network.BootstrapRelayClient
import ru.cherepokivan.standalonevoicechat.network.DiagnosticCheck
import ru.cherepokivan.standalonevoicechat.network.SafeUdpDiagnostics
import ru.cherepokivan.standalonevoicechat.protocol.ConnectionState
import ru.cherepokivan.standalonevoicechat.protocol.ConnectionStateMachine
import ru.cherepokivan.standalonevoicechat.protocol.ProtocolVersion
import ru.cherepokivan.standalonevoicechat.protocol.RealSimpleVoiceChat26Adapter
import ru.cherepokivan.standalonevoicechat.protocol.ServerSharePayload
import ru.cherepokivan.standalonevoicechat.protocol.SessionBootstrap

class VoiceChatViewModel(application: Application) : AndroidViewModel(application) {
    private val stateMachine = ConnectionStateMachine()
    private val diagnostics = SafeUdpDiagnostics()
    private val bootstrapRelayClient = BootstrapRelayClient()
    private val protocolAdapter = RealSimpleVoiceChat26Adapter()
    private val savedServersRepository = SavedServersRepository(application)
    private val mutableState = MutableStateFlow(
        VoiceChatUiState(savedServers = savedServersRepository.getAll())
    )
    val state: StateFlow<VoiceChatUiState> = mutableState.asStateFlow()

    fun updateHost(host: String) = update { copy(host = host) }
    fun updateBootstrapRelayUrl(url: String) = update { copy(bootstrapRelayUrl = url) }
    fun updatePairingCode(code: String) = update { copy(pairingCode = code) }
    fun updateMinecraftPort(port: String) = update { copy(minecraftPort = port) }
    fun updateVoicePort(port: String) = update { copy(voicePort = port) }
    fun updateInputVolume(volume: Float) = update { copy(inputVolume = volume) }
    fun updateOutputVolume(volume: Float) = update { copy(outputVolume = volume) }
    fun toggleMute() = update { copy(muted = !muted) }
    fun setPushToTalkPressed(pressed: Boolean) = update { copy(isPushToTalkPressed = pressed) }
    fun togglePttMode() = update { copy(pushToTalkToggleMode = !pushToTalkToggleMode) }
    fun onForegroundAudioStarted() = update { copy(foregroundAudioActive = true, statusMessage = "Foreground microphone service is active. Voice packets remain disabled until server bootstrap is available.") }
    fun onPermissionDenied(message: String) = update { copy(statusMessage = message) }

    fun connectSafely() {
        val current = state.value
        val minecraftPort = current.minecraftPort.toIntOrNull()
        val voicePort = current.voicePort.toIntOrNull()
        if (current.host.isBlank() || minecraftPort == null || voicePort == null || minecraftPort !in 1..65535 || voicePort !in 1..65535) {
            update { copy(connectionState = ConnectionState.Error, statusMessage = "Enter a valid host and ports in the range 1–65535.") }
            return
        }
        stateMachine.transitionTo(ConnectionState.Connecting)
        update { copy(connectionState = stateMachine.state, statusMessage = "Checking endpoint and local UDP prerequisites…") }
        viewModelScope.launch {
            var bootstrap: SessionBootstrap? = null
            try {
                if (current.pairingCode.isNotBlank()) {
                    stateMachine.transitionTo(ConnectionState.Authenticating)
                    update { copy(connectionState = stateMachine.state, statusMessage = "Подтверждаем одноразовый код через защищённый relay…") }
                    val relayBootstrap = bootstrapRelayClient.exchange(current.bootstrapRelayUrl, current.pairingCode)
                    try {
                        bootstrap = SessionBootstrap(
                            playerId = relayBootstrap.playerUuid,
                            voiceHost = relayBootstrap.voiceHost,
                            voicePort = relayBootstrap.voicePort,
                            protocolVersion = ProtocolVersion.SimpleVoiceChat262,
                            secret = relayBootstrap.secret,
                            mtuSize = 1024,
                            keepAliveMilliseconds = 1_000,
                            groupsEnabled = false
                        )
                    } finally {
                        relayBootstrap.clearSecret()
                    }
                }
                
                val checks = diagnostics.probe(current.host.trim(), voicePort)
                
                if (bootstrap != null) {
                    val result = protocolAdapter.connect(bootstrap)
                    if (result.isConnected) {
                        stateMachine.transitionTo(ConnectionState.Connected)
                    } else {
                        stateMachine.fail()
                    }
                    update {
                        copy(
                            diagnostics = checks,
                            connectionState = stateMachine.state,
                            statusMessage = result.message
                        )
                    }
                } else {
                    stateMachine.fail()
                    update {
                        copy(
                            diagnostics = checks,
                            connectionState = stateMachine.state,
                            statusMessage = "Введите код из Minecraft для получения серверного bootstrap. Приложение не создаёт UUID, секрет или предполагаемый UDP handshake."
                        )
                    }
                }
            } catch (exception: Exception) {
                stateMachine.fail()
                update { copy(connectionState = stateMachine.state, statusMessage = exception.message ?: "Не удалось подтвердить код подключения.") }
            } finally {
                bootstrap?.close()
            }
        }
    }

    fun saveCurrentServer() {
        val current = state.value
        val minecraftPort = current.minecraftPort.toIntOrNull() ?: return
        val voicePort = current.voicePort.toIntOrNull() ?: return
        if (current.host.isBlank()) return
        val updated = savedServersRepository.upsert(current.serverName, current.host, minecraftPort, voicePort)
        update { copy(savedServers = updated, statusMessage = "Server saved locally. No secrets were stored.") }
    }

    fun selectServer(server: SavedServer) = update {
        copy(serverName = server.name, host = server.host, minecraftPort = server.minecraftPort.toString(), voicePort = server.voicePort.toString())
    }

    fun removeServer(id: String) = update { copy(savedServers = savedServersRepository.remove(id)) }

    fun importShareCode(value: String) {
        ServerSharePayload.parse(value).onSuccess { payload ->
            update { copy(host = payload.host, minecraftPort = payload.minecraftPort.toString(), voicePort = payload.voicePort.toString(), statusMessage = "Public server configuration imported. No credentials were included.") }
        }.onFailure { exception ->
            update { copy(statusMessage = "Invalid server share code: ${exception.message}") }
        }
    }

    private inline fun update(transform: VoiceChatUiState.() -> VoiceChatUiState) {
        mutableState.value = mutableState.value.transform()
    }
}

data class VoiceChatUiState(
    val serverName: String = "",
    val host: String = "",
    val bootstrapRelayUrl: String = "https://simple-voice-bootstrap-relay-next.vercel.app",
    val pairingCode: String = "",
    val minecraftPort: String = "25565",
    val voicePort: String = "24454",
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val statusMessage: String = "Enter a server address to run safe local diagnostics.",
    val diagnostics: List<DiagnosticCheck> = emptyList(),
    val savedServers: List<SavedServer> = emptyList(),
    val inputVolume: Float = 100f,
    val outputVolume: Float = 100f,
    val muted: Boolean = false,
    val isPushToTalkPressed: Boolean = false,
    val pushToTalkToggleMode: Boolean = false,
    val foregroundAudioActive: Boolean = false
)
