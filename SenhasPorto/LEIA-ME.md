# Senhas Porto (Windows 11, portable)

Mostra quantas senhas estão em espera em:

- **Loja do Cidadão do Porto** (Av. Fernão de Magalhães, 1862)
- **IEFP – Serviço de Emprego do Porto** (Rua Guedes de Azevedo, 212 – Delegação Regional do Norte)

e tem botões para **tirar senha digital** em cada um.

## Como usar

1. Descarregue `SenhasPorto.exe` da página **Releases** do repositório (ou, para versões de
   desenvolvimento, do separador *Actions* → última execução de
   "SenhasPorto – build Windows portable" → artefacto `SenhasPorto-win-x64-portable`).
2. Coloque-o numa pasta qualquer (ou numa pen USB) e faça duplo clique. Não precisa de instalação.
3. O separador **Resumo** mostra, para cada local, o total e o número em espera por balcão/serviço.
   Atualiza sozinho a cada 2 minutos (ou carregue em **Atualizar agora**).
4. Os botões **Tirar senha digital** abrem a página oficial no seu navegador.
   Cada local tem também um separador com a página oficial, onde pode ver e tirar a senha dentro da app.

Requisitos: Windows 10/11 64-bit com o *Microsoft Edge WebView2 Runtime* (já vem com o Windows 11).

## Como funciona

A app não usa nenhuma API privada: abre as páginas públicas oficiais (gov.pt e iefp.pt) num
navegador interno (WebView2) e lê os números "em espera" que aparecem no ecrã. Se um site mudar o
aspeto e a leitura automática falhar, a app avisa e pode sempre consultar o separador da página oficial.

## Configuração (`config.json`)

Na primeira execução é criado um `config.json` ao lado do `.exe`. Pode editá-lo para mudar o
intervalo de atualização (`refreshSeconds`), os endereços (`statusUrl`), os botões de senha
(`ticketLinks`) ou acrescentar outros locais (ex.: outro Serviço de Emprego). `clickTexts` são
separadores da página que a app clica antes de ler os números (ex.: "Balcões e senhas" no gov.pt).
Apague o ficheiro para repor os valores por omissão.

A pasta `SenhasPorto-dados` guarda a cache do navegador interno; pode ser apagada à vontade.

## Compilar

```
dotnet publish SenhasPorto/SenhasPorto.csproj -c Release -o publish
```
