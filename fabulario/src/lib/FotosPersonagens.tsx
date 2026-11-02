import { Image, Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { gravarImagem } from "./arquivos";
import { escolherFoto, type Foto } from "./fotos";
import type { FotoGuardada } from "./storage";
import { cores, estilos } from "./tema";

const MAX_FOTOS = 4;

export type FotoComNome = Foto & { nome: string };

/** Grava as fotos escolhidas na pasta da história e devolve-as no formato guardado. */
export async function guardarFotos(historiaId: string, fotos: FotoComNome[]): Promise<FotoGuardada[] | undefined> {
  if (!fotos.length) return undefined;
  return Promise.all(
    fotos.map(async (f, i) => ({
      nome: f.nome.trim(),
      arquivo: await gravarImagem(historiaId, `foto-${i + 1}`, f.base64, "jpg"),
    })),
  );
}

/** Secção para juntar fotos (câmara ou galeria) com o nome de cada personagem. */
export function FotosPersonagens({
  fotos,
  setFotos,
  onErro,
  descricao,
}: {
  fotos: FotoComNome[];
  setFotos: (atualizar: (fs: FotoComNome[]) => FotoComNome[]) => void;
  onErro: (mensagem: string | null) => void;
  descricao: string;
}) {
  async function adicionar(origem: "camera" | "galeria") {
    onErro(null);
    try {
      const foto = await escolherFoto(origem);
      if (foto) setFotos((fs) => [...fs, { ...foto, nome: "" }]);
    } catch (e) {
      onErro(e instanceof Error ? e.message : "Não foi possível obter a foto.");
    }
  }

  return (
    <>
      <Text style={estilos.rotulo}>Fotos das personagens (opcional)</Text>
      <Text style={{ color: cores.suave }}>{descricao}</Text>
      {fotos.map((f, i) => (
        <View key={f.uri} style={s.foto}>
          <Image source={{ uri: f.uri }} style={s.miniatura} />
          <View style={{ flex: 1, gap: 6 }}>
            <TextInput
              style={[estilos.campo, { paddingVertical: 8 }]}
              value={f.nome}
              onChangeText={(t) => setFotos((fs) => fs.map((x, j) => (j === i ? { ...x, nome: t } : x)))}
              placeholder="Nome da personagem (opcional)"
              placeholderTextColor={cores.suave}
            />
            <Pressable
              onPress={() => setFotos((fs) => fs.filter((_, j) => j !== i))}
              style={{ alignSelf: "flex-start" }}
              hitSlop={8}
            >
              <Text style={{ color: cores.perigo }}>Remover foto</Text>
            </Pressable>
          </View>
        </View>
      ))}
      {fotos.length < MAX_FOTOS && (
        <View style={{ flexDirection: "row", gap: 12 }}>
          <Pressable style={[estilos.botaoSecundario, { flex: 1 }]} onPress={() => adicionar("camera")}>
            <Text style={estilos.botaoSecundarioTexto}>📷 Tirar foto</Text>
          </Pressable>
          <Pressable style={[estilos.botaoSecundario, { flex: 1 }]} onPress={() => adicionar("galeria")}>
            <Text style={estilos.botaoSecundarioTexto}>🖼️ Galeria</Text>
          </Pressable>
        </View>
      )}
    </>
  );
}

const s = StyleSheet.create({
  foto: {
    flexDirection: "row",
    gap: 12,
    alignItems: "center",
    backgroundColor: cores.cartao,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: cores.borda,
    padding: 10,
  },
  miniatura: { width: 72, height: 72, borderRadius: 10, backgroundColor: cores.borda },
});
