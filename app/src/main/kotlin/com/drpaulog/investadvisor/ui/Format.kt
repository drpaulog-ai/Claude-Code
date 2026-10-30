package com.drpaulog.investadvisor.ui

import androidx.compose.ui.graphics.Color
import com.drpaulog.investadvisor.core.Trend
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

val PT: Locale = Locale.forLanguageTag("pt-PT")

fun euros(value: Double): String =
    NumberFormat.getCurrencyInstance(PT).apply {
        currency = Currency.getInstance("EUR")
        maximumFractionDigits = if (value >= 1000) 0 else 2
    }.format(value)

fun price(value: Double, currency: String): String {
    val nf = NumberFormat.getNumberInstance(PT).apply {
        minimumFractionDigits = if (value < 1) 4 else 2
        maximumFractionDigits = if (value < 1) 4 else 2
    }
    return if (currency.isBlank()) nf.format(value) else "${nf.format(value)} $currency"
}

fun percent(value: Double?, signed: Boolean = true): String {
    if (value == null) return "—"
    val nf = NumberFormat.getNumberInstance(PT).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    val sign = if (signed && value > 0) "+" else ""
    return "$sign${nf.format(value * 100)}%"
}

fun shortDate(epochSeconds: Long): String =
    SimpleDateFormat("MMM yyyy", PT).format(Date(epochSeconds * 1000))

fun horizonLabel(months: Int): String = when {
    months < 12 -> "$months ${if (months == 1) "mês" else "meses"}"
    months % 12 == 0 -> "${months / 12} ${if (months == 12) "ano" else "anos"}"
    else -> "${months / 12} a ${months % 12} m"
}

val Gain = Color(0xFF1B9E5A)
val Loss = Color(0xFFD64545)
val Neutral = Color(0xFF8A8F98)

fun changeColor(value: Double?): Color = when {
    value == null -> Neutral
    value > 0 -> Gain
    value < 0 -> Loss
    else -> Neutral
}

fun trendColor(trend: Trend?): Color = when (trend) {
    Trend.ALTA -> Gain
    Trend.BAIXA -> Loss
    else -> Neutral
}
