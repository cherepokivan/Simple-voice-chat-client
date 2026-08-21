package ru.cherepokivan.standalonevoicechat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import ru.cherepokivan.standalonevoicechat.service.VoiceChatForegroundService
import ru.cherepokivan.standalonevoicechat.ui.VoiceChatApp
import ru.cherepokivan.standalonevoicechat.ui.theme.StandaloneVoiceChatTheme
import ru.cherepokivan.standalonevoicechat.viewmodel.VoiceChatViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: VoiceChatViewModel by viewModels()

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, VoiceChatForegroundService::class.java)
                    .setAction(VoiceChatForegroundService.ACTION_START)
            )
            viewModel.onForegroundAudioStarted()
        } else {
            viewModel.onPermissionDenied("Для работы с микрофоном требуется разрешение RECORD_AUDIO.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsState()
            StandaloneVoiceChatTheme {
                VoiceChatApp(
                    state = state,
                    onHostChanged = viewModel::updateHost,
                    onBootstrapRelayUrlChanged = viewModel::updateBootstrapRelayUrl,
                    onPairingCodeChanged = viewModel::updatePairingCode,
                    onMinecraftPortChanged = viewModel::updateMinecraftPort,
                    onVoicePortChanged = viewModel::updateVoicePort,
                    onConnect = viewModel::connectSafely,
                    onSaveServer = viewModel::saveCurrentServer,
                    onSelectServer = viewModel::selectServer,
                    onRemoveServer = viewModel::removeServer,
                    onImportShareCode = viewModel::importShareCode,
                    onForegroundAudio = ::ensureForegroundAudioPermission,
                    onToggleMute = viewModel::toggleMute,
                    onPttPressed = viewModel::setPushToTalkPressed,
                    onPttToggle = viewModel::togglePttMode,
                    onInputVolumeChanged = viewModel::updateInputVolume,
                    onOutputVolumeChanged = viewModel::updateOutputVolume
                )
            }
        }
    }

    private fun ensureForegroundAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, VoiceChatForegroundService::class.java)
                    .setAction(VoiceChatForegroundService.ACTION_START)
            )
            viewModel.onForegroundAudioStarted()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
