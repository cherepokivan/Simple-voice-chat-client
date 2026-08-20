package ru.cherepokivan.standalonevoicechat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerSharePayloadTest {
    @Test
    fun `round trips public server fields only`() {
        val original = ServerSharePayload("play.example.com", 25565, 24454)
        val parsed = ServerSharePayload.parse(original.encode()).getOrThrow()
        assertEquals(original, parsed)
    }

    @Test
    fun `rejects payload without a host`() {
        assertFalse(ServerSharePayload.parse("svc://server?voicePort=24454").isSuccess)
    }
}
