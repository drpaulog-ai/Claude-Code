import * as ImagePicker from "expo-image-picker";
import { ImageManipulator, SaveFormat } from "expo-image-manipulator";

export type Foto = { uri: string; base64: string };

const LADO_MAXIMO = 1024; // reduz as fotos para o envio ser rápido e barato

/** Abre a câmara ou a galeria e devolve a foto reduzida, ou null se o utilizador cancelar. */
export async function escolherFoto(origem: "camera" | "galeria"): Promise<Foto | null> {
  const permissao =
    origem === "camera"
      ? await ImagePicker.requestCameraPermissionsAsync()
      : await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!permissao.granted) {
    throw new Error(
      origem === "camera"
        ? "Sem permissão para usar a câmara. Autorize nas definições do Android."
        : "Sem permissão para aceder às fotos. Autorize nas definições do Android.",
    );
  }

  const opcoes: ImagePicker.ImagePickerOptions = { mediaTypes: ["images"], quality: 1 };
  const resultado =
    origem === "camera"
      ? await ImagePicker.launchCameraAsync(opcoes)
      : await ImagePicker.launchImageLibraryAsync(opcoes);
  if (resultado.canceled || !resultado.assets[0]) return null;

  const { uri, width, height } = resultado.assets[0];
  const contexto = ImageManipulator.manipulate(uri);
  if (Math.max(width, height) > LADO_MAXIMO) {
    contexto.resize(width >= height ? { width: LADO_MAXIMO } : { height: LADO_MAXIMO });
  }
  const imagem = await contexto.renderAsync();
  const final = await imagem.saveAsync({ format: SaveFormat.JPEG, compress: 0.75, base64: true });
  if (!final.base64) throw new Error("Não foi possível preparar a foto.");
  return { uri: final.uri, base64: final.base64 };
}
