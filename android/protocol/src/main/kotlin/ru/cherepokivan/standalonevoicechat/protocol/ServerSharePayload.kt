package ru.cherepokivan.standalonevoicechat.protocol

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Public configuration payload. It deliberately contains no UUID, secret, password or authentication data. */
data class ServerSharePayload(
    val host: String,
    val minecraftPort: Int = 25565,
    val voicePort: Int = 24454
) {
    init {
        require(host.isNotBlank() && host.length <= 253) { "Host is invalid." }
        require(minecraftPort in 1..65535) { "Minecraft port is invalid." }
        require(voicePort in 1..65535) { "Voice port is invalid." }
    }

    fun encode(): String = "svc://server?host=${host.encoded()}&minecraftPort=$minecraftPort&voicePort=$voicePort"

    companion object {
        fun parse(value: String): Result<ServerSharePayload> = runCatching {
            val uri = URI(value.trim())
            require(uri.scheme == "svc" && uri.host == "server") { "Unsupported share code." }
            val values = uri.rawQuery.orEmpty().split('&').associate { part ->
                val pieces = part.split('=', limit = 2)
                pieces[0] to pieces.getOrElse(1) { "" }
            }
            val host = values["host"]?.let(::decode).orEmpty()
            val minecraftPort = values["minecraftPort"]?.toIntOrNull() ?: 25565
            val voicePort = values["voicePort"]?.toIntOrNull() ?: 24454
            ServerSharePayload(host, minecraftPort, voicePort)
        }

        private fun decode(value: String): String = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)
    }
}

private fun String.encoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
