package com.drpaulog.investadvisor.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5D4B),
    secondary = Color(0xFF3B6470),
    tertiary = Color(0xFFB7791F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD8B5),
    secondary = Color(0xFFA2CDDB),
    tertiary = Color(0xFFF2BE63),
)

@Composable
fun InvestTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Cores fixas para as fatias da carteira (distinguíveis em claro e escuro). */
val SliceColors = listOf(
    Color(0xFF2A9D8F), Color(0xFF457B9D), Color(0xFFE9C46A), Color(0xFFF4A261),
    Color(0xFFE76F51), Color(0xFF8E7DBE), Color(0xFF6A994E), Color(0xFFBC4749),
    Color(0xFF4CC9F0), Color(0xFFB5838D), Color(0xFF7F5539), Color(0xFF90A955),
)
