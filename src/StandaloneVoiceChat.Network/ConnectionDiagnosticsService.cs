using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using StandaloneVoiceChat.Protocol;

namespace StandaloneVoiceChat.Network;

public enum DiagnosticStatus
{
    Pending,
    Passed,
    Warning,
    Failed
}

public sealed record DiagnosticCheck(string Name, DiagnosticStatus Status, string Detail);

/// <summary>
/// Conservative diagnostics: it verifies local prerequisites and name resolution but never treats a
/// UDP Connect call as evidence that a remote SVC service authenticated the session.
/// </summary>
public sealed class ConnectionDiagnosticsService
{
    public async Task<IReadOnlyList<DiagnosticCheck>> ProbeAsync(ServerEndpoint endpoint, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(endpoint);
        var checks = new List<DiagnosticCheck>();

        try
        {
            IPAddress[] addresses = endpoint.IsIpAddress
                ? [IPAddress.Parse(endpoint.Host)]
                : await Dns.GetHostAddressesAsync(endpoint.Host, cancellationToken).ConfigureAwait(false);

            checks.Add(addresses.Length > 0
                ? new DiagnosticCheck("Адрес сервера", DiagnosticStatus.Passed, $"Разрешено адресов: {addresses.Length}.")
                : new DiagnosticCheck("Адрес сервера", DiagnosticStatus.Failed, "DNS не вернул адреса."));
        }
        catch (Exception exception) when (exception is SocketException or ArgumentException)
        {
            checks.Add(new DiagnosticCheck("Адрес сервера", DiagnosticStatus.Failed, $"Не удалось разрешить имя: {exception.Message}"));
        }

        checks.Add(ProbeLocalUdp());
        checks.Add(new DiagnosticCheck(
            "Голосовой UDP-порт",
            DiagnosticStatus.Warning,
            $"Назначен {endpoint.VoicePort}. Подтверждение доступности возможно только после серверного bootstrap и handshake."));
        checks.Add(new DiagnosticCheck(
            "Аутентификация и шифрование",
            DiagnosticStatus.Warning,
            "Ожидается официальный короткоживущий bootstrap от сервера; локальное создание секрета запрещено."));

        return checks;
    }

    private static DiagnosticCheck ProbeLocalUdp()
    {
        try
        {
            using var udp = new UdpClient(AddressFamily.InterNetwork);
            udp.Client.Bind(new IPEndPoint(IPAddress.Loopback, 0));
            return new DiagnosticCheck("Локальный UDP", DiagnosticStatus.Passed, "Локальный UDP-сокет доступен.");
        }
        catch (SocketException exception)
        {
            return new DiagnosticCheck("Локальный UDP", DiagnosticStatus.Failed, exception.Message);
        }
    }
}
