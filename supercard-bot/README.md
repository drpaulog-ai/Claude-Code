# Bot WWE SuperCard (Windows)

Recolhe as recompensas diárias e repete eventos no WWE SuperCard, controlando o
telemóvel a partir do PC via ADB e reconhecendo os botões no ecrã com OpenCV.

> **Aviso:** os termos de uso da 2K proíbem automação e há contas banidas por isso.
> Usa por tua conta e risco; de preferência testa primeiro numa conta secundária.

## 1. Instalação (só uma vez)

1. **Python**: instala a partir de <https://www.python.org/downloads/>.
   Na instalação marca **"Add python.exe to PATH"**.
2. **ADB**: descarrega o *SDK Platform-Tools for Windows* em
   <https://developer.android.com/tools/releases/platform-tools> e extrai a pasta
   `platform-tools` para dentro desta pasta (fica `supercard-bot\platform-tools\adb.exe`).
3. **Bibliotecas**: faz duplo clique em `instalar.bat`.
4. **Telemóvel**:
   - Definições → Acerca do telefone → toca 7 vezes em **Número de compilação**
     para ativar as Opções de programador.
   - Definições → Opções de programador → ativa **Depuração USB**.
   - Liga o cabo USB ao PC e aceita o pedido **"Permitir depuração USB"** no telemóvel.
5. Confirma a ligação: abre uma linha de comandos nesta pasta e escreve
   `platform-tools\adb devices`. Tem de aparecer o telemóvel com `device`.

## 2. Criar as imagens de referência

O bot reconhece os ecrãs comparando-os com pequenas imagens que tu recortas do
**teu** telemóvel (assim batem certo com a tua resolução).

1. Abre no jogo o ecrã que tem o botão (ex.: o pop-up da recompensa diária).
2. Faz duplo clique em `capturar.bat`.
3. Seleciona com o rato o botão, carrega **ENTER** e dá-lhe o nome usado no
   `config.yaml` (ex.: `botao_recolher`).
4. Repete para cada imagem que aparece no `config.yaml`.

Dicas:
- Recorta só a parte fixa do botão (o texto/ícone), sem números que mudam
  (contadores, tempos, energia).
- Para `ecra_carregamento` e `menu_principal` escolhe algo que só exista
  nesse ecrã (um logótipo, um ícone do menu).
- Para descobrires posições a usar em `tocar_em` ou `regiao`:
  `python capturar.py coordenadas` e clica na imagem.

Depois corre `testar.bat` com o jogo aberto: mostra a semelhança (0 a 1) de cada
imagem com o ecrã atual. Uma imagem visível deve dar **SIM** (≥ 0.85).

## 3. Usar

- `iniciar_diarias.bat` → recolhe as recompensas e para no menu principal.
- `iniciar_evento.bat` → entra no evento e repete até acabar a energia ou
  chegar ao número de `repeticoes`.

Para parar a qualquer momento: **Ctrl+C** na janela.

Antes do evento, abre o jogo no ecrã do evento que queres repetir.

## 4. Ajustar às tuas orientações

Tudo é configurado no `config.yaml` (abre com o Bloco de Notas). Cada tarefa
é uma lista de regras "se vires esta imagem → faz isto", verificadas de cima
para baixo. Exemplos:

```yaml
# parar quando aparecer o ecrã de "sem energia"
- imagem: sem_energia.png
  acao: parar

# tocar num ponto fixo (aqui: meio do ecrã, 80% para baixo)
- imagem: ecra_escolher_carta.png
  acao: tocar_em
  posicao: [0.5, 0.80]

# só procurar o botão na metade de baixo do ecrã
- imagem: botao_continuar.png
  acao: tocar
  regiao: [0, 0.5, 1, 1]
```

Podes criar novas tarefas (outro evento, por exemplo) copiando um bloco e
mudando o nome; corre-se com `python bot.py tarefa <nome>`.

## Segurança do bot

O bot para sozinho e guarda uma captura em `capturas\` quando:
- não reconhece nenhum ecrã durante `tempo_max_desconhecido` segundos;
- a mesma regra se repete demasiadas vezes seguidas (algo ficou preso);
- passa o tempo `max_minutos` da tarefa.

Se parar sem razão aparente, abre a captura para veres em que ecrã estava e
cria uma imagem/regra para esse caso.

## Problemas comuns

| Mensagem | Solução |
|---|---|
| `adb não encontrado` | Confirma que existe `platform-tools\adb.exe` nesta pasta. |
| `unauthorized` | Aceita o pedido de depuração USB no telemóvel. |
| `imagem em falta: templates/...` | Cria essa imagem com `capturar.bat`. |
| Botão visível mas não reconhecido | Recorta de novo com menos margem, ou baixa o `limiar` dessa regra para 0.8. |
| Toca no botão errado | Usa `regiao` para limitar a zona, ou recorta uma parte mais única. |
