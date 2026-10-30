package com.drpaulog.investadvisor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drpaulog.investadvisor.core.Advice
import com.drpaulog.investadvisor.core.RiskProfile

private val horizonSteps = listOf(1, 3, 6, 9, 12, 18, 24, 36, 48, 60, 84, 120, 180, 240, 300, 360)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvisorScreen(state: UiState, onAdvise: (Double, Int, RiskProfile) -> Unit) {
    var amountText by rememberSaveable { mutableStateOf("10000") }
    var horizonIdx by rememberSaveable { mutableFloatStateOf(horizonSteps.indexOf(60).toFloat()) }
    var profile by rememberSaveable { mutableStateOf(RiskProfile.MODERADO) }
    val focus = LocalFocusManager.current
    val amount = parseAmount(amountText)
    val horizon = horizonSteps[horizonIdx.toInt().coerceIn(0, horizonSteps.lastIndex)]

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Onde investir?") })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' || c == '.' || c == ' ' } },
                label = { Text("Quanto quer investir (€)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amount == null || amount <= 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Daqui a quanto tempo precisa do dinheiro: ${horizonLabel(horizon)}", fontWeight = FontWeight.Medium)
            Slider(
                value = horizonIdx,
                onValueChange = { horizonIdx = it },
                valueRange = 0f..horizonSteps.lastIndex.toFloat(),
                steps = horizonSteps.size - 2,
            )
            Text("Perfil de risco", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiskProfile.entries.forEach { p ->
                    FilterChip(selected = p == profile, onClick = { profile = p }, label = { Text(p.label) })
                }
            }
            Text(profile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    focus.clearFocus()
                    if (amount != null && amount > 0) onAdvise(amount, horizon, profile)
                },
                enabled = amount != null && amount > 0 && !state.adviceLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.adviceLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("A analisar mercados…")
                } else {
                    Text("Aconselhar")
                }
            }
            state.adviceError?.let { Text(it, color = Loss) }
            state.advice?.let { AdviceResult(it) }
            Disclaimer()
        }
    }
}

@Composable
private fun AdviceResult(advice: Advice) {
    val req = advice.request
    val band = advice.finalBand
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Em ${horizonLabel(req.horizonMonths)}, ${euros(req.amount)} poderão valer:", style = MaterialTheme.typography.titleSmall)
            Text(euros(band.p50), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("cenário central (ganho de ${euros(band.p50 - req.amount)})")
            Spacer(Modifier.height(6.dp))
            Text("Entre ${euros(band.p10)} e ${euros(band.p90)} com 80% de probabilidade.")
            Text("Probabilidade de terminar com menos do que investiu: ${percent(band.probabilityOfLoss, signed = false)}")
            Text(
                "Retorno anual esperado ${percent(advice.portfolio.expectedReturn)} · volatilidade ${percent(advice.portfolio.volatility, signed = false)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Só em depósitos: cerca de ${euros(advice.cashOnlyBand.p50)}.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    Text("Evolução prevista", style = MaterialTheme.typography.titleMedium)
    FanChart(advice.path, req.amount, Modifier.fillMaxWidth().height(200.dp))
    Text(
        "Linha: cenário central · Sombra: 80% dos cenários · Tracejado: valor investido",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("Carteira sugerida", style = MaterialTheme.typography.titleMedium)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AllocationDonut(advice.allocations.map { it.weight }, Modifier.size(180.dp))
    }
    advice.allocations.forEachIndexed { i, a ->
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(SliceColors[i % SliceColors.size], CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(a.asset.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(percent(a.weight, signed = false), fontWeight = FontWeight.Bold)
                }
                Text(
                    "${euros(a.amount)} · ${a.asset.assetClass.label}" +
                        if (a.asset.symbol != "LIQUIDEZ") " · ${a.asset.symbol}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(a.reason, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Retorno esperado ${percent(a.estimate.expectedReturn)}/ano · risco ${percent(a.estimate.volatility, signed = false)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = trendColor(a.trend),
                )
            }
        }
    }

    if (advice.notes.isNotEmpty()) {
        Text("Notas", style = MaterialTheme.typography.titleMedium)
        advice.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Aceita "10000", "10 000", "10.000", "10.000,50" e "1500.75". */
fun parseAmount(text: String): Double? {
    val t = text.replace(" ", "")
    val normalized = when {
        ',' in t -> t.replace(".", "").replace(',', '.')
        Regex("""\d{1,3}(\.\d{3})+""").matches(t) -> t.replace(".", "")
        else -> t
    }
    return normalized.toDoubleOrNull()
}
