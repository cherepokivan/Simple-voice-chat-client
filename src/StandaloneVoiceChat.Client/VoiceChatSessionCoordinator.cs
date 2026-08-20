using StandaloneVoiceChat.Network;
using StandaloneVoiceChat.Protocol;

namespace StandaloneVoiceChat.Client;

/// <summary>
/// Coordinates a safe connection attempt. It does not manufacture SVC credentials and remains fail-closed
/// when the server has not supplied a verified bootstrap.
/// </summary>
public sealed class VoiceChatSessionCoordinator
{
    private readonly ConnectionDiagnosticsService _diagnostics;
    private readonly ISvcProtocolAdapter _protocolAdapter;

    public VoiceChatSessionCoordinator(ConnectionDiagnosticsService diagnostics, ISvcProtocolAdapter protocolAdapter)
    {
        _diagnostics = diagnostics;
        _protocolAdapter = protocolAdapter;
        StateMachine = new ConnectionStateMachine();
    }

    public ConnectionStateMachine StateMachine { get; }

    public async Task<ConnectionAttemptResult> DiagnoseAndConnectAsync(
        ServerEndpoint endpoint,
        SessionBootstrap? bootstrap,
        CancellationToken cancellationToken)
    {
        StateMachine.TransitionTo(ConnectionState.Connecting);
        IReadOnlyList<DiagnosticCheck> checks = await _diagnostics.ProbeAsync(endpoint, cancellationToken).ConfigureAwait(false);

        if (bootstrap is null)
        {
            StateMachine.Fail();
            return new ConnectionAttemptResult(
                checks,
                StateMachine.State,
                "Требуется официальный серверный bootstrap: UUID, короткоживущий секрет и согласованные параметры. Клиент не создаёт их самостоятельно.");
        }

        try
        {
            StateMachine.TransitionTo(ConnectionState.Authenticating);
            ProtocolHandshakeResult result = await _protocolAdapter.ConnectAsync(bootstrap, cancellationToken).ConfigureAwait(false);
            if (!result.IsConnected)
            {
                StateMachine.Fail();
                return new ConnectionAttemptResult(checks, StateMachine.State, result.Message);
            }

            StateMachine.TransitionTo(ConnectionState.Connected);
            return new ConnectionAttemptResult(checks, StateMachine.State, result.Message);
        }
        catch (OperationCanceledException)
        {
            StateMachine.TransitionTo(ConnectionState.Disconnecting);
            StateMachine.TransitionTo(ConnectionState.Disconnected);
            throw;
        }
        catch (Exception exception)
        {
            StateMachine.Fail();
            return new ConnectionAttemptResult(checks, StateMachine.State, $"Ошибка подключения: {exception.Message}");
        }
    }

    public void Disconnect()
    {
        if (StateMachine.State is ConnectionState.Disconnected)
        {
            return;
        }

        if (StateMachine.State != ConnectionState.Error)
        {
            StateMachine.TransitionTo(ConnectionState.Disconnecting);
        }

        StateMachine.TransitionTo(ConnectionState.Disconnected);
    }
}

public sealed record ConnectionAttemptResult(
    IReadOnlyList<DiagnosticCheck> Diagnostics,
    ConnectionState State,
    string Message);
