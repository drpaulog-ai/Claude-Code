import { useState } from "react";
import { ActivityIndicator, Image, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { router } from "expo-router";
import { Chip } from "../lib/componentes";
import { escolherFoto, type Foto } from "../lib/fotos";
import { ErroIA, gerarCapitulo } from "../lib/ia";
import { IDIOMAS, novoId, salvarHistoria, type Historia, type Idioma } from "../lib/storage";
import { cores, estilos } from "../lib/tema";

const GENEROS = ["Aventura", "Fantasia", "Mistério", "Ficção científica", "Comédia", "Fábula", "Romance", "Terror leve"];
const MAX_FOTOS = 4;

type FotoComNome = Foto & { nome: string };

const PUBLICOS = ["Crianças (4–7)", "Crianças (8–12)", "Adolescentes", "Adultos"];

export default function Criar() {
  const [genero, setGenero] = useState(GENEROS[0]);
  const [publico, setPublico] = useState(PUBLICOS[1]);
  const [idioma, setIdioma] = useState<Idioma>("pt-BR");
  const [tema, setTema] = useState("");
  const [personagens, setPersonagens] = useState("");
  const [fotos, setFotos] = useState<FotoComNome[]>([]);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function adicionarFoto(origem: "camera" | "galeria") {
    setErro(null);
    try {
      const foto = await escolherFoto(origem);
      if (foto) setFotos((fs) => [...fs, { ...foto, nome: "" }]);
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Não foi possível obter a foto.");
    }
  }

  function mudarNome(i: number, nome: string) {
    setFotos((fs) => fs.map((f, j) => (j === i ? { ...f, nome } : f)));
  }

  function removerFoto(i: number) {
    setFotos((fs) => fs.filter((_, j) => j !== i));
  }

  async function criar() {
    setCarregando(true);
    setErro(null);
    try {
      const base = { genero, publico, idioma, tema: tema.trim(), personagens: personagens.trim() };
      const cap = await gerarCapitulo(
        { ...base, titulo: "", resumo: "", capitulos: [] },
        fotos.map((f) => ({ nome: f.nome.trim(), base64: f.base64 })),
      );
      const historia: Historia = {
        ...base,
        id: novoId(),
        titulo: cap.titulo_historia,
        resumo: cap.resumo_atualizado,
        descricaoPersonagens: cap.descricao_personagens,
        capitulos: [{ titulo: cap.titulo_capitulo, texto: cap.texto }],
        criadaEm: Date.now(),
      };
      await salvarHistoria(historia);
      router.replace({ pathname: "/historia/[id]", params: { id: historia.id } });
    } catch (e) {
      setErro(e instanceof ErroIA ? e.message : "Algo deu errado ao criar a história.");
      setCarregando(false);
    }
  }

  return (
    <ScrollView style={estilos.tela} contentContainerStyle={estilos.conteudo} keyboardShouldPersistTaps="handled">
      <Text style={estilos.rotulo}>Gênero</Text>
      <View style={estilos.chips}>
        {GENEROS.map((g) => (
          <Chip key={g} rotulo={g} ativo={g === genero} onPress={() => setGenero(g)} />
        ))}
      </View>

      <Text style={estilos.rotulo}>Para quem é a história?</Text>
      <View style={estilos.chips}>
        {PUBLICOS.map((p) => (
          <Chip key={p} rotulo={p} ativo={p === publico} onPress={() => setPublico(p)} />
        ))}
      </View>

      <Text style={estilos.rotulo}>Idioma da história</Text>
      <View style={estilos.chips}>
        {IDIOMAS.map((i) => (
          <Chip key={i.codigo} rotulo={i.nome} ativo={i.codigo === idioma} onPress={() => setIdioma(i.codigo)} />
        ))}
      </View>
      {idioma !== "pt-BR" && (
        <Text style={{ color: cores.suave }}>
          Ótimo para aprender idiomas: toque em qualquer palavra para ver a tradução.
        </Text>
      )}

      <Text style={estilos.rotulo}>Tema ou ideia (opcional)</Text>
      <TextInput
        style={[estilos.campo, { minHeight: 80, textAlignVertical: "top" }]}
        multiline
        placeholder="Ex.: uma viagem ao fundo do mar para salvar um farol"
        placeholderTextColor={cores.suave}
        value={tema}
        onChangeText={setTema}
      />

      <Text style={estilos.rotulo}>Personagens (opcional)</Text>
      <TextInput
        style={[estilos.campo, { minHeight: 80, textAlignVertical: "top" }]}
        multiline
        placeholder="Ex.: Lia, uma menina curiosa, e Bolota, seu cachorro falante"
        placeholderTextColor={cores.suave}
        value={personagens}
        onChangeText={setPersonagens}
      />

      <Text style={estilos.rotulo}>Fotos para criar personagens (opcional)</Text>
      <Text style={{ color: cores.suave }}>
        Junte fotos de pessoas, animais, brinquedos ou desenhos. A IA cria as personagens a partir
        delas. As fotos são enviadas à IA só para criar a história e não ficam guardadas na app.
      </Text>
      {fotos.map((f, i) => (
        <View key={f.uri} style={s.foto}>
          <Image source={{ uri: f.uri }} style={s.miniatura} />
          <View style={{ flex: 1, gap: 6 }}>
            <TextInput
              style={[estilos.campo, { paddingVertical: 8 }]}
              value={f.nome}
              onChangeText={(t) => mudarNome(i, t)}
              placeholder="Nome da personagem (opcional)"
              placeholderTextColor={cores.suave}
            />
            <Pressable onPress={() => removerFoto(i)} style={{ alignSelf: "flex-start" }} hitSlop={8}>
              <Text style={{ color: cores.perigo }}>Remover foto</Text>
            </Pressable>
          </View>
        </View>
      ))}
      {fotos.length < MAX_FOTOS && (
        <View style={{ flexDirection: "row", gap: 12 }}>
          <Pressable style={[estilos.botaoSecundario, { flex: 1 }]} onPress={() => adicionarFoto("camera")}>
            <Text style={estilos.botaoSecundarioTexto}>📷 Tirar foto</Text>
          </Pressable>
          <Pressable style={[estilos.botaoSecundario, { flex: 1 }]} onPress={() => adicionarFoto("galeria")}>
            <Text style={estilos.botaoSecundarioTexto}>🖼️ Galeria</Text>
          </Pressable>
        </View>
      )}

      {erro && <Text style={estilos.erro}>{erro}</Text>}

      <Pressable
        style={[estilos.botao, { marginTop: 8, opacity: carregando ? 0.7 : 1 }]}
        onPress={criar}
        disabled={carregando}
      >
        {carregando ? (
          <View style={{ flexDirection: "row", gap: 10, alignItems: "center" }}>
            <ActivityIndicator color="#fff" />
            <Text style={estilos.botaoTexto}>Escrevendo o capítulo 1…</Text>
          </View>
        ) : (
          <Text style={estilos.botaoTexto}>Criar história</Text>
        )}
      </Pressable>
    </ScrollView>
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
