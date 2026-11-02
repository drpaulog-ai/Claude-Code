import { useState } from "react";
import { Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { router } from "expo-router";
import { Chip } from "../lib/componentes";
import { FotosPersonagens, guardarFotos, type FotoComNome } from "../lib/FotosPersonagens";
import { dividirEmCapitulos } from "../lib/importar";
import { IDIOMAS, novoId, salvarHistoria, type Historia, type Idioma } from "../lib/storage";
import { cores, estilos } from "../lib/tema";

const PUBLICOS = ["Crianças (4–7)", "Crianças (8–12)", "Adolescentes", "Adultos"];

export default function Importar() {
  const [titulo, setTitulo] = useState("");
  const [texto, setTexto] = useState("");
  const [idioma, setIdioma] = useState<Idioma>("pt-BR");
  const [publico, setPublico] = useState(PUBLICOS[3]);
  const [personagens, setPersonagens] = useState("");
  const [fotos, setFotos] = useState<FotoComNome[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [aGuardar, setAGuardar] = useState(false);

  const capitulos = texto.trim() ? dividirEmCapitulos(texto) : [];
  const palavras = texto.trim() ? texto.trim().split(/\s+/).length : 0;

  async function importar(irParaManga: boolean) {
    if (!texto.trim()) {
      setErro("Cole ou escreva o texto da história.");
      return;
    }
    setAGuardar(true);
    setErro(null);
    try {
      const id = novoId();
      const nomesComFoto = fotos.map((f) => f.nome.trim()).filter(Boolean);
      const descricao = [
        personagens.trim(),
        nomesComFoto.length ? `Personagens com foto de referência: ${nomesComFoto.join(", ")}.` : "",
      ]
        .filter(Boolean)
        .join(" ");
      const historia: Historia = {
        id,
        titulo: titulo.trim() || "História importada",
        genero: "Importada",
        tema: "",
        personagens: personagens.trim(),
        publico,
        idioma,
        capitulos,
        descricaoPersonagens: descricao || undefined,
        fotos: await guardarFotos(id, fotos),
        resumo: "",
        criadaEm: Date.now(),
      };
      await salvarHistoria(historia);
      router.replace({ pathname: "/historia/[id]", params: { id } });
      if (irParaManga) router.push({ pathname: "/manga/[id]", params: { id, cap: "0" } });
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Não foi possível guardar a história.");
      setAGuardar(false);
    }
  }

  return (
    <ScrollView style={estilos.tela} contentContainerStyle={estilos.conteudo} keyboardShouldPersistTaps="handled">
      <Text style={{ color: cores.suave }}>
        Cole uma história que já tenha escrita (ou escreva-a aqui). A app divide-a em capítulos e
        depois pode ouvi-la, traduzir palavras e transformar cada capítulo em manga.
      </Text>

      <Text style={estilos.rotulo}>Título</Text>
      <TextInput
        style={estilos.campo}
        value={titulo}
        onChangeText={setTitulo}
        placeholder="Ex.: Os dois Paulos"
        placeholderTextColor={cores.suave}
      />

      <Text style={estilos.rotulo}>Texto da história</Text>
      <TextInput
        style={[estilos.campo, { minHeight: 220, textAlignVertical: "top" }]}
        multiline
        value={texto}
        onChangeText={setTexto}
        placeholder="Cole aqui o texto. Se tiver títulos como «Capítulo 1», a app usa-os para dividir."
        placeholderTextColor={cores.suave}
      />
      {capitulos.length > 0 && (
        <Text style={{ color: cores.suave, fontSize: 13 }}>
          {palavras} palavras · {capitulos.length} {capitulos.length === 1 ? "capítulo" : "capítulos"}:{" "}
          {capitulos.map((c) => c.titulo).join(", ")}
        </Text>
      )}

      <Text style={estilos.rotulo}>Idioma do texto</Text>
      <View style={estilos.chips}>
        {IDIOMAS.map((i) => (
          <Chip key={i.codigo} rotulo={i.nome} ativo={i.codigo === idioma} onPress={() => setIdioma(i.codigo)} />
        ))}
      </View>

      <Text style={estilos.rotulo}>Público</Text>
      <View style={estilos.chips}>
        {PUBLICOS.map((p) => (
          <Chip key={p} rotulo={p} ativo={p === publico} onPress={() => setPublico(p)} />
        ))}
      </View>

      <Text style={estilos.rotulo}>Como são as personagens? (opcional)</Text>
      <TextInput
        style={[estilos.campo, { minHeight: 80, textAlignVertical: "top" }]}
        multiline
        value={personagens}
        onChangeText={setPersonagens}
        placeholder="Ex.: Paulo Guimarães, 50 anos, careca, óculos, sempre de fato; Paulo Rocha, jovem, barba, gravata azul"
        placeholderTextColor={cores.suave}
      />

      <FotosPersonagens
        fotos={fotos}
        setFotos={setFotos}
        onErro={setErro}
        descricao="As fotos servem de referência para desenhar as personagens no modo manga (com o Gemini). Escreva o nome de cada personagem tal como aparece no texto."
      />

      {erro && <Text style={estilos.erro}>{erro}</Text>}

      <Pressable
        style={[estilos.botao, { marginTop: 8, opacity: aGuardar ? 0.7 : 1 }]}
        onPress={() => importar(true)}
        disabled={aGuardar}
      >
        <Text style={estilos.botaoTexto}>🎨 Guardar e criar manga</Text>
      </Pressable>
      <Pressable style={estilos.botaoSecundario} onPress={() => importar(false)} disabled={aGuardar}>
        <Text style={estilos.botaoSecundarioTexto}>Só guardar a história</Text>
      </Pressable>
    </ScrollView>
  );
}
