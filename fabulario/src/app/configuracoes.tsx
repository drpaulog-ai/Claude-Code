import { useEffect, useState } from "react";
import { Pressable, ScrollView, Text, TextInput } from "react-native";
import { obterChaveApi, salvarChaveApi } from "../lib/storage";
import { cores, estilos } from "../lib/tema";

export default function Configuracoes() {
  const [chave, setChave] = useState("");
  const [mensagem, setMensagem] = useState<string | null>(null);

  useEffect(() => {
    obterChaveApi().then((c) => setChave(c ?? ""));
  }, []);

  async function salvar() {
    await salvarChaveApi(chave);
    setMensagem(chave.trim() ? "Chave salva com segurança neste aparelho." : "Chave removida.");
  }

  return (
    <ScrollView style={estilos.tela} contentContainerStyle={estilos.conteudo} keyboardShouldPersistTaps="handled">
      <Text style={estilos.rotulo}>Chave da API da Anthropic</Text>
      <Text style={{ color: cores.suave }}>
        O Fabulário usa o Claude para escrever as histórias e traduzir palavras. Crie uma chave em
        platform.claude.com e cole aqui. Ela fica guardada de forma criptografada só neste aparelho.
      </Text>
      <TextInput
        style={estilos.campo}
        value={chave}
        onChangeText={(t) => {
          setChave(t);
          setMensagem(null);
        }}
        placeholder="sk-ant-..."
        placeholderTextColor={cores.suave}
        autoCapitalize="none"
        autoCorrect={false}
        secureTextEntry
      />
      <Pressable style={estilos.botao} onPress={salvar}>
        <Text style={estilos.botaoTexto}>Salvar</Text>
      </Pressable>
      {mensagem && <Text style={{ color: cores.primariaEscura }}>{mensagem}</Text>}

      <Text style={[estilos.rotulo, { marginTop: 24 }]}>Narração</Text>
      <Text style={{ color: cores.suave }}>
        A narração usa a voz do próprio Android. Para vozes melhores ou outros idiomas, vá em
        Configurações do Android → Acessibilidade → Saída de conversão de texto em voz e baixe os
        pacotes de idioma.
      </Text>
    </ScrollView>
  );
}
