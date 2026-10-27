namespace SenhasPorto;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm(AppConfig.Load()));
    }
}
