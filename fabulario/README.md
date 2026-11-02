# Fabulário (protótipo)

App Android em português que cria histórias com IA, capítulo por capítulo, e lê em voz alta destacando cada palavra no momento em que é falada.

## Funcionalidades

- **Criar história**: escolha gênero, público (crianças, adolescentes, adultos), idioma (português, inglês, espanhol, francês), tema e personagens. O Claude escreve o capítulo 1 e dá um título à história.
- **Capítulos seguintes**: o botão "Gerar próximo capítulo" continua a história. Um resumo da trama é guardado para manter a coerência.
- **Narração**: usa a voz do próprio Android, com destaque do parágrafo e da palavra que está sendo lida, em três velocidades. Toque e segure uma palavra para começar a ouvir dali.
- **Tradução**: toque em qualquer palavra para ver a tradução ou o significado em português, explicado no contexto da frase.
- **Vocabulário**: salve palavras e revise em modo de cartões.
- Tudo fica salvo no aparelho. A chave da API é guardada criptografada (expo-secure-store).

## Como rodar

Requisitos: Node 20+ e o app **Expo Go** instalado no celular Android.

```bash
cd fabulario
npm install
npx expo start
```

Escaneie o QR code com o Expo Go. No app, abra **Configurações** e cole uma chave da API da Anthropic (crie em https://platform.claude.com).

### Gerar um APK (para instalar direto no celular)

O projeto já tem o perfil `apk` configurado em `eas.json`. No seu computador:

```bash
cd fabulario
npm install
npx eas-cli@latest login                       # conta Expo gratuita (expo.dev)
npx eas-cli@latest build -p android --profile apk
```

Na primeira vez, o EAS pergunta se pode criar o projeto e uma chave de assinatura: responda **sim** às duas.
A compilação roda na nuvem da Expo (cerca de 10 a 20 minutos). No fim aparece um link e um QR code:
abra no celular, baixe o `.apk` e instale (o Android vai pedir para permitir "instalar apps de fontes desconhecidas").

## Estrutura

```
src/app/_layout.tsx          navegação (Expo Router)
src/app/index.tsx            lista de histórias
src/app/criar.tsx            formulário de nova história
src/app/historia/[id].tsx    leitor: narração, destaque de palavras, tradução
src/app/vocabulario.tsx      palavras salvas e revisão
src/app/configuracoes.tsx    chave da API
src/lib/ia.ts                chamadas ao Claude (capítulos e tradução)
src/lib/storage.ts           armazenamento local
```

## Antes de publicar na loja

Este é um protótipo. Para uma versão de produção:

- **Não deixe a chave da API no app.** Crie um backend (por exemplo, uma função serverless) que faça as chamadas ao Claude e controle os créditos de cada usuário.
- Adicione login e sincronização na nuvem (Supabase ou Firebase).
- Use vozes neurais (ElevenLabs, Azure ou Google TTS) com marcação de tempo por palavra, para uma narração mais natural.
- Implemente compras e assinaturas (RevenueCat).
- Se o público incluir crianças, siga as regras de privacidade (LGPD/COPPA) e as políticas de apps infantis do Google Play.
