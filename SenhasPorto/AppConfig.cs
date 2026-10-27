using System.Text.Json;

namespace SenhasPorto;

public sealed class LinkConfig
{
    public string Label { get; set; } = "";
    public string Url { get; set; } = "";
}

public sealed class LocationConfig
{
    public string Name { get; set; } = "";
    public string Address { get; set; } = "";

    /// <summary>Página oficial onde aparecem as senhas em espera.</summary>
    public string StatusUrl { get; set; } = "";

    /// <summary>Textos de separadores/botões a clicar antes de ler os números (ex.: "Balcões e senhas").</summary>
    public List<string> ClickTexts { get; set; } = new();

    public List<LinkConfig> TicketLinks { get; set; } = new();
}

public sealed class AppConfig
{
    public int RefreshSeconds { get; set; } = 120;
    public List<LocationConfig> Locations { get; set; } = new();

    private static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        ReadCommentHandling = JsonCommentHandling.Skip,
        AllowTrailingCommas = true,
        WriteIndented = true,
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };

    /// <summary>Pasta do .exe (modo portable: tudo fica ao lado do executável).</summary>
    public static string BaseDir => AppContext.BaseDirectory;

    public static string ConfigPath => Path.Combine(BaseDir, "config.json");

    public static AppConfig Load()
    {
        if (File.Exists(ConfigPath))
        {
            try
            {
                var cfg = JsonSerializer.Deserialize<AppConfig>(File.ReadAllText(ConfigPath), Options);
                if (cfg is { Locations.Count: > 0 })
                    return cfg;
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    $"O ficheiro config.json tem um erro e foi ignorado:\n\n{ex.Message}\n\nA usar a configuração por omissão.",
                    "Senhas Porto", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return Default();
            }
        }

        var defaults = Default();
        try
        {
            File.WriteAllText(ConfigPath, JsonSerializer.Serialize(defaults, Options));
        }
        catch
        {
            // Pasta só de leitura: funciona na mesma com os valores por omissão.
        }
        return defaults;
    }

    public static AppConfig Default() => new()
    {
        RefreshSeconds = 120,
        Locations =
        {
            new LocationConfig
            {
                Name = "Loja do Cidadão do Porto",
                Address = "Av. Fernão de Magalhães, 1862, Porto",
                StatusUrl = "https://www2.gov.pt/locais-de-atendimento-de-servicos-publicos/loja-de-cidadao-do-porto",
                ClickTexts = { "Balcões e senhas" },
                TicketLinks =
                {
                    new LinkConfig
                    {
                        Label = "Tirar senha digital (gov.pt)",
                        Url = "https://www2.gov.pt/locais-de-atendimento-de-servicos-publicos/loja-de-cidadao-do-porto",
                    },
                    new LinkConfig
                    {
                        Label = "Como tirar senha online",
                        Url = "https://www2.gov.pt/locais-de-atendimento-de-servicos-publicos/tirar-senha-online-para-loja-de-cidadao",
                    },
                },
            },
            new LocationConfig
            {
                Name = "IEFP – Serviço de Emprego do Porto",
                Address = "Rua Guedes de Azevedo, 212, Porto (Delegação Regional do Norte)",
                StatusUrl = "https://www.iefp.pt/detalheservico?id=323659",
                TicketLinks =
                {
                    new LinkConfig
                    {
                        Label = "Tirar senha digital (IEFP / sigaApp)",
                        Url = "https://www.iefp.pt/detalheservico?id=323659",
                    },
                    new LinkConfig
                    {
                        Label = "Portal SIGA (gov.pt)",
                        Url = "https://www2.gov.pt/servicos/agendar-atendimento-pelo-sistema-siga",
                    },
                },
            },
        },
    };
}
