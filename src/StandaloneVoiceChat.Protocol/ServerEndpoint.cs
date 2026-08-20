using System.Net;

namespace StandaloneVoiceChat.Protocol;

public sealed record ServerEndpoint(string Host, int MinecraftPort, int VoicePort)
{
    public static ServerEndpoint Create(string host, int minecraftPort, int voicePort)
    {
        if (string.IsNullOrWhiteSpace(host) || host.Trim().Length > 253)
        {
            throw new ArgumentException("Введите допустимый IP-адрес или имя сервера.", nameof(host));
        }

        ValidatePort(minecraftPort, nameof(minecraftPort));
        ValidatePort(voicePort, nameof(voicePort));
        return new ServerEndpoint(host.Trim(), minecraftPort, voicePort);
    }

    public bool IsIpAddress => IPAddress.TryParse(Host, out _);

    private static void ValidatePort(int port, string parameterName)
    {
        if (port is < 1 or > 65535)
        {
            throw new ArgumentOutOfRangeException(parameterName, "Порт должен находиться в диапазоне 1–65535.");
        }
    }
}
