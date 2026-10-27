using System.Diagnostics;
using System.Reflection;
using System.Text.Json;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace SenhasPorto;

public sealed record QueueItem(string Name, int Waiting, string Info);

/// <summary>
/// Um local de atendimento: o cartão no "Resumo" e o separador com a página oficial,
/// de onde são lidas as senhas em espera.
/// </summary>
public sealed class LocationView
{
    private const int PollIntervalMs = 2000;
    private const int MaxAttempts = 15;

    private static readonly string ExtractorTemplate = LoadExtractor();

    private readonly LocationConfig _location;
    private readonly TabControl _tabs;
    private readonly TabPage _page;
    private readonly WebView2 _web = new() { Dock = DockStyle.Fill };
    private readonly ListView _list = new()
    {
        View = View.Details,
        FullRowSelect = true,
        GridLines = true,
        HeaderStyle = ColumnHeaderStyle.Nonclickable,
        Dock = DockStyle.Fill,
    };
    private readonly Label _total = new()
    {
        AutoSize = true,
        Font = new Font("Segoe UI", 16F, FontStyle.Bold),
        Text = "Em espera: …",
        Margin = new Padding(3, 4, 3, 4),
    };
    private readonly Label _status = new() { AutoSize = true, ForeColor = SystemColors.GrayText, Text = "A iniciar…" };
    private readonly System.Windows.Forms.Timer _poll = new() { Interval = PollIntervalMs };
    private readonly List<CoreWebView2Frame> _frames = new();
    private readonly string _script;

    private bool _expectingRefresh;
    private bool _busy;
    private int _attempt;
    private string? _lastSignature;
    private List<QueueItem> _lastItems = new();

    public GroupBox Card { get; }

    public LocationView(LocationConfig location, TabControl tabs)
    {
        _location = location;
        _tabs = tabs;
        _script = ExtractorTemplate.Replace("__CLICK_TEXTS__", JsonSerializer.Serialize(location.ClickTexts));

        _page = new TabPage(location.Name);
        _page.Controls.Add(_web);
        tabs.TabPages.Add(_page);

        _list.Columns.Add("Serviço / balcão", 320);
        _list.Columns.Add("Em espera", 90, HorizontalAlignment.Right);
        _list.Columns.Add("Detalhes", 400);
        _list.Resize += (_, _) =>
        {
            var rest = _list.ClientSize.Width - _list.Columns[0].Width - _list.Columns[1].Width;
            if (rest > 100)
                _list.Columns[2].Width = rest;
        };

        var buttons = new FlowLayoutPanel { AutoSize = true, Dock = DockStyle.Fill, WrapContents = true };
        foreach (var link in location.TicketLinks)
        {
            var button = new Button { Text = link.Label, AutoSize = true, Tag = link.Url };
            button.Click += (_, _) => OpenExternal(link.Url);
            buttons.Controls.Add(button);
        }
        var showPage = new Button { Text = "Ver página oficial aqui", AutoSize = true };
        showPage.Click += (_, _) => _tabs.SelectedTab = _page;
        buttons.Controls.Add(showPage);

        var address = new Label { AutoSize = true, Text = location.Address, ForeColor = SystemColors.GrayText };

        var layout = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 5 };
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.Controls.Add(address, 0, 0);
        layout.Controls.Add(_total, 0, 1);
        layout.Controls.Add(_list, 0, 2);
        layout.Controls.Add(buttons, 0, 3);
        layout.Controls.Add(_status, 0, 4);

        Card = new GroupBox
        {
            Text = location.Name,
            Font = new Font("Segoe UI", 11F, FontStyle.Bold),
            Margin = new Padding(3, 3, 3, 12),
            Padding = new Padding(8),
        };
        // Conteúdo com letra normal; só o título do cartão a negrito.
        layout.Font = new Font("Segoe UI", 10F);
        Card.Controls.Add(layout);

        _poll.Tick += async (_, _) => await PollAsync();
    }

    public async Task InitAsync(CoreWebView2Environment env)
    {
        try
        {
            await _web.EnsureCoreWebView2Async(env);
            var core = _web.CoreWebView2;
            core.NavigationCompleted += (_, e) =>
            {
                if (!_expectingRefresh)
                    return; // o utilizador está a navegar no separador
                _expectingRefresh = false;
                if (e.IsSuccess)
                    StartPolling();
                else
                    ShowFailure($"Não foi possível abrir a página oficial ({e.WebErrorStatus}). Verifique a ligação à Internet.");
            };
            core.FrameCreated += (_, e) =>
            {
                var frame = e.Frame;
                _frames.Add(frame);
                frame.Destroyed += (_, _) => _frames.Remove(frame);
            };
            core.NewWindowRequested += (_, e) =>
            {
                // Janelas novas (ex.: formulário da senha digital) abrem-se no navegador predefinido.
                e.Handled = true;
                OpenExternal(e.Uri);
            };
            Refresh(force: true);
        }
        catch (Exception ex)
        {
            ShowFailure("Não foi possível iniciar o navegador interno: " + ex.Message);
        }
    }

    public void Refresh(bool force)
    {
        var core = _web.CoreWebView2;
        if (core == null || string.IsNullOrWhiteSpace(_location.StatusUrl))
            return;
        // Não interromper quem está a usar a página oficial (ex.: a tirar uma senha).
        if (!force && _tabs.SelectedTab == _page)
            return;

        _poll.Stop();
        _status.Text = "A atualizar…";
        _expectingRefresh = true;
        core.Navigate(_location.StatusUrl);
    }

    private void StartPolling()
    {
        _attempt = 0;
        _lastSignature = null;
        _poll.Start();
    }

    private async Task PollAsync()
    {
        if (_busy || _web.CoreWebView2 == null)
            return;
        _busy = true;
        try
        {
            _attempt++;
            var items = new List<QueueItem>();
            var clicked = Parse(await _web.CoreWebView2.ExecuteScriptAsync(_script), items);
            foreach (var frame in _frames.ToList())
            {
                try
                {
                    if (frame.IsDestroyed() == 0)
                        clicked |= Parse(await frame.ExecuteScriptAsync(_script), items);
                }
                catch
                {
                    // Frame que entretanto desapareceu.
                }
            }

            items = items.DistinctBy(i => (i.Name, i.Waiting)).ToList();
            var signature = string.Join("\n", items.Select(i => $"{i.Name}|{i.Waiting}"));

            // Esperar até os números estabilizarem (as páginas carregam os dados aos poucos).
            if (items.Count > 0 && !clicked && signature == _lastSignature)
            {
                _poll.Stop();
                ShowItems(items);
            }
            else if (_attempt >= MaxAttempts)
            {
                _poll.Stop();
                if (items.Count > 0)
                    ShowItems(items);
                else
                    ShowFailure("Não foi possível ler os números automaticamente nesta atualização. " +
                                "Consulte o separador da página oficial.");
            }
            _lastSignature = signature;
        }
        catch (Exception ex)
        {
            _poll.Stop();
            ShowFailure("Erro ao ler a página: " + ex.Message);
        }
        finally
        {
            _busy = false;
        }
    }

    /// <summary>Lê o resultado do extract.js; devolve true se foi clicado um separador.</summary>
    private static bool Parse(string json, List<QueueItem> into)
    {
        if (string.IsNullOrEmpty(json) || json == "null")
            return false;
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        if (root.ValueKind != JsonValueKind.Object)
            return false;
        if (root.TryGetProperty("items", out var items) && items.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in items.EnumerateArray())
            {
                into.Add(new QueueItem(
                    item.GetProperty("name").GetString() ?? "",
                    item.GetProperty("waiting").GetInt32(),
                    item.GetProperty("info").GetString() ?? ""));
            }
        }
        return root.TryGetProperty("clicked", out var c) && c.ValueKind == JsonValueKind.True;
    }

    private void ShowItems(List<QueueItem> items)
    {
        _lastItems = items;
        _list.BeginUpdate();
        _list.Items.Clear();
        foreach (var item in items)
            _list.Items.Add(new ListViewItem(new[] { item.Name, item.Waiting.ToString(), item.Info }));
        _list.EndUpdate();

        var total = items.Sum(i => i.Waiting);
        _total.Text = $"Em espera: {total} " + (total == 1 ? "senha" : "senhas");
        _total.ForeColor = SystemColors.ControlText;
        _status.ForeColor = SystemColors.GrayText;
        _status.Text = $"Atualizado às {DateTime.Now:HH:mm:ss}";
    }

    private void ShowFailure(string message)
    {
        _status.ForeColor = Color.Firebrick;
        _status.Text = $"{DateTime.Now:HH:mm} – {message}";
        if (_lastItems.Count == 0)
        {
            _total.Text = "Em espera: indisponível";
            _total.ForeColor = SystemColors.GrayText;
        }
        else
        {
            _status.Text += " (a mostrar os últimos valores lidos)";
        }
    }

    public static void OpenExternal(string url)
    {
        try
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Não foi possível abrir:\n{url}\n\n{ex.Message}", "Senhas Porto",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    private static string LoadExtractor()
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("SenhasPorto.extract.js")
            ?? throw new InvalidOperationException("extract.js em falta");
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }
}
