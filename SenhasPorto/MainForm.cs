using Microsoft.Web.WebView2.Core;

namespace SenhasPorto;

public sealed class MainForm : Form
{
    private const string WebView2Download = "https://go.microsoft.com/fwlink/p/?LinkId=2124703";

    private readonly AppConfig _config;
    private readonly TabControl _tabs = new() { Dock = DockStyle.Fill };
    private readonly FlowLayoutPanel _summary = new()
    {
        Dock = DockStyle.Fill,
        AutoScroll = true,
        FlowDirection = FlowDirection.TopDown,
        WrapContents = false,
        Padding = new Padding(8),
    };
    private readonly CheckBox _auto = new() { Text = "Atualizar automaticamente", Checked = true, AutoSize = true, Margin = new Padding(12, 6, 3, 3) };
    private readonly System.Windows.Forms.Timer _timer = new();
    private readonly List<LocationView> _views = new();

    public MainForm(AppConfig config)
    {
        _config = config;

        SuspendLayout();
        AutoScaleDimensions = new SizeF(96F, 96F);
        AutoScaleMode = AutoScaleMode.Dpi;
        Font = new Font("Segoe UI", 10F);
        Text = "Senhas em espera – Porto";
        ClientSize = new Size(1000, 760);
        MinimumSize = new Size(640, 480);
        StartPosition = FormStartPosition.CenterScreen;

        var refresh = new Button { Text = "Atualizar agora", AutoSize = true };
        refresh.Click += (_, _) => RefreshAll(force: true);

        var every = new Label
        {
            Text = $"(a cada {Math.Max(30, config.RefreshSeconds)} s)",
            AutoSize = true,
            ForeColor = SystemColors.GrayText,
            Margin = new Padding(0, 8, 3, 3),
        };

        var top = new FlowLayoutPanel
        {
            Dock = DockStyle.Top,
            AutoSize = true,
            Padding = new Padding(8, 8, 8, 4),
            WrapContents = false,
        };
        top.Controls.AddRange(new Control[] { refresh, _auto, every });

        var summaryPage = new TabPage("Resumo");
        summaryPage.Controls.Add(_summary);
        _tabs.TabPages.Add(summaryPage);

        foreach (var location in config.Locations)
        {
            var view = new LocationView(location, _tabs);
            _views.Add(view);
            _summary.Controls.Add(view.Card);
        }

        _summary.Resize += (_, _) => LayoutCards();

        Controls.Add(_tabs);
        Controls.Add(top);
        ResumeLayout(false);
        PerformLayout();

        _timer.Interval = Math.Max(30, config.RefreshSeconds) * 1000;
        _timer.Tick += (_, _) =>
        {
            if (_auto.Checked)
                RefreshAll(force: false);
        };

        Load += async (_, _) =>
        {
            LayoutCards();
            await InitAsync();
        };
    }

    private void LayoutCards()
    {
        var width = _summary.ClientSize.Width - _summary.Padding.Horizontal - SystemInformation.VerticalScrollBarWidth;
        foreach (var view in _views)
        {
            view.Card.Width = Math.Max(300, width);
            view.Card.Height = _summary.LogicalToDeviceUnits(360);
        }
    }

    private async Task InitAsync()
    {
        CoreWebView2Environment env;
        try
        {
            env = await CoreWebView2Environment.CreateAsync(null, UserDataFolder());
        }
        catch (WebView2RuntimeNotFoundException)
        {
            MessageBox.Show(
                "Esta aplicação precisa do Microsoft Edge WebView2 Runtime (já vem incluído no Windows 11).\n\n" +
                $"Pode instalá-lo a partir de:\n{WebView2Download}",
                Text, MessageBoxButtons.OK, MessageBoxIcon.Error);
            LocationView.OpenExternal(WebView2Download);
            return;
        }

        foreach (var view in _views)
            await view.InitAsync(env);

        _timer.Start();
    }

    private void RefreshAll(bool force)
    {
        foreach (var view in _views)
            view.Refresh(force);
    }

    /// <summary>Cache/cookies do navegador interno ficam ao lado do .exe (portable).</summary>
    private static string UserDataFolder()
    {
        var portable = Path.Combine(AppConfig.BaseDir, "SenhasPorto-dados");
        try
        {
            Directory.CreateDirectory(portable);
            var probe = Path.Combine(portable, ".teste");
            File.WriteAllText(probe, "");
            File.Delete(probe);
            return portable;
        }
        catch
        {
            // Ex.: a correr a partir de uma pasta só de leitura.
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "SenhasPorto");
        }
    }
}
