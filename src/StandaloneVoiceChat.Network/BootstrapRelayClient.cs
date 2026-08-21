using System.Net.Http.Json;
using System.Text.Json;
using StandaloneVoiceChat.Protocol;

namespace StandaloneVoiceChat.Network;

/// <summary>
/// Exchanges a short-lived pairing code over HTTPS for server-issued session material.
/// Pairing codes and session secrets are neither logged nor persisted.
/// </summary>
public sealed class BootstrapRelayClient
{
    private readonly HttpClient _httpClient;

    public BootstrapRelayClient(HttpClient? httpClient = null)
    {
        _httpClient = httpClient ?? new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
    }

    public async Task<SessionBootstrap> ExchangeAsync(
        Uri relayBaseUri,
        string pairingCode,
        CancellationToken cancellationToken)
    {
        ValidateRelayUri(relayBaseUri);
        string normalizedCode = NormalizeCode(pairingCode);
        Uri requestUri = new(relayBaseUri, "api/pair/request");

        using HttpResponseMessage requestResponse = await _httpClient.PostAsJsonAsync(
            requestUri,
            new { code = normalizedCode },
            cancellationToken).ConfigureAwait(false);
        if (requestResponse.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            throw new InvalidOperationException("Код подключения недействителен или истёк.");
        }

        requestResponse.EnsureSuccessStatusCode();
        PairRequest request = await requestResponse.Content.ReadFromJsonAsync<PairRequest>(cancellationToken: cancellationToken).ConfigureAwait(false)
            ?? throw new InvalidOperationException("Relay не вернул данные запроса.");
        if (string.IsNullOrWhiteSpace(request.RequestId) || string.IsNullOrWhiteSpace(request.ReadKey) || request.ExpiresInSeconds is < 1 or > 300)
        {
            throw new InvalidOperationException("Relay вернул некорректные данные запроса.");
        }

        DateTimeOffset deadline = DateTimeOffset.UtcNow.AddSeconds(request.ExpiresInSeconds);
        Uri statusUri = new(relayBaseUri, "api/pair/status");
        while (DateTimeOffset.UtcNow < deadline)
        {
            cancellationToken.ThrowIfCancellationRequested();
            using HttpResponseMessage statusResponse = await _httpClient.PostAsJsonAsync(
                statusUri,
                new { requestId = request.RequestId, readKey = request.ReadKey },
                cancellationToken).ConfigureAwait(false);

            if (statusResponse.StatusCode == System.Net.HttpStatusCode.Accepted)
            {
                await Task.Delay(TimeSpan.FromSeconds(1), cancellationToken).ConfigureAwait(false);
                continue;
            }

            if (statusResponse.StatusCode == System.Net.HttpStatusCode.NotFound || statusResponse.StatusCode == System.Net.HttpStatusCode.Gone)
            {
                throw new InvalidOperationException("Код подключения истёк или был отозван.");
            }

            statusResponse.EnsureSuccessStatusCode();
            using JsonDocument json = await JsonDocument.ParseAsync(
                await statusResponse.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false),
                cancellationToken: cancellationToken).ConfigureAwait(false);
            JsonElement root = json.RootElement;
            if (!root.TryGetProperty("status", out JsonElement state) || state.GetString() != "ready" || !root.TryGetProperty("bootstrap", out JsonElement bootstrap))
            {
                throw new InvalidOperationException("Relay вернул некорректный ответ bootstrap.");
            }

            return ParseBootstrap(bootstrap);
        }

        throw new TimeoutException("Срок действия кода подключения истёк до подтверждения сервером.");
    }

    private static SessionBootstrap ParseBootstrap(JsonElement bootstrap)
    {
        string protocol = bootstrap.GetProperty("protocol").GetString() ?? string.Empty;
        if (!string.Equals(protocol, "svc-2.6", StringComparison.Ordinal))
        {
            throw new InvalidOperationException("Сервер вернул неподдерживаемую версию голосового протокола.");
        }

        Guid playerId = bootstrap.GetProperty("playerUuid").GetGuid();
        string voiceHost = bootstrap.GetProperty("voiceHost").GetString() ?? string.Empty;
        int voicePort = bootstrap.GetProperty("voicePort").GetInt32();
        long expiresAtEpochMs = bootstrap.GetProperty("expiresAtEpochMs").GetInt64();
        if (DateTimeOffset.FromUnixTimeMilliseconds(expiresAtEpochMs) <= DateTimeOffset.UtcNow)
        {
            throw new InvalidOperationException("Серверный bootstrap уже истёк.");
        }

        string encodedSecret = bootstrap.GetProperty("secret").GetString() ?? string.Empty;
        byte[] secret = DecodeBase64Url(encodedSecret);
        if (secret.Length != 16)
        {
            throw new InvalidOperationException("Сервер вернул секрет недопустимой длины.");
        }

        return new SessionBootstrap(
            playerId,
            voiceHost,
            voicePort,
            ProtocolVersion.SimpleVoiceChat262,
            secret,
            mtuSize: 1024,
            keepAliveMilliseconds: 1_000,
            groupsEnabled: false);
    }

    private static void ValidateRelayUri(Uri relayBaseUri)
    {
        ArgumentNullException.ThrowIfNull(relayBaseUri);
        if (!relayBaseUri.IsAbsoluteUri || !string.Equals(relayBaseUri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase) || string.IsNullOrWhiteSpace(relayBaseUri.Host))
        {
            throw new ArgumentException("Адрес relay должен быть абсолютным HTTPS URL.", nameof(relayBaseUri));
        }
    }

    private static string NormalizeCode(string pairingCode)
    {
        string code = (pairingCode ?? string.Empty).Replace("-", string.Empty, StringComparison.Ordinal).Trim().ToUpperInvariant();
        if (code.Length is < 8 or > 64 || code.Any(character => !((character is >= 'A' and <= 'Z') || (character is >= '2' and <= '9'))))
        {
            throw new ArgumentException("Введите одноразовый код подключения из Minecraft.", nameof(pairingCode));
        }

        return code;
    }

    private static byte[] DecodeBase64Url(string value)
    {
        string padded = value.Replace('-', '+').Replace('_', '/');
        padded = padded.PadRight(padded.Length + ((4 - padded.Length % 4) % 4), '=');
        return Convert.FromBase64String(padded);
    }

    private sealed record PairRequest(string? RequestId, string? ReadKey, int ExpiresInSeconds);
}
