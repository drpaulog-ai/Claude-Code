package com.drpaulog.investadvisor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drpaulog.investadvisor.core.Analytics
import com.drpaulog.investadvisor.core.AssetStats
import com.drpaulog.investadvisor.core.Forecaster
import com.drpaulog.investadvisor.core.Trend

private val ranges = listOf("1M" to 30, "6M" to 182, "1A" to 365, "5A" to 365 * 5)
private val horizons = listOf(1, 6, 12, 36, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(row: AssetRow, isCustom: Boolean, onBack: () -> Unit, onDelete: () -> Unit) {
    var rangeIdx by rememberSaveable { mutableIntStateOf(2) }
    var horizon by rememberSaveable { mutableIntStateOf(12) }
    val series = row.series
    val stats = row.stats
    val currency = series?.currency.orEmpty()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(row.asset.name) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = { if (isCustom) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Remover") } },
        )
        if (series == null || stats == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (row.loading) CircularProgressIndicator()
                else Text(row.error ?: "Sem dados", Modifier.padding(24.dp))
            }
            return@Column
        }

        val estimate = remember(stats, row.asset) { Forecaster.estimate(stats, row.asset.assetClass) }
        val days = ranges[rangeIdx].second
        val cutoff = series.points.last().epochSeconds - days * 86_400L
        val start = series.points.indexOfFirst { it.epochSeconds >= cutoff }.coerceAtLeast(0)
        val closes = remember(series) { series.points.map { it.close } }
        val maPeriod = if (days <= 182) 50 else 200
        val ma = remember(closes, maPeriod) { Analytics.smaSeries(closes, maPeriod) }
        val forecast = remember(estimate, horizon, stats) { Forecaster.path(stats.lastPrice, estimate, horizon, steps = 24) }
        val finalBand = forecast.last()

        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${row.asset.symbol} · ${row.asset.market} · ${row.asset.assetClass.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(price(stats.lastPrice, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("  ${percent(stats.change1d)} hoje", color = changeColor(stats.change1d))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ranges.forEachIndexed { i, (label, _) ->
                    FilterChip(selected = i == rangeIdx, onClick = { rangeIdx = i }, label = { Text(label) })
                }
            }
            PriceChart(
                values = closes.subList(start, closes.size),
                movingAverage = ma.subList(start, ma.size),
                forecast = forecast,
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
            Text(
                "Linha: preço · Laranja: média móvel de $maPeriod dias · À direita: previsão a ${horizonLabel(horizon)} " +
                    "(tracejado = cenário central, sombra = 80% de probabilidade)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Previsão", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        horizons.forEach { h ->
                            FilterChip(selected = h == horizon, onClick = { horizon = h }, label = { Text(horizonLabel(h)) })
                        }
                    }
                    Metric("Pessimista (10%)", price(finalBand.p10, currency), changeColor(finalBand.p10 - stats.lastPrice))
                    Metric("Central (mediana)", price(finalBand.p50, currency), changeColor(finalBand.p50 - stats.lastPrice))
                    Metric("Otimista (90%)", price(finalBand.p90, currency), changeColor(finalBand.p90 - stats.lastPrice))
                    Metric("Retorno anual esperado", percent(estimate.expectedReturn))
                    Metric("Probabilidade de perder dinheiro", percent(finalBand.probabilityOfLoss, signed = false))
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Análise", style = MaterialTheme.typography.titleMedium)
                    Text(signalText(stats), color = trendColor(stats.trend))
                    Metric("Variação 1 mês", percent(stats.change1m), changeColor(stats.change1m))
                    Metric("Variação 1 ano", percent(stats.change1y), changeColor(stats.change1y))
                    Metric("Crescimento anual (${"%.1f".format(PT, stats.years)} anos)", percent(stats.cagr), changeColor(stats.cagr))
                    Metric("Volatilidade anual", percent(stats.volatility, signed = false))
                    Metric("Maior queda (máx. → mín.)", percent(stats.maxDrawdown), Loss)
                    Metric("Índice de Sharpe", "%.2f".format(PT, stats.sharpe))
                    Metric("RSI (14)", stats.rsi14?.let { "%.0f".format(PT, it) } ?: "—")
                    Metric("Média móvel 50 dias", stats.sma50?.let { price(it, currency) } ?: "—")
                    Metric("Média móvel 200 dias", stats.sma200?.let { price(it, currency) } ?: "—")
                }
            }
            Disclaimer()
        }
    }
}

@Composable
fun Metric(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Disclaimer() {
    Text(
        "Aviso: esta aplicação é uma ferramenta educativa. As previsões são estimativas estatísticas baseadas em " +
            "dados históricos e não garantem resultados futuros. Não constitui aconselhamento financeiro " +
            "profissional; consulte um consultor certificado antes de investir.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun signalText(s: AssetStats): String {
    val rsi = s.rsi14
    val base = when (s.trend) {
        Trend.ALTA -> "Preço acima das médias de 50 e 200 dias: momento positivo."
        Trend.BAIXA -> "Preço abaixo das médias de 50 e 200 dias: momento negativo; prudência ao comprar."
        Trend.LATERAL -> "Sinais mistos: o mercado não mostra uma direção clara."
    }
    val rsiNote = when {
        rsi == null -> ""
        rsi > 70 -> " RSI elevado (${"%.0f".format(PT, rsi)}): possivelmente sobrecomprado, pode corrigir."
        rsi < 30 -> " RSI baixo (${"%.0f".format(PT, rsi)}): possivelmente sobrevendido."
        else -> ""
    }
    return base + rsiNote
}
