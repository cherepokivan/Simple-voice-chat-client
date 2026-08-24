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
        
        // Convert UUID to big-endian bytes (Java standard)
        byte[] uuidBytes = GuidToBigEndianBytes(_playerId);
        
        byte[] datagram = new byte[16 + 12 + ciphertext.Length + 16];
        Buffer.BlockCopy(uuidBytes, 0, datagram, 0, 16);
        Buffer.BlockCopy(nonce, 0, datagram, 16, 12);
        Buffer.BlockCopy(ciphertext, 0, datagram, 28, ciphertext.Length);
        Buffer.BlockCopy(tag, 0, datagram, 28 + ciphertext.Length, 16);

        await _udpClient.SendAsync(datagram, datagram.Length).ConfigureAwait(false);
    }

    public async Task<(byte TypeId, byte[] Payload)> ReceivePacketAsync(CancellationToken cancellationToken)
    {
        UdpReceiveResult result = await _udpClient.ReceiveAsync(cancellationToken).ConfigureAwait(false);
        byte[] datagram = result.Buffer;

        // Minimum size: UUID(16) + Nonce(12) + Tag(16) + TypeId(1) = 45 bytes
        if (datagram.Length < 45)
        {
            throw new InvalidOperationException("Received datagram is too small.");
        }

        // Extract parts
        byte[] uuidBytes = new byte[16];
        Buffer.BlockCopy(datagram, 0, uuidBytes, 0, 16);
        
        byte[] nonce = new byte[12];
        Buffer.BlockCopy(datagram, 16, nonce, 0, 12);
        
        int ciphertextLength = datagram.Length - 16 - 12 - 16;
        byte[] ciphertext = new byte[ciphertextLength];
        Buffer.BlockCopy(datagram, 28, ciphertext, 0, ciphertextLength);
        
        byte[] tag = new byte[16];
        Buffer.BlockCopy(datagram, 28 + ciphertextLength, tag, 0, 16);

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
