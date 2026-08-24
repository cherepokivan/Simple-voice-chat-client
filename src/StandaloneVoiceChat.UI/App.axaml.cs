using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using StandaloneVoiceChat.Client;
using StandaloneVoiceChat.Protocol;
using StandaloneVoiceChat.Network;
using StandaloneVoiceChat.UI.ViewModels;
using StandaloneVoiceChat.UI.Views;

namespace StandaloneVoiceChat.UI;

public partial class App : Application
{
    public override void Initialize() => AvaloniaXamlLoader.Load(this);

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            var coordinator = new VoiceChatSessionCoordinator(new RealSimpleVoiceChat26Adapter());

            desktop.MainWindow = new MainWindow
            {
                DataContext = new MainWindowViewModel(coordinator)
            };
        }

        base.OnFrameworkInitializationCompleted();
    }
}
