import { useCallback, useState } from "react";
import { Alert, FlatList, Pressable, Text, View } from "react-native";
import { Link, router, useFocusEffect } from "expo-router";
import { apagarPastaHistoria } from "../lib/arquivos";
import { apagarHistoria, listarHistorias, nomeIdioma, type Historia } from "../lib/storage";
import { cores, estilos } from "../lib/tema";

export default function Inicio() {
  const [historias, setHistorias] = useState<Historia[]>([]);

  useFocusEffect(
    useCallback(() => {
      listarHistorias().then(setHistorias);
    }, []),
  );

  function confirmarApagar(h: Historia) {
    Alert.alert("Apagar história", `Apagar "${h.titulo}"?`, [
      { text: "Cancelar", style: "cancel" },
      {
        text: "Apagar",
        style: "destructive",
        onPress: async () => {
          await apagarHistoria(h.id);
          apagarPastaHistoria(h.id);
          setHistorias(await listarHistorias());
        },
      },
    ]);
  }

  return (
    <View style={estilos.tela}>
      <FlatList
        contentContainerStyle={estilos.conteudo}
        data={historias}
        keyExtractor={(h) => h.id}
        ListHeaderComponent={
          <View style={{ gap: 12, marginBottom: 4 }}>
            <Text style={estilos.subtitulo}>
              Crie histórias com IA, ouça a narração e aprenda palavras novas.
            </Text>
            <Pressable style={estilos.botao} onPress={() => router.push("/criar")}>
              <Text style={estilos.botaoTexto}>✨ Criar nova história</Text>
            </Pressable>
            <View style={{ flexDirection: "row", gap: 12 }}>
              <Link href="/vocabulario" asChild>
                <Pressable style={[estilos.botaoSecundario, { flex: 1 }]}>
                  <Text style={estilos.botaoSecundarioTexto}>📚 Vocabulário</Text>
                </Pressable>
              </Link>
              <Link href="/configuracoes" asChild>
                <Pressable style={[estilos.botaoSecundario, { flex: 1 }]}>
                  <Text style={estilos.botaoSecundarioTexto}>⚙️ Configurações</Text>
                </Pressable>
              </Link>
            </View>
            {historias.length > 0 && <Text style={estilos.rotulo}>Minhas histórias</Text>}
          </View>
        }
        ListEmptyComponent={
          <Text style={[estilos.subtitulo, { textAlign: "center", marginTop: 32 }]}>
            Você ainda não criou nenhuma história.
          </Text>
        }
        renderItem={({ item }) => (
          <Pressable
            style={estilos.cartao}
            onPress={() => router.push({ pathname: "/historia/[id]", params: { id: item.id } })}
            onLongPress={() => confirmarApagar(item)}
          >
            <Text style={{ fontSize: 18, fontWeight: "700", color: cores.texto }}>{item.titulo}</Text>
            <Text style={{ color: cores.suave, marginTop: 4 }}>
              {item.genero} · {nomeIdioma(item.idioma)} · {item.capitulos.length}{" "}
              {item.capitulos.length === 1 ? "capítulo" : "capítulos"}
            </Text>
          </Pressable>
        )}
        ListFooterComponent={
          historias.length > 0 ? (
            <Text style={{ color: cores.suave, textAlign: "center", fontSize: 13 }}>
              Toque e segure uma história para apagá-la.
            </Text>
          ) : undefined
        }
      />
    </View>
  );
}
