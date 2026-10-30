package com.drpaulog.investadvisor.core

/** Classe de ativo, usada para estimativas de longo prazo e para construir a carteira. */
enum class AssetClass(val label: String) {
    MONETARIO("Liquidez / Monetário"),
    OBRIGACOES("Obrigações"),
    ACOES("Ações (ETF diversificado)"),
    ACAO_INDIVIDUAL("Ação individual"),
    INDICE("Índice de mercado"),
    OURO("Ouro"),
    CRIPTO("Criptomoeda");

    val isEquity: Boolean get() = this == ACOES || this == ACAO_INDIVIDUAL || this == INDICE
}

data class Asset(
    val symbol: String,
    val name: String,
    val assetClass: AssetClass,
    val market: String,
)

data class PricePoint(val epochSeconds: Long, val close: Double)

data class PriceSeries(
    val asset: Asset,
    val currency: String,
    val points: List<PricePoint>,
) {
    val lastPrice: Double? get() = points.lastOrNull()?.close
}

enum class Trend(val label: String) {
    ALTA("Tendência de alta"),
    LATERAL("Tendência lateral"),
    BAIXA("Tendência de baixa"),
}

enum class RiskProfile(val label: String, val description: String) {
    CONSERVADOR("Conservador", "Prefiro proteger o capital, mesmo ganhando menos."),
    MODERADO("Moderado", "Aceito algumas oscilações para ganhar mais."),
    ARROJADO("Arrojado", "Aceito grandes quedas temporárias em troca de mais retorno."),
}
