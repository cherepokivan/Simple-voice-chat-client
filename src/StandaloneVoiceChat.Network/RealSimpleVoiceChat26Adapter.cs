using StandaloneVoiceChat.Protocol;

namespace StandaloneVoiceChat.Network;

public sealed class RealSimpleVoiceChat26Adapter : ISvcProtocolAdapter
{
    public ProtocolVersion Version => ProtocolVersion.SimpleVoiceChat262;

    public async Task<ProtocolHandshakeResult> ConnectAsync(SessionBootstrap bootstrap, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(bootstrap);
        cancellationToken.ThrowIfCancellationRequested();

        const int handshakeTimeoutSeconds = 5;
        string stage = "создание UDP-сокета";

        try
        {
            using var transport = new SvcUdpTransport(bootstrap);

            // AuthenticatePacket (0x5): UUID (16 bytes) followed by the server-issued secret (16 bytes).
            stage = "отправка AuthenticatePacket (0x5)";
            byte[] authPayload = new byte[32];
            byte[] uuidBytes = GuidToBigEndianBytes(bootstrap.PlayerId);
            byte[] secretBytes = bootstrap.GetSecret().ToArray();
            Buffer.BlockCopy(uuidBytes, 0, authPayload, 0, 16);
            Buffer.BlockCopy(secretBytes, 0, authPayload, 16, 16);
            await transport.SendPacketAsync(0x5, authPayload, cancellationToken).ConfigureAwait(false);

            stage = "ожидание AuthenticateAck (0x6)";
            await WaitForPacketAsync(transport, 0x6, handshakeTimeoutSeconds, cancellationToken).ConfigureAwait(false);

            stage = "отправка ConnectionCheckPacket (0x9)";
            await transport.SendPacketAsync(0x9, Array.Empty<byte>(), cancellationToken).ConfigureAwait(false);

            stage = "ожидание ConnectionCheckAck (0xA)";
            await WaitForPacketAsync(transport, 0xA, handshakeTimeoutSeconds, cancellationToken).ConfigureAwait(false);

            return new ProtocolHandshakeResult(true, "Успешное подключение к UDP-серверу Simple Voice Chat.");
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (OperationCanceledException)
        {
            return ProtocolHandshakeResult.Unsupported($"Тайм-аут UDP-handshake: {stage} не завершён за {handshakeTimeoutSeconds} с.");
        }
        catch (Exception exception)
        {
            string detail = string.IsNullOrWhiteSpace(exception.Message)
                ? exception.GetType().Name
                : $"{exception.GetType().Name}: {exception.Message}";
            return ProtocolHandshakeResult.Unsupported($"Ошибка UDP-подключения на этапе «{stage}»: {detail}");
        }
    }

    private static async Task WaitForPacketAsync(
        SvcUdpTransport transport,
        byte expectedType,
        int timeoutSeconds,
        CancellationToken cancellationToken)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(timeoutSeconds));

        while (true)
        {
            (byte typeId, _) = await transport.ReceivePacketAsync(timeout.Token).ConfigureAwait(false);
            if (typeId == expectedType)
            {
                return;
            }
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
