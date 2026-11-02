import AsyncStorage from "@react-native-async-storage/async-storage";
import * as SecureStore from "expo-secure-store";

export type Idioma = "pt-BR" | "en-US" | "es-ES" | "fr-FR";

export const IDIOMAS: { codigo: Idioma; nome: string }[] = [
  { codigo: "pt-BR", nome: "Português" },
  { codigo: "en-US", nome: "Inglês" },
  { codigo: "es-ES", nome: "Espanhol" },
  { codigo: "fr-FR", nome: "Francês" },
];

export function nomeIdioma(codigo: Idioma): string {
  return IDIOMAS.find((i) => i.codigo === codigo)?.nome ?? codigo;
}

export type Fala = { personagem: string; texto: string };

export type PaginaManga = {
  arquivo: string; // URI do ficheiro da imagem no aparelho
  vinhetas: { cena: string; narracao: string; falas: Fala[] }[];
};

export type Manga = {
  estilo: string;
  fornecedor: FornecedorImagem;
  personagensVisuais: string; // descrição visual fixa (em inglês) usada em todas as páginas
  paginas: PaginaManga[];
};

export type Capitulo = {
  titulo: string;
  texto: string;
  manga?: Manga;
};

export type FotoGuardada = { nome: string; arquivo: string };

export type Historia = {
  id: string;
  titulo: string;
  genero: string;
  tema: string;
  personagens: string;
  publico: string;
  idioma: Idioma;
  capitulos: Capitulo[];
  descricaoPersonagens?: string;
  fotos?: FotoGuardada[]; // fotos usadas para criar as personagens (guardadas como ficheiros) // aparência e personalidade (pode vir das fotos)
  resumo: string; // resumo de tudo o que aconteceu até agora (para manter coerência)
  criadaEm: number;
};

export type Palavra = {
  id: string;
  termo: string;
  traducao: string;
  explicacao: string;
  frase: string;
  idioma: Idioma;
  salvaEm: number;
};

const CHAVE_HISTORIAS = "fabulario:historias";
const CHAVE_VOCAB = "fabulario:vocabulario";
const CHAVE_API = "fabulario_api_key";

export function novoId(): string {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

async function lerLista<T>(chave: string): Promise<T[]> {
  const bruto = await AsyncStorage.getItem(chave);
  if (!bruto) return [];
  try {
    return JSON.parse(bruto) as T[];
  } catch {
    return [];
  }
}

// ---------- Histórias ----------

export async function listarHistorias(): Promise<Historia[]> {
  const lista = await lerLista<Historia>(CHAVE_HISTORIAS);
  return lista.sort((a, b) => b.criadaEm - a.criadaEm);
}

export async function obterHistoria(id: string): Promise<Historia | undefined> {
  return (await lerLista<Historia>(CHAVE_HISTORIAS)).find((h) => h.id === id);
}

export async function salvarHistoria(historia: Historia): Promise<void> {
  const lista = await lerLista<Historia>(CHAVE_HISTORIAS);
  const resto = lista.filter((h) => h.id !== historia.id);
  await AsyncStorage.setItem(CHAVE_HISTORIAS, JSON.stringify([historia, ...resto]));
}

export async function apagarHistoria(id: string): Promise<void> {
  const lista = await lerLista<Historia>(CHAVE_HISTORIAS);
  await AsyncStorage.setItem(
    CHAVE_HISTORIAS,
    JSON.stringify(lista.filter((h) => h.id !== id)),
  );
}

// ---------- Vocabulário ----------

export async function listarVocabulario(): Promise<Palavra[]> {
  const lista = await lerLista<Palavra>(CHAVE_VOCAB);
  return lista.sort((a, b) => b.salvaEm - a.salvaEm);
}

export async function salvarPalavra(palavra: Palavra): Promise<void> {
  const lista = await lerLista<Palavra>(CHAVE_VOCAB);
  const semDuplicata = lista.filter(
    (p) => !(p.termo.toLowerCase() === palavra.termo.toLowerCase() && p.idioma === palavra.idioma),
  );
  await AsyncStorage.setItem(CHAVE_VOCAB, JSON.stringify([palavra, ...semDuplicata]));
}

export async function apagarPalavra(id: string): Promise<void> {
  const lista = await lerLista<Palavra>(CHAVE_VOCAB);
  await AsyncStorage.setItem(CHAVE_VOCAB, JSON.stringify(lista.filter((p) => p.id !== id)));
}

// ---------- Fornecedor de IA e chaves (guardadas de forma segura no aparelho) ----------

export type Fornecedor = "claude" | "gemini";

export const MODELO_GEMINI_PADRAO = "gemini-flash-latest";

const CHAVE_FORNECEDOR = "fabulario_fornecedor";
const CHAVE_GEMINI = "fabulario_gemini_key";
const CHAVE_MODELO_GEMINI = "fabulario_gemini_modelo";

async function guardar(chave: string, valor: string): Promise<void> {
  if (valor.trim()) {
    await SecureStore.setItemAsync(chave, valor.trim());
  } else {
    await SecureStore.deleteItemAsync(chave);
  }
}

export async function obterFornecedor(): Promise<Fornecedor> {
  return (await SecureStore.getItemAsync(CHAVE_FORNECEDOR)) === "gemini" ? "gemini" : "claude";
}

export async function salvarFornecedor(f: Fornecedor): Promise<void> {
  await SecureStore.setItemAsync(CHAVE_FORNECEDOR, f);
}

export async function obterChaveApi(): Promise<string | null> {
  return SecureStore.getItemAsync(CHAVE_API);
}

export async function salvarChaveApi(chave: string): Promise<void> {
  await guardar(CHAVE_API, chave);
}

export async function obterChaveGemini(): Promise<string | null> {
  return SecureStore.getItemAsync(CHAVE_GEMINI);
}

export async function salvarChaveGemini(chave: string): Promise<void> {
  await guardar(CHAVE_GEMINI, chave);
}

/** Aceita "Gemini Flash Latest" ou "models/gemini-flash-latest" e devolve "gemini-flash-latest". */
export function normalizarModelo(bruto: string): string {
  return bruto.trim().replace(/^models\//, "").toLowerCase().replace(/\s+/g, "-");
}

export async function obterModeloGemini(): Promise<string> {
  const m = await SecureStore.getItemAsync(CHAVE_MODELO_GEMINI);
  return normalizarModelo(m ?? "") || MODELO_GEMINI_PADRAO;
}

export async function salvarModeloGemini(modelo: string): Promise<void> {
  await guardar(CHAVE_MODELO_GEMINI, normalizarModelo(modelo));
}

// ---------- Imagens (modo manga) ----------

export type FornecedorImagem = "gemini" | "pollinations";

const CHAVE_FORNECEDOR_IMAGEM = "fabulario_fornecedor_imagem";
const CHAVE_POLLINATIONS = "fabulario_pollinations_key";
const CHAVE_MODELO_IMAGEM_GEMINI = "fabulario_gemini_modelo_imagem";

export async function obterFornecedorImagem(): Promise<FornecedorImagem> {
  return (await SecureStore.getItemAsync(CHAVE_FORNECEDOR_IMAGEM)) === "pollinations" ? "pollinations" : "gemini";
}

export async function salvarFornecedorImagem(f: FornecedorImagem): Promise<void> {
  await SecureStore.setItemAsync(CHAVE_FORNECEDOR_IMAGEM, f);
}

export async function obterChavePollinations(): Promise<string | null> {
  return SecureStore.getItemAsync(CHAVE_POLLINATIONS);
}

export async function salvarChavePollinations(chave: string): Promise<void> {
  await guardar(CHAVE_POLLINATIONS, chave);
}

/** Vazio = a app escolhe automaticamente um modelo de imagem disponível para a chave. */
export async function obterModeloImagemGemini(): Promise<string> {
  return normalizarModelo((await SecureStore.getItemAsync(CHAVE_MODELO_IMAGEM_GEMINI)) ?? "");
}

export async function salvarModeloImagemGemini(modelo: string): Promise<void> {
  await guardar(CHAVE_MODELO_IMAGEM_GEMINI, normalizarModelo(modelo));
}
