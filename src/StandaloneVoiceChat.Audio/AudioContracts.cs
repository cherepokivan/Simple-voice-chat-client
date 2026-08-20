namespace StandaloneVoiceChat.Audio;

public sealed record AudioDeviceDescriptor(string Id, string DisplayName, bool IsDefault);

public sealed record AudioSettings(
    string InputDeviceId,
    string OutputDeviceId,
    double InputVolume,
    double OutputVolume,
    bool InputMuted,
    bool OutputMuted,
    bool PushToTalkEnabled,
    string PushToTalkKey);

public interface IAudioCapture : IAsyncDisposable
{
    event EventHandler<ReadOnlyMemory<short>>? FrameCaptured;
    Task StartAsync(CancellationToken cancellationToken);
    Task StopAsync(CancellationToken cancellationToken);
}

public interface IAudioPlayback : IAsyncDisposable
{
    Task PlayAsync(ReadOnlyMemory<short> pcmFrame, CancellationToken cancellationToken);
}

public interface IOpusCodec : IDisposable
{
    ReadOnlyMemory<byte> Encode(ReadOnlySpan<short> pcmFrame);
    ReadOnlyMemory<short> Decode(ReadOnlySpan<byte> opusPayload, int missingFrames);
}

/// <summary>
/// Keeps audio concerns separate from transport. Windows capture/playback and Opus are wired only when
/// a validated upstream SVC adapter is enabled.
/// </summary>
public sealed class AudioPipeline
{
    public AudioPipeline(IAudioCapture capture, IOpusCodec opus, IAudioPlayback playback)
    {
        Capture = capture;
        Opus = opus;
        Playback = playback;
    }

    public IAudioCapture Capture { get; }
    public IOpusCodec Opus { get; }
    public IAudioPlayback Playback { get; }
}
