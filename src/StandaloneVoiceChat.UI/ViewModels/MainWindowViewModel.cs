using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StandaloneVoiceChat.Client;
using StandaloneVoiceChat.Network;
using StandaloneVoiceChat.Protocol;

namespace StandaloneVoiceChat.UI.ViewModels;

public sealed partial class MainWindowViewModel : ObservableObject, IDisposable
{
    private readonly VoiceChatSessionCoordinator _coordinator;
    private CancellationTokenSource? _connectionCancellation;

    public MainWindowViewModel(VoiceChatSessionCoordinator coordinator)
    {
        _coordinator = coordinator;
        _coordinator.StateMachine.StateChanged += (_, _) => RefreshStateText();

        ProtocolVersions = new ObservableCollection<string>(["Auto", "Simple Voice Chat 26.2"]);
        InputDevices = new ObservableCollection<string>(["Default microphone (device discovery pending)"]);
        OutputDevices = new ObservableCollection<string>(["Default speakers (device discovery pending)"]);
        Groups = new ObservableCollection<VoiceGroupViewModel>();
        Diagnostics = new ObservableCollection<DiagnosticItemViewModel>(
        [
            new("Minecraft server", "Ожидает проверки"),
            new("Voice UDP port", "Ожидает bootstrap"),
            new("Authentication", "Требуется официальный server bootstrap"),
            new("Encryption", "Будет использован только штатный механизм SVC"),
            new("Audio input", "Устройство не инициализировано"),
            new("Audio output", "Устройство не инициализировано")
        ]);
        RefreshStateText();
    }

    public ObservableCollection<string> ProtocolVersions { get; }
    public ObservableCollection<string> InputDevices { get; }
    public ObservableCollection<string> OutputDevices { get; }
    public ObservableCollection<VoiceGroupViewModel> Groups { get; }
    public ObservableCollection<DiagnosticItemViewModel> Diagnostics { get; }

    [ObservableProperty]
    private string _serverHost = string.Empty;

    [ObservableProperty]
    private decimal _minecraftPort = 25565;

    [ObservableProperty]
    private decimal _voicePort = 24454;

    [ObservableProperty]
    private string _selectedProtocolVersion = "Auto";

    [ObservableProperty]
    private string _selectedInputDevice = "Default microphone (device discovery pending)";

    [ObservableProperty]
    private string _selectedOutputDevice = "Default speakers (device discovery pending)";

    [ObservableProperty]
    private bool _connectAutomatically;

    [ObservableProperty]
    private bool _inputMuted;

    [ObservableProperty]
    private bool _outputMuted;

    [ObservableProperty]
    private bool _pushToTalkEnabled = true;

    [ObservableProperty]
    private string _pushToTalkKey = "V";

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(InputVolumeLabel))]
    private double _inputVolume = 100;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(OutputVolumeLabel))]
    private double _outputVolume = 100;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ConnectButtonText))]
    private string _connectionStateText = "● Disconnected";

    [ObservableProperty]
    private string _connectionStateBrush = "#B8C4D4";

    [ObservableProperty]
    private string _statusMessage = "Введите адрес сервера и выполните диагностику. Полное подключение будет доступно только через официальный bootstrap сервера.";

    public string ConnectButtonText => _connectionCancellation is null ? "Connect" : "Cancel";
    public string InputVolumeLabel => $"{Math.Round(InputVolume):0}%";
    public string OutputVolumeLabel => $"{Math.Round(OutputVolume):0}%";

    [RelayCommand]
    private async Task ToggleConnectionAsync()
    {
        if (_connectionCancellation is not null)
        {
            _connectionCancellation.Cancel();
            return;
        }

        try
        {
            ServerEndpoint endpoint = ServerEndpoint.Create(ServerHost, decimal.ToInt32(MinecraftPort), decimal.ToInt32(VoicePort));
            _connectionCancellation = new CancellationTokenSource();
            OnPropertyChanged(nameof(ConnectButtonText));
            StatusMessage = "Checking endpoint and local network prerequisites…";

            ConnectionAttemptResult result = await _coordinator.DiagnoseAndConnectAsync(endpoint, bootstrap: null, _connectionCancellation.Token);
            Diagnostics.Clear();
            foreach (DiagnosticCheck check in result.Diagnostics)
            {
                Diagnostics.Add(new DiagnosticItemViewModel(check.Name, $"{check.Status}: {check.Detail}"));
            }

            StatusMessage = result.Message;
        }
        catch (OperationCanceledException)
        {
            StatusMessage = "Проверка отменена.";
        }
        catch (Exception exception)
        {
            StatusMessage = exception.Message;
        }
        finally
        {
            _connectionCancellation?.Dispose();
            _connectionCancellation = null;
            OnPropertyChanged(nameof(ConnectButtonText));
            RefreshStateText();
        }
    }

    public void Dispose()
    {
        _connectionCancellation?.Cancel();
        _connectionCancellation?.Dispose();
        _connectionCancellation = null;
        GC.SuppressFinalize(this);
    }

    private void RefreshStateText()
    {
        (ConnectionStateText, ConnectionStateBrush) = _coordinator.StateMachine.State switch
        {
            ConnectionState.Disconnected => ("● Disconnected", "#B8C4D4"),
            ConnectionState.Connecting => ("● Connecting…", "#5EA7FF"),
            ConnectionState.Authenticating => ("● Authenticating…", "#F8C555"),
            ConnectionState.Connected => ("● Connected", "#57D39B"),
            ConnectionState.JoiningGroup => ("● Joining group…", "#5EA7FF"),
            ConnectionState.ConnectedToGroup => ("● Connected to group", "#57D39B"),
            ConnectionState.Disconnecting => ("● Disconnecting…", "#F8C555"),
            ConnectionState.Error => ("● Connection blocked safely", "#F17878"),
            _ => ("● Unknown", "#B8C4D4")
        };
    }
}

public sealed record VoiceGroupViewModel(string Name, int Participants)
{
    public string ParticipantLabel => $"{Participants} players";
}

public sealed record DiagnosticItemViewModel(string Name, string Detail);
