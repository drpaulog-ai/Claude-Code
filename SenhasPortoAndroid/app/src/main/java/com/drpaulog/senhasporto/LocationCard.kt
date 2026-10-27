package com.drpaulog.senhasporto

import android.content.Intent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cartão de um local no ecrã principal: total, lista por balcão e botões para a senha digital. */
class LocationCard(private val activity: MainActivity, private val location: Location) {
    private val context = activity
    private val total = context.text("Em espera: …", 28f, bold = true)
    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val status = context.text("A iniciar…", 12f, R.color.subtext)
    private var hasItems = false

    val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = context.roundedBackground(R.color.card, R.color.divider)
        val pad = context.dp(16)
        setPadding(pad, pad, pad, pad)

        addView(context.text(location.name, 19f, bold = true))
        addView(context.text(location.address, 13f, R.color.subtext))
        addView(total, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            topMargin = context.dp(10)
            bottomMargin = context.dp(4)
        })
        addView(rows)
        addView(status, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(8) })

        for (link in location.ticketLinks) {
            addView(button(link.label) { context.openExternal(link.url) })
        }
        addView(button("Ver página oficial na app") {
            context.startActivity(
                Intent(context, PageActivity::class.java)
                    .putExtra(PageActivity.EXTRA_URL, location.statusUrl)
                    .putExtra(PageActivity.EXTRA_TITLE, location.name)
            )
        })
    }

    fun showLoading() {
        status.setTextColor(context.color(R.color.subtext))
        status.text = "A atualizar…"
    }

    fun showItems(items: List<QueueItem>) {
        hasItems = true
        rows.removeAllViews()
        for (item in items) {
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(context.text(item.name, 15f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(context.text(item.waiting.toString(), 18f, bold = true))
            }
            val block = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, context.dp(6), 0, context.dp(6))
                addView(line)
                if (item.info.isNotBlank()) addView(context.text(item.info, 12f, R.color.subtext))
            }
            rows.addView(block)
            rows.addView(LinearLayout(context).apply { setBackgroundColor(context.color(R.color.divider)) },
                LinearLayout.LayoutParams(MATCH_PARENT, context.dp(1)))
        }
        val sum = items.sumOf { it.waiting }
        total.text = "Em espera: $sum " + if (sum == 1) "senha" else "senhas"
        total.setTextColor(context.color(R.color.text))
        status.setTextColor(context.color(R.color.subtext))
        status.text = "Atualizado às ${now("HH:mm:ss")}"
    }

    fun showFailure(message: String) {
        status.setTextColor(context.color(R.color.error))
        status.text = "${now("HH:mm")} – $message" + if (hasItems) " (a mostrar os últimos valores lidos)" else ""
        if (!hasItems) {
            total.text = "Em espera: indisponível"
            total.setTextColor(context.color(R.color.subtext))
        }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(context).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(6) }
    }

    private fun now(pattern: String) = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
}
