package ru.cherepokivan.standalonevoicechat.protocol

import java.util.UUID

enum class ProtocolVersion { Auto, SimpleVoiceChat262 }

data class VoiceGroupSnapshot(
    val id: UUID,
    val name: String,
    val participantCount: Int,
    val isPrivate: Boolean,
    val isJoined: Boolean
)

/**
 * Short-lived server-issued material. This class intentionally holds a private copy and clears it on close.
 * It must never be serialized into preferences, logs, crash reports, QR data or notifications.
 */
class SessionBootstrap(
    val playerId: UUID,
    val voiceHost: String,
    val voicePort: Int,
    val protocolVersion: ProtocolVersion,
    secret: ByteArray,
    val mtuSize: Int,
    val keepAliveMilliseconds: Int,
    val groupsEnabled: Boolean
) : AutoCloseable {
    private var sessionSecret: ByteArray? = secret.copyOf()

    init {
        require(playerId != UUID(0L, 0L)) { "Player identity must be specified." }
        require(voiceHost.isNotBlank()) { "Voice host must be specified." }
        require(voicePort in 1..65535) { "Voice port must be in range 1..65535." }
        require(secret.isNotEmpty()) { "Session secret must not be empty." }
    }

    fun secretCopy(): ByteArray = sessionSecret?.copyOf() ?: throw IllegalStateException("Session bootstrap is closed.")

    override fun close() {
        sessionSecret?.fill(0)
        sessionSecret = null
    }
}

data class ProtocolHandshakeResult(val isConnected: Boolean, val message: String)

interface SvcProtocolAdapter {
    val version: ProtocolVersion
    suspend fun connect(bootstrap: SessionBootstrap): ProtocolHandshakeResult
}

/**
 * No private protocol bytes are inferred here. The official SVC API currently provides no documented
 * standalone bootstrap, therefore this implementation remains deliberately fail-closed.
 */
class SimpleVoiceChat26Adapter : SvcProtocolAdapter {
    override val version = ProtocolVersion.SimpleVoiceChat262

    override suspend fun connect(bootstrap: SessionBootstrap): ProtocolHandshakeResult =
        ProtocolHandshakeResult(
            isConnected = false,
            message = "Подключение остановлено безопасно: сервер не предоставил официальный standalone bootstrap."
        )
}
