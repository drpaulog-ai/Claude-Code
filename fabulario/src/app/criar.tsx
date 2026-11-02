import { useState } from "react";
import { ActivityIndicator, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { router } from "expo-router";
import { Chip } from "../lib/componentes";
import { ErroIA, gerarCapitulo } from "../lib/ia";
import { IDIOMAS, novoId, salvarHistoria, type Historia, type Idioma } from "../lib/storage";
import { cores, estilos } from "../lib/tema";

const GENEROS = ["Aventura", "Fantasia", "Mistério", "Ficção científica", "Comédia", "Fábula", "Romance", "Terror leve"];
const PUBLICOS = ["Crianças (4–7)", "Crianças (8–12)", "Adolescentes", "Adultos"];

export default function Criar() {
  const [genero, setGenero] = useState(GENEROS[0]);
  const [publico, setPublico] = useState(PUBLICOS[1]);
  const [idioma, setIdioma] = useState<Idioma>("pt-BR");
  const [tema, setTema] = useState("");
  const [personagens, setPersonagens] = useState("");
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function criar() {
    setCarregando(true);
    setErro(null);
    try {
      const base = { genero, publico, idioma, tema: tema.trim(), personagens: personagens.trim() };
      const cap = await gerarCapitulo({ ...base, titulo: "", resumo: "", capitulos: [] });
      const historia: Historia = {
        ...base,
        id: novoId(),
        titulo: cap.titulo_historia,
        resumo: cap.resumo_atualizado,
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
