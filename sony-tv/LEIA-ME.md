# Limpar apps da TV Sony (Android TV / Google TV)

Scripts para remover apps e limpar a cache de uma TV Sony via ADB, a partir de
um computador ligado **à mesma rede** que a TV.

## 1. Preparar a TV

1. **Definições → Preferências do dispositivo → Acerca de** → carregue 7 vezes
   em **Compilação** até aparecer "Agora é programador".
2. **Definições → Preferências do dispositivo → Opções de programador** →
   ative **Depuração USB** (em alguns modelos também **Depuração ADB pela rede**).
3. Confirme o IP da TV em **Definições → Rede e Internet** (ex.: `192.168.1.183`).

## 2. Instalar o adb no computador

| Sistema       | Comando |
|---------------|---------|
| Windows       | `powershell -ExecutionPolicy Bypass -File .\instalar_adb.ps1` |
| macOS / Linux | `./instalar_adb.sh` |

## 3. Correr a limpeza

| Sistema       | Comando |
|---------------|---------|
| Windows       | `powershell -ExecutionPolicy Bypass -File .\limpar_apps_tv.ps1 -TvIp 192.168.1.183` |
| macOS / Linux | `./limpar_apps_tv.sh 192.168.1.183` |

Na primeira ligação aparece na TV um pedido **"Permitir depuração?"** —
marque "Permitir sempre a partir deste computador" e aceite.

O menu permite:

1. Listar as apps que instalou
2. Desinstalá-las uma a uma (com confirmação)
3. Remover apps de sistema habituais (Sony Select, Play Filmes, Play Jogos, YouTube Music, Prime Video, Netflix)
4. Remover outra app de sistema pelo nome do pacote
5. Limpar a cache de todas as apps
6. Restaurar uma app de sistema removida

## Segurança

- As apps de sistema são removidas só para o utilizador (`pm uninstall -k --user 0`);
  o ficheiro fica na TV e pode ser reposto com a opção 6 ou com uma reposição de fábrica.
- Pacotes essenciais (launcher, Definições, serviços Google e Sony) estão bloqueados.
- No fim, pode desligar a depuração ADB nas Opções de programador.

## Problemas comuns

- **"sem ligação/autorização"**: confirme que o computador e a TV estão na mesma
  rede, que a depuração está ativa e que aceitou o pedido no ecrã da TV.
- **`unauthorized`**: corra `adb kill-server` e tente de novo para voltar a ver o pedido.
- **Windows bloqueia o script**: use exatamente o comando com `-ExecutionPolicy Bypass`.
