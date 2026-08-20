using StandaloneVoiceChat.Protocol;

namespace StandaloneVoiceChat.Tests;

public sealed class ServerEndpointTests
{
    [Fact]
    public void CreatesEndpointForHostname()
    {
        ServerEndpoint endpoint = ServerEndpoint.Create("play.example.test", 25565, 24454);

        Assert.Equal("play.example.test", endpoint.Host);
        Assert.False(endpoint.IsIpAddress);
    }

    [Fact]
    public void RejectsEmptyHostname()
    {
        Assert.Throws<ArgumentException>(() => ServerEndpoint.Create(" ", 25565, 24454));
    }
}
