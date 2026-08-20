package ru.cherepokivan.standalonevoicechat.network

import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DiagnosticStatus { Pending, Passed, Warning, Failed }
data class DiagnosticCheck(val name: String, val status: DiagnosticStatus, val detail: String)

/**
 * Performs only non-invasive checks. It intentionally does not send inferred SVC packets and does not
 * interpret DatagramSocket.connect() as proof of remote UDP reachability.
 */
class SafeUdpDiagnostics {
    suspend fun probe(host: String, voicePort: Int): List<DiagnosticCheck> = withContext(Dispatchers.IO) {
        val checks = mutableListOf<DiagnosticCheck>()
        try {
            val addresses = InetAddress.getAllByName(host)
            checks += DiagnosticCheck("Server address", DiagnosticStatus.Passed, "Resolved ${addresses.size} address(es).")
        } catch (exception: Exception) {
            checks += DiagnosticCheck("Server address", DiagnosticStatus.Failed, "Unable to resolve address: ${exception.message}")
        }

        try {
            DatagramSocket().use { socket ->
                checks += DiagnosticCheck("Local UDP", DiagnosticStatus.Passed, "Local UDP socket is available on ${socket.localPort}.")
            }
        } catch (exception: Exception) {
            checks += DiagnosticCheck("Local UDP", DiagnosticStatus.Failed, exception.message ?: "Unable to open UDP socket.")
        }

        checks += DiagnosticCheck(
            "Voice UDP port",
            DiagnosticStatus.Warning,
            "Configured port $voicePort requires a server-issued bootstrap and verified handshake."
        )
        checks += DiagnosticCheck(
            "Authentication & encryption",
            DiagnosticStatus.Warning,
            "The client will only accept an official, short-lived server bootstrap."
        )
        checks
    }
}
