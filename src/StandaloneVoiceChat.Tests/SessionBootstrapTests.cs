using StandaloneVoiceChat.Protocol;

namespace StandaloneVoiceChat.Tests;

public sealed class SessionBootstrapTests
{
    [Fact]
    public void CopiesInputSecretAndRejectsAccessAfterDispose()
    {
        var original = new byte[] { 1, 2, 3, 4 };
        var bootstrap = new SessionBootstrap(
            Guid.NewGuid(),
            "voice.example.test",
            24454,
            ProtocolVersion.SimpleVoiceChat262,
            original,
            1024,
            1000,
            true);

        original[0] = 99;
        Assert.Equal((byte)1, bootstrap.GetSecret().Span[0]);

        bootstrap.Dispose();

        Assert.True(bootstrap.IsDisposed);
        Assert.Throws<ObjectDisposedException>(() => bootstrap.GetSecret());
    }

    [Theory]
    [InlineData(0)]
    [InlineData(65536)]
    public void RejectsInvalidPort(int port)
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => new SessionBootstrap(
            Guid.NewGuid(), "voice.example.test", port, ProtocolVersion.Auto, [1], 1024, 1000, false));
    }
}
