# Letrista 🎵

Aplicação Android para criar letras de músicas com a ajuda da IA gratuita **Google Gemini**.

## Como funciona

A app guia-te num assistente de 4 passos:

1. **Estilo musical**: lista de mais de 150 estilos agrupados (Música Portuguesa, Brasileira, Pop, Rock,
   Metal, Hip-Hop, Eletrónica, Latina, Jazz, Folk, Mundo…), com pesquisa. Se o estilo não
   existir, podes escrever o teu.
2. **Tema(s)**: escolhe um ou mais temas da lista (Amor, Saudade, Liberdade, Emigração…) ou
   escreve temas personalizados.
3. **Intérprete de referência**: a app pergunta se queres indicar um intérprete ou banda para servir
   de base. A letra é sempre original, inspirada só no estilo do intérprete.
4. **Últimos detalhes**: idioma, ambiente, com ou sem refrão, e notas livres.

A letra aparece em tempo real (streaming) e podes **copiá-la**, **partilhá-la**, gerar **outra versão**
ou começar uma **nova canção**.

## Configuração (grátis)

Na primeira utilização a app pede uma **chave gratuita do Gemini**:

1. Abre [aistudio.google.com/apikey](https://aistudio.google.com/apikey) e entra com a conta Google.
2. Toca em **Create API key** e copia a chave.
3. Cola-a na app. Fica guardada só no dispositivo e pode ser alterada no ícone ⚙️.

Por defeito usa o modelo `gemini-flash-latest`. Se não estiver disponível para a tua chave, a app
mostra a lista de modelos que podes usar e podes escrever outro nas definições. O plano gratuito
tem limites por minuto/dia; se forem atingidos, basta esperar um pouco.
Se o modelo estiver sobrecarregado (erro 503) ou sem quota, a app tenta automaticamente outros
modelos gratuitos (`gemini-2.5-flash`, `gemini-flash-lite-latest`, `gemini-2.5-flash-lite`).

## Compilar

Requisitos: Android Studio (Narwhal ou mais recente) ou JDK 17 + Android SDK 36.

```bash
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

O workflow `.github/workflows/letrista-apk.yml` compila o APK em cada push e publica-o numa
**release** do GitHub (`letrista-<n>`), com link direto para instalar no telemóvel.

## Estrutura

| Ficheiro | Função |
|---|---|
| `core/MusicCatalog.kt` | Listas de estilos, temas, idiomas e ambientes |
| `core/SongRequest.kt` | Pedido do utilizador e construção dos prompts |
| `core/LyricsGenerator.kt` | Chamada à API do Google Gemini (OkHttp) em streaming |
| `data/SettingsRepository.kt` | Guarda a chave e o modelo do Gemini |
| `ui/LyricsViewModel.kt` | Estado do assistente e geração |
| `ui/LetristaApp.kt` | Ecrãs em Jetpack Compose (Material 3) |

Tecnologias: Kotlin, Jetpack Compose, Material 3, DataStore, Coroutines, OkHttp e a API do Google Gemini.
