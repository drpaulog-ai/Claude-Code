package com.drpaulog.senhasporto

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val readers = mutableListOf<QueueReader>()
    private val cards = mutableListOf<LocationCard>()

    private val autoRefresh = object : Runnable {
        override fun run() {
            refreshAll()
            handler.postDelayed(this, REFRESH_SECONDS * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Os WebViews que leem as páginas oficiais ficam por baixo do ecrã principal (opaco):
        // precisam de estar no ecrã para as páginas carregarem normalmente.
        val hidden = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
        }
        list.addView(
            text("Senhas em espera agora. Atualiza a cada ${REFRESH_SECONDS / 60} minutos.", 13f, R.color.subtext),
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) },
        )

        for (location in LOCATIONS) {
            val card = LocationCard(this, location)
            val reader = QueueReader(this, location, card::showItems, card::showFailure)
            cards += card
            readers += reader
            list.addView(card.view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
            hidden.addView(reader.webView, LinearLayout.LayoutParams(MATCH_PARENT, dp(700)))
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(color(R.color.page))
            addView(list)
        }
        val content = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.page))
            addView(hidden, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

        setContentView(scaffold(getString(R.string.app_name), content, action = "Atualizar" to ::restartAutoRefresh))
    }

    override fun onResume() {
        super.onResume()
        restartAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoRefresh)
        readers.forEach { it.stop() }
    }

    override fun onDestroy() {
        readers.forEach { it.destroy() }
        super.onDestroy()
    }

    private fun restartAutoRefresh() {
        handler.removeCallbacks(autoRefresh)
        handler.post(autoRefresh)
    }

    private fun refreshAll() {
        cards.forEach { it.showLoading() }
        readers.forEach { it.refresh() }
    }
}
