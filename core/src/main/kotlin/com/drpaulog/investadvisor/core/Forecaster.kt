package com.drpaulog.investadvisor.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Distribuição prevista do valor de um investimento num dado horizonte. */
data class ForecastBand(
    val months: Int,
    /** Cenário pessimista: 10% de probabilidade de ficar abaixo. */
    val p10: Double,
    /** Cenário central (mediana). */
    val p50: Double,
    /** Cenário otimista: 10% de probabilidade de ficar acima. */
    val p90: Double,
    /** Valor esperado (média). */
    val expected: Double,
    /** Probabilidade de terminar abaixo do valor inicial. */
    val probabilityOfLoss: Double,
)

/** Estimativa usada nas previsões: retorno anual esperado e volatilidade anual. */
data class ReturnEstimate(val expectedReturn: Double, val volatility: Double)

/**
 * Previsões probabilísticas com um modelo de movimento browniano geométrico.
 *
 * O retorno esperado não é uma simples extrapolação do passado (que sobrestima ativos que
 * subiram muito): combina o histórico com uma expectativa de longo prazo por classe de ativo
 * e um pequeno ajuste de tendência (momentum).
 */
object Forecaster {

    /** Retorno anual esperado de longo prazo (em EUR, antes de impostos) por classe. */
    fun longTermPrior(assetClass: AssetClass): ReturnEstimate = when (assetClass) {
        AssetClass.MONETARIO -> ReturnEstimate(0.022, 0.005)
        AssetClass.OBRIGACOES -> ReturnEstimate(0.033, 0.06)
        AssetClass.ACOES, AssetClass.INDICE -> ReturnEstimate(0.07, 0.16)
        AssetClass.ACAO_INDIVIDUAL -> ReturnEstimate(0.075, 0.30)
        AssetClass.OURO -> ReturnEstimate(0.045, 0.15)
        AssetClass.CRIPTO -> ReturnEstimate(0.12, 0.65)
    }

    fun estimate(stats: AssetStats?, assetClass: AssetClass): ReturnEstimate {
        val prior = longTermPrior(assetClass)
        if (stats == null) return prior
        // O histórico ganha peso com os anos de dados, mas nunca domina a estimativa.
        val historyWeight = (stats.years / 20.0).coerceIn(0.0, 0.35)
        val hist = stats.historicalReturn.coerceIn(-0.30, 0.60)
        val momentum = when (stats.trend) {
            Trend.ALTA -> 0.01
            Trend.BAIXA -> -0.01
            Trend.LATERAL -> 0.0
        }
        val expected = historyWeight * hist + (1 - historyWeight) * prior.expectedReturn + momentum
        val vol = if (stats.volatility > 1e-4) stats.volatility else prior.volatility
        return ReturnEstimate(expected, vol)
    }

    fun band(initial: Double, estimate: ReturnEstimate, months: Int): ForecastBand {
        val t = months / 12.0
        if (t <= 0) return ForecastBand(0, initial, initial, initial, initial, 0.0)
        val mu = ln(1 + estimate.expectedReturn)
        val sigma = estimate.volatility
        val m = (mu - sigma * sigma / 2) * t
        val s = sigma * sqrt(t)
        fun quantile(z: Double) = initial * exp(m + z * s)
        val probLoss = if (s < 1e-12) (if (m < 0) 1.0 else 0.0) else normalCdf(-m / s)
        return ForecastBand(
            months = months,
            p10 = quantile(-Z90),
            p50 = quantile(0.0),
            p90 = quantile(Z90),
            expected = initial * (1 + estimate.expectedReturn).pow(t),
            probabilityOfLoss = probLoss,
        )
    }

    /** Bandas mensais (ou espaçadas) de 0 até [months], para desenhar um gráfico em leque. */
    fun path(initial: Double, estimate: ReturnEstimate, months: Int, steps: Int = 24): List<ForecastBand> {
        val n = steps.coerceAtMost(months).coerceAtLeast(1)
        return (0..n).map { i -> band(initial, estimate, (months * i / n.toDouble()).roundToInt()) }
    }

    private const val Z90 = 1.2815515655446004

    /** Função de distribuição normal padrão (aproximação de Abramowitz-Stegun, erro < 1e-7). */
    fun normalCdf(x: Double): Double {
        val t = 1 / (1 + 0.2316419 * abs(x))
        val d = 0.3989422804014327 * exp(-x * x / 2)
        val p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        return if (x >= 0) 1 - p else p
    }
}
