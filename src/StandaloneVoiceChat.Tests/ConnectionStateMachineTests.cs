using StandaloneVoiceChat.Protocol;

namespace StandaloneVoiceChat.Tests;

public sealed class ConnectionStateMachineTests
{
    [Fact]
    public void AllowsExpectedHandshakePath()
    {
        var stateMachine = new ConnectionStateMachine();

        stateMachine.TransitionTo(ConnectionState.Connecting);
        stateMachine.TransitionTo(ConnectionState.Authenticating);
        stateMachine.TransitionTo(ConnectionState.Connected);

        Assert.Equal(ConnectionState.Connected, stateMachine.State);
    }

    [Fact]
    public void RejectsInvalidTransition()
    {
        var stateMachine = new ConnectionStateMachine();

        Assert.Throws<InvalidOperationException>(() => stateMachine.TransitionTo(ConnectionState.Connected));
    }

    [Fact]
    public void FailureCanBeResetBeforeAnotherAttempt()
    {
        var stateMachine = new ConnectionStateMachine();
        stateMachine.TransitionTo(ConnectionState.Connecting);
        stateMachine.Fail();
        stateMachine.TransitionTo(ConnectionState.Disconnected);
        stateMachine.TransitionTo(ConnectionState.Connecting);

        Assert.Equal(ConnectionState.Connecting, stateMachine.State);
    }
}
