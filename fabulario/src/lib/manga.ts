import { descarregarImagem, gravarImagem, lerBase64 } from "./arquivos";
import { ErroIA, listarModelosGemini, pedirJSON, URL_GEMINI } from "./ia";
import {
  nomeIdioma,
  obterChaveGemini,
  obterChavePollinations,
  obterFornecedorImagem,
  obterModeloImagemGemini,
  type Fala,
  type FornecedorImagem,
  type Historia,
  type PaginaManga,
} from "./storage";

// ---------- Estilos ----------

export const ESTILOS_MANGA = [
  {
    nome: "Manga P&B",
    prompt: "black-and-white Japanese manga, clean ink line art, screentone shading, dynamic panel composition",
  },
  {
    nome: "Manga a cores",
    prompt: "full-color modern manga / anime style, vibrant cel shading, expressive faces",
  },
  {
    nome: "BD europeia",
    prompt: "Franco-Belgian 'ligne claire' comic style, clean outlines, flat bright colors, detailed backgrounds",
  },
  {
    nome: "Livro infantil",
    prompt: "soft watercolor children's picture-book comic, rounded friendly characters, warm pastel colors",
  },
] as const;

export type NomeEstilo = (typeof ESTILOS_MANGA)[number]["nome"];

function promptEstilo(nome: string): string {
  return ESTILOS_MANGA.find((e) => e.nome === nome)?.prompt ?? ESTILOS_MANGA[0].prompt;
}

// ---------- Guião (texto) ----------

type VinhetaGuiao = { cena: string; narracao: string; falas: Fala[] };
type Guiao = { personagens_visuais: string; paginas: { vinhetas: VinhetaGuiao[] }[] };

const SCHEMA_GUIAO = {
  type: "object",
  properties: {
    personagens_visuais: { type: "string" },
    paginas: {
      type: "array",
      items: {
        type: "object",
        properties: {
          vinhetas: {
            type: "array",
            items: {
              type: "object",
              properties: {
                cena: { type: "string" },
                narracao: { type: "string" },
                falas: {
                  type: "array",
                  items: {
                    type: "object",
                    properties: {
                      personagem: { type: "string" },
                      texto: { type: "string" },
                    },
                    required: ["personagem", "texto"],
                    additionalProperties: false,
                  },
                },
              },
              required: ["cena", "narracao", "falas"],
              additionalProperties: false,
            },
          },
        },
        required: ["vinhetas"],
        additionalProperties: false,
      },
    },
  },
  required: ["personagens_visuais", "paginas"],
  additionalProperties: false,
} as const;

const SYSTEM_GUIONISTA = `És um guionista de manga e banda desenhada. Transformas um capítulo de uma história num guião curto para desenhar.
Regras:
- Entre 2 e 3 páginas, cada uma com 3 a 5 vinhetas, cobrindo os momentos mais importantes do capítulo, do início ao fim.
- "cena": descrição visual da vinheta EM INGLÊS para um ilustrador (enquadramento, personagens presentes, ações, expressões, cenário). Não ponhas diálogos aqui.
- "narracao": texto curto para a caixa de narração, no idioma da história, ou "" se não houver.
- "falas": no máximo 2 falas por vinheta, cada uma com no máximo 12 palavras, no idioma da história.
- "personagens_visuais": EM INGLÊS, descrição visual fixa e concisa de cada personagem principal (idade aparente, cabelo, roupa, cores, traços marcantes), no máximo 90 palavras no total, para manter as personagens sempre iguais em todas as páginas.`;

export async function gerarGuiao(historia: Historia, capIdx: number): Promise<Guiao> {
  const cap = historia.capitulos[capIdx];

  // As fotos vão também para a IA de texto, para que a descrição visual das personagens
  // (usada em todas as páginas) se baseie nelas — útil sobretudo com o Pollinations,
  // que não aceita imagens de referência.
  const imagens: { legenda: string; base64: string }[] = [];
  for (const [i, foto] of (historia.fotos ?? []).entries()) {
    const base64 = await lerBase64(foto.arquivo);
    if (base64) {
      imagens.push({
        legenda: `Foto ${i + 1}${foto.nome ? ` — personagem: ${foto.nome}` : " — personagem principal"}`,
        base64,
      });
    }
  }

  const guiao = await pedirJSON<Guiao>({
    system: SYSTEM_GUIONISTA,
    prompt: [
      `Idioma da história: ${nomeIdioma(historia.idioma)}.`,
      `Público: ${historia.publico}. Género: ${historia.genero}.`,
      historia.descricaoPersonagens ? `Personagens: ${historia.descricaoPersonagens}` : "",
      imagens.length
        ? `As fotos acima mostram as personagens indicadas nas legendas. Em "personagens_visuais", descreve cada uma com base na foto (cabelo, rosto, óculos, barba, roupa, cores), de forma respeitosa e sem comentar o peso.`
        : "",
      `Capítulo ${capIdx + 1}: ${cap.titulo}\n\n${cap.texto}`,
    ]
      .filter(Boolean)
      .join("\n\n"),
    schema: SCHEMA_GUIAO,
    esforco: "medium",
    imagens,
  });
  if (!guiao.paginas?.length) throw new ErroIA("A IA não conseguiu dividir o capítulo em páginas.");
  return guiao;
}

// ---------- Prompt da página ----------

function promptPagina(
  guiao: Guiao,
  pagina: { vinhetas: VinhetaGuiao[] },
  estilo: string,
  comTexto: boolean,
  idioma: string,
): string {
  const n = pagina.vinhetas.length;
  const vinhetas = pagina.vinhetas
    .map((v, i) => {
      const partes = [`Panel ${i + 1}: ${v.cena}`];
      if (comTexto) {
        if (v.narracao) partes.push(`Narration box: "${v.narracao}"`);
        for (const f of v.falas) partes.push(`Speech bubble from ${f.personagem}: "${f.texto}"`);
      }
      return partes.join(" ");
    })
    .join("\n");

  return [
    `Draw ONE complete comic page in portrait format, style: ${estilo}.`,
    `The page has ${n} panels in a clear layout with white gutters, read left-to-right, top-to-bottom.`,
    `Characters (keep them exactly consistent with this description and with any reference images): ${
      comTexto ? guiao.personagens_visuais : guiao.personagens_visuais.slice(0, 600)
    }`,
    vinhetas,
    comTexto
      ? `Write the dialogue and narration text exactly as given, in ${idioma}, clearly legible, inside speech bubbles and narration boxes. Do not add any other text, titles or signatures.`
      : "Do not draw any text, letters, speech bubbles or captions.",
  ].join("\n\n");
}

// ---------- Gemini (imagens) ----------

let modeloImagemEscolhido: string | null = null;

async function escolherModeloImagem(chave: string): Promise<string> {
  const configurado = await obterModeloImagemGemini();
  if (configurado) return configurado;
  if (modeloImagemEscolhido) return modeloImagemEscolhido;

  let modelos: string[];
  try {
    modelos = await listarModelosGemini(chave);
  } catch {
    throw new ErroIA("Sem ligação ao Gemini para escolher o modelo de imagem.");
  }
  const candidatos = modelos
    .filter((m) => m.startsWith("gemini") && m.includes("image"))
    .sort((a, b) => {
      // Preferir "flash" (bom e barato), depois "lite", e só por fim "pro" (mais caro).
      const nota = (m: string) => (m.includes("pro") ? 0 : m.includes("lite") ? 1 : m.includes("flash") ? 2 : 0);
      return nota(b) - nota(a) || b.localeCompare(a, undefined, { numeric: true });
    });
  if (!candidatos.length) {
    throw new ErroIA(
      "A sua chave do Gemini não tem modelos de imagem disponíveis. Escolha o Pollinations em Configurações → Imagens.",
    );
  }
  modeloImagemEscolhido = candidatos[0];
  return modeloImagemEscolhido;
}

type Imagem = { data: string; mimeType: string };

async function desenharGemini(prompt: string, referencias: Imagem[]): Promise<Imagem> {
  const chave = await obterChaveGemini();
  if (!chave) throw new ErroIA("Configure a chave do Gemini em Configurações.");
  const modelo = await escolherModeloImagem(chave);

  const pedir = (comFormato: boolean) =>
    fetch(`${URL_GEMINI}/models/${modelo}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": chave },
      body: JSON.stringify({
        contents: [
          {
            role: "user",
            parts: [
              ...referencias.map((img) => ({ inlineData: { mimeType: img.mimeType, data: img.data } })),
              {
                text:
                  (referencias.length
                    ? "Use the attached images only as visual reference for the characters' appearance and the art style.\n\n"
                    : "") + prompt,
              },
            ],
          },
        ],
        generationConfig: {
          responseModalities: ["TEXT", "IMAGE"],
          ...(comFormato ? { imageConfig: { aspectRatio: "3:4" } } : {}),
        },
      }),
    });

  let r: Response;
  try {
    r = await pedir(true);
    if (r.status === 400) {
      const erro = await r.clone().text();
      if (/imageConfig|aspect/i.test(erro)) r = await pedir(false); // modelo sem suporte a proporção
    }
  } catch {
    throw new ErroIA("Sem ligação à internet ou ao Gemini.");
  }

  const json = (await r.json().catch(() => ({}))) as {
    error?: { message?: string };
    candidates?: {
      finishReason?: string;
      content?: { parts?: { text?: string; inlineData?: { mimeType?: string; data?: string } }[] };
    }[];
  };

  if (!r.ok) {
    const msg = json.error?.message ?? "";
    if (r.status === 429) {
      throw new ErroIA(
        /limit: 0|free_tier/i.test(msg)
          ? "A sua chave do Gemini não tem quota gratuita para imagens. Ative a faturação no Google AI Studio ou escolha o Pollinations em Configurações → Imagens."
          : "Limite de imagens do Gemini atingido por agora. Aguarde um pouco e tente de novo.",
      );
    }
    if (r.status === 404) {
      modeloImagemEscolhido = null;
      throw new ErroIA(`O modelo de imagem "${modelo}" não está disponível. Deixe o campo do modelo de imagem vazio em Configurações.`);
    }
    throw new ErroIA(`Erro do Gemini ao desenhar (${r.status}): ${msg}`);
  }

  const partes = json.candidates?.[0]?.content?.parts ?? [];
  const inline = partes.find((p) => p.inlineData?.data)?.inlineData;
  if (!inline?.data) {
    const motivo = json.candidates?.[0]?.finishReason;
    throw new ErroIA(
      motivo && motivo !== "STOP"
        ? `O Gemini não desenhou esta página (${motivo}). Tente "Refazer página".`
        : "O Gemini não devolveu nenhuma imagem. Tente \"Refazer página\".",
    );
  }
  return { data: inline.data, mimeType: inline.mimeType ?? "image/png" };
}

// ---------- Pollinations (imagens) ----------

async function desenharPollinations(
  historiaId: string,
  nomeArquivo: string,
  prompt: string,
  semente: number,
): Promise<string> {
  const chave = await obterChavePollinations();
  if (!chave) {
    throw new ErroIA(
      "Configure a chave do Pollinations em Configurações → Imagens (crie-a grátis em enter.pollinations.ai).",
    );
  }
  // O prompt vai no URL: encurtar para não exceder limites.
  const curto = prompt.length > 1800 ? `${prompt.slice(0, 1800)}…` : prompt;
  const url =
    `https://gen.pollinations.ai/image/${encodeURIComponent(curto)}` +
    `?width=832&height=1216&seed=${semente}`;
  try {
    return await descarregarImagem(historiaId, nomeArquivo, url, { Authorization: `Bearer ${chave}` });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    if (/401|403/.test(msg)) throw new ErroIA("Chave do Pollinations inválida. Confira em Configurações → Imagens.");
    if (/402/.test(msg)) throw new ErroIA("Acabaram os créditos (Pollen) do Pollinations por hoje. Tente amanhã.");
    if (/429|503/.test(msg)) throw new ErroIA("O Pollinations está ocupado. Aguarde um pouco e tente de novo.");
    throw new ErroIA(`Não foi possível desenhar a página no Pollinations: ${msg}`);
  }
}

// ---------- Desenhar uma página ----------

export type ContextoManga = {
  historia: Historia;
  guiao: Guiao;
  estilo: string;
  fornecedor: FornecedorImagem;
};

/** Cria o guião de um capítulo e prepara o desenho com o fornecedor de imagens escolhido. */
export async function novoContextoManga(historia: Historia, capIdx: number, estilo: string): Promise<ContextoManga> {
  return {
    historia,
    guiao: await gerarGuiao(historia, capIdx),
    estilo,
    fornecedor: await obterFornecedorImagem(),
  };
}

/** Reaproveita o guião já guardado (para refazer uma página sem gerar tudo de novo). */
export async function contextoGuardado(historia: Historia, capIdx: number): Promise<ContextoManga | null> {
  const manga = historia.capitulos[capIdx]?.manga;
  if (!manga) return null;
  return {
    historia,
    guiao: { personagens_visuais: manga.personagensVisuais, paginas: manga.paginas.map((p) => ({ vinhetas: p.vinhetas })) },
    estilo: manga.estilo,
    fornecedor: await obterFornecedorImagem(),
  };
}

/**
 * Desenha a página `indice` do guião e grava-a no aparelho.
 * `paginaAnterior` (URI) é enviada ao Gemini como referência para manter as personagens iguais.
 */
export async function desenharPagina(
  ctx: ContextoManga,
  capIdx: number,
  indice: number,
  paginaAnterior?: string,
): Promise<PaginaManga> {
  const pagina = ctx.guiao.paginas[indice];
  const comTexto = ctx.fornecedor === "gemini";
  const prompt = promptPagina(ctx.guiao, pagina, promptEstilo(ctx.estilo), comTexto, nomeIdioma(ctx.historia.idioma));
  const nomeArquivo = `manga-c${capIdx + 1}-p${indice + 1}-${Date.now().toString(36)}`;

  let arquivo: string;
  if (ctx.fornecedor === "gemini") {
    const referencias: Imagem[] = [];
    for (const uri of [...(ctx.historia.fotos ?? []).map((f) => f.arquivo), paginaAnterior]) {
      if (!uri) continue;
      const data = await lerBase64(uri);
      if (data) referencias.push({ data, mimeType: uri.endsWith(".png") ? "image/png" : "image/jpeg" });
    }
    const imagem = await desenharGemini(prompt, referencias.slice(-4));
    const extensao = imagem.mimeType.includes("png") ? "png" : imagem.mimeType.includes("webp") ? "webp" : "jpg";
    arquivo = await gravarImagem(ctx.historia.id, nomeArquivo, imagem.data, extensao);
  } else {
    arquivo = await desenharPollinations(ctx.historia.id, nomeArquivo, prompt, 1000 + capIdx * 10 + indice);
  }

  return {
    arquivo,
    vinhetas: pagina.vinhetas.map((v) => ({ cena: v.cena, narracao: v.narracao, falas: v.falas })),
  };
}
