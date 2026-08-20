package ru.cherepokivan.standalonevoicebridge;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues short-lived, one-time pairing tokens to players that Minecraft has already authenticated.
 * Tokens are never written to disk or plugin logs, and a newly issued token revokes the old token
 * for the same player.
 */
public final class StandaloneTokenService {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Duration lifetime;
    private final int tokenLength;
    private final Map<String, IssuedToken> byToken = new HashMap<>();
    private final Map<UUID, String> tokenByPlayer = new HashMap<>();

    public StandaloneTokenService(Duration lifetime, int tokenLength) {
        this(lifetime, tokenLength, new SecureRandom(), Clock.systemUTC());
    }

    StandaloneTokenService(Duration lifetime, int tokenLength, SecureRandom secureRandom, Clock clock) {
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("Token lifetime must be positive.");
        }
        if (tokenLength < 8 || tokenLength > 64) {
            throw new IllegalArgumentException("Token length must be between 8 and 64 characters.");
        }
        this.lifetime = lifetime;
        this.tokenLength = tokenLength;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    /**
     * Replaces a player's previous unused token and returns a new token for private delivery in game.
     */
    public synchronized String issue(UUID playerId) {
        purgeExpired();
        revoke(playerId);

        String token;
        do {
            token = generateToken();
        } while (byToken.containsKey(token));

        byToken.put(token, new IssuedToken(playerId, clock.instant().plus(lifetime)));
        tokenByPlayer.put(playerId, token);
        return token;
    }

    /**
     * Atomically consumes a valid token. The external pairing endpoint must call this exactly once.
     */
    public synchronized Optional<UUID> consume(String suppliedToken) {
        purgeExpired();
        String token = normalize(suppliedToken);
        IssuedToken issued = byToken.remove(token);
        if (issued == null || !issued.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }

        tokenByPlayer.remove(issued.playerId(), token);
        return Optional.of(issued.playerId());
    }

    public synchronized void revoke(UUID playerId) {
        String token = tokenByPlayer.remove(playerId);
        if (token != null) {
            byToken.remove(token);
        }
    }

    public synchronized void purgeExpired() {
        Instant now = clock.instant();
        byToken.entrySet().removeIf(entry -> {
            if (!entry.getValue().expiresAt().isAfter(now)) {
                tokenByPlayer.remove(entry.getValue().playerId(), entry.getKey());
                return true;
            }
            return false;
        });
    }

    public Duration lifetime() {
        return lifetime;
    }

    public static String display(String token) {
        String normalized = normalize(token);
        int midpoint = normalized.length() / 2;
        return normalized.substring(0, midpoint) + "-" + normalized.substring(midpoint);
    }

    private String generateToken() {
        StringBuilder builder = new StringBuilder(tokenLength);
        for (int index = 0; index < tokenLength; index++) {
            builder.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }

    private static String normalize(String token) {
        if (token == null) {
            return "";
        }
        return token.replace("-", "").replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    private record IssuedToken(UUID playerId, Instant expiresAt) {
    }
}
