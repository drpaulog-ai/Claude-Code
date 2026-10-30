package com.drpaulog.investadvisor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drpaulog.investadvisor.ui.AdvisorScreen
import com.drpaulog.investadvisor.ui.AppViewModel
import com.drpaulog.investadvisor.ui.DetailScreen
import com.drpaulog.investadvisor.ui.InvestTheme
import com.drpaulog.investadvisor.ui.MarketsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InvestTheme { App(viewModel) }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        // As barras de topo de cada ecrã já tratam da barra de estado.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0; selected = null },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text("Mercados") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1; selected = null },
                    icon = { Icon(Icons.Default.Star, null) },
                    label = { Text("Aconselhar") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val symbol = selected
            val row = symbol?.let { state.rows[it] }
            when {
                row != null -> {
                    BackHandler { selected = null }
                    LaunchedEffect(symbol) { vm.ensureLoaded(symbol) }
                    DetailScreen(
                        row = row,
                        isCustom = symbol in state.customSymbols,
                        onBack = { selected = null },
                        onDelete = { selected = null; vm.removeCustom(symbol) },
                    )
                }
                tab == 0 -> MarketsScreen(
                    state = state,
                    onOpen = { selected = it },
                    onRefresh = { vm.refreshAll(force = true) },
                    onAdd = vm::addCustom,
                )
                else -> AdvisorScreen(state = state, onAdvise = vm::requestAdvice)
            }
        }
    }
}
