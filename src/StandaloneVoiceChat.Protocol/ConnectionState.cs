namespace StandaloneVoiceChat.Protocol;

/// <summary>Observable lifecycle of a voice-chat session.</summary>
public enum ConnectionState
{
    Disconnected,
    Connecting,
    Authenticating,
    Connected,
    JoiningGroup,
    ConnectedToGroup,
    Disconnecting,
    Error
}
