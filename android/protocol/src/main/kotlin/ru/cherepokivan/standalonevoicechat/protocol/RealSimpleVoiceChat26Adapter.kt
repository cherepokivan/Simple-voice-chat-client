package ru.cherepokivan.standalonevoicechat.protocol

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RealSimpleVoiceChat26Adapter : SvcProtocolAdapter {
    override val version = ProtocolVersion.SimpleVoiceChat262

    override suspend fun connect(bootstrap: SessionBootstrap): ProtocolHandshakeResult {
        return try {
            SvcUdpTransport(bootstrap).use { transport ->
                // 1. Send AuthenticatePacket (0x5)
                val authPayload = ByteArray(32)
                val uuidBytes = uuidToBytes(bootstrap.playerId)
                val secretBytes = bootstrap.secretCopy()
                System.arraycopy(uuidBytes, 0, authPayload, 0, 16)
                System.arraycopy(secretBytes, 0, authPayload, 16, 16)

                transport.sendPacket(0x5.toByte(), authPayload)

                // 2. Wait for AuthenticateAckPacket (0x6)
                withTimeout(5000) {
                    var authenticated = false
                    while (!authenticated) {
                        val (typeId, _) = transport.receivePacket()
                        if (typeId == 0x6.toByte()) {
                            authenticated = true
                        }
                    }
                }

                // 3. Send ConnectionCheckPacket (0x9)
                transport.sendPacket(0x9.toByte(), ByteArray(0))

                // 4. Wait for ConnectionCheckAckPacket (0xA)
                withTimeout(5000) {
                    var connected = false
                    while (!connected) {
                        val (typeId, _) = transport.receivePacket()
                        if (typeId == 0xA.toByte()) {
                            connected = true
                        }
                    }
                }

                ProtocolHandshakeResult(true, "Успешное подключение к UDP-серверу Simple Voice Chat.")
            }
        } catch (e: TimeoutCancellationException) {
            ProtocolHandshakeResult(false, "Тайм-аут подключения к UDP-серверу. Проверьте, открыт ли UDP-порт.")
        } catch (e: Exception) {
            ProtocolHandshakeResult(false, "Ошибка UDP-подключения: ${e.message}")
        }
    }

    private fun uuidToBytes(uuid: java.util.UUID): ByteArray {
        val buffer = ByteBuffer.allocate(16)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }
}
