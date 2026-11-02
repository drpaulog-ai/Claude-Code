import Anthropic from "@anthropic-ai/sdk";
import { obterChaveApi, nomeIdioma, type Historia, type Idioma } from "./storage";

const MODELO = "claude-opus-5-5";

export class ErroIA extends Error {}

async function cliente(): Promise<Anthropic> {
  const apiKey = await obterChaveApi();
  if (!apiKey) {
    throw new ErroIA("Configure sua chave da API em Configurações antes de criar histórias.");
  }
  // Protótipo: a chave fica no aparelho do usuário. Em produção, as chamadas
  // devem passar por um servidor próprio para não expor a chave no app.
  return new Anthropic({ apiKey, dangerouslyAllowBrowser: true });
}

/**
 * Faz uma chamada que devolve JSON no formato do schema informado.
 * Usa fallbacks do lado do servidor: se o modelo recusar o pedido, a API
 * tenta automaticamente o modelo recomendado.
 */
async function pedirJSON<T>(params: {
  system: string;
  prompt: string;
  schema: Record<string, unknown>;
  esforco: "low" | "medium";
}): Promise<T> {
  const ia = await cliente();
  let resposta;
  try {
    resposta = await ia.beta.messages.create({
      model: MODELO,
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
      throw new ErroIA("Chave da API inválida. Confira em Configurações.");
    }
    if (e instanceof Anthropic.RateLimitError) {
      throw new ErroIA("Muitas requisições seguidas. Aguarde um pouco e tente de novo.");
    }
    if (e instanceof Anthropic.APIConnectionError) {
      throw new ErroIA("Sem conexão com a internet ou com o serviço de IA.");
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

  const texto = resposta.content
    .map((bloco) => (bloco.type === "text" ? bloco.text : ""))
    .join("");
  try {
    return JSON.parse(texto) as T;
  } catch {
    throw new ErroIA("A resposta da IA veio num formato inesperado. Tente novamente.");
  }
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
