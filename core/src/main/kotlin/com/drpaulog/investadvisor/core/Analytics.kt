package com.drpaulog.investadvisor.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Indicadores históricos e técnicos calculados a partir de uma série de preços. */
data class AssetStats(
    val lastPrice: Double,
    /** Variações simples (0.05 = +5%). Nulas quando não há histórico suficiente. */
    val change1d: Double?,
    val change1m: Double?,
    val change1y: Double?,
    /** Taxa de crescimento anual composta em todo o histórico disponível. */
    val cagr: Double,
    /** Volatilidade anualizada dos retornos logarítmicos. */
    val volatility: Double,
    /** Maior queda do pico ao vale (valor negativo, ex.: -0.35). */
    val maxDrawdown: Double,
    /** Retorno anual aritmético médio observado (exp(μ + σ²/2) - 1). */
    val historicalReturn: Double,
    val sharpe: Double,
    val sma50: Double?,
    val sma200: Double?,
    val rsi14: Double?,
    val trend: Trend,
    val years: Double,
    val periodsPerYear: Double,
)

object Analytics {
    private const val SECONDS_PER_YEAR = 365.25 * 24 * 3600

    fun compute(points: List<PricePoint>, riskFreeRate: Double = 0.025): AssetStats? {
        val clean = points.filter { it.close > 0 && it.close.isFinite() }.sortedBy { it.epochSeconds }
        if (clean.size < 30) return null
        val closes = clean.map { it.close }
        val years = (clean.last().epochSeconds - clean.first().epochSeconds) / SECONDS_PER_YEAR
        if (years <= 0) return null
        val periodsPerYear = (clean.size - 1) / years

        val logReturns = logReturns(closes)
        val meanLog = logReturns.average()
        val sdLog = stdDev(logReturns)
        val volatility = sdLog * sqrt(periodsPerYear)
        val annualLogMean = meanLog * periodsPerYear
        val historicalReturn = exp(annualLogMean + volatility * volatility / 2) - 1
        val cagr = exp(ln(closes.last() / closes.first()) / years) - 1
        val sharpe = if (volatility > 1e-9) (historicalReturn - riskFreeRate) / volatility else 0.0

        val last = closes.last()
        val sma50 = sma(closes, 50)
        val sma200 = sma(closes, 200)

        return AssetStats(
            lastPrice = last,
            change1d = changeSince(clean, 1),
            change1m = changeOverDays(clean, 30),
            change1y = changeOverDays(clean, 365),
            cagr = cagr,
            volatility = volatility,
            maxDrawdown = maxDrawdown(closes),
            historicalReturn = historicalReturn,
            sharpe = sharpe,
            sma50 = sma50,
            sma200 = sma200,
            rsi14 = rsi(closes, 14),
            trend = trend(last, sma50, sma200),
            years = years,
            periodsPerYear = periodsPerYear,
        )
    }

    fun logReturns(closes: List<Double>): List<Double> =
        closes.zipWithNext { a, b -> ln(b / a) }

    fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }

    fun sma(closes: List<Double>, period: Int): Double? =
        if (closes.size < period) null else closes.takeLast(period).average()

    /** Série completa de médias móveis (null enquanto não há dados suficientes), útil para gráficos. */
    fun smaSeries(closes: List<Double>, period: Int): List<Double?> {
        val out = ArrayList<Double?>(closes.size)
        var sum = 0.0
        for (i in closes.indices) {
            sum += closes[i]
            if (i >= period) sum -= closes[i - period]
            out.add(if (i >= period - 1) sum / period else null)
        }
        return out
    }

    /** RSI de Wilder. */
    fun rsi(closes: List<Double>, period: Int = 14): Double? {
        if (closes.size <= period) return null
        val diffs = closes.zipWithNext { a, b -> b - a }
        var avgGain = diffs.take(period).sumOf { max(it, 0.0) } / period
        var avgLoss = diffs.take(period).sumOf { max(-it, 0.0) } / period
        for (d in diffs.drop(period)) {
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100 - 100 / (1 + rs)
    }

    fun maxDrawdown(closes: List<Double>): Double {
        var peak = closes.first()
        var worst = 0.0
        for (c in closes) {
            if (c > peak) peak = c
            val dd = c / peak - 1
            if (dd < worst) worst = dd
        }
        return worst
    }

    fun trend(last: Double, sma50: Double?, sma200: Double?): Trend {
        if (sma50 == null || sma200 == null) {
            return when {
                sma50 == null -> Trend.LATERAL
                last > sma50 * 1.02 -> Trend.ALTA
                last < sma50 * 0.98 -> Trend.BAIXA
                else -> Trend.LATERAL
            }
        }
        return when {
            last > sma200 && sma50 > sma200 -> Trend.ALTA
            last < sma200 && sma50 < sma200 -> Trend.BAIXA
            else -> Trend.LATERAL
        }
    }

    private fun changeSince(points: List<PricePoint>, stepsBack: Int): Double? {
        if (points.size <= stepsBack) return null
        return points.last().close / points[points.size - 1 - stepsBack].close - 1
    }

    private fun changeOverDays(points: List<PricePoint>, days: Int): Double? {
        val target = points.last().epochSeconds - days * 86_400L
        if (points.first().epochSeconds > target) return null
        val ref = points.lastOrNull { it.epochSeconds <= target } ?: return null
        return points.last().close / ref.close - 1
    }
}
