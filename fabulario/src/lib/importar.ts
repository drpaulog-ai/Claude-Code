import type { Capitulo } from "./storage";

const TAMANHO_MAXIMO = 3500; // caracteres por capítulo (cerca de 600 palavras)

const TITULO_CAPITULO = /^\s*(cap[ií]tulo|chapter|cap\.|parte|part)\s+[\wºª.-]+.*$/i;

/** Junta parágrafos em blocos de até TAMANHO_MAXIMO caracteres. */
function dividirPorTamanho(texto: string): string[] {
  const paragrafos = texto.split(/\n\s*\n|\n/).map((p) => p.trim()).filter(Boolean);
  const blocos: string[] = [];
  let atual = "";
  for (const p of paragrafos) {
    if (atual && atual.length + p.length + 2 > TAMANHO_MAXIMO) {
      blocos.push(atual);
      atual = p;
    } else {
      atual = atual ? `${atual}\n\n${p}` : p;
    }
  }
  if (atual) blocos.push(atual);
  return blocos;
}

/**
 * Divide um texto colado em capítulos. Usa os títulos "Capítulo X" / "Chapter X" / "Parte X"
 * se existirem; caso contrário, corta em partes de tamanho parecido.
 */
export function dividirEmCapitulos(texto: string): Capitulo[] {
  const linhas = texto.replace(/\r\n?/g, "\n").split("\n");
  const secoes: { titulo: string; linhas: string[] }[] = [];
  for (const linha of linhas) {
    if (TITULO_CAPITULO.test(linha) && linha.trim().length <= 80) {
      secoes.push({ titulo: linha.trim(), linhas: [] });
    } else {
      if (!secoes.length) secoes.push({ titulo: "", linhas: [] });
      secoes[secoes.length - 1].linhas.push(linha);
    }
  }

  const comTitulos = secoes.filter((s) => s.titulo).length >= 2;
  const capitulos: Capitulo[] = [];
  const base = comTitulos
    ? secoes.filter((s) => s.titulo || s.linhas.join("").trim())
    : [{ titulo: "", linhas }];

  for (const secao of base) {
    const corpo = secao.linhas.join("\n").trim();
    if (!corpo) continue;
    const partes = dividirPorTamanho(corpo);
    partes.forEach((parte, i) => {
      const titulo = secao.titulo
        ? partes.length > 1
          ? `${secao.titulo} (${i + 1})`
          : secao.titulo
        : comTitulos
          ? "Início"
          : `Parte ${capitulos.length + 1}`;
      capitulos.push({ titulo, texto: parte });
    });
  }
  return capitulos;
}
