package com.drpaulog.investadvisor.core

import kotlin.math.max
import kotlin.math.sqrt

data class AdviceRequest(
    val amount: Double,
    val horizonMonths: Int,
    val profile: RiskProfile,
)

/** Ativo com dados de mercado já analisados, candidato a entrar na carteira. */
data class AnalyzedAsset(
    val asset: Asset,
    val stats: AssetStats?,
    val estimate: ReturnEstimate,
) {
    companion object {
        fun of(asset: Asset, stats: AssetStats?) =
            AnalyzedAsset(asset, stats, Forecaster.estimate(stats, asset.assetClass))
    }
}

data class Allocation(
    val asset: Asset,
    val weight: Double,
    val amount: Double,
    val estimate: ReturnEstimate,
    val trend: Trend?,
    val reason: String,
)

data class Advice(
    val request: AdviceRequest,
    val allocations: List<Allocation>,
    val portfolio: ReturnEstimate,
    val finalBand: ForecastBand,
    val path: List<ForecastBand>,
    /** O mesmo montante deixado apenas em liquidez/depósitos, para comparação. */
    val cashOnlyBand: ForecastBand,
    val notes: List<String>,
)

/**
 * Motor de aconselhamento: define a repartição por classes de ativo consoante o prazo e o
 * perfil de risco, escolhe os melhores ativos de cada classe pelos dados de mercado e
 * projeta o resultado provável da carteira.
 */
object Advisor {

    /** Alternativa sem risco de mercado, sempre disponível. */
    val CASH = Asset(
        symbol = "LIQUIDEZ",
        name = "Depósito a prazo / Certificados de Aforro / Fundo monetário",
        assetClass = AssetClass.MONETARIO,
        market = "Portugal / Zona Euro",
    )

    /** Pesos-alvo por classe (somam 1) em função do prazo e do perfil. */
    fun strategicWeights(horizonMonths: Int, profile: RiskProfile): Map<AssetClass, Double> {
        val m = AssetClass.MONETARIO
        val o = AssetClass.OBRIGACOES
        val a = AssetClass.ACOES
        val g = AssetClass.OURO
        val c = AssetClass.CRIPTO
        val table: Map<AssetClass, Int> = when {
            horizonMonths < 12 -> when (profile) {
                RiskProfile.CONSERVADOR -> mapOf(m to 85, o to 15)
                RiskProfile.MODERADO -> mapOf(m to 65, o to 25, a to 10)
                RiskProfile.ARROJADO -> mapOf(m to 45, o to 30, a to 20, g to 5)
            }
            horizonMonths < 36 -> when (profile) {
                RiskProfile.CONSERVADOR -> mapOf(m to 40, o to 45, a to 10, g to 5)
                RiskProfile.MODERADO -> mapOf(m to 20, o to 40, a to 30, g to 10)
                RiskProfile.ARROJADO -> mapOf(m to 10, o to 25, a to 50, g to 10, c to 5)
            }
            horizonMonths < 84 -> when (profile) {
                RiskProfile.CONSERVADOR -> mapOf(m to 15, o to 45, a to 30, g to 10)
                RiskProfile.MODERADO -> mapOf(m to 5, o to 30, a to 50, g to 10, c to 5)
                RiskProfile.ARROJADO -> mapOf(o to 15, a to 65, g to 10, c to 10)
            }
            else -> when (profile) {
                RiskProfile.CONSERVADOR -> mapOf(m to 5, o to 40, a to 45, g to 10)
                RiskProfile.MODERADO -> mapOf(o to 20, a to 65, g to 10, c to 5)
                RiskProfile.ARROJADO -> mapOf(o to 5, a to 75, g to 5, c to 15)
            }
        }
        return table.mapValues { it.value / 100.0 }
    }

    /** Pontuação de atratividade: retorno ajustado ao risco + sinais técnicos. */
    fun score(candidate: AnalyzedAsset, riskFreeRate: Double = 0.022): Double {
        val e = candidate.estimate
        var s = if (e.volatility > 1e-6) (e.expectedReturn - riskFreeRate) / e.volatility else 0.0
        val stats = candidate.stats ?: return s - 0.5
        s += when (stats.trend) {
            Trend.ALTA -> 0.15
            Trend.LATERAL -> 0.0
            Trend.BAIXA -> -0.15
        }
        val rsi = stats.rsi14
        if (rsi != null && rsi > 75) s -= 0.1 // sobrecomprado
        if (rsi != null && rsi < 30 && stats.trend != Trend.BAIXA) s += 0.05 // correção numa tendência saudável
        // Penaliza quedas máximas muito profundas.
        s += stats.maxDrawdown * 0.2
        return s
    }

    fun advise(request: AdviceRequest, universe: List<AnalyzedAsset>): Advice {
        require(request.amount > 0) { "O montante tem de ser positivo." }
        require(request.horizonMonths > 0) { "O prazo tem de ser positivo." }
        val notes = mutableListOf<String>()
        val classWeights = strategicWeights(request.horizonMonths, request.profile).toMutableMap()
        val picks = mutableListOf<Pair<AnalyzedAsset, Double>>() // ativo, peso na carteira
        val cash = AnalyzedAsset.of(CASH, null)

        val sleeves = listOf(
            AssetClass.OBRIGACOES to 2,
            AssetClass.ACOES to 3,
            AssetClass.OURO to 1,
            AssetClass.CRIPTO to 2,
        )
        for ((assetClass, maxPicks) in sleeves) {
            var sleeveWeight = classWeights[assetClass] ?: continue
            if (sleeveWeight <= 0) continue
            val candidates = universe.filter { it.asset.assetClass == assetClass && it.stats != null }
                .sortedByDescending { score(it) }
            if (candidates.isEmpty()) {
                notes += "Sem dados de mercado para ${assetClass.label.lowercase()}: essa parte ficou em liquidez."
                classWeights[AssetClass.MONETARIO] = (classWeights[AssetClass.MONETARIO] ?: 0.0) + sleeveWeight
                continue
            }

            // Componente de ações individuais para perfis com prazo longo.
            if (assetClass == AssetClass.ACOES && request.horizonMonths >= 36 && request.profile != RiskProfile.CONSERVADOR) {
                val share = if (request.profile == RiskProfile.ARROJADO) 0.2 else 0.1
                val n = if (request.profile == RiskProfile.ARROJADO) 3 else 2
                // Só empresas com retorno ajustado ao risco positivo e que não estejam em queda.
                val stocks = universe
                    .filter { it.asset.assetClass == AssetClass.ACAO_INDIVIDUAL && it.stats != null }
                    .filter { it.stats?.trend != Trend.BAIXA && score(it) > 0 }
                    .sortedByDescending { score(it) }
                    .take(n)
                if (stocks.isNotEmpty()) {
                    val stockWeight = sleeveWeight * share
                    sleeveWeight -= stockWeight
                    picks += distribute(stocks, stockWeight)
                }
            }

            val chosen = candidates.take(maxPicks)
            // Ajuste tático: se a maioria dos escolhidos está em tendência de baixa, reduz exposição.
            val falling = chosen.count { it.stats?.trend == Trend.BAIXA }
            if ((assetClass == AssetClass.ACOES || assetClass == AssetClass.CRIPTO) && falling * 2 > chosen.size) {
                val cut = sleeveWeight * 0.25
                sleeveWeight -= cut
                classWeights[AssetClass.MONETARIO] = (classWeights[AssetClass.MONETARIO] ?: 0.0) + cut
                notes += "${assetClass.label}: o mercado está em tendência de baixa, por isso 25% desta parte " +
                    "fica temporariamente em liquidez. Pode ser investida aos poucos (ex.: mensalmente)."
            }
            picks += distribute(chosen, sleeveWeight)
        }
        val cashWeight = classWeights[AssetClass.MONETARIO] ?: 0.0
        if (cashWeight > 0) picks.add(0, cash to cashWeight)

        val allocations = picks
            .groupBy { it.first.asset.symbol }
            .map { (_, list) -> list.first().first to list.sumOf { it.second } }
            .filter { it.second > 0.0005 }
            .map { (a, w) ->
                Allocation(
                    asset = a.asset,
                    weight = w,
                    amount = request.amount * w,
                    estimate = a.estimate,
                    trend = a.stats?.trend,
                    reason = reasonFor(a, request),
                )
            }
            .sortedByDescending { it.weight }

        val portfolio = portfolioEstimate(allocations)
        val months = request.horizonMonths
        notes += generalNotes(request)

        return Advice(
            request = request,
            allocations = allocations,
            portfolio = portfolio,
            finalBand = Forecaster.band(request.amount, portfolio, months),
            path = Forecaster.path(request.amount, portfolio, months),
            cashOnlyBand = Forecaster.band(request.amount, cash.estimate, months),
            notes = notes,
        )
    }

    private fun distribute(chosen: List<AnalyzedAsset>, weight: Double): List<Pair<AnalyzedAsset, Double>> {
        val raw = chosen.map { max(score(it), 0.0) + 0.3 }
        val total = raw.sum()
        return chosen.mapIndexed { i, a -> a to weight * raw[i] / total }
    }

    /** Correlações aproximadas entre classes de ativos (estimativas de longo prazo). */
    fun correlation(a: Asset, b: Asset): Double {
        if (a.symbol == b.symbol) return 1.0
        val x = a.assetClass
        val y = b.assetClass
        if (x == AssetClass.MONETARIO || y == AssetClass.MONETARIO) return 0.0
        fun pair(p: AssetClass, q: AssetClass) = (x == p && y == q) || (x == q && y == p)
        return when {
            x.isEquity && y.isEquity -> if (x == AssetClass.ACAO_INDIVIDUAL || y == AssetClass.ACAO_INDIVIDUAL) 0.55 else 0.85
            x == y -> 0.8
            (x.isEquity && y == AssetClass.CRIPTO) || (y.isEquity && x == AssetClass.CRIPTO) -> 0.35
            (x.isEquity && y == AssetClass.OBRIGACOES) || (y.isEquity && x == AssetClass.OBRIGACOES) -> 0.1
            (x.isEquity && y == AssetClass.OURO) || (y.isEquity && x == AssetClass.OURO) -> 0.1
            pair(AssetClass.OBRIGACOES, AssetClass.OURO) -> 0.25
            pair(AssetClass.OURO, AssetClass.CRIPTO) -> 0.15
            pair(AssetClass.OBRIGACOES, AssetClass.CRIPTO) -> 0.05
            else -> 0.2
        }
    }

    fun portfolioEstimate(allocations: List<Allocation>): ReturnEstimate {
        val totalWeight = allocations.sumOf { it.weight }
        if (totalWeight <= 0) return Forecaster.longTermPrior(AssetClass.MONETARIO)
        val expected = allocations.sumOf { it.weight * it.estimate.expectedReturn } / totalWeight
        var variance = 0.0
        for (i in allocations) for (j in allocations) {
            variance += i.weight * j.weight * i.estimate.volatility * j.estimate.volatility * correlation(i.asset, j.asset)
        }
        return ReturnEstimate(expected, sqrt(max(variance, 0.0)) / totalWeight)
    }

    private fun reasonFor(a: AnalyzedAsset, request: AdviceRequest): String {
        val stats = a.stats
        return when (a.asset.assetClass) {
            AssetClass.MONETARIO ->
                if (request.horizonMonths < 12) "Dinheiro necessário em breve: protege o capital e mantém liquidez."
                else "Almofada de segurança com risco quase nulo; reduz as oscilações da carteira."
            AssetClass.OBRIGACOES -> "Rendimento estável e baixa correlação com ações; amortece quedas."
            AssetClass.OURO -> "Diversificação e proteção em crises e contra a inflação."
            AssetClass.CRIPTO -> "Potencial de valorização elevado, mas muito volátil" +
                (stats?.let { " (já caiu ${pct(it.maxDrawdown)} do máximo)" } ?: "") + ". Apenas uma pequena parte."
            AssetClass.ACOES, AssetClass.INDICE -> "Motor de crescimento a longo prazo, muito diversificado" +
                (stats?.let { "; ${it.trend.label.lowercase()}" } ?: "") + "."
            AssetClass.ACAO_INDIVIDUAL -> "Empresa com o melhor retorno ajustado ao risco da lista" +
                (stats?.let { " (${it.trend.label.lowercase()})" } ?: "") + "; risco específico elevado."
        }
    }

    private fun generalNotes(request: AdviceRequest): List<String> = buildList {
        if (request.horizonMonths < 12) {
            add("Com menos de 1 ano de prazo, ações e criptomoedas podem estar em queda precisamente quando precisar do dinheiro, por isso o peso delas é mínimo.")
        }
        if (request.horizonMonths >= 36) {
            add("Rebalanceie a carteira uma vez por ano para voltar às percentagens indicadas.")
        }
        if (request.horizonMonths >= 24) {
            add("Nos últimos 12–24 meses antes de precisar do dinheiro, vá passando gradualmente para liquidez/obrigações.")
        }
        add("Em Portugal, as mais-valias e juros são normalmente tributados a 28%; os valores apresentados são antes de impostos e comissões.")
        add("Prefira ETFs de acumulação com custos baixos (TER) e uma corretora regulada pela CMVM ou equivalente europeu.")
    }

    private fun pct(x: Double) = "%.0f%%".format(kotlin.math.abs(x) * 100)
}
