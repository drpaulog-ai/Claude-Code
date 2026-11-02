import { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Image,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Stack, useLocalSearchParams } from "expo-router";
import { apagarArquivo } from "../../lib/arquivos";
import { Chip } from "../../lib/componentes";
import { ErroIA } from "../../lib/ia";
import {
  contextoGuardado,
  desenharPagina,
  ESTILOS_MANGA,
  novoContextoManga,
  type ContextoManga,
} from "../../lib/manga";
import {
  obterFornecedorImagem,
  obterHistoria,
  salvarHistoria,
  type FornecedorImagem,
  type Historia,
  type Manga,
} from "../../lib/storage";
import { cores, estilos } from "../../lib/tema";

const PROPORCAO: Record<FornecedorImagem, number> = { gemini: 3 / 4, pollinations: 832 / 1216 };

export default function LeitorManga() {
  const { id, cap } = useLocalSearchParams<{ id: string; cap: string }>();
  const capIdx = Number(cap ?? 0);

  const [historia, setHistoria] = useState<Historia | null>(null);
  const [estilo, setEstilo] = useState<string>(ESTILOS_MANGA[0].nome);
  const [fornecedor, setFornecedor] = useState<FornecedorImagem>("gemini");
  const [estado, setEstado] = useState<string | null>(null); // mensagem de progresso
  const [erro, setErro] = useState<string | null>(null);
  const [ampliada, setAmpliada] = useState<string | null>(null);
  const historiaRef = useRef<Historia | null>(null);

  useEffect(() => {
    obterHistoria(id).then((h) => {
      if (h) {
        historiaRef.current = h;
        setHistoria(h);
        if (h.capitulos[capIdx]?.manga) setEstilo(h.capitulos[capIdx].manga!.estilo);
      }
    });
    obterFornecedorImagem().then(setFornecedor);
  }, [id, capIdx]);

  const manga = historia?.capitulos[capIdx]?.manga;
  const capitulo = historia?.capitulos[capIdx];
  const ocupado = estado !== null;

  /** Guarda o manga do capítulo (na história e no ecrã). */
  async function guardarManga(novo: Manga | undefined) {
    const atual = historiaRef.current;
    if (!atual) return;
    const atualizada: Historia = {
      ...atual,
      capitulos: atual.capitulos.map((c, i) => (i === capIdx ? { ...c, manga: novo } : c)),
    };
    historiaRef.current = atualizada;
    setHistoria(atualizada);
    await salvarHistoria(atualizada);
  }

  function mensagemErro(e: unknown): string {
    if (e instanceof ErroIA) return e.message;
    return e instanceof Error ? e.message : "Algo correu mal ao criar o manga.";
  }

  /** Desenha as páginas que ainda não têm imagem, uma de cada vez, guardando o progresso. */
  async function desenharEmFalta(ctx: ContextoManga, inicial: Manga) {
    let atual = inicial;
    const total = atual.paginas.length;
    for (let i = 0; i < total; i++) {
      if (atual.paginas[i].arquivo) continue;
      setEstado(`A desenhar a página ${i + 1} de ${total}…`);
      const anterior = i > 0 ? atual.paginas[i - 1].arquivo || undefined : undefined;
      const pagina = await desenharPagina(ctx, capIdx, i, anterior);
      atual = { ...atual, paginas: atual.paginas.map((p, j) => (j === i ? pagina : p)) };
      await guardarManga(atual);
    }
  }

  async function criarManga() {
    const h = historiaRef.current;
    if (!h) return;
    setErro(null);
    try {
      setEstado("A escrever o guião do manga…");
      const ctx = await novoContextoManga(h, capIdx, estilo);
      const novo: Manga = {
        estilo,
        fornecedor: ctx.fornecedor,
        personagensVisuais: ctx.guiao.personagens_visuais,
        paginas: ctx.guiao.paginas.map((p) => ({ arquivo: "", vinhetas: p.vinhetas })),
      };
      await guardarManga(novo);
      await desenharEmFalta(ctx, novo);
    } catch (e) {
      setErro(mensagemErro(e));
    } finally {
      setEstado(null);
    }
  }

  async function continuar() {
    const h = historiaRef.current;
    const m = h?.capitulos[capIdx]?.manga;
    if (!h || !m) return;
    setErro(null);
    try {
      const ctx = await contextoGuardado(h, capIdx);
      if (ctx) await desenharEmFalta(ctx, m);
    } catch (e) {
      setErro(mensagemErro(e));
    } finally {
      setEstado(null);
    }
  }

  async function refazerPagina(i: number) {
    const h = historiaRef.current;
    const m = h?.capitulos[capIdx]?.manga;
    if (!h || !m) return;
    setErro(null);
    try {
      setEstado(`A redesenhar a página ${i + 1}…`);
      const ctx = await contextoGuardado(h, capIdx);
      if (!ctx) return;
      const anterior = i > 0 ? m.paginas[i - 1].arquivo || undefined : undefined;
      const pagina = await desenharPagina(ctx, capIdx, i, anterior);
      apagarArquivo(m.paginas[i].arquivo);
      await guardarManga({ ...m, fornecedor: ctx.fornecedor, paginas: m.paginas.map((p, j) => (j === i ? pagina : p)) });
    } catch (e) {
      setErro(mensagemErro(e));
    } finally {
      setEstado(null);
    }
  }

  function apagarManga() {
    if (!manga) return;
    Alert.alert("Apagar manga", "Apagar as páginas deste capítulo para criar de novo?", [
      { text: "Cancelar", style: "cancel" },
      {
        text: "Apagar",
        style: "destructive",
        onPress: async () => {
          manga.paginas.forEach((p) => p.arquivo && apagarArquivo(p.arquivo));
          await guardarManga(undefined);
        },
      },
    ]);
  }

  if (!historia || !capitulo) {
    return (
      <View style={[estilos.tela, { justifyContent: "center", alignItems: "center" }]}>
        <ActivityIndicator color={cores.primaria} />
      </View>
    );
  }

  const faltam = manga ? manga.paginas.filter((p) => !p.arquivo).length : 0;

  return (
    <View style={estilos.tela}>
      <Stack.Screen options={{ title: `Manga · Cap. ${capIdx + 1}` }} />
      <ScrollView contentContainerStyle={[estilos.conteudo, { paddingBottom: 48 }]}>
        <Text style={estilos.titulo}>{capitulo.titulo}</Text>

        {!manga && (
          <>
            <Text style={estilos.rotulo}>Estilo do desenho</Text>
            <View style={estilos.chips}>
              {ESTILOS_MANGA.map((e) => (
                <Chip key={e.nome} rotulo={e.nome} ativo={e.nome === estilo} onPress={() => setEstilo(e.nome)} />
              ))}
            </View>
            <Text style={{ color: cores.suave }}>
              {fornecedor === "gemini"
                ? "As páginas são desenhadas pelo Gemini, com os balões de fala dentro da imagem. Cada página é uma imagem (2 a 3 por capítulo)."
                : "As páginas são desenhadas pelo Pollinations (créditos grátis diários). As falas aparecem por baixo de cada página."}{" "}
              Pode mudar isto em Configurações → Imagens.
            </Text>
            {(historia.fotos?.length ?? 0) > 0 && fornecedor === "gemini" && (
              <Text style={{ color: cores.suave }}>
                As fotos desta história são usadas como referência para as personagens.
              </Text>
            )}
            <Pressable style={[estilos.botao, ocupado && { opacity: 0.7 }]} onPress={criarManga} disabled={ocupado}>
              <Text style={estilos.botaoTexto}>🎨 Criar manga deste capítulo</Text>
            </Pressable>
          </>
        )}

        {manga?.paginas.map((p, i) => (
          <View key={i} style={{ gap: 8 }}>
            <Text style={{ color: cores.suave, fontWeight: "600" }}>Página {i + 1}</Text>
            {p.arquivo ? (
              <Pressable onPress={() => setAmpliada(p.arquivo)}>
                <Image
                  source={{ uri: p.arquivo }}
                  style={[s.pagina, { aspectRatio: PROPORCAO[manga.fornecedor] }]}
                  resizeMode="contain"
                />
              </Pressable>
            ) : (
              <View style={[s.pagina, s.vazia, { aspectRatio: PROPORCAO[manga.fornecedor] }]}>
                <Text style={{ color: cores.suave }}>Por desenhar</Text>
              </View>
            )}

            {manga.fornecedor === "pollinations" && (
              <View style={[estilos.cartao, { gap: 6 }]}>
                {p.vinhetas.map((v, j) => (
                  <Text key={j} style={{ color: cores.texto, fontSize: 15, lineHeight: 22 }}>
                    <Text style={{ fontWeight: "700" }}>{j + 1}. </Text>
                    {v.narracao ? <Text style={{ fontStyle: "italic" }}>{v.narracao} </Text> : null}
                    {v.falas.map((f) => `${f.personagem}: «${f.texto}»`).join("  ")}
                  </Text>
                ))}
              </View>
            )}

            {p.arquivo ? (
              <Pressable onPress={() => refazerPagina(i)} disabled={ocupado} style={{ alignSelf: "flex-end" }} hitSlop={8}>
                <Text style={{ color: cores.primariaEscura, opacity: ocupado ? 0.5 : 1 }}>↻ Refazer página</Text>
              </Pressable>
            ) : null}
          </View>
        ))}

        {estado && (
          <View style={{ flexDirection: "row", gap: 10, alignItems: "center", paddingVertical: 8 }}>
            <ActivityIndicator color={cores.primaria} />
            <Text style={{ color: cores.texto }}>{estado}</Text>
          </View>
        )}
        {erro && <Text style={estilos.erro}>{erro}</Text>}

        {manga && faltam > 0 && !ocupado && (
          <Pressable style={estilos.botao} onPress={continuar}>
            <Text style={estilos.botaoTexto}>Continuar a desenhar ({faltam} em falta)</Text>
          </Pressable>
        )}
        {manga && !ocupado && (
          <Pressable style={estilos.botaoSecundario} onPress={apagarManga}>
            <Text style={estilos.botaoSecundarioTexto}>Apagar e criar de novo</Text>
          </Pressable>
        )}
      </ScrollView>

      <Modal visible={!!ampliada} transparent animationType="fade" onRequestClose={() => setAmpliada(null)}>
        <Pressable style={s.fundoAmpliada} onPress={() => setAmpliada(null)}>
          {ampliada && <Image source={{ uri: ampliada }} style={{ width: "100%", height: "100%" }} resizeMode="contain" />}
        </Pressable>
      </Modal>
    </View>
  );
}

const s = StyleSheet.create({
  pagina: { width: "100%", borderRadius: 8, backgroundColor: "#fff", borderWidth: 1, borderColor: cores.borda },
  vazia: { alignItems: "center", justifyContent: "center" },
  fundoAmpliada: { flex: 1, backgroundColor: "#000" },
});
