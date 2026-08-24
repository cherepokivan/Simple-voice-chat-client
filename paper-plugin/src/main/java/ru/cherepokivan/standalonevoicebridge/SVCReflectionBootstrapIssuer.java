package ru.cherepokivan.standalonevoicebridge;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Version-isolated adapter for SVC 2.6.x internals. It uses only a server-authorised UUID that has
 * already completed Minecraft authentication and never accepts one from an external client.
 */
final class SVCReflectionBootstrapIssuer {
    private final Plugin voicechatPlugin;
    private final String publicVoiceHost;

    SVCReflectionBootstrapIssuer(Plugin voicechatPlugin, String publicVoiceHost) {
        this.voicechatPlugin = Objects.requireNonNull(voicechatPlugin, "voicechatPlugin");
        this.publicVoiceHost = requireHost(publicVoiceHost);
    }

    BootstrapData issue(UUID playerUuid) {
        try {
            ClassLoader loader = voicechatPlugin.getClass().getClassLoader();
            Class<?> voicechatClass = Class.forName("de.maxhenkel.voicechat.Voicechat", true, loader);
            Field serverEventsField = voicechatClass.getField("SERVER");
            Object serverEvents = serverEventsField.get(null);
            if (serverEvents == null) {
                throw new IllegalStateException("Simple Voice Chat server is not ready.");
            }
            Object server = serverEvents.getClass().getMethod("getServer").invoke(serverEvents);
            if (server == null) {
                throw new IllegalStateException("Simple Voice Chat UDP server is not available.");
            }

            Object secret = server.getClass().getMethod("getSecret", UUID.class).invoke(server, playerUuid);
            if (secret == null) {
                throw new IllegalStateException("Simple Voice Chat did not provide a session secret.");
            }
            byte[] secretBytes = (byte[]) secret.getClass().getMethod("getSecret").invoke(secret);
            if (secretBytes.length != 16) {
                throw new IllegalStateException("Unexpected Simple Voice Chat secret length.");
            }
            int voicePort = (Integer) server.getClass().getMethod("getPort").invoke(server);
            return new BootstrapData(
                "svc-2.6",
                playerUuid,
                Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes),
                publicVoiceHost,
                voicePort,
                System.currentTimeMillis() + 30_000L
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The installed Simple Voice Chat version is not compatible with the bootstrap adapter.", exception);
        }
    }

    private static String requireHost(String value) {
        String host = Objects.requireNonNull(value, "publicVoiceHost").trim();
        if (host.isBlank() || host.contains("://") || host.contains("/") || host.contains("\\\\")) {
            throw new IllegalArgumentException("public-voice-host must be a hostname or IP address only.");
        }
        return host;
    }

    record BootstrapData(String protocol, UUID playerUuid, String secret, String voiceHost, int voicePort, long expiresAtEpochMs) {
        String toJson() {
            return "{\"protocol\":" + BridgeCrypto.jsonString(protocol)
                + ",\"playerUuid\":" + BridgeCrypto.jsonString(playerUuid.toString())
                + ",\"secret\":" + BridgeCrypto.jsonString(secret)
                + ",\"voiceHost\":" + BridgeCrypto.jsonString(voiceHost)
                + ",\"voicePort\":" + voicePort
                + ",\"expiresAtEpochMs\":" + expiresAtEpochMs + "}";
        }
    }
}
