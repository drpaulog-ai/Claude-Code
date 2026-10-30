package com.drpaulog.investadvisor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drpaulog.investadvisor.core.AssetClass
import com.drpaulog.investadvisor.data.Catalog
import com.drpaulog.investadvisor.data.MarketGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(
    state: UiState,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onAdd: (String, String, AssetClass) -> Unit,
) {
    var group by rememberSaveable { mutableStateOf(MarketGroup.INDICES) }
    var showAdd by remember { mutableStateOf(false) }
    val rows = state.assets
        .filter { Catalog.groupOf(it, state.customSymbols) == group }
        .mapNotNull { state.rows[it.symbol] }
    val loadingCount = state.rows.values.count { it.loading }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Mercados") },
            actions = {
                if (loadingCount > 0) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Adicionar símbolo") }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Atualizar") }
            },
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarketGroup.entries.forEach { g ->
                FilterChip(selected = g == group, onClick = { group = g }, label = { Text(g.label) })
            }
        }
        if (rows.isEmpty()) {
            Text(
                if (group == MarketGroup.MEUS) "Ainda não acrescentou símbolos. Toque em + para adicionar (ex.: KO, NOS.LS, ADA-EUR)."
                else "Sem ativos.",
                Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.asset.symbol }) { row ->
                AssetRowItem(row, onClick = { onOpen(row.asset.symbol) })
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showAdd) {
        AddSymbolDialog(onDismiss = { showAdd = false }, onAdd = { s, n, c -> onAdd(s, n, c); showAdd = false; group = MarketGroup.MEUS })
    }
}

@Composable
private fun AssetRowItem(row: AssetRow, onClick: () -> Unit) {
    val stats = row.stats
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.asset.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${row.asset.symbol} · ${row.asset.market}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (stats != null) {
                Text(stats.trend.label, style = MaterialTheme.typography.labelSmall, color = trendColor(stats.trend))
            } else if (row.error != null && !row.loading) {
                Text("Sem dados: ${row.error}", style = MaterialTheme.typography.labelSmall, color = Loss, maxLines = 1)
            }
        }
        val closes = row.series?.points?.takeLast(90)?.map { it.close }.orEmpty()
        if (closes.size > 2) {
            Sparkline(closes, changeColor(stats?.change1m), Modifier.width(64.dp).height(32.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(104.dp)) {
            when {
                stats != null -> {
                    Text(price(stats.lastPrice, row.series?.currency.orEmpty()), fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(percent(stats.change1d), color = changeColor(stats.change1d), style = MaterialTheme.typography.bodySmall)
                    Text("1A ${percent(stats.change1y)}", color = changeColor(stats.change1y), style = MaterialTheme.typography.labelSmall)
                }
                row.loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else -> Text("—")
            }
        }
    }
}

@Composable
private fun AddSymbolDialog(onDismiss: () -> Unit, onAdd: (String, String, AssetClass) -> Unit) {
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf(AssetClass.ACAO_INDIVIDUAL) }
    val choices = listOf(AssetClass.ACAO_INDIVIDUAL, AssetClass.ACOES, AssetClass.OBRIGACOES, AssetClass.OURO, AssetClass.CRIPTO, AssetClass.INDICE)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar símbolo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Use o símbolo do Yahoo Finance. Exemplos: KO, NOS.LS (Lisboa), BMW.DE (Frankfurt), ADA-EUR (cripto).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(symbol, { symbol = it }, label = { Text("Símbolo") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Nome (opcional)") }, singleLine = true)
                Text("Tipo", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { c -> FilterChip(selected = c == cls, onClick = { cls = c }, label = { Text(c.label) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(symbol, name, cls) }, enabled = symbol.isNotBlank()) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
