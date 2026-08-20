package ru.cherepokivan.standalonevoicechat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConnectionStateMachineTest {
    @Test
    fun `allows the expected handshake path`() {
        val machine = ConnectionStateMachine()
        machine.transitionTo(ConnectionState.Connecting)
        machine.transitionTo(ConnectionState.Authenticating)
        machine.transitionTo(ConnectionState.Connected)
        assertEquals(ConnectionState.Connected, machine.state)
    }

    @Test
    fun `rejects invalid transition`() {
        val machine = ConnectionStateMachine()
        assertThrows(IllegalStateException::class.java) {
            machine.transitionTo(ConnectionState.Connected)
        }
    }
}
