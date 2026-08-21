package ru.cherepokivan.standalonevoicechat.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.UUID

/** Exchanges a short-lived Minecraft pairing code for a server-issued SVC bootstrap over HTTPS. */
class BootstrapRelayClient {
    suspend fun exchange(relayUrl: String, pairingCode: String): BootstrapSession = withContext(Dispatchers.IO) {
        val baseUrl = validateRelayUrl(relayUrl)
        val code = normalizeCode(pairingCode)
        val request = postJson("$baseUrl/api/pair/request", JSONObject().put("code", code))
        if (request.status == HttpURLConnection.HTTP_NOT_FOUND) {
            throw IllegalStateException("Код подключения недействителен или истёк.")
        }
        requireSuccess(request)
        val requestJson = JSONObject(request.body)
        val requestId = requestJson.optString("requestId")
        val readKey = requestJson.optString("readKey")
        val lifetimeSeconds = requestJson.optInt("expiresInSeconds", 0)
        if (requestId.length < 20 || readKey.length < 20 || lifetimeSeconds !in 1..300) {
            throw IllegalStateException("Relay вернул некорректные данные запроса.")
        }

        val deadline = System.currentTimeMillis() + lifetimeSeconds * 1_000L
        while (System.currentTimeMillis() < deadline) {
            val status = postJson("$baseUrl/api/pair/status", JSONObject().put("requestId", requestId).put("readKey", readKey))
            when (status.status) {
                HttpURLConnection.HTTP_ACCEPTED -> delay(1_000)
                HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE -> throw IllegalStateException("Код подключения истёк или был отозван.")
                else -> {
                    requireSuccess(status)
                    val response = JSONObject(status.body)
                    if (response.optString("status") != "ready") {
                        throw IllegalStateException("Relay вернул некорректный статус bootstrap.")
                    }
                    return@withContext parseBootstrap(response.getJSONObject("bootstrap"))
                }
            }
        }
        throw IllegalStateException("Срок действия кода подключения истёк до подтверждения сервером.")
    }

    private fun parseBootstrap(bootstrap: JSONObject): BootstrapSession {
        if (bootstrap.optString("protocol") != "svc-2.6") {
            throw IllegalStateException("Сервер вернул неподдерживаемую версию голосового протокола.")
        }
        val playerUuid = runCatching { UUID.fromString(bootstrap.getString("playerUuid")) }
            .getOrElse { throw IllegalStateException("Сервер вернул недопустимый UUID.") }
        val voiceHost = bootstrap.getString("voiceHost")
        val voicePort = bootstrap.getInt("voicePort")
        val expiresAtEpochMs = bootstrap.getLong("expiresAtEpochMs")
        val secret = decodeBase64Url(bootstrap.getString("secret"))
        if (voiceHost.isBlank() || voicePort !in 1..65535 || secret.size != 16 || expiresAtEpochMs <= System.currentTimeMillis()) {
            throw IllegalStateException("Сервер вернул недопустимый bootstrap.")
        }
        return BootstrapSession(playerUuid, voiceHost, voicePort, secret, expiresAtEpochMs)
    }

    private fun postJson(url: String, payload: JSONObject): HttpResult {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
        }
        return try {
            connection.outputStream.use { stream -> stream.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResult(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun validateRelayUrl(value: String): String {
        val url = URL(value.trim())
        if (url.protocol != "https" || url.host.isBlank()) {
            throw IllegalArgumentException("Адрес relay должен быть HTTPS URL.")
        }
        return value.trim().trimEnd('/')
    }

    private fun normalizeCode(value: String): String {
        val code = value.replace("-", "").trim().uppercase()
        if (code.length !in 8..64 || code.any { !((it in 'A'..'Z') || (it in '2'..'9')) }) {
            throw IllegalArgumentException("Введите одноразовый код подключения из Minecraft.")
        }
        return code
    }

    private fun decodeBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private fun requireSuccess(result: HttpResult) {
        if (result.status !in 200..299) {
            throw IllegalStateException("Relay временно недоступен (HTTP ${result.status}).")
        }
    }

    private data class HttpResult(val status: Int, val body: String)
}

data class BootstrapSession(
    val playerUuid: UUID,
    val voiceHost: String,
    val voicePort: Int,
    val secret: ByteArray,
    val expiresAtEpochMs: Long
) {
    fun clearSecret() = secret.fill(0)
}
