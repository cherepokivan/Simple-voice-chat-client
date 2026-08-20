using Avalonia.Controls;
using Avalonia.Markup.Xaml;

namespace StandaloneVoiceChat.UI.Views;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        AvaloniaXamlLoader.Load(this);
    }
}
