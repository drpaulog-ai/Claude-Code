package pt.assistente.pessoal.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pt.assistente.pessoal.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val s = vm.settings
    var claudeKey by remember { mutableStateOf(s.claudeKey) }
    var geminiKey by remember { mutableStateOf(s.geminiKey) }
    var openaiKey by remember { mutableStateOf(s.openaiKey) }
    var claudeModel by remember { mutableStateOf(s.claudeModel) }
    var geminiModel by remember { mutableStateOf(s.geminiModel) }
    var openaiModel by remember { mutableStateOf(s.openaiModel) }
    var webSearch by remember { mutableStateOf(s.webSearch) }
    var escalate by remember { mutableFloatStateOf(s.escalateBelow.toFloat()) }
    var accept by remember { mutableFloatStateOf(s.acceptFrom.toFloat()) }

    fun saveAndExit() {
        s.claudeKey = claudeKey
        s.geminiKey = geminiKey
        s.openaiKey = openaiKey
        s.claudeModel = claudeModel
        s.geminiModel = geminiModel
        s.openaiModel = openaiModel
        s.webSearch = webSearch
        s.escalateBelow = escalate.toInt()
        s.acceptFrom = accept.toInt()
        onBack()
    }

    BackHandler { saveAndExit() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Definições") },
                navigationIcon = {
                    IconButton(onClick = { saveAndExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Modelo local")
            Text(vm.localStatus, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !vm.localBusy) {
                    Text("Importar ficheiro .task")
                }
                OutlinedButton(onClick = { vm.loadLocal() }, enabled = !vm.localBusy) {
                    Text("Recarregar")
                }
            }
            Hint(
                "Descarrega um modelo Gemma em formato .task (MediaPipe/LiteRT) do Hugging Face " +
                    "para a pasta Transferências e importa-o aqui. O ficheiro é copiado para a app."
            )

            HorizontalDivider()
            SectionTitle("Nuvem (por ordem de prioridade)")
            KeyField("1. Chave API Claude (Anthropic)", claudeKey) { claudeKey = it }
            ModelField("Modelo Claude", claudeModel) { claudeModel = it }
            KeyField("2. Chave API Gemini (Google)", geminiKey) { geminiKey = it }
            ModelField("Modelo Gemini", geminiModel) { geminiModel = it }
            KeyField("3. Chave API OpenAI (ChatGPT)", openaiKey) { openaiKey = it }
            ModelField("Modelo OpenAI", openaiModel) { openaiModel = it }
            Hint("Deixa uma chave em branco para não usar esse serviço. As chaves ficam cifradas só neste telemóvel.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Permitir pesquisa na web na nuvem", Modifier.weight(1f))
                Switch(checked = webSearch, onCheckedChange = { webSearch = it })
            }

            HorizontalDivider()
            SectionTitle("Quando recorrer à nuvem (modo Automático)")
            Text("A nuvem responde se a confiança local for menor que ${escalate.toInt()}")
            Slider(
                value = escalate,
                onValueChange = {
                    escalate = it
                    if (accept < it) accept = it
                },
                valueRange = 0f..10f,
                steps = 9,
            )
            Text("Aceita a resposta local sem validar a partir de ${accept.toInt()}")
            Slider(
                value = accept,
                onValueChange = {
                    accept = it
                    if (escalate > it) escalate = it
                },
                valueRange = 0f..11f,
                steps = 10,
            )
            Hint(
                "Entre ${escalate.toInt()} e ${accept.toInt() - 1}: a nuvem valida a resposta local e corrige-a se for preciso. " +
                    "Põe o segundo valor em 11 para validar sempre. Perguntas sobre informação atual vão sempre à nuvem."
            )

            Button(onClick = { saveAndExit() }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    modifier = Modifier.fillMaxWidth(),
)

@Composable
private fun ModelField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
)
