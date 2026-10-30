package com.drpaulog.investadvisor.data

import android.content.Context
import com.drpaulog.investadvisor.core.Asset
import com.drpaulog.investadvisor.core.AssetClass
import com.drpaulog.investadvisor.core.PricePoint
import com.drpaulog.investadvisor.core.PriceSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Obtém históricos diários de preços (5 anos) do Yahoo Finance, com cache em memória e em disco
 * para funcionar offline e não repetir pedidos.
 */
class MarketRepository(context: Context) {
    private val cacheDir = File(context.filesDir, "prices").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("investadvisor", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, Pair<Long, PriceSeries>>()

    suspend fun series(asset: Asset, forceRefresh: Boolean = false): PriceSeries = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            memory[asset.symbol]?.let { (time, s) -> if (now - time < TTL_MS) return@withContext s }
            readDisk(asset)?.let { (time, s) ->
                memory[asset.symbol] = time to s
                if (now - time < TTL_MS) return@withContext s
            }
        }
        try {
            val fresh = fetchYahoo(asset)
            memory[asset.symbol] = now to fresh
            writeDisk(fresh, now)
            fresh
        } catch (e: IOException) {
            // Sem rede: usa a última cópia conhecida, mesmo que antiga.
            memory[asset.symbol]?.second ?: throw e
        }
    }

    private fun fetchYahoo(asset: Asset): PriceSeries {
        val symbol = URLEncoder.encode(asset.symbol, "UTF-8")
        var lastError: IOException? = null
        for (host in listOf("query1", "query2")) {
            try {
                val url = URL("https://$host.finance.yahoo.com/v8/finance/chart/$symbol?range=5y&interval=1d&includePrePost=false")
                val body = httpGet(url)
                return parseYahoo(asset, body)
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Falha ao obter ${asset.symbol}")
    }

    private fun httpGet(url: URL): String {
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseYahoo(asset: Asset, body: String): PriceSeries {
        try {
            val chart = JSONObject(body).getJSONObject("chart")
            val results = chart.optJSONArray("result")
            if (results == null || results.length() == 0) {
                val msg = chart.optJSONObject("error")?.optString("description") ?: "sem dados"
                throw IOException("${asset.symbol}: $msg")
            }
            val result = results.getJSONObject(0)
            val meta = result.getJSONObject("meta")
            val timestamps = result.optJSONArray("timestamp") ?: throw IOException("${asset.symbol}: sem histórico")
            val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
            // Preferir o preço ajustado (dividendos e desdobramentos) quando existe.
            val closes = result.getJSONObject("indicators").optJSONArray("adjclose")
                ?.optJSONObject(0)?.optJSONArray("adjclose")
                ?: quote.getJSONArray("close")
            val points = ArrayList<PricePoint>(timestamps.length())
            for (i in 0 until timestamps.length()) {
                if (i >= closes.length() || closes.isNull(i)) continue
                val c = closes.getDouble(i)
                if (c > 0) points.add(PricePoint(timestamps.getLong(i), c))
            }
            // O último fecho diário pode estar desatualizado durante a sessão: usa o preço atual.
            val live = meta.optDouble("regularMarketPrice", Double.NaN)
            val liveTime = meta.optLong("regularMarketTime", 0L)
            if (live > 0 && points.isNotEmpty() && liveTime > points.last().epochSeconds) {
                if (liveTime - points.last().epochSeconds < 86_400) points[points.lastIndex] = PricePoint(liveTime, live)
                else points.add(PricePoint(liveTime, live))
            }
            return PriceSeries(asset, meta.optString("currency", ""), points)
        } catch (e: org.json.JSONException) {
            throw IOException("Resposta inválida para ${asset.symbol}", e)
        }
    }

    private fun fileFor(symbol: String) = File(cacheDir, symbol.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".csv")

    private fun writeDisk(series: PriceSeries, time: Long) {
        runCatching {
            fileFor(series.asset.symbol).bufferedWriter().use { w ->
                w.write("$time|${series.currency}\n")
                for (p in series.points) w.write("${p.epochSeconds},${p.close}\n")
            }
        }
    }

    private fun readDisk(asset: Asset): Pair<Long, PriceSeries>? = runCatching {
        val f = fileFor(asset.symbol)
        if (!f.exists()) return null
        val lines = f.readLines()
        val (time, currency) = lines.first().split("|", limit = 2)
        val points = lines.drop(1).mapNotNull { line ->
            val parts = line.split(",")
            if (parts.size == 2) PricePoint(parts[0].toLong(), parts[1].toDouble()) else null
        }
        time.toLong() to PriceSeries(asset, currency, points)
    }.getOrNull()

    // --- Símbolos acrescentados pelo utilizador ---

    fun customAssets(): List<Asset> =
        prefs.getStringSet(KEY_CUSTOM, emptySet()).orEmpty().mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size != 3) return@mapNotNull null
            val cls = runCatching { AssetClass.valueOf(parts[2]) }.getOrNull() ?: return@mapNotNull null
            Asset(parts[0], parts[1], cls, "Personalizado")
        }.sortedBy { it.symbol }

    fun addCustom(asset: Asset) {
        val set = prefs.getStringSet(KEY_CUSTOM, emptySet()).orEmpty()
            .filterNot { it.startsWith(asset.symbol + "|") }.toMutableSet()
        set += "${asset.symbol}|${asset.name.replace("|", " ")}|${asset.assetClass.name}"
        prefs.edit().putStringSet(KEY_CUSTOM, set).apply()
    }

    fun removeCustom(symbol: String) {
        val set = prefs.getStringSet(KEY_CUSTOM, emptySet()).orEmpty()
            .filterNot { it.startsWith("$symbol|") }.toSet()
        prefs.edit().putStringSet(KEY_CUSTOM, set).apply()
        memory.remove(symbol)
        fileFor(symbol).delete()
    }

    companion object {
        private const val TTL_MS = 4 * 60 * 60 * 1000L
        private const val KEY_CUSTOM = "custom_assets"
    }
}
