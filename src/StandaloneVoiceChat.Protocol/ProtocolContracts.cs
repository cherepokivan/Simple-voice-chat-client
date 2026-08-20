using System.Security.Cryptography;

namespace StandaloneVoiceChat.Protocol;

public enum ProtocolVersion
{
    Auto,
    SimpleVoiceChat262
}

/// <summary>
/// Short-lived, server-issued session material. It must never be persisted or logged.
/// </summary>
public sealed class SessionBootstrap : IDisposable
{
    private byte[]? _secret;

    public SessionBootstrap(
        Guid playerId,
        string voiceHost,
        int voicePort,
        ProtocolVersion protocolVersion,
        byte[] secret,
        int mtuSize,
        int keepAliveMilliseconds,
        bool groupsEnabled)
    {
        if (playerId == Guid.Empty)
        {
            throw new ArgumentException("Player identity must be specified.", nameof(playerId));
        }

        if (string.IsNullOrWhiteSpace(voiceHost))
        {
            throw new ArgumentException("Voice host must be specified.", nameof(voiceHost));
        }

        if (voicePort is < 1 or > 65535)
        {
            throw new ArgumentOutOfRangeException(nameof(voicePort));
        }

        if (secret.Length == 0)
        {
            throw new ArgumentException("Session secret must not be empty.", nameof(secret));
        }

        PlayerId = playerId;
        VoiceHost = voiceHost;
        VoicePort = voicePort;
        ProtocolVersion = protocolVersion;
        _secret = secret.ToArray();
        MtuSize = mtuSize;
        KeepAliveMilliseconds = keepAliveMilliseconds;
        GroupsEnabled = groupsEnabled;
    }

    public Guid PlayerId { get; }
    public string VoiceHost { get; }
    public int VoicePort { get; }
    public ProtocolVersion ProtocolVersion { get; }
    public int MtuSize { get; }
    public int KeepAliveMilliseconds { get; }
    public bool GroupsEnabled { get; }
    public bool IsDisposed => _secret is null;

    public ReadOnlyMemory<byte> GetSecret()
    {
        ObjectDisposedException.ThrowIf(_secret is null, this);
        return _secret;
    }

    public void Dispose()
    {
        if (_secret is not null)
        {
            CryptographicOperations.ZeroMemory(_secret);
            _secret = null;
        }

        GC.SuppressFinalize(this);
    }
}

public sealed record VoiceGroupSnapshot(Guid Id, string Name, int ParticipantCount, bool IsPrivate, bool IsJoined);

public sealed record ProtocolHandshakeResult(bool IsConnected, string Message)
{
    public static ProtocolHandshakeResult Unsupported(string message) => new(false, message);
}

/// <summary>
/// Internal protocol implementations are versioned deliberately. No UI code may serialize SVC packets.
/// </summary>
public interface ISvcProtocolAdapter
{
    ProtocolVersion Version { get; }

    Task<ProtocolHandshakeResult> ConnectAsync(SessionBootstrap bootstrap, CancellationToken cancellationToken);
}

/// <summary>
/// The researched SVC version requires server-issued bootstrap material. Packet support remains disabled
/// until an upstream-supported standalone bootstrap extension exists and is verified by integration tests.
/// </summary>
public sealed class SimpleVoiceChat26Adapter : ISvcProtocolAdapter
{
    public ProtocolVersion Version => ProtocolVersion.SimpleVoiceChat262;

    public Task<ProtocolHandshakeResult> ConnectAsync(SessionBootstrap bootstrap, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(bootstrap);
        cancellationToken.ThrowIfCancellationRequested();
        return Task.FromResult(ProtocolHandshakeResult.Unsupported(
            "Подключение остановлено безопасно: исследованная версия Simple Voice Chat не предоставляет публичный bootstrap-контракт для standalone-клиента."));
    }
}
