# 📸 Fotos Antigos Alunos

App Android para o grupo de WhatsApp de antigos alunos do colégio verem e partilharem
as fotos do álbum partilhado do Google Photos:
<https://photos.app.goo.gl/sdW9v5uhs2yCHBLL7>

## O que faz

- **Galeria** com todas as fotos do álbum (puxar para baixo para atualizar).
- **Visualizador** em ecrã inteiro: deslizar entre fotos, toque duplo / pinçar para zoom.
- **Enviar no WhatsApp** com um toque (ou partilhar para qualquer outra app).
- **Guardar** a foto original no telemóvel (pasta `Imagens/AntigosAlunos`).
- **Adicionar fotos**: abre o álbum na app Google Photos, onde se carrega em
  "Adicionar fotos" (é preciso ter conta Google e ter aderido ao álbum).
- **Partilhar o link do álbum** no grupo.
- **Versão web** do álbum dentro da app, como alternativa.

Não precisa de login nem de chaves de API.

## Instalar

1. Abrir o separador **Actions** do repositório no GitHub → último build verde →
   descarregar o artefacto `FotosAntigosAlunos-apk` (vem num .zip com o `.apk`).
   Para um link direto e permanente, criar uma tag `v1.0` — o APK fica anexado à
   **Release**, e esse link pode ser colado no grupo de WhatsApp.
2. No telemóvel, abrir o `.apk` e permitir "instalar apps de origem desconhecida".

Requer Android 10 ou superior.

## Notas técnicas

- Kotlin + Jetpack Compose (Material 3), Coil para imagens, OkHttp.
- A Google **não permite** que apps de terceiros leiam ou acrescentem fotos a álbuns
  partilhados através da API do Google Photos (restrição em vigor desde março de 2025).
  Por isso a app lê a página pública do álbum (`AlbumParser.kt`) e envia os membros
  para a app Google Photos para carregar fotos.
- Como a página pública não é uma API oficial, o formato pode mudar. Se a galeria
  deixar de aparecer, a app oferece a versão web; a correção fica em `AlbumParser.kt`.
- Em álbuns muito grandes a página pública pode trazer só as primeiras centenas de
  fotos; a versão web mostra todas.
- Para usar outro álbum, mudar `ALBUM_URL` em `app/build.gradle.kts`.

## Compilar localmente

Com Android Studio (ou Android SDK + JDK 17):

```bash
./gradlew assembleRelease   # APK em app/build/outputs/apk/release/
./gradlew testDebugUnitTest
```

O APK de release vem assinado com a chave de debug, o que chega para instalar
diretamente; para a Play Store seria preciso uma chave própria.
