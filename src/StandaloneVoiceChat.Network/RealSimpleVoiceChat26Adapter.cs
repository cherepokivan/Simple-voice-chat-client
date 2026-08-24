using System.Diagnostics;
using StandaloneVoiceChat.Protocol;

namespace StandaloneVoiceChat.Network;

public sealed class RealSimpleVoiceChat26Adapter : ISvcProtocolAdapter
{
    public ProtocolVersion Version => ProtocolVersion.SimpleVoiceChat262;

    public async Task<ProtocolHandshakeResult> ConnectAsync(SessionBootstrap bootstrap, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(bootstrap);
        cancellationToken.ThrowIfCancellationRequested();

        try
        {
            using var transport = new SvcUdpTransport(bootstrap);

            // 1. Send AuthenticatePacket (0x5)
            // Payload: UUID (16 bytes) + Secret (16 bytes)
            byte[] authPayload = new byte[32];
            byte[] uuidBytes = GuidToBigEndianBytes(bootstrap.PlayerId);
            byte[] secretBytes = bootstrap.GetSecret().ToArray();
            
            Buffer.BlockCopy(uuidBytes, 0, authPayload, 0, 16);
            Buffer.BlockCopy(secretBytes, 0, authPayload, 16, 16);

            await transport.SendPacketAsync(0x5, authPayload, cancellationToken).ConfigureAwait(false);

            // 2. Wait for AuthenticateAckPacket (0x6)
            using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeoutCts.CancelAfter(TimeSpan.FromSeconds(5));

            bool authenticated = false;
            while (!authenticated)
            {
                var (typeId, payload) = await transport.ReceivePacketAsync(timeoutCts.Token).ConfigureAwait(false);
                if (typeId == 0x6) // AuthenticateAck
                {
                    authenticated = true;
                }
            }

            // 3. Send ConnectionCheckPacket (0x9)
            // Empty payload
            await transport.SendPacketAsync(0x9, Array.Empty<byte>(), cancellationToken).ConfigureAwait(false);

            // 4. Wait for ConnectionCheckAckPacket (0xA)
            bool connected = false;
            while (!connected)
            {
                var (typeId, payload) = await transport.ReceivePacketAsync(timeoutCts.Token).ConfigureAwait(false);
                if (typeId == 0xA) // ConnectionCheckAck
                {
                    connected = true;
                }
            }

            // Handshake successful!
            // In a full implementation, we would return a connected session object here,
            // or start background tasks for KeepAlive and Mic/Sound packets.
            
            return new ProtocolHandshakeResult(true, "Успешное подключение к UDP-серверу Simple Voice Chat.");
        }
        catch (OperationCanceledException)
        {
            return ProtocolHandshakeResult.Unsupported("Тайм-аут подключения к UDP-серверу. Пакеты AuthAck (0x6) не получены.");
        }
        catch (Exception ex)
        {
            return ProtocolHandshakeResult.Unsupported($"Ошибка UDP-подключения: {ex.Message}");
        }
    }
    
    private static byte[] GuidToBigEndianBytes(Guid guid)
    {
        byte[] bytes = guid.ToByteArray();
        if (BitConverter.IsLittleEndian)
        {
            Array.Reverse(bytes, 0, 4);
            Array.Reverse(bytes, 4, 2);
            Array.Reverse(bytes, 6, 2);
        }
        return bytes;
    }
}
