import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type ScrollViewInstance,
} from "react-native";
import { Stack, useLocalSearchParams } from "expo-router";
import * as Speech from "expo-speech";
import { Chip } from "../../lib/componentes";
import { ErroIA, gerarCapitulo, traduzirPalavra, type Traducao } from "../../lib/ia";
import {
  novoId,
  obterHistoria,
  salvarHistoria,
  salvarPalavra,
  type Historia,
} from "../../lib/storage";
import { cores, estilos } from "../../lib/tema";

// ---------- Divisão do texto em parágrafos e palavras ----------

type Token = { texto: string; inicio: number; fim: number; ehPalavra: boolean };
type Paragrafo = { texto: string; tokens: Token[] };

function dividirTexto(texto: string): Paragrafo[] {
  return texto
    .split(/\n+/)
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => {
      const tokens: Token[] = [];
      let pos = 0;
      for (const parte of p.split(/(\s+)/)) {
        if (!parte) continue;
        tokens.push({ texto: parte, inicio: pos, fim: pos + parte.length, ehPalavra: !/^\s+$/.test(parte) });
        pos += parte.length;
      }
      return { texto: p, tokens };
    });
}

/** Devolve a frase do parágrafo que contém a posição indicada. */
function fraseEm(paragrafo: string, pos: number): string {
  const fimDeFrase = /[.!?…]/;
  let ini = pos;
  while (ini > 0 && !fimDeFrase.test(paragrafo[ini - 1])) ini--;
  let fim = pos;
  while (fim < paragrafo.length && !fimDeFrase.test(paragrafo[fim])) fim++;
  return paragrafo.slice(ini, Math.min(fim + 1, paragrafo.length)).trim();
}

function limparPalavra(p: string): string {
  return p.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, "");
}

const VELOCIDADES = [
  { rotulo: "Devagar", valor: 0.75 },
  { rotulo: "Normal", valor: 1 },
  { rotulo: "Rápido", valor: 1.25 },
];

type Selecao = { palavra: string; frase: string };

export default function Leitor() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [historia, setHistoria] = useState<Historia | null>(null);
  const [capIdx, setCapIdx] = useState(0);

  const [lendo, setLendo] = useState(false);
  const [posicao, setPosicao] = useState<{ p: number; c: number } | null>(null);
  const [velocidade, setVelocidade] = useState(1);
  const sessao = useRef(0); // ignora eventos de narrações já interrompidas

  const [gerando, setGerando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const [selecao, setSelecao] = useState<Selecao | null>(null);
  const [traducao, setTraducao] = useState<Traducao | null>(null);
  const [traduzindo, setTraduzindo] = useState(false);
  const [salva, setSalva] = useState(false);

  const scrollRef = useRef<ScrollViewInstance>(null);
  const posicoesParagrafos = useRef<number[]>([]);

  useEffect(() => {
    obterHistoria(id).then((h) => {
      if (h) {
        setHistoria(h);
        setCapIdx(h.capitulos.length - 1);
      }
    });
  }, [id]);

  const capitulo = historia?.capitulos[capIdx];
  const paragrafos = useMemo(() => (capitulo ? dividirTexto(capitulo.texto) : []), [capitulo]);

  const parar = useCallback(() => {
    sessao.current++;
    Speech.stop();
    setLendo(false);
  }, []);

  // Para a narração ao sair da tela ou trocar de capítulo.
  useEffect(() => parar, [parar]);
  useEffect(() => {
    parar();
    setPosicao(null);
    scrollRef.current?.scrollTo({ y: 0, animated: false });
  }, [capIdx, parar]);

  function falarParagrafo(p: number, minhaSessao: number) {
    if (!historia || p >= paragrafos.length) {
      setLendo(false);
      setPosicao(null);
      return;
    }
    setPosicao({ p, c: -1 });
    const y = posicoesParagrafos.current[p];
    if (y !== undefined) scrollRef.current?.scrollTo({ y: Math.max(0, y - 80), animated: true });

    Speech.speak(paragrafos[p].texto, {
      language: historia.idioma,
      rate: velocidade,
      onBoundary: (ev: unknown) => {
        const charIndex = (ev as { charIndex?: number } | undefined)?.charIndex;
        if (sessao.current === minhaSessao && typeof charIndex === "number") {
          setPosicao({ p, c: charIndex });
        }
      },
      onDone: () => {
        if (sessao.current === minhaSessao) falarParagrafo(p + 1, minhaSessao);
      },
      onError: () => {
        if (sessao.current === minhaSessao) {
          setLendo(false);
          setErro("Não foi possível usar a voz do aparelho. Verifique o mecanismo de voz do Android.");
        }
      },
    });
  }

  function alternarLeitura() {
    if (lendo) {
      parar(); // mantém a posição para continuar do mesmo parágrafo
      return;
    }
    setErro(null);
    const minhaSessao = ++sessao.current;
    setLendo(true);
    falarParagrafo(posicao?.p ?? 0, minhaSessao);
  }

  function lerAPartirDe(p: number) {
    parar();
    const minhaSessao = ++sessao.current;
    setLendo(true);
    falarParagrafo(p, minhaSessao);
  }

  async function proximoCapitulo() {
    if (!historia) return;
    if (capIdx < historia.capitulos.length - 1) {
      setCapIdx(capIdx + 1);
      return;
    }
    parar();
    setGerando(true);
    setErro(null);
    try {
      const cap = await gerarCapitulo(historia);
      const atualizada: Historia = {
        ...historia,
        resumo: cap.resumo_atualizado,
        capitulos: [...historia.capitulos, { titulo: cap.titulo_capitulo, texto: cap.texto }],
      };
      await salvarHistoria(atualizada);
      setHistoria(atualizada);
      setCapIdx(atualizada.capitulos.length - 1);
    } catch (e) {
      setErro(e instanceof ErroIA ? e.message : "Não foi possível gerar o próximo capítulo.");
    } finally {
      setGerando(false);
    }
  }

  async function abrirPalavra(paragrafo: Paragrafo, token: Token) {
    if (!historia) return;
    const palavra = limparPalavra(token.texto);
    if (!palavra) return;
    if (lendo) parar();
    setSelecao({ palavra, frase: fraseEm(paragrafo.texto, token.inicio) });
    setTraducao(null);
    setSalva(false);
    setTraduzindo(true);
    try {
      setTraducao(await traduzirPalavra(palavra, fraseEm(paragrafo.texto, token.inicio), historia.idioma));
    } catch (e) {
      setTraducao({
        termo: palavra,
        traducao: "—",
        explicacao: e instanceof ErroIA ? e.message : "Não foi possível traduzir agora.",
      });
    } finally {
      setTraduzindo(false);
    }
  }

  async function salvarSelecao() {
    if (!historia || !selecao || !traducao) return;
    await salvarPalavra({
      id: novoId(),
      termo: traducao.termo || selecao.palavra,
      traducao: traducao.traducao,
      explicacao: traducao.explicacao,
      frase: selecao.frase,
      idioma: historia.idioma,
      salvaEm: Date.now(),
    });
    setSalva(true);
  }

  if (!historia || !capitulo) {
    return (
      <View style={[estilos.tela, { justifyContent: "center", alignItems: "center" }]}>
        <ActivityIndicator color={cores.primaria} />
      </View>
    );
  }

  const ehUltimo = capIdx === historia.capitulos.length - 1;

  return (
    <View style={estilos.tela}>
      <Stack.Screen options={{ title: historia.titulo }} />

      {historia.capitulos.length > 1 && (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.barraCapitulos}
          contentContainerStyle={{ gap: 8, paddingHorizontal: 16, paddingVertical: 8 }}>
          {historia.capitulos.map((_, i) => (
            <Chip key={i} rotulo={`Cap. ${i + 1}`} ativo={i === capIdx} onPress={() => setCapIdx(i)} />
          ))}
        </ScrollView>
      )}

      <ScrollView ref={scrollRef} contentContainerStyle={[estilos.conteudo, { paddingBottom: 140 }]}>
        <Text style={s.numeroCapitulo}>Capítulo {capIdx + 1}</Text>
        <Text style={estilos.titulo}>{capitulo.titulo}</Text>
        <Text style={{ color: cores.suave, fontSize: 13 }}>
          Toque numa palavra para traduzir · toque e segure para ouvir a partir dali
        </Text>

        {paragrafos.map((par, pi) => {
          const atual = posicao?.p === pi;
          return (
            <View
              key={pi}
              onLayout={(e) => (posicoesParagrafos.current[pi] = e.nativeEvent.layout.y)}
              style={[s.paragrafo, atual && lendo && s.paragrafoAtual]}
            >
              <Text style={s.texto}>
                {par.tokens.map((t, ti) =>
                  t.ehPalavra ? (
                    <Text
                      key={ti}
                      onPress={() => abrirPalavra(par, t)}
                      onLongPress={() => lerAPartirDe(pi)}
                      style={
                        atual && posicao && posicao.c >= t.inicio && posicao.c < t.fim
                          ? s.palavraAtual
                          : undefined
                      }
                    >
                      {t.texto}
                    </Text>
                  ) : (
                    t.texto
                  ),
                )}
              </Text>
            </View>
          );
        })}

        {erro && <Text style={estilos.erro}>{erro}</Text>}

        <Pressable
          style={[estilos.botaoSecundario, { marginTop: 8, opacity: gerando ? 0.7 : 1 }]}
          onPress={proximoCapitulo}
          disabled={gerando}
        >
          {gerando ? (
            <View style={{ flexDirection: "row", gap: 10, alignItems: "center" }}>
              <ActivityIndicator color={cores.primaria} />
              <Text style={estilos.botaoSecundarioTexto}>Escrevendo o próximo capítulo…</Text>
            </View>
          ) : (
            <Text style={estilos.botaoSecundarioTexto}>
              {ehUltimo ? "✨ Gerar próximo capítulo" : "Próximo capítulo →"}
            </Text>
          )}
        </Pressable>
      </ScrollView>

      {/* Barra do narrador */}
      <View style={s.player}>
        <Pressable style={s.botaoPlay} onPress={alternarLeitura} accessibilityLabel={lendo ? "Pausar" : "Ouvir"}>
          <Text style={s.botaoPlayTexto}>{lendo ? "❚❚" : "▶"}</Text>
        </Pressable>
        <View style={{ flex: 1, gap: 6 }}>
          <Text style={{ color: cores.texto, fontWeight: "600" }}>
            {lendo ? "Narrando…" : posicao ? "Pausado" : "Ouvir capítulo"}
          </Text>
          <View style={{ flexDirection: "row", gap: 6 }}>
            {VELOCIDADES.map((v) => (
              <Pressable
                key={v.valor}
                onPress={() => {
                  setVelocidade(v.valor);
                  if (lendo) parar();
                }}
                style={[s.vel, velocidade === v.valor && s.velAtiva]}
              >
                <Text style={[s.velTexto, velocidade === v.valor && { color: "#fff" }]}>{v.rotulo}</Text>
              </Pressable>
            ))}
          </View>
        </View>
      </View>

      {/* Tradução da palavra */}
      <Modal visible={!!selecao} transparent animationType="slide" onRequestClose={() => setSelecao(null)}>
        <Pressable style={s.fundoModal} onPress={() => setSelecao(null)}>
          <Pressable style={s.folha} onPress={() => {}}>
            {selecao && (
              <>
                <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
                  <Text style={[estilos.titulo, { flex: 1 }]}>{traducao?.termo || selecao.palavra}</Text>
                  <Pressable
                    style={s.botaoOuvir}
                    onPress={() => Speech.speak(selecao.palavra, { language: historia.idioma, rate: 0.8 })}
                  >
                    <Text style={{ fontSize: 18 }}>🔊</Text>
                  </Pressable>
                </View>
                <Text style={{ color: cores.suave, fontStyle: "italic" }}>“{selecao.frase}”</Text>
                {traduzindo ? (
                  <ActivityIndicator color={cores.primaria} style={{ marginVertical: 16 }} />
                ) : (
                  traducao && (
                    <>
                      <Text style={{ fontSize: 20, fontWeight: "700", color: cores.primariaEscura }}>
                        {traducao.traducao}
                      </Text>
                      <Text style={{ fontSize: 16, color: cores.texto }}>{traducao.explicacao}</Text>
                      <Pressable
                        style={[estilos.botao, salva && { backgroundColor: cores.suave }]}
                        onPress={salvarSelecao}
                        disabled={salva || traducao.traducao === "—"}
                      >
                        <Text style={estilos.botaoTexto}>{salva ? "✓ Salva no vocabulário" : "Salvar no vocabulário"}</Text>
                      </Pressable>
                    </>
                  )
                )}
              </>
            )}
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}

const s = StyleSheet.create({
  barraCapitulos: { flexGrow: 0, borderBottomWidth: 1, borderBottomColor: cores.borda },
  numeroCapitulo: { color: cores.primariaEscura, fontWeight: "700", textTransform: "uppercase", letterSpacing: 1 },
  paragrafo: { borderRadius: 10, padding: 6, marginHorizontal: -6 },
  paragrafoAtual: { backgroundColor: cores.fraseAtual },
  texto: { fontSize: 20, lineHeight: 32, color: cores.texto },
  palavraAtual: { backgroundColor: cores.destaque, color: "#000" },
  player: {
    position: "absolute",
    left: 12,
    right: 12,
    bottom: 20,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: cores.cartao,
    borderRadius: 20,
    padding: 12,
    borderWidth: 1,
    borderColor: cores.borda,
    elevation: 6,
    shadowColor: "#000",
    shadowOpacity: 0.12,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
  },
  botaoPlay: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: cores.primaria,
    alignItems: "center",
    justifyContent: "center",
  },
  botaoPlayTexto: { color: "#fff", fontSize: 22, fontWeight: "700" },
  vel: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 12, borderWidth: 1, borderColor: cores.borda },
  velAtiva: { backgroundColor: cores.primaria, borderColor: cores.primaria },
  velTexto: { fontSize: 13, color: cores.texto },
  fundoModal: { flex: 1, backgroundColor: "rgba(0,0,0,0.35)", justifyContent: "flex-end" },
  folha: {
    backgroundColor: cores.fundo,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 20,
    paddingBottom: 36,
    gap: 12,
  },
  botaoOuvir: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: cores.cartao,
    borderWidth: 1,
    borderColor: cores.borda,
    alignItems: "center",
    justifyContent: "center",
  },
});
