package ru.cherepokivan.standalonevoicebridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTTPS client for the external relay. It never writes sensitive request bodies to logs. */
final class ExternalBootstrapClient {
    private static final Pattern STRING_FIELD = Pattern.compile("\\\"([A-Za-z][A-Za-z0-9]*)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    private final HttpClient http;
    private final URI baseUri;
    private final String serverId;
    private final String sharedSecret;

    ExternalBootstrapClient(String baseUrl, String serverId, String sharedSecret) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUri = normalizeBaseUri(baseUrl);
        this.serverId = requireText(serverId, "bridge server ID");
        this.sharedSecret = requireText(sharedSecret, "bridge shared secret");
        if (this.sharedSecret.length() < 32) {
            throw new IllegalArgumentException("Bridge shared secret must contain at least 32 characters.");
        }
    }

    void register(String tokenHash, UUID playerUuid) {
        String payload = "{\"tokenHash\":" + BridgeCrypto.jsonString(tokenHash) + ",\"playerUuid\":" + BridgeCrypto.jsonString(playerUuid.toString()) + "}";
        signedPost("register", "/api/plugin/register", payload, tokenHash, playerUuid.toString());
    }

    BridgeClaim check(String tokenHash) {
        String payload = "{\"tokenHash\":" + BridgeCrypto.jsonString(tokenHash) + "}";
        String response = signedPost("check", "/api/plugin/check", payload, tokenHash);
        String status = field(response, "status");
        return new BridgeClaim("claimed".equals(status), field(response, "requestId"));
    }

    void complete(String tokenHash, String requestId, String bootstrapJson) {
        String payload = "{\"tokenHash\":" + BridgeCrypto.jsonString(tokenHash)
            + ",\"requestId\":" + BridgeCrypto.jsonString(requestId)
            + ",\"bootstrap\":" + bootstrapJson + "}";
        signedPost("complete", "/api/plugin/complete", payload, tokenHash, requestId, bootstrapJson);
    }

    static String canonicalRequest(String action, String serverId, long timestamp, String... fields) {
        return String.join("\n", action, serverId, Long.toString(timestamp), String.join("\n", fields));
    }

    private String signedPost(String action, String path, String payload, String... canonicalFields) {
        long timestamp = Instant.now().toEpochMilli();
        String canonical = canonicalRequest(action, serverId, timestamp, canonicalFields);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("X-Bridge-Server", serverId)
            .header("X-Bridge-Timestamp", Long.toString(timestamp))
            .header("X-Bridge-Signature", BridgeCrypto.hmacBase64Url(sharedSecret, canonical))
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("External bridge rejected a request (HTTP " + response.statusCode() + ").");
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("External bridge request was interrupted.", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("External bridge request failed: " + exception.getClass().getSimpleName() + ".", exception);
        }
    }

    private static URI normalizeBaseUri(String value) {
        URI uri = URI.create(requireText(value, "bridge base URL"));
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Bridge base URL must be an HTTPS URL.");
        }
        return uri.resolve("/");
    }

    private static String field(String json, String name) {
        Matcher matcher = STRING_FIELD.matcher(json);
        while (matcher.find()) {
            if (name.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }

    private static String requireText(String value, String description) {
        String normalized = Objects.requireNonNull(value, description).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Missing " + description + ".");
        }
        return normalized;
    }

    record BridgeClaim(boolean claimed, String requestId) {
    }
}
