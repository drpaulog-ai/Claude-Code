package com.letrista.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrista.app.core.MusicCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetristaApp(vm: LyricsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.step != Step.STYLE) { vm.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.step == Step.STYLE) "Letrista" else state.step.title) },
                navigationIcon = {
                    if (state.step != Step.STYLE) {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::openSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Definições")
                    }
                },
            )
        },
        bottomBar = {
            if (state.step != Step.RESULT) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = vm::next,
                        enabled = state.canContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .imePadding(),
                    ) {
                        Text(if (state.step == Step.OPTIONS) "Gerar letra" else "Seguinte")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.step != Step.RESULT) {
                LinearProgressIndicator(
                    progress = { (state.step.ordinal + 1f) / Step.RESULT.ordinal },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (state.step) {
                Step.STYLE -> StyleStep(state, vm)
                Step.THEMES -> ThemesStep(state, vm)
                Step.ARTIST -> ArtistStep(state, vm)
                Step.OPTIONS -> OptionsStep(state, vm)
                Step.RESULT -> ResultStep(state, vm)
            }
        }
    }

    if (state.showSettings) {
        ApiKeyDialog(
            current = state.apiKey,
            onSave = vm::saveApiKey,
            onDismiss = vm::closeSettings,
        )
    }
}

// ---------------------------------------------------------------- Passo 1

@Composable
private fun StyleStep(state: UiState, vm: LyricsViewModel) {
    val query = state.styleQuery.trim()
    val groups = remember(query) {
        MusicCatalog.styleGroups.mapNotNull { g ->
            val matches = if (query.isEmpty()) g.styles
            else g.styles.filter { it.contains(query, ignoreCase = true) }
            if (matches.isEmpty()) null else g.name to matches
        }
    }
    val exactMatch = MusicCatalog.allStyles.any { it.equals(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Que estilo de música queres criar?",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        OutlinedTextField(
            value = state.styleQuery,
            onValueChange = vm::setStyleQuery,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Pesquisar ou escrever outro estilo") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        state.style?.let {
            Text(
                "Selecionado: $it",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (query.isNotEmpty() && !exactMatch) {
                item {
                    StyleRow(
                        label = "Usar \"$query\"",
                        selected = state.style == query,
                        onClick = { vm.selectStyle(query) },
                    )
                    HorizontalDivider()
                }
            }
            groups.forEach { (groupName, styles) ->
                item(key = "h-$groupName") {
                    Text(
                        groupName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(styles, key = { "s-$groupName-$it" }) { style ->
                    StyleRow(style, state.style == style) { vm.selectStyle(style) }
                }
            }
        }
    }
}

@Composable
private fun StyleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

// ---------------------------------------------------------------- Passo 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemesStep(state: UiState, vm: LyricsViewModel) {
    var custom by remember { mutableStateOf("") }
    val addCustom = {
        vm.addCustomTheme(custom)
        custom = ""
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Sobre o que fala a canção?", style = MaterialTheme.typography.titleLarge)
        Text(
            "Escolhe um ou mais temas, ou escreve o teu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            placeholder = { Text("Ex.: o primeiro dia de escola do meu filho") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { addCustom() }),
            trailingIcon = {
                IconButton(onClick = addCustom, enabled = custom.isNotBlank()) {
                    Icon(Icons.Filled.Add, contentDescription = "Adicionar tema")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (state.customThemes + MusicCatalog.themes).forEach { theme ->
                val selected = theme in state.themes
                FilterChip(
                    selected = selected,
                    onClick = { vm.toggleTheme(theme) },
                    label = { Text(theme) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Passo 3

@Composable
private fun ArtistStep(state: UiState, vm: LyricsViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Queres indicar algum intérprete para servir de base?",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "A letra será original, apenas inspirada no estilo do intérprete " +
                "(tom, imagens, vocabulário).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceCard("Sim, quero indicar um intérprete", state.wantsArtist == true) {
            vm.setWantsArtist(true)
        }
        ChoiceCard("Não, deixa a criatividade livre", state.wantsArtist == false) {
            vm.setWantsArtist(false)
        }
        if (state.wantsArtist == true) {
            OutlinedTextField(
                value = state.artist,
                onValueChange = vm::setArtist,
                label = { Text("Intérprete ou banda") },
                placeholder = { Text("Ex.: Amália Rodrigues, Xutos & Pontapés…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChoiceCard(text: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ---------------------------------------------------------------- Passo 4

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsStep(state: UiState, vm: LyricsViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Resumo", style = MaterialTheme.typography.titleLarge)
        Text("Estilo: ${state.style}")
        Text("Temas: ${state.themes.joinToString(", ")}")
        Text(
            "Intérprete: " +
                if (state.wantsArtist == true) state.artist.trim() else "nenhum",
        )

        Spacer(Modifier.height(8.dp))
        Text("Idioma", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicCatalog.languages.forEach { lang ->
                FilterChip(
                    selected = state.language == lang,
                    onClick = { vm.setLanguage(lang) },
                    label = { Text(lang) },
                )
            }
        }

        Text("Ambiente", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicCatalog.moods.forEach { mood ->
                FilterChip(
                    selected = state.mood == mood,
                    onClick = { vm.setMood(mood) },
                    label = { Text(mood) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Incluir refrão", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = state.includeChorus, onCheckedChange = vm::setIncludeChorus)
        }

        OutlinedTextField(
            value = state.notes,
            onValueChange = vm::setNotes,
            label = { Text("Notas adicionais (opcional)") },
            placeholder = { Text("Nomes, lugares, histórias, palavras a incluir…") },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------- Resultado

@Composable
private fun ResultStep(state: UiState, vm: LyricsViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when {
                state.error != null -> {
                    Text(
                        state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                state.lyrics.isEmpty() && state.isGenerating -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.size(12.dp))
                        Text("A compor a tua canção…")
                    }
                }
                else -> SelectionContainer {
                    Text(state.lyrics, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (state.isGenerating && state.lyrics.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        Surface(tonalElevation = 3.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val done = !state.isGenerating && state.lyrics.isNotEmpty()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { copyToClipboard(context, state.lyrics) },
                        enabled = done,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Copiar")
                    }
                    OutlinedButton(
                        onClick = { share(context, state.lyrics) },
                        enabled = done,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Partilhar")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = vm::generate,
                        enabled = !state.isGenerating,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Outra versão")
                    }
                    Button(onClick = vm::startOver, modifier = Modifier.weight(1f)) {
                        Text("Nova canção")
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Letra", text))
    Toast.makeText(context, "Letra copiada", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Partilhar letra"))
}

// ---------------------------------------------------------------- Definições

@Composable
private fun ApiKeyDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chave da API do Claude") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "As letras são criadas pelo Claude. Cria uma chave em " +
                        "platform.claude.com e cola-a aqui. Fica guardada apenas neste dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }, enabled = key.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
