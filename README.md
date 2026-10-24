# Puzzle de Fotos (Android)

App Android que transforma as fotos do dispositivo em níveis de puzzle.

## Como jogar

- Ao dar acesso às fotos, escolhes quais queres usar como níveis (nenhuma vem
  selecionada à partida). Podes mudar a escolha mais tarde no botão **Fotos**.
  A foto mais antiga escolhida é o Nível 1. Também podes usar **Escolher foto**
  para jogar com uma imagem específica.
- Escolhe o tamanho da grelha: **4×4**, **5×5** ou **6×6**.
- O tabuleiro ocupa 85% da altura do ecrã; a foto é recortada ao centro para esse formato.
- Toca numa peça e depois noutra célula: as peças trocam de lugar.
- Peças que ficam encostadas na posição certa uma em relação à outra **unem-se** e
  passam a mover-se juntas como um grupo (a peça tocada vai para a célula do segundo
  toque e o resto do grupo acompanha; as peças que estavam no caminho ocupam o espaço
  libertado).
- Peças no sítio certo ficam **bloqueadas** e não podem ser movidas nem empurradas.
- O progresso (níveis completos e melhor número de jogadas) é guardado por tamanho de grelha.

## Instalar

O GitHub Actions compila o APK em cada push: abre o separador **Actions**, escolhe a
última execução de *Android APK* e descarrega o artefacto `puzzle-de-fotos-apk`.
Copia o `.apk` para o telemóvel e instala-o (é preciso permitir a instalação de
fontes desconhecidas). Requer Android 8.0 ou superior.

## Compilar localmente

Com o Android SDK instalado (ou no Android Studio):

```sh
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest    # testes da lógica do puzzle
```

## Estrutura

- `PuzzleBoard.kt` — regras do jogo (trocas, grupos, bloqueio), em Kotlin puro e testada.
- `PuzzleViewModel.kt` — estado do jogo e carregamento das imagens.
- `PhotoRepository.kt` — lista as fotos (MediaStore) e guarda o progresso.
- `ui/` — ecrãs em Jetpack Compose (escolha das fotos, lista de níveis e tabuleiro animado).
