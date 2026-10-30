package com.drpaulog.investadvisor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drpaulog.investadvisor.core.Advice
import com.drpaulog.investadvisor.core.AdviceRequest
import com.drpaulog.investadvisor.core.Advisor
import com.drpaulog.investadvisor.core.Analytics
import com.drpaulog.investadvisor.core.AnalyzedAsset
import com.drpaulog.investadvisor.core.Asset
import com.drpaulog.investadvisor.core.AssetClass
import com.drpaulog.investadvisor.core.AssetStats
import com.drpaulog.investadvisor.core.PriceSeries
import com.drpaulog.investadvisor.core.RiskProfile
import com.drpaulog.investadvisor.data.Catalog
import com.drpaulog.investadvisor.data.MarketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class AssetRow(
    val asset: Asset,
    val series: PriceSeries? = null,
    val stats: AssetStats? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class UiState(
    val assets: List<Asset> = emptyList(),
    val customSymbols: Set<String> = emptySet(),
    val rows: Map<String, AssetRow> = emptyMap(),
    val adviceLoading: Boolean = false,
    val advice: Advice? = null,
    val adviceError: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MarketRepository(app)
    private val limiter = Semaphore(6)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reloadCatalog()
        refreshAll(force = false)
    }

    private fun reloadCatalog() {
        val custom = repo.customAssets()
        val customSymbols = custom.map { it.symbol }.toSet()
        val assets = Catalog.all.filterNot { it.symbol in customSymbols } + custom
        _state.update { s ->
            s.copy(
                assets = assets,
                customSymbols = customSymbols,
                rows = assets.associate { a -> a.symbol to (s.rows[a.symbol]?.copy(asset = a) ?: AssetRow(a)) },
            )
        }
    }

    fun refreshAll(force: Boolean) {
        viewModelScope.launch {
            _state.value.assets.map { a -> async { load(a, force) } }.awaitAll()
        }
    }

    fun ensureLoaded(symbol: String) {
        val row = _state.value.rows[symbol] ?: return
        if (row.series == null && !row.loading) viewModelScope.launch { load(row.asset, force = false) }
    }

    private suspend fun load(asset: Asset, force: Boolean): AssetRow {
        setRow(asset.symbol) { it.copy(loading = true, error = null) }
        val row = limiter.withPermit {
            try {
                val series = repo.series(asset, force)
                val stats = withContext(Dispatchers.Default) { Analytics.compute(series.points) }
                AssetRow(asset, series, stats, loading = false, error = if (stats == null) "Histórico insuficiente" else null)
            } catch (e: Exception) {
                val previous = _state.value.rows[asset.symbol]
                AssetRow(asset, previous?.series, previous?.stats, loading = false, error = e.message ?: "Erro de rede")
            }
        }
        setRow(asset.symbol) { row }
        return row
    }

    private fun setRow(symbol: String, transform: (AssetRow) -> AssetRow) {
        _state.update { s ->
            val current = s.rows[symbol] ?: return@update s
            s.copy(rows = s.rows + (symbol to transform(current)))
        }
    }

    fun addCustom(symbol: String, name: String, assetClass: AssetClass) {
        val clean = symbol.trim().uppercase()
        if (clean.isEmpty()) return
        val asset = Asset(clean, name.trim().ifEmpty { clean }, assetClass, "Personalizado")
        repo.addCustom(asset)
        reloadCatalog()
        viewModelScope.launch { load(asset, force = true) }
    }

    fun removeCustom(symbol: String) {
        repo.removeCustom(symbol)
        reloadCatalog()
    }

    fun requestAdvice(amount: Double, horizonMonths: Int, profile: RiskProfile) {
        _state.update { it.copy(adviceLoading = true, adviceError = null) }
        viewModelScope.launch {
            try {
                val investable = _state.value.assets.filter { it.assetClass != AssetClass.INDICE }
                val rows = investable.map { a ->
                    async {
                        val row = _state.value.rows[a.symbol]
                        if (row?.stats != null) row else load(a, force = false)
                    }
                }.awaitAll()
                val advice = withContext(Dispatchers.Default) {
                    val universe = rows.map { AnalyzedAsset.of(it.asset, it.stats) }
                    Advisor.advise(AdviceRequest(amount, horizonMonths, profile), universe)
                }
                _state.update { it.copy(adviceLoading = false, advice = advice) }
            } catch (e: Exception) {
                _state.update { it.copy(adviceLoading = false, adviceError = e.message ?: "Erro ao calcular") }
            }
        }
    }

    fun clearAdvice() {
        _state.update { it.copy(advice = null, adviceError = null) }
    }
}
