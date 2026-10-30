# Conselheiro de Investimentos (Android)

App Android que acompanha a evolução de ações, índices, ETFs e criptomoedas (Bitcoin, Ethereum…) em vários mercados, faz previsões probabilísticas e sugere onde investir com base em:

- **quanto** tem para investir,
- **daqui a quanto tempo** precisa do dinheiro,
- o seu **perfil de risco** (conservador, moderado ou arrojado).

> ⚠️ Ferramenta educativa. As previsões são estimativas estatísticas e não garantem resultados. Não é aconselhamento financeiro profissional.

## Funcionalidades

**Mercados**
- Índices: S&P 500, Nasdaq, Dow Jones, Euro Stoxx 50, PSI, DAX, CAC 40, IBEX 35, FTSE 100, Nikkei, Hang Seng, Ibovespa.
- Ações (EUA, Europa e Portugal: EDP, Galp, Jerónimo Martins, BCP…), ETFs (ações, obrigações, ouro) e cripto (BTC, ETH, SOL, XRP em EUR).
- Pode acrescentar qualquer símbolo do Yahoo Finance (botão **+**).
- Dados de 5 anos, guardados em cache para funcionar sem rede.

**Análise e previsão (por ativo)**
- Variação diária, mensal e anual, crescimento anual composto, volatilidade, maior queda, índice de Sharpe, RSI(14), médias móveis de 50 e 200 dias e tendência.
- Previsão a 1 mês, 6 meses, 1, 3 e 5 anos: cenário pessimista (10%), central e otimista (90%) e probabilidade de perda, desenhada no gráfico como um leque.

**Aconselhar**
- Repartição por classes (liquidez, obrigações, ações, ouro, cripto) segundo o prazo e o perfil — quanto mais curto o prazo, mais proteção do capital; sem cripto para prazos inferiores a 1 ano.
- Dentro de cada classe escolhe os ativos com melhor retorno ajustado ao risco e sinais técnicos; reduz a exposição quando o mercado está em tendência de baixa.
- Mostra o valor provável no fim do prazo, o intervalo de 80%, a probabilidade de perder dinheiro e a comparação com deixar tudo em depósitos.

## Como funciona a previsão

1. O retorno esperado de cada ativo combina o histórico (no máximo 35% do peso) com uma expectativa de longo prazo da sua classe, mais um pequeno ajuste de tendência. Isto evita extrapolar subidas extraordinárias (ex.: Bitcoin) como se fossem repetir-se.
2. A volatilidade vem dos dados reais.
3. A evolução é modelada como um movimento browniano geométrico (log-normal); a carteira usa correlações aproximadas entre classes.

O código está em `core/` (Kotlin puro, com testes) e é independente do Android.

## Instalar

O GitHub Actions compila a app a cada push: abra o separador **Actions → Investimentos – APK Android → (última execução) → Artifacts**, descarregue `conselheiro-investimentos-apk`, copie o `app-release.apk` para o telemóvel e instale-o (é preciso permitir “instalar apps de origem desconhecida”).

## Compilar localmente

Requisitos: Android Studio (ou Android SDK 35) e JDK 17.

```bash
./gradlew :core:test           # testes do motor de análise
./gradlew :app:assembleDebug   # APK em app/build/outputs/apk/debug/
```

## Estrutura

```
core/   motor de análise (Analytics, Forecaster, Advisor) + testes
app/    app Android (Jetpack Compose)
  data/ Catalog (lista de ativos) e MarketRepository (Yahoo Finance + cache)
  ui/   ecrãs Mercados, Detalhe e Aconselhar, gráficos
```
