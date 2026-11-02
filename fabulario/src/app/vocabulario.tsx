import { useCallback, useState } from "react";
import { FlatList, Pressable, Text, View } from "react-native";
import { useFocusEffect } from "expo-router";
import * as Speech from "expo-speech";
import { Chip } from "../lib/componentes";
import { apagarPalavra, listarVocabulario, nomeIdioma, type Palavra } from "../lib/storage";
import { cores, estilos } from "../lib/tema";

export default function Vocabulario() {
  const [palavras, setPalavras] = useState<Palavra[]>([]);
  const [modoRevisao, setModoRevisao] = useState(false);
  const [reveladas, setReveladas] = useState<Set<string>>(new Set());

  useFocusEffect(
    useCallback(() => {
      listarVocabulario().then(setPalavras);
    }, []),
  );

  function alternarRevisao(ativo: boolean) {
    setModoRevisao(ativo);
    setReveladas(new Set());
    if (ativo) setPalavras((ps) => [...ps].sort(() => Math.random() - 0.5));
  }

  async function remover(id: string) {
    await apagarPalavra(id);
    setPalavras((ps) => ps.filter((p) => p.id !== id));
  }

  return (
    <View style={estilos.tela}>
      <FlatList
        contentContainerStyle={estilos.conteudo}
        data={palavras}
        keyExtractor={(p) => p.id}
        ListHeaderComponent={
          palavras.length > 0 ? (
            <View style={estilos.chips}>
              <Chip rotulo="Lista" ativo={!modoRevisao} onPress={() => alternarRevisao(false)} />
              <Chip rotulo="Revisar (cartões)" ativo={modoRevisao} onPress={() => alternarRevisao(true)} />
            </View>
          ) : undefined
        }
        ListEmptyComponent={
          <Text style={[estilos.subtitulo, { textAlign: "center", marginTop: 32 }]}>
            Nenhuma palavra salva ainda. Toque numa palavra durante a leitura para traduzir e salvar.
          </Text>
        }
        renderItem={({ item }) => {
          const oculta = modoRevisao && !reveladas.has(item.id);
          return (
            <Pressable
              style={estilos.cartao}
              onPress={() => modoRevisao && setReveladas((r) => new Set(r).add(item.id))}
            >
              <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                <Text style={{ fontSize: 20, fontWeight: "700", color: cores.texto, flex: 1 }}>{item.termo}</Text>
                <Pressable onPress={() => Speech.speak(item.termo, { language: item.idioma, rate: 0.8 })} hitSlop={10}>
                  <Text style={{ fontSize: 18 }}>🔊</Text>
                </Pressable>
              </View>
              <Text style={{ color: cores.suave, fontSize: 13 }}>{nomeIdioma(item.idioma)}</Text>
              {oculta ? (
                <Text style={{ color: cores.primariaEscura, marginTop: 8 }}>Toque para ver o significado</Text>
              ) : (
                <View style={{ marginTop: 8, gap: 4 }}>
                  <Text style={{ fontSize: 17, fontWeight: "600", color: cores.primariaEscura }}>{item.traducao}</Text>
                  <Text style={{ color: cores.texto }}>{item.explicacao}</Text>
                  <Text style={{ color: cores.suave, fontStyle: "italic" }}>“{item.frase}”</Text>
                  {!modoRevisao && (
                    <Pressable onPress={() => remover(item.id)} style={{ alignSelf: "flex-end", marginTop: 4 }}>
                      <Text style={{ color: cores.perigo }}>Remover</Text>
                    </Pressable>
                  )}
                </View>
              )}
            </Pressable>
          );
        }}
      />
    </View>
  );
}
