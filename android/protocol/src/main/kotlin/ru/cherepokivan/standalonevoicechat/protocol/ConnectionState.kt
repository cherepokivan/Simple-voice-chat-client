package ru.cherepokivan.standalonevoicechat.protocol

enum class ConnectionState {
    Disconnected,
    Connecting,
    Authenticating,
    Connected,
    JoiningGroup,
    ConnectedToGroup,
    Disconnecting,
    Error
}

class ConnectionStateMachine {
    private val transitions: Map<ConnectionState, Set<ConnectionState>> = mapOf(
        ConnectionState.Disconnected to setOf(ConnectionState.Connecting),
        ConnectionState.Connecting to setOf(ConnectionState.Authenticating, ConnectionState.Error, ConnectionState.Disconnecting),
        ConnectionState.Authenticating to setOf(ConnectionState.Connected, ConnectionState.Error, ConnectionState.Disconnecting),
        ConnectionState.Connected to setOf(ConnectionState.JoiningGroup, ConnectionState.Disconnecting, ConnectionState.Error),
        ConnectionState.JoiningGroup to setOf(ConnectionState.ConnectedToGroup, ConnectionState.Connected, ConnectionState.Error, ConnectionState.Disconnecting),
        ConnectionState.ConnectedToGroup to setOf(ConnectionState.Connected, ConnectionState.Disconnecting, ConnectionState.Error),
        ConnectionState.Disconnecting to setOf(ConnectionState.Disconnected, ConnectionState.Error),
        ConnectionState.Error to setOf(ConnectionState.Disconnected, ConnectionState.Connecting)
    )

    var state: ConnectionState = ConnectionState.Disconnected
        private set

    fun transitionTo(next: ConnectionState) {
        check(next in transitions.getValue(state)) { "Transition from $state to $next is not allowed." }
        state = next
    }

    fun fail() {
        state = ConnectionState.Error
    }
}
