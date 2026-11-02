import Anthropic from "@anthropic-ai/sdk";
import {
  nomeIdioma,
  obterChaveApi,
  obterChaveGemini,
  obterFornecedor,
  obterModeloGemini,
  type Historia,
  type Idioma,
} from "./storage";

const MODELO_CLAUDE = "claude-opus-5-5";

export class ErroIA extends Error {}

type PedidoJSON = {
  system: string;
  prompt: string;
  schema: Record<string, unknown>;
  esforco: "low" | "medium";
};

/** Faz um pedido que devolve JSON, usando o fornecedor escolhido nas Configurações. */
async function pedirJSON<T>(params: PedidoJSON): Promise<T> {
  const texto =
    (await obterFornecedor()) === "gemini" ? await pedirGemini(params) : await pedirClaude(params);
  try {
    return JSON.parse(texto) as T;
  } catch {
    throw new ErroIA("A resposta da IA veio num formato inesperado. Tente novamente.");
  }
}

// ---------- Claude (Anthropic) ----------

async function pedirClaude(params: PedidoJSON): Promise<string> {
  const apiKey = await obterChaveApi();
  if (!apiKey) {
    throw new ErroIA("Configure a sua chave da Anthropic em Configurações (ou escolha o Gemini).");
  }
  // A chave fica só no aparelho do utilizador (app de uso pessoal).
  const ia = new Anthropic({ apiKey, dangerouslyAllowBrowser: true });
  let resposta;
  try {
    // Fallbacks do lado do servidor: se o modelo recusar o pedido, a API
    // tenta automaticamente o modelo recomendado.
    resposta = await ia.beta.messages.create({
      model: MODELO_CLAUDE,
      max_tokens: 16000,
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default",
      system: params.system,
      messages: [{ role: "user", content: params.prompt }],
      output_config: {
        effort: params.esforco,
        format: { type: "json_schema", schema: params.schema },
      },
    });
  } catch (e) {
    if (e instanceof Anthropic.AuthenticationError) {
      throw new ErroIA("Chave da Anthropic inválida. Confira em Configurações.");
    }
    if (e instanceof Anthropic.RateLimitError) {
      throw new ErroIA("Muitos pedidos seguidos. Aguarde um pouco e tente de novo.");
    }
    if (e instanceof Anthropic.APIConnectionError) {
      throw new ErroIA("Sem ligação à internet ou ao serviço de IA.");
    }
    if (e instanceof Anthropic.APIError) {
      throw new ErroIA(`Erro do serviço de IA (${e.status ?? "?"}): ${e.message}`);
    }
    throw e;
  }

  if (resposta.stop_reason === "refusal") {
    throw new ErroIA("A IA não pôde criar esse conteúdo. Tente outro tema ou personagens.");
  }
  if (resposta.stop_reason === "max_tokens") {
    throw new ErroIA("A resposta ficou longa demais e foi cortada. Tente novamente.");
  }
  return resposta.content.map((bloco) => (bloco.type === "text" ? bloco.text : "")).join("");
}

// ---------- Gemini (Google) ----------

const URL_GEMINI = "https://generativelanguage.googleapis.com/v1beta";

/** Converte o JSON Schema usado no Claude para o formato de schema do Gemini. */
function schemaGemini(schema: Record<string, unknown>): Record<string, unknown> {
  const props = schema.properties as Record<string, { type: string }>;
  return {
    type: "OBJECT",
    properties: Object.fromEntries(
      Object.entries(props).map(([k, v]) => [k, { type: v.type.toUpperCase() }]),
    ),
    required: schema.required,
    propertyOrdering: Object.keys(props),
  };
}

async function modelosGemini(chave: string): Promise<string> {
  try {
    const r = await fetch(`${URL_GEMINI}/models?pageSize=200`, { headers: { "x-goog-api-key": chave } });
    const json = (await r.json()) as {
      models?: { name: string; supportedGenerationMethods?: string[] }[];
    };
    const nomes = (json.models ?? [])
      .filter((m) => m.supportedGenerationMethods?.includes("generateContent"))
      .map((m) => m.name.replace(/^models\//, ""))
      .filter((n) => n.startsWith("gemini") && !/embedding|tts|image|audio|live/.test(n))
      .sort((a, b) => Number(b.includes("flash")) - Number(a.includes("flash")) || b.localeCompare(a));
    return nomes.length ? nomes.slice(0, 8).join(", ") : "(nenhum encontrado)";
  } catch {
    return "(não foi possível obter a lista)";
  }
}

async function pedirGemini(params: PedidoJSON): Promise<string> {
  const chave = await obterChaveGemini();
  if (!chave) {
    throw new ErroIA("Configure a sua chave do Gemini em Configurações.");
  }
  const modelo = await obterModeloGemini();

  let r: Response;
  try {
    r = await fetch(`${URL_GEMINI}/models/${modelo}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": chave },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: params.system }] },
        contents: [{ role: "user", parts: [{ text: params.prompt }] }],
        generationConfig: {
          responseMimeType: "application/json",
          responseSchema: schemaGemini(params.schema),
        },
      }),
    });
  } catch {
    throw new ErroIA("Sem ligação à internet ou ao Gemini.");
  }

  const json = (await r.json().catch(() => ({}))) as {
    error?: { message?: string; status?: string };
    promptFeedback?: { blockReason?: string };
    candidates?: { finishReason?: string; content?: { parts?: { text?: string }[] } }[];
  };

  if (!r.ok) {
    const msg = json.error?.message ?? "";
    if (r.status === 404 || (r.status === 400 && /model/i.test(msg) && !/api key/i.test(msg))) {
      throw new ErroIA(
        `O modelo "${modelo}" não existe ou não está disponível para esta chave. ` +
          `Copie um destes para Configurações → Modelo Gemini: ${await modelosGemini(chave)}`,
      );
    }
    if (r.status === 400 || r.status === 401 || r.status === 403) {
      throw new ErroIA("Chave do Gemini inválida ou sem permissão. Confira em Configurações.");
    }
    if (r.status === 429) {
      throw new ErroIA("Limite gratuito do Gemini atingido por agora. Aguarde um minuto e tente de novo.");
    }
    throw new ErroIA(`Erro do Gemini (${r.status}): ${msg}`);
  }

  const candidato = json.candidates?.[0];
  if (json.promptFeedback?.blockReason || candidato?.finishReason === "SAFETY") {
    throw new ErroIA("A IA não pôde criar esse conteúdo. Tente outro tema ou personagens.");
  }
  if (candidato?.finishReason === "MAX_TOKENS") {
    throw new ErroIA("A resposta ficou longa demais e foi cortada. Tente novamente.");
  }
  const texto = (candidato?.content?.parts ?? []).map((p) => p.text ?? "").join("");
  if (!texto) throw new ErroIA("O Gemini não devolveu texto. Tente novamente.");
  return texto;
}

// ---------- Geração de capítulos ----------

const SCHEMA_CAPITULO = {
  type: "object",
  properties: {
    titulo_historia: { type: "string" },
    titulo_capitulo: { type: "string" },
    texto: { type: "string" },
    resumo_atualizado: { type: "string" },
  },
  required: ["titulo_historia", "titulo_capitulo", "texto", "resumo_atualizado"],
  additionalProperties: false,
} as const;

type RespostaCapitulo = {
  titulo_historia: string;
  titulo_capitulo: string;
  texto: string;
  resumo_atualizado: string;
};

const SYSTEM_ESCRITOR = `Você é um escritor de histórias seriadas para um app de leitura em voz alta.
Escreva sempre no idioma pedido, com linguagem adequada ao público indicado.
Cada capítulo deve ter entre 350 e 550 palavras, dividido em parágrafos curtos separados por uma linha em branco, e terminar com um gancho que dê vontade de ler o próximo.
O texto será lido por uma voz sintética: não use títulos, listas, emojis, markdown ou marcações dentro de "texto".
Em "resumo_atualizado", escreva em português um resumo de até 120 palavras de tudo o que aconteceu na história até o fim deste capítulo, incluindo nomes e fatos importantes para manter a continuidade.`;

export async function gerarCapitulo(
  historia: Pick<Historia, "genero" | "tema" | "personagens" | "publico" | "idioma" | "resumo" | "titulo"> & {
    capitulos: Historia["capitulos"];
  },
): Promise<RespostaCapitulo> {
  const numero = historia.capitulos.length + 1;
  const ultimo = historia.capitulos[historia.capitulos.length - 1];

  const partes = [
    `Idioma da história: ${nomeIdioma(historia.idioma)}.`,
    `Público: ${historia.publico}.`,
    `Gênero: ${historia.genero}.`,
    `Tema ou ideia: ${historia.tema || "livre"}.`,
    `Personagens: ${historia.personagens || "crie personagens marcantes"}.`,
  ];
  if (numero === 1) {
    partes.push("Escreva o capítulo 1 e crie um título para a história.");
  } else {
    partes.push(
      `Título da história: ${historia.titulo} (mantenha este título em "titulo_historia").`,
      `Resumo até agora: ${historia.resumo}`,
      `Último capítulo (${ultimo.titulo}):\n${ultimo.texto}`,
      `Escreva o capítulo ${numero}, continuando de onde o último parou.`,
    );
  }

  return pedirJSON<RespostaCapitulo>({
    system: SYSTEM_ESCRITOR,
    prompt: partes.join("\n\n"),
    schema: SCHEMA_CAPITULO,
    esforco: "medium",
  });
}

// ---------- Tradução de palavras ----------

const SCHEMA_TRADUCAO = {
  type: "object",
  properties: {
    termo: { type: "string" },
    traducao: { type: "string" },
    explicacao: { type: "string" },
  },
  required: ["termo", "traducao", "explicacao"],
  additionalProperties: false,
} as const;

export type Traducao = { termo: string; traducao: string; explicacao: string };

export async function traduzirPalavra(
  palavra: string,
  frase: string,
  idioma: Idioma,
): Promise<Traducao> {
  const emPortugues = idioma === "pt-BR";
  return pedirJSON<Traducao>({
    system:
      "Você ajuda pessoas que falam português a entender palavras de histórias. Responda de forma curta e simples.",
    prompt: [
      `Palavra: "${palavra}"`,
      `Frase onde aparece: "${frase}"`,
      `Idioma da frase: ${nomeIdioma(idioma)}.`,
      `Em "termo", escreva a palavra na forma de dicionário (sem pontuação).`,
      emPortugues
        ? `Em "traducao", dê um sinônimo simples em português. Em "explicacao", explique o significado nesta frase em uma linha.`
        : `Em "traducao", dê a tradução para o português no sentido usado na frase. Em "explicacao", explique em português, em uma linha, o uso nesta frase.`,
    ].join("\n"),
    schema: SCHEMA_TRADUCAO,
    esforco: "low",
  });
}
