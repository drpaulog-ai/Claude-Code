package com.drpaulog.investadvisor.core

import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private val day = 86_400L

    /** Série diária sintética com crescimento anual [growth] e uma oscilação para gerar volatilidade. */
    private fun series(days: Int, growth: Double, wiggle: Double = 0.01): List<PricePoint> =
        (0 until days).map { i ->
            val t = i / 365.25
            PricePoint(i * day, 100 * exp(kotlin.math.ln(1 + growth) * t) * (1 + wiggle * sin(i / 3.0)))
        }

    @Test
    fun `stats detect growth, trend and drawdown`() {
        val stats = assertNotNull(Analytics.compute(series(365 * 3, 0.10)))
        assertEquals(0.10, stats.cagr, 0.01)
        assertEquals(Trend.ALTA, stats.trend)
        assertTrue(stats.volatility > 0)
        assertTrue(stats.maxDrawdown <= 0 && stats.maxDrawdown > -0.05)
        assertNotNull(stats.change1y)
        assertEquals(0.10, stats.change1y!!, 0.03)
    }

    @Test
    fun `falling market is a downtrend`() {
        val stats = assertNotNull(Analytics.compute(series(400, -0.30)))
        assertEquals(Trend.BAIXA, stats.trend)
        assertTrue(stats.maxDrawdown < -0.25)
    }

    @Test
    fun `too little data gives no stats`() {
        assertNull(Analytics.compute(series(10, 0.1)))
    }

    @Test
    fun `rsi is bounded`() {
        val up = (1..50).map { it.toDouble() }
        assertEquals(100.0, Analytics.rsi(up)!!, 1e-9)
        val rsi = Analytics.rsi(series(200, 0.0, 0.05).map { it.close })!!
        assertTrue(rsi in 0.0..100.0)
    }

    @Test
    fun `sma series matches direct average`() {
        val closes = (1..10).map { it.toDouble() }
        val s = Analytics.smaSeries(closes, 3)
        assertNull(s[1])
        assertEquals(9.0, s[9]!!, 1e-9)
        assertEquals(Analytics.sma(closes, 3)!!, s.last()!!, 1e-9)
    }

    @Test
    fun `forecast band is ordered and expected value compounds`() {
        val band = Forecaster.band(1000.0, ReturnEstimate(0.07, 0.16), 60)
        assertTrue(band.p10 < band.p50 && band.p50 < band.p90)
        assertEquals(1000 * Math.pow(1.07, 5.0), band.expected, 1e-6)
        assertTrue(band.probabilityOfLoss in 0.05..0.4)
        val cash = Forecaster.band(1000.0, ReturnEstimate(0.02, 0.0), 12)
        assertEquals(0.0, cash.probabilityOfLoss, 1e-9)
        assertEquals(1020.0, cash.p50, 1e-6)
    }

    @Test
    fun `normal cdf is accurate`() {
        assertEquals(0.5, Forecaster.normalCdf(0.0), 1e-7)
        assertEquals(0.975, Forecaster.normalCdf(1.959964), 1e-6)
        assertEquals(0.025, Forecaster.normalCdf(-1.959964), 1e-6)
    }

    @Test
    fun `estimate shrinks extreme history toward the prior`() {
        val stats = assertNotNull(Analytics.compute(series(365 * 5, 1.5, 0.05)))
        val est = Forecaster.estimate(stats, AssetClass.CRIPTO)
        assertTrue(est.expectedReturn < 0.40, "got ${est.expectedReturn}")
    }

    @Test
    fun `strategic weights always sum to one`() {
        for (h in listOf(3, 12, 24, 36, 60, 84, 240)) for (p in RiskProfile.entries) {
            assertEquals(1.0, Advisor.strategicWeights(h, p).values.sum(), 1e-9, "h=$h p=$p")
        }
    }

    private fun universe(): List<AnalyzedAsset> {
        fun a(sym: String, cls: AssetClass, g: Double, w: Double) =
            AnalyzedAsset.of(Asset(sym, sym, cls, "Teste"), Analytics.compute(series(365 * 5, g, w)))
        return listOf(
            a("WORLD", AssetClass.ACOES, 0.09, 0.02),
            a("SP500", AssetClass.ACOES, 0.11, 0.02),
            a("EM", AssetClass.ACOES, 0.03, 0.03),
            a("EUROBOND", AssetClass.OBRIGACOES, 0.02, 0.005),
            a("GOLD", AssetClass.OURO, 0.06, 0.02),
            a("BTC", AssetClass.CRIPTO, 0.40, 0.10),
            a("AAPL", AssetClass.ACAO_INDIVIDUAL, 0.15, 0.04),
            a("XYZ", AssetClass.ACAO_INDIVIDUAL, -0.2, 0.06),
        )
    }

    @Test
    fun `short horizon advice is mostly cash and has no crypto`() {
        val advice = Advisor.advise(AdviceRequest(10_000.0, 6, RiskProfile.MODERADO), universe())
        assertEquals(1.0, advice.allocations.sumOf { it.weight }, 1e-9)
        assertEquals(10_000.0, advice.allocations.sumOf { it.amount }, 1e-6)
        val cash = advice.allocations.first { it.asset.assetClass == AssetClass.MONETARIO }
        assertTrue(cash.weight >= 0.6)
        assertTrue(advice.allocations.none { it.asset.assetClass == AssetClass.CRIPTO })
        assertTrue(advice.finalBand.probabilityOfLoss < 0.2)
    }

    @Test
    fun `long aggressive advice is growth oriented`() {
        val advice = Advisor.advise(AdviceRequest(50_000.0, 120, RiskProfile.ARROJADO), universe())
        assertEquals(1.0, advice.allocations.sumOf { it.weight }, 1e-9)
        val equity = advice.allocations.filter { it.asset.assetClass.isEquity }.sumOf { it.weight }
        assertTrue(equity >= 0.7, "equity=$equity")
        assertTrue(advice.allocations.any { it.asset.symbol == "AAPL" })
        assertTrue(advice.allocations.none { it.asset.symbol == "XYZ" })
        assertTrue(advice.finalBand.p50 > advice.cashOnlyBand.p50)
        assertEquals(advice.path.last().months, 120)
    }

    @Test
    fun `missing data falls back to cash`() {
        val advice = Advisor.advise(AdviceRequest(1_000.0, 60, RiskProfile.MODERADO), emptyList())
        assertEquals(1, advice.allocations.size)
        assertEquals(Advisor.CASH, advice.allocations.single().asset)
        assertTrue(advice.notes.any { it.contains("Sem dados") })
    }
}
