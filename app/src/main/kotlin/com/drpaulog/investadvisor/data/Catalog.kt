package com.drpaulog.investadvisor.data

import com.drpaulog.investadvisor.core.Asset
import com.drpaulog.investadvisor.core.AssetClass

/** Grupos mostrados no ecrã de mercados. */
enum class MarketGroup(val label: String) {
    INDICES("Índices"),
    ACOES("Ações"),
    ETFS("ETFs"),
    CRIPTO("Cripto"),
    MEUS("Meus"),
}

/** Lista inicial de ativos (símbolos do Yahoo Finance). O utilizador pode acrescentar outros. */
object Catalog {
    private fun a(symbol: String, name: String, cls: AssetClass, market: String) = Asset(symbol, name, cls, market)

    val indices = listOf(
        a("^GSPC", "S&P 500", AssetClass.INDICE, "EUA"),
        a("^IXIC", "Nasdaq Composite", AssetClass.INDICE, "EUA"),
        a("^DJI", "Dow Jones", AssetClass.INDICE, "EUA"),
        a("^STOXX50E", "Euro Stoxx 50", AssetClass.INDICE, "Zona Euro"),
        a("PSI20.LS", "PSI (Lisboa)", AssetClass.INDICE, "Portugal"),
        a("^GDAXI", "DAX", AssetClass.INDICE, "Alemanha"),
        a("^FCHI", "CAC 40", AssetClass.INDICE, "França"),
        a("^IBEX", "IBEX 35", AssetClass.INDICE, "Espanha"),
        a("^FTSE", "FTSE 100", AssetClass.INDICE, "Reino Unido"),
        a("^N225", "Nikkei 225", AssetClass.INDICE, "Japão"),
        a("^HSI", "Hang Seng", AssetClass.INDICE, "Hong Kong"),
        a("^BVSP", "Ibovespa", AssetClass.INDICE, "Brasil"),
    )

    val stocks = listOf(
        a("AAPL", "Apple", AssetClass.ACAO_INDIVIDUAL, "EUA"),
        a("MSFT", "Microsoft", AssetClass.ACAO_INDIVIDUAL, "EUA"),
        a("NVDA", "NVIDIA", AssetClass.ACAO_INDIVIDUAL, "EUA"),
        a("GOOGL", "Alphabet", AssetClass.ACAO_INDIVIDUAL, "EUA"),
        a("AMZN", "Amazon", AssetClass.ACAO_INDIVIDUAL, "EUA"),
        a("META", "Meta Platforms", AssetClass.ACAO_INDIVIDUAL, "EUA"),
        a("TSLA", "Tesla", AssetClass.ACAO_INDIVIDUAL, "EUA"),
        a("ASML.AS", "ASML", AssetClass.ACAO_INDIVIDUAL, "Países Baixos"),
        a("SAP.DE", "SAP", AssetClass.ACAO_INDIVIDUAL, "Alemanha"),
        a("MC.PA", "LVMH", AssetClass.ACAO_INDIVIDUAL, "França"),
        a("NOVO-B.CO", "Novo Nordisk", AssetClass.ACAO_INDIVIDUAL, "Dinamarca"),
        a("EDP.LS", "EDP", AssetClass.ACAO_INDIVIDUAL, "Portugal"),
        a("GALP.LS", "Galp Energia", AssetClass.ACAO_INDIVIDUAL, "Portugal"),
        a("JMT.LS", "Jerónimo Martins", AssetClass.ACAO_INDIVIDUAL, "Portugal"),
        a("BCP.LS", "Millennium BCP", AssetClass.ACAO_INDIVIDUAL, "Portugal"),
        a("EDPR.LS", "EDP Renováveis", AssetClass.ACAO_INDIVIDUAL, "Portugal"),
    )

    val etfs = listOf(
        a("VWCE.DE", "Vanguard FTSE All-World", AssetClass.ACOES, "Global"),
        a("IWDA.AS", "iShares Core MSCI World", AssetClass.ACOES, "Global (desenvolvidos)"),
        a("SXR8.DE", "iShares Core S&P 500", AssetClass.ACOES, "EUA"),
        a("EQQQ.DE", "Invesco Nasdaq-100", AssetClass.ACOES, "EUA"),
        a("MEUD.PA", "Amundi Stoxx Europe 600", AssetClass.ACOES, "Europa"),
        a("IS3N.DE", "iShares Core MSCI Emerging Markets", AssetClass.ACOES, "Emergentes"),
        a("IBGS.AS", "iShares Euro Govt Bond 1-3 anos", AssetClass.OBRIGACOES, "Zona Euro"),
        a("IEAG.AS", "iShares Euro Aggregate Bond", AssetClass.OBRIGACOES, "Zona Euro"),
        a("AGG", "iShares Core US Aggregate Bond", AssetClass.OBRIGACOES, "EUA (USD)"),
        a("4GLD.DE", "Xetra-Gold", AssetClass.OURO, "Ouro (EUR)"),
        a("GLD", "SPDR Gold Shares", AssetClass.OURO, "Ouro (USD)"),
    )

    val crypto = listOf(
        a("BTC-EUR", "Bitcoin", AssetClass.CRIPTO, "Cripto"),
        a("ETH-EUR", "Ethereum", AssetClass.CRIPTO, "Cripto"),
        a("SOL-EUR", "Solana", AssetClass.CRIPTO, "Cripto"),
        a("XRP-EUR", "XRP", AssetClass.CRIPTO, "Cripto"),
    )

    val all: List<Asset> = indices + stocks + etfs + crypto

    fun groupOf(asset: Asset, custom: Set<String>): MarketGroup = when {
        asset.symbol in custom -> MarketGroup.MEUS
        asset.assetClass == AssetClass.INDICE -> MarketGroup.INDICES
        asset.assetClass == AssetClass.ACAO_INDIVIDUAL -> MarketGroup.ACOES
        asset.assetClass == AssetClass.CRIPTO -> MarketGroup.CRIPTO
        else -> MarketGroup.ETFS
    }
}
