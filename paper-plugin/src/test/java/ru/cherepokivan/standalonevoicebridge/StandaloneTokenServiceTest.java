package ru.cherepokivan.standalonevoicebridge;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneTokenServiceTest {
    @Test
    void tokenCanOnlyBeConsumedOnce() {
        UUID playerId = UUID.randomUUID();
        StandaloneTokenService service = serviceAt(Instant.parse("2026-08-21T00:00:00Z"));
        String token = service.issue(playerId);

        assertEquals(Optional.of(playerId), service.consume(token));
        assertTrue(service.consume(token).isEmpty());
    }

    @Test
    void issuingReplacementRevokesPreviousToken() {
        UUID playerId = UUID.randomUUID();
        StandaloneTokenService service = serviceAt(Instant.parse("2026-08-21T00:00:00Z"));
        String oldToken = service.issue(playerId);
        String replacementToken = service.issue(playerId);

        assertNotEquals(oldToken, replacementToken);
        assertTrue(service.consume(oldToken).isEmpty());
        assertEquals(Optional.of(playerId), service.consume(replacementToken));
    }

    @Test
    void tokenIsDisplayedWithoutChangingItsValue() {
        UUID playerId = UUID.randomUUID();
        StandaloneTokenService service = serviceAt(Instant.parse("2026-08-21T00:00:00Z"));
        String token = service.issue(playerId);

        String displayed = StandaloneTokenService.display(token);

        assertEquals(token, displayed.replace("-", ""));
        assertFalse(displayed.contains("I"));
        assertFalse(displayed.contains("O"));
    }

    private static StandaloneTokenService serviceAt(Instant instant) {
        return new StandaloneTokenService(
            Duration.ofMinutes(2),
            12,
            new SecureRandom(),
            Clock.fixed(instant, ZoneOffset.UTC));
    }
}
