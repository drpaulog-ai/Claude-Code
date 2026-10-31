# Assistente Pessoal (Android)

App de IA pessoal que corre **localmente no telemóvel** e recorre à nuvem só quando precisa.

## Como decide quem responde (modo Automático)

1. O **modelo local** (Gemma, via MediaPipe) responde primeiro e dá uma nota de confiança (0–10).
2. Se a pergunta precisar de informação atual (notícias, preços, meteorologia…) ou a confiança
   for **baixa** (< 5 por defeito) → a **nuvem responde**.
3. Confiança **média** (5–7) → a nuvem **valida** a resposta local e corrige-a se for preciso.
4. Confiança **alta** (≥ 8) → fica a resposta local, sem nada sair do telemóvel.

Nuvem por ordem de prioridade: **Claude → Gemini → ChatGPT**. Se um falhar ou não tiver chave,
passa ao seguinte. Cada resposta mostra de onde veio (ex.: "Local · validado por Claude").

Há também os modos **Só local** (nada sai do telemóvel) e **Só nuvem**.

## Instalar no telemóvel

1. No GitHub, abre o separador **Releases** deste repositório e descarrega o
   `AssistentePessoal.apk` mais recente (cada alteração ao código gera um novo automaticamente).
2. Abre o ficheiro no telemóvel e autoriza "instalar apps desconhecidas" para o navegador/ficheiros.
3. As versões seguintes instalam-se por cima, sem perder dados.

## Modelo local

1. No [Hugging Face](https://huggingface.co/litert-community), procura um modelo Gemma em formato
   **`.task`** (ex.: Gemma 3 1B para rapidez; Gemma 3n E4B ou Gemma 3 4B para melhor qualidade).
   Pode ser preciso criar conta e aceitar a licença do Gemma.
2. Descarrega o ficheiro para o telemóvel.
3. Na app: **Definições → Importar ficheiro .task**. O modelo fica carregado e volta a carregar
   sozinho sempre que abres a app.

## Chaves de API (nuvem)

| Serviço | Onde criar a chave |
|---|---|
| Claude | https://console.anthropic.com → API Keys |
| Gemini | https://aistudio.google.com → Get API key |
| ChatGPT | https://platform.openai.com → API keys |

Cola-as em **Definições**. Ficam cifradas apenas no telemóvel (Android Keystore).
Os nomes dos modelos podem ser alterados nas Definições quando saírem versões novas.

## Notas

- A conversa fica guardada só no telemóvel.
- Quando a nuvem é usada, as últimas mensagens da conversa são enviadas a esse fornecedor.
- A nota de confiança de modelos pequenos é aproximada; ajusta os limites nas Definições.
