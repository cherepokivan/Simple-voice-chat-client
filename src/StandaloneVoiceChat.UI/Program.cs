using System.Runtime.InteropServices;
using System.Text;
using Avalonia;

namespace StandaloneVoiceChat.UI;

internal static class Program
{
    private const uint MbOk = 0x00000000;
    private const uint MbIconError = 0x00000010;

    [STAThread]
    public static void Main(string[] args)
    {
        WriteStartupLog("Application startup requested.");
        try
        {
            BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
        }
        catch (Exception exception)
        {
            WriteStartupLog("Fatal startup exception.", exception);
            ShowStartupFailure();
        }
    }

    public static AppBuilder BuildAvaloniaApp() => AppBuilder.Configure<App>()
        .UsePlatformDetect()
        .WithInterFont()
        .LogToTrace();

    private static void WriteStartupLog(string message, Exception? exception = null)
    {
        try
        {
            string directory = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "SimpleVoiceChatClient");
            Directory.CreateDirectory(directory);
            string file = Path.Combine(directory, "startup.log");
            var entry = new StringBuilder()
                .Append('[').Append(DateTimeOffset.Now.ToString("O")).Append("] ")
                .AppendLine(message);

            if (exception is not null)
            {
                entry.AppendLine(exception.GetType().FullName)
                    .AppendLine(exception.Message)
                    .AppendLine(exception.StackTrace ?? "No stack trace is available.");
            }

            File.AppendAllText(file, entry.AppendLine().ToString(), Encoding.UTF8);
        }
        catch
        {
            // Startup logging must never prevent the application from showing a diagnostic message.
        }
    }

    private static void ShowStartupFailure()
    {
        const string caption = "Simple Voice Chat Client — ошибка запуска";
        const string text = "Приложение не удалось запустить.\n\n" +
                            "Откройте файл %LOCALAPPDATA%\\SimpleVoiceChatClient\\startup.log и отправьте его содержимое разработчику.";
        _ = MessageBoxW(IntPtr.Zero, text, caption, MbOk | MbIconError);
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int MessageBoxW(IntPtr hWnd, string text, string caption, uint type);
}
