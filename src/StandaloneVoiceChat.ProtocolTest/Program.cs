using StandaloneVoiceChat.Client;
using StandaloneVoiceChat.Network;
using StandaloneVoiceChat.Protocol;

if (args.Length is < 1 or > 3)
{
    Console.WriteLine("Usage: StandaloneVoiceChat.ProtocolTest <host> [minecraftPort] [voicePort]");
    Console.WriteLine("Runs non-invasive address and local UDP diagnostics. It will not fabricate SVC identity or secrets.");
    return 2;
}

try
{
    string host = args[0];
    int minecraftPort = args.Length >= 2 ? int.Parse(args[1], System.Globalization.CultureInfo.InvariantCulture) : 25565;
    int voicePort = args.Length >= 3 ? int.Parse(args[2], System.Globalization.CultureInfo.InvariantCulture) : 24454;
    ServerEndpoint endpoint = ServerEndpoint.Create(host, minecraftPort, voicePort);
    var coordinator = new VoiceChatSessionCoordinator(new ConnectionDiagnosticsService(), new SimpleVoiceChat26Adapter());
    ConnectionAttemptResult result = await coordinator.DiagnoseAndConnectAsync(endpoint, bootstrap: null, CancellationToken.None);

    Console.WriteLine($"State: {result.State}");
    Console.WriteLine(result.Message);
    foreach (DiagnosticCheck check in result.Diagnostics)
    {
        Console.WriteLine($"[{check.Status}] {check.Name}: {check.Detail}");
    }

    return result.State == ConnectionState.Error ? 1 : 0;
}
catch (Exception exception) when (exception is ArgumentException or FormatException or OverflowException)
{
    Console.Error.WriteLine($"Invalid input: {exception.Message}");
    return 2;
}
