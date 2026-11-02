import { useEffect, useState } from "react";
import { Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { Chip } from "../lib/componentes";
import {
  MODELO_GEMINI_PADRAO,
  obterChaveApi,
  obterChaveGemini,
  obterFornecedor,
  obterModeloGemini,
  salvarChaveApi,
  salvarChaveGemini,
  salvarFornecedor,
  salvarModeloGemini,
  type Fornecedor,
} from "../lib/storage";
import { cores, estilos } from "../lib/tema";

export default function Configuracoes() {
  const [fornecedor, setFornecedor] = useState<Fornecedor>("claude");
  const [chaveClaude, setChaveClaude] = useState("");
  const [chaveGemini, setChaveGemini] = useState("");
  const [modeloGemini, setModeloGemini] = useState("");
  const [mensagem, setMensagem] = useState<string | null>(null);

  useEffect(() => {
    obterFornecedor().then(setFornecedor);
    obterChaveApi().then((c) => setChaveClaude(c ?? ""));
    obterChaveGemini().then((c) => setChaveGemini(c ?? ""));
    obterModeloGemini().then(setModeloGemini);
  }, []);

  async function salvar() {
    await salvarFornecedor(fornecedor);
    await salvarChaveApi(chaveClaude);
    await salvarChaveGemini(chaveGemini);
    await salvarModeloGemini(modeloGemini);
    setModeloGemini(await obterModeloGemini());
    setMensagem("Configurações guardadas neste aparelho.");
  }

  function alterar<T>(set: (v: T) => void) {
    return (v: T) => {
      set(v);
      setMensagem(null);
    };
  }

  return (
    <ScrollView style={estilos.tela} contentContainerStyle={estilos.conteudo} keyboardShouldPersistTaps="handled">
      <Text style={estilos.rotulo}>Inteligência artificial</Text>
      <View style={estilos.chips}>
        <Chip rotulo="Gemini (Google)" ativo={fornecedor === "gemini"} onPress={() => alterar(setFornecedor)("gemini")} />
        <Chip rotulo="Claude (Anthropic)" ativo={fornecedor === "claude"} onPress={() => alterar(setFornecedor)("claude")} />
      </View>

      {fornecedor === "gemini" ? (
        <>
          <Text style={{ color: cores.suave }}>
            O Gemini tem um nível gratuito. Crie uma chave em aistudio.google.com (menu "Get API key") e
            cole-a aqui.
          </Text>
          <Text style={estilos.rotulo}>Chave do Gemini</Text>
          <TextInput
            style={estilos.campo}
            value={chaveGemini}
            onChangeText={alterar(setChaveGemini)}
            placeholder="AIza..."
            placeholderTextColor={cores.suave}
            autoCapitalize="none"
            autoCorrect={false}
            secureTextEntry
          />
          <Text style={estilos.rotulo}>Modelo Gemini</Text>
          <TextInput
            style={estilos.campo}
            value={modeloGemini}
            onChangeText={alterar(setModeloGemini)}
            placeholder={MODELO_GEMINI_PADRAO}
            placeholderTextColor={cores.suave}
            autoCapitalize="none"
            autoCorrect={false}
          />
          <Text style={{ color: cores.suave, fontSize: 13 }}>
            Deixe em branco para usar {MODELO_GEMINI_PADRAO}. Se o modelo não existir, a app mostra a
            lista dos modelos disponíveis para a sua chave.
          </Text>
        </>
      ) : (
        <>
          <Text style={{ color: cores.suave }}>
            Crie uma chave em platform.claude.com e cole-a aqui. O uso é pago por consumo.
          </Text>
          <Text style={estilos.rotulo}>Chave da Anthropic</Text>
          <TextInput
            style={estilos.campo}
            value={chaveClaude}
            onChangeText={alterar(setChaveClaude)}
            placeholder="sk-ant-..."
            placeholderTextColor={cores.suave}
            autoCapitalize="none"
            autoCorrect={false}
            secureTextEntry
          />
        </>
      )}

      <Pressable style={[estilos.botao, { marginTop: 8 }]} onPress={salvar}>
        <Text style={estilos.botaoTexto}>Guardar</Text>
      </Pressable>
      {mensagem && <Text style={{ color: cores.primariaEscura }}>{mensagem}</Text>}
      <Text style={{ color: cores.suave, fontSize: 13 }}>
        As chaves ficam guardadas de forma encriptada só neste aparelho.
      </Text>

      <Text style={[estilos.rotulo, { marginTop: 24 }]}>Narração</Text>
      <Text style={{ color: cores.suave }}>
        A narração usa a voz do próprio Android. Para vozes melhores ou outros idiomas, vá a
        Definições do Android → Acessibilidade → Saída de texto para voz e descarregue os pacotes de
        idioma.
      </Text>
    </ScrollView>
  );
}
