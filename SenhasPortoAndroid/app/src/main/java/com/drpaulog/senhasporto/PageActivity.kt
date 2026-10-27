package com.drpaulog.senhasporto

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** Página oficial dentro da app (para confirmar os números ou tirar a senha digital). */
class PageActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme
                    if (scheme == "http" || scheme == "https") return false
                    // tel:, mailto:, intent: … abrem na app respetiva.
                    openExternal(request.url.toString())
                    return true
                }
            }
        }
        if (savedInstanceState == null) web.loadUrl(url) else web.restoreState(savedInstanceState)

        setContentView(
            scaffold(
                title,
                web,
                onBack = ::finish,
                action = "Abrir no browser" to { openExternal(web.url ?: url) },
            )
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Usado apenas para navegar para trás dentro da página")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }
}
