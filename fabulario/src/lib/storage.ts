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

export type Capitulo = {
  titulo: string;
  texto: string;
};

export type Historia = {
  id: string;
  titulo: string;
  genero: string;
  tema: string;
  personagens: string;
  publico: string;
  idioma: Idioma;
  capitulos: Capitulo[];
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

// ---------- Chave da API (guardada de forma segura no aparelho) ----------

export async function obterChaveApi(): Promise<string | null> {
  return SecureStore.getItemAsync(CHAVE_API);
}

export async function salvarChaveApi(chave: string): Promise<void> {
  if (chave.trim()) {
    await SecureStore.setItemAsync(CHAVE_API, chave.trim());
  } else {
    await SecureStore.deleteItemAsync(CHAVE_API);
  }
}
