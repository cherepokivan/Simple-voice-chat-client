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

        val encryptedLength = 12 + ciphertextWithTag.size
        val lengthBytes = writeVarInt(encryptedLength)
        
        val datagram = ByteArray(1 + 16 + lengthBytes.size + encryptedLength)
        datagram[0] = 0xFF.toByte()
        System.arraycopy(playerIdBytes, 0, datagram, 1, 16)
        System.arraycopy(lengthBytes, 0, datagram, 17, lengthBytes.size)
        
        val payloadOffset = 17 + lengthBytes.size
        System.arraycopy(nonce, 0, datagram, payloadOffset, 12)
        System.arraycopy(ciphertextWithTag, 0, datagram, payloadOffset + 12, ciphertextWithTag.size)

        val packet = DatagramPacket(datagram, datagram.size, address, port)
        socket.send(packet)
    }

    suspend fun receivePacket(): Pair<Byte, ByteArray> = withContext(Dispatchers.IO) {
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)

        val length = packet.length
        if (length < 1 + 1 + 12 + 16) {
            throw IllegalStateException("Received datagram is too small")
        }

        if (buffer[0] != 0xFF.toByte()) {
            throw IllegalStateException("Invalid magic byte")
        }

        val varIntResult = readVarInt(buffer, 1)
        val encryptedLength = varIntResult.first
        val offset = varIntResult.second

        if (offset + encryptedLength > length || encryptedLength < 28) {
            throw IllegalStateException("Invalid datagram length")
        }

        val nonce = ByteArray(12)
        System.arraycopy(buffer, offset, nonce, 0, 12)

        val ciphertextWithTag = ByteArray(encryptedLength - 12)
        System.arraycopy(buffer, offset + 12, ciphertextWithTag, 0, ciphertextWithTag.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
        val unencryptedData = cipher.doFinal(ciphertextWithTag)

        val typeId = unencryptedData[0]
        val payload = ByteArray(unencryptedData.size - 1)
        System.arraycopy(unencryptedData, 1, payload, 0, payload.size)

        Pair(typeId, payload)
    }

    private fun writeVarInt(value: Int): ByteArray {
        var v = value
        val bytes = mutableListOf<Byte>()
        while ((v and -128) != 0) {
            bytes.add((v and 127 or 128).toByte())
            v = v ushr 7
        }
        bytes.add(v.toByte())
        return bytes.toByteArray()
    }

    private fun readVarInt(buffer: ByteArray, startIndex: Int): Pair<Int, Int> {
        var value = 0
        var position = 0
        var offset = startIndex
        var currentByte: Byte

        while (true) {
            currentByte = buffer[offset++]
            value = value or ((currentByte.toInt() and 0x7F) shl position)
            if ((currentByte.toInt() and 0x80) == 0) break
            position += 7
            if (position >= 32) throw IllegalStateException("VarInt is too big")
        }

        return Pair(value, offset)
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
