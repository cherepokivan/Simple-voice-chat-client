using System.Buffers.Binary;
using System.Net.Sockets;
using System.Security.Cryptography;
using StandaloneVoiceChat.Protocol;

namespace StandaloneVoiceChat.Network;

/// <summary>
/// Handles UDP datagrams with AES-GCM encryption for Simple Voice Chat 2.6.x.
/// </summary>
public sealed class SvcUdpTransport : IDisposable
{
    private readonly UdpClient _udpClient;
    private readonly byte[] _secret;
    private readonly Guid _playerId;
    private readonly byte[] _playerIdBytes;
    
    // AES-GCM tag is always 16 bytes
    private const int GcmTagSize = 16;
    // Magic byte (varies by implementation, usually 0x00 or omitted on app layer, but let's assume raw payload for now)
    // UUID is 16 bytes
    
    public SvcUdpTransport(SessionBootstrap bootstrap)
    {
        _udpClient = new UdpClient();
        _udpClient.Connect(bootstrap.VoiceHost, bootstrap.VoicePort);
        
        _secret = bootstrap.GetSecret().ToArray();
        _playerId = bootstrap.PlayerId;
        _playerIdBytes = _playerId.ToByteArray(); // Note: Endianness matters, might need UUID specific serialization
    }

    public async Task SendPacketAsync(byte typeId, byte[] payload, CancellationToken cancellationToken)
    {
        // 1. Combine typeId and payload
        byte[] unencryptedData = new byte[1 + payload.Length];
        unencryptedData[0] = typeId;
        Buffer.BlockCopy(payload, 0, unencryptedData, 1, payload.Length);

        // 2. Generate random IV (12 bytes for AES-GCM)
        byte[] nonce = new byte[12];
        RandomNumberGenerator.Fill(nonce);

        // 3. Encrypt data
        byte[] ciphertext = new byte[unencryptedData.Length];
        byte[] tag = new byte[GcmTagSize];
        
        using (var aesGcm = new AesGcm(_secret, GcmTagSize))
        {
            aesGcm.Encrypt(nonce, unencryptedData, ciphertext, tag);
        }

        // 4. Construct final datagram: [UUID (16)] + [Nonce (12)] + [Ciphertext] + [Tag (16)]
        // Some implementations might include a magic byte at the very beginning.
        // Let's assume standard format: UUID + Nonce + Ciphertext + Tag
        
        // Construct final datagram: [Magic (0xFF)] + [UUID (16)] + [VarInt Length] + [Nonce (12)] + [Ciphertext] + [Tag (16)]
        byte[] uuidBytes = GuidToBigEndianBytes(_playerId);
        
        int encryptedLength = 12 + ciphertext.Length + 16;
        byte[] lengthBytes = WriteVarInt(encryptedLength);
        
        byte[] datagram = new byte[1 + 16 + lengthBytes.Length + encryptedLength];
        datagram[0] = 0xFF;
        Buffer.BlockCopy(uuidBytes, 0, datagram, 1, 16);
        Buffer.BlockCopy(lengthBytes, 0, datagram, 17, lengthBytes.Length);
        
        int payloadOffset = 17 + lengthBytes.Length;
        Buffer.BlockCopy(nonce, 0, datagram, payloadOffset, 12);
        Buffer.BlockCopy(ciphertext, 0, datagram, payloadOffset + 12, ciphertext.Length);
        Buffer.BlockCopy(tag, 0, datagram, payloadOffset + 12 + ciphertext.Length, 16);

        await _udpClient.SendAsync(datagram, datagram.Length).ConfigureAwait(false);
    }

    public async Task<(byte TypeId, byte[] Payload)> ReceivePacketAsync(CancellationToken cancellationToken)
    {
        UdpReceiveResult result = await _udpClient.ReceiveAsync(cancellationToken).ConfigureAwait(false);
        byte[] datagram = result.Buffer;

        // Server response format: [Magic (0xFF)] + [VarInt Length] + [Nonce (12)] + [Ciphertext] + [Tag (16)]
        if (datagram.Length < 1 + 1 + 12 + 16)
        {
            throw new InvalidOperationException("Received datagram is too small.");
        }

        if (datagram[0] != 0xFF)
        {
            throw new InvalidOperationException("Invalid magic byte.");
        }

        int offset = 1;
        int encryptedLength = ReadVarInt(datagram, ref offset);
        
        if (offset + encryptedLength > datagram.Length || encryptedLength < 28)
        {
            throw new InvalidOperationException("Invalid datagram length.");
        }

        byte[] nonce = new byte[12];
        Buffer.BlockCopy(datagram, offset, nonce, 0, 12);
        
        int ciphertextLength = encryptedLength - 12 - 16;
        byte[] ciphertext = new byte[ciphertextLength];
        Buffer.BlockCopy(datagram, offset + 12, ciphertext, 0, ciphertextLength);
        
        byte[] tag = new byte[16];
        Buffer.BlockCopy(datagram, offset + 12 + ciphertextLength, tag, 0, 16);

        // Decrypt
        byte[] unencryptedData = new byte[ciphertextLength];
        using (var aesGcm = new AesGcm(_secret, GcmTagSize))
        {
            aesGcm.Decrypt(nonce, ciphertext, tag, unencryptedData);
        }

        byte typeId = unencryptedData[0];
        byte[] payload = new byte[unencryptedData.Length - 1];
        Buffer.BlockCopy(unencryptedData, 1, payload, 0, payload.Length);

        return (typeId, payload);
    }

    private static byte[] WriteVarInt(int value)
    {
        using var ms = new MemoryStream(5);
        while ((value & -128) != 0)
        {
            ms.WriteByte((byte)(value & 127 | 128));
            value = (int)((uint)value >> 7);
        }
        ms.WriteByte((byte)value);
        return ms.ToArray();
    }

    private static int ReadVarInt(byte[] buffer, ref int offset)
    {
        int value = 0;
        int position = 0;
        byte currentByte;

        while (true)
        {
            currentByte = buffer[offset++];
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 32) throw new InvalidOperationException("VarInt is too big");
        }

        return value;
    }

    private static byte[] GuidToBigEndianBytes(Guid guid)
    {
        byte[] bytes = guid.ToByteArray();
        // C# Guid.ToByteArray() returns:
        // Data1 (4 bytes, little-endian)
        // Data2 (2 bytes, little-endian)
        // Data3 (2 bytes, little-endian)
        // Data4 (8 bytes, big-endian)
        // We need to reverse the first three parts to match Java's big-endian UUID.
        
        if (BitConverter.IsLittleEndian)
        {
            Array.Reverse(bytes, 0, 4);
            Array.Reverse(bytes, 4, 2);
            Array.Reverse(bytes, 6, 2);
        }
        return bytes;
    }

    public void Dispose()
    {
        _udpClient.Dispose();
        CryptographicOperations.ZeroMemory(_secret);
    }
}
