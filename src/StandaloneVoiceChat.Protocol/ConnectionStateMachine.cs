namespace StandaloneVoiceChat.Protocol;

/// <summary>
/// Guards session state transitions. The UI consumes this state, but does not own protocol logic.
/// </summary>
public sealed class ConnectionStateMachine
{
    private static readonly Dictionary<ConnectionState, IReadOnlySet<ConnectionState>> AllowedTransitions =
        new Dictionary<ConnectionState, IReadOnlySet<ConnectionState>>
        {
            [ConnectionState.Disconnected] = new HashSet<ConnectionState> { ConnectionState.Connecting },
            [ConnectionState.Connecting] = new HashSet<ConnectionState> { ConnectionState.Authenticating, ConnectionState.Error, ConnectionState.Disconnecting },
            [ConnectionState.Authenticating] = new HashSet<ConnectionState> { ConnectionState.Connected, ConnectionState.Error, ConnectionState.Disconnecting },
            [ConnectionState.Connected] = new HashSet<ConnectionState> { ConnectionState.JoiningGroup, ConnectionState.Disconnecting, ConnectionState.Error },
            [ConnectionState.JoiningGroup] = new HashSet<ConnectionState> { ConnectionState.ConnectedToGroup, ConnectionState.Connected, ConnectionState.Error, ConnectionState.Disconnecting },
            [ConnectionState.ConnectedToGroup] = new HashSet<ConnectionState> { ConnectionState.Connected, ConnectionState.Disconnecting, ConnectionState.Error },
            [ConnectionState.Disconnecting] = new HashSet<ConnectionState> { ConnectionState.Disconnected, ConnectionState.Error },
            [ConnectionState.Error] = new HashSet<ConnectionState> { ConnectionState.Disconnected, ConnectionState.Connecting }
        };

    public ConnectionState State { get; private set; } = ConnectionState.Disconnected;

    public event EventHandler<ConnectionState>? StateChanged;

    public bool CanTransitionTo(ConnectionState next) => AllowedTransitions[State].Contains(next);

    public void TransitionTo(ConnectionState next)
    {
        if (!CanTransitionTo(next))
        {
            throw new InvalidOperationException($"Transition from {State} to {next} is not allowed.");
        }

        State = next;
        StateChanged?.Invoke(this, next);
    }

    public void Fail()
    {
        if (State != ConnectionState.Error)
        {
            State = ConnectionState.Error;
            StateChanged?.Invoke(this, State);
        }
    }
}
