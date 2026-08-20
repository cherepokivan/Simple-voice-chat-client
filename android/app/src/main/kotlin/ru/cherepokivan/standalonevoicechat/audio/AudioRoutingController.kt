package ru.cherepokivan.standalonevoicechat.audio

import android.content.Context
import android.media.AudioManager
import android.os.Build

class AudioRoutingController(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun prepareVoiceRoute() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.firstOrNull()?.let(audioManager::setCommunicationDevice)
        }
    }

    fun releaseVoiceRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    fun connectedOutputNames(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        audioManager.availableCommunicationDevices.map { it.productName.toString() }
    } else {
        listOf("System default")
    }
}
