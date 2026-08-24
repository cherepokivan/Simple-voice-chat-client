package ru.cherepokivan.standalonevoicechat.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SvcUdpTransport(private val bootstrap: SessionBootstrap) : AutoCloseable {
    private val socket = DatagramSocket()
    private val address = InetAddress.getByName(bootstrap.voiceHost)
    private val port = bootstrap.voicePort
    private val secretKey = SecretKeySpec(bootstrap.secretCopy(), "AES")
    private val secureRandom = SecureRandom()
    private val playerIdBytes = uuidToBytes(bootstrap.playerId)

    suspend fun sendPacket(typeId: Byte, payload: ByteArray) = withContext(Dispatchers.IO) {
        val unencryptedData = ByteArray(1 + payload.size)
        unencryptedData[0] = typeId
        System.arraycopy(payload, 0, unencryptedData, 1, payload.size)

        val nonce = ByteArray(12)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
        val ciphertextWithTag = cipher.doFinal(unencryptedData)

        val datagram = ByteArray(16 + 12 + ciphertextWithTag.size)
        System.arraycopy(playerIdBytes, 0, datagram, 0, 16)
        System.arraycopy(nonce, 0, datagram, 16, 12)
        System.arraycopy(ciphertextWithTag, 0, datagram, 28, ciphertextWithTag.size)

        val packet = DatagramPacket(datagram, datagram.size, address, port)
        socket.send(packet)
    }

    suspend fun receivePacket(): Pair<Byte, ByteArray> = withContext(Dispatchers.IO) {
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)

        val length = packet.length
        if (length < 45) {
            throw IllegalStateException("Received datagram is too small")
        }

        val nonce = ByteArray(12)
        System.arraycopy(buffer, 16, nonce, 0, 12)

        val ciphertextWithTag = ByteArray(length - 28)
        System.arraycopy(buffer, 28, ciphertextWithTag, 0, ciphertextWithTag.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
        val unencryptedData = cipher.doFinal(ciphertextWithTag)

        val typeId = unencryptedData[0]
        val payload = ByteArray(unencryptedData.size - 1)
        System.arraycopy(unencryptedData, 1, payload, 0, payload.size)

        Pair(typeId, payload)
    }

    private fun uuidToBytes(uuid: UUID): ByteArray {
        val buffer = ByteBuffer.allocate(16)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }

    override fun close() {
        socket.close()
    }
}
