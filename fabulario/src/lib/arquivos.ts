import { Directory, File, Paths } from "expo-file-system";

/** Pasta onde ficam as imagens de uma história (fotos e páginas de manga). */
function pastaHistoria(historiaId: string): Directory {
  const pasta = new Directory(Paths.document, "historias", historiaId);
  if (!pasta.exists) pasta.create({ intermediates: true, idempotent: true });
  return pasta;
}

/** Grava uma imagem (base64) e devolve o URI do ficheiro. */
export async function gravarImagem(
  historiaId: string,
  nome: string,
  base64: string,
  extensao = "jpg",
): Promise<string> {
  const arquivo = new File(pastaHistoria(historiaId), `${nome}.${extensao}`);
  if (arquivo.exists) arquivo.delete();
  arquivo.create();
  await arquivo.write(base64, { encoding: "base64" });
  return arquivo.uri;
}

/** Descarrega uma imagem de um URL para a pasta da história. */
export async function descarregarImagem(
  historiaId: string,
  nome: string,
  url: string,
  headers: Record<string, string>,
): Promise<string> {
  const destino = new File(pastaHistoria(historiaId), `${nome}.jpg`);
  if (destino.exists) destino.delete();
  const arquivo = await File.downloadFileAsync(url, destino, { headers, idempotent: true });
  return arquivo.uri;
}

export async function lerBase64(uri: string): Promise<string | null> {
  const arquivo = new File(uri);
  return arquivo.exists ? arquivo.base64() : null;
}

export function apagarArquivo(uri: string): void {
  try {
    const arquivo = new File(uri);
    if (arquivo.exists) arquivo.delete();
  } catch {
    // não faz mal se já não existir
  }
}

export function apagarPastaHistoria(historiaId: string): void {
  try {
    const pasta = new Directory(Paths.document, "historias", historiaId);
    if (pasta.exists) pasta.delete();
  } catch {
    // ignora
  }
}
