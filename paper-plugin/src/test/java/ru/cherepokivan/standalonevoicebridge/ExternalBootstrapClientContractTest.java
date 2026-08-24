package ru.cherepokivan.standalonevoicebridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalBootstrapClientContractTest {
    @Test
    void canonicalRequestUsesRealNewlineSeparatorsCompatibleWithRelay() {
        String canonical = ExternalBootstrapClient.canonicalRequest(
            "register",
            "server-a",
            1_700_000_000_000L,
            "token-hash",
            "123e4567-e89b-42d3-a456-426614174000");

        assertEquals(
            "register\nserver-a\n1700000000000\ntoken-hash\n123e4567-e89b-42d3-a456-426614174000",
            canonical);
        assertEquals(
            "RajubqfxQAT7Yi2rMzBmNEi2vEI3pfGJeEvCNWN2lGA",
            BridgeCrypto.hmacBase64Url("01234567890123456789012345678901", canonical));
    }
}
