# Letrista 🎵

Aplicação Android para criar letras de músicas com a ajuda do Claude (Anthropic).

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

## Configuração

Na primeira utilização a app pede uma **chave da API do Claude** (cria-a em
[platform.claude.com](https://platform.claude.com)). A chave fica guardada só no dispositivo
(DataStore privado da app) e pode ser alterada no ícone ⚙️.

## Compilar

Requisitos: Android Studio (Narwhal ou mais recente) ou JDK 17 + Android SDK 36.

```bash
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

O workflow `.github/workflows/android.yml` compila o APK em cada push e publica-o como artefacto
(`letrista-debug-apk`) no separador **Actions** do GitHub.

## Estrutura

| Ficheiro | Função |
|---|---|
| `core/MusicCatalog.kt` | Listas de estilos, temas, idiomas e ambientes |
| `core/SongRequest.kt` | Pedido do utilizador e construção dos prompts |
| `core/LyricsGenerator.kt` | Chamada à API do Claude (SDK oficial `anthropic-java`) em streaming |
| `data/SettingsRepository.kt` | Guarda a chave da API |
| `ui/LyricsViewModel.kt` | Estado do assistente e geração |
| `ui/LetristaApp.kt` | Ecrãs em Jetpack Compose (Material 3) |

Tecnologias: Kotlin, Jetpack Compose, Material 3, DataStore, Coroutines e o modelo `claude-opus-5-5`.
