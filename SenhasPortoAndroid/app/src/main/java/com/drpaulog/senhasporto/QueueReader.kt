package com.drpaulog.senhasporto

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

data class QueueItem(val name: String, val waiting: Int, val info: String)

/**
 * Abre a página oficial de um local num WebView (escondido atrás do ecrã principal) e lê
 * as senhas em espera com o mesmo extract.js da versão Windows.
 */
@SuppressLint("SetJavaScriptEnabled")
class QueueReader(
    context: Context,
    private val location: Location,
    private val onItems: (List<QueueItem>) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    val webView = WebView(context)

    private val handler = Handler(Looper.getMainLooper())
    private val script = context.assets.open("extract.js").bufferedReader().use { it.readText() }
        .replace("__CLICK_TEXTS__", JSONArray(location.clickTexts).toString())

    private var generation = 0
    private var attempt = 0
    private var lastSignature: String? = null
    private var mainFrameError = false

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (!mainFrameError) startPolling()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    mainFrameError = true
                    generation++
                    onFailure("Não foi possível abrir a página oficial (${error.description}). Verifique a ligação à Internet.")
                }
            }
        }
    }

    fun refresh() {
        generation++
        mainFrameError = false
        webView.loadUrl(location.statusUrl)
    }

    fun stop() {
        generation++
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
    }

    fun destroy() {
        stop()
        webView.destroy()
    }

    private fun startPolling() {
        // onPageFinished pode chegar mais de uma vez (redireções): recomeça a contagem.
        val gen = ++generation
        attempt = 0
        lastSignature = null
        handler.postDelayed({ poll(gen) }, POLL_INTERVAL_MS)
    }

    private fun poll(gen: Int) {
        if (gen != generation) return
        attempt++
        webView.evaluateJavascript(script) { json ->
            if (gen != generation) return@evaluateJavascript
            val (items, clicked) = parse(json)
            val signature = items.joinToString("\n") { "${it.name}|${it.waiting}" }
            when {
                // Esperar até os números estabilizarem (as páginas carregam os dados aos poucos).
                items.isNotEmpty() && !clicked && signature == lastSignature -> onItems(items)
                attempt >= MAX_ATTEMPTS ->
                    if (items.isNotEmpty()) onItems(items)
                    else onFailure("Não foi possível ler os números automaticamente nesta atualização. Consulte a página oficial.")
                else -> {
                    lastSignature = signature
                    handler.postDelayed({ poll(gen) }, POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun parse(json: String?): Pair<List<QueueItem>, Boolean> {
        if (json.isNullOrEmpty() || json == "null") return emptyList<QueueItem>() to false
        return try {
            val root = JSONObject(json)
            val array = root.optJSONArray("items") ?: JSONArray()
            val items = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                QueueItem(o.optString("name"), o.optInt("waiting"), o.optString("info"))
            }.distinctBy { it.name to it.waiting }
            items to root.optBoolean("clicked")
        } catch (e: Exception) {
            emptyList<QueueItem>() to false
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2000L
        const val MAX_ATTEMPTS = 15
    }
}
