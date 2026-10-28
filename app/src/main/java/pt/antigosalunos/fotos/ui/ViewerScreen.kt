package pt.antigosalunos.fotos.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import pt.antigosalunos.fotos.Photo
import pt.antigosalunos.fotos.PhotoActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(photos: List<Photo>, startIndex: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startIndex) { photos.size }
    var busy by remember { mutableStateOf(false) }

    fun run(label: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { photos[it].id }) { page ->
            ZoomableImage(photos[page])
        }
        TopAppBar(
            title = { Text("${pager.currentPage + 1} / ${photos.size}") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            },
            actions = {
                val photo = photos[pager.currentPage]
                IconButton(onClick = {
                    run("A preparar para o WhatsApp…") { PhotoActions.sharePhoto(context, photo, onlyWhatsApp = true) }
                }) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar no WhatsApp") }
                IconButton(onClick = {
                    run("A preparar…") { PhotoActions.sharePhoto(context, photo, onlyWhatsApp = false) }
                }) { Icon(Icons.Default.Share, "Partilhar") }
                IconButton(onClick = {
                    run("A guardar…") {
                        PhotoActions.savePhoto(context, photo)
                        Toast.makeText(context, "Guardada em Imagens/AntigosAlunos", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Default.Download, "Guardar") }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.4f),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
        )
    }
}

/** Imagem com zoom: toque duplo para ampliar; com zoom ativo, arrastar e pinçar. */
@Composable
private fun ZoomableImage(photo: Photo) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f; offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .then(
                // Só capta gestos quando há zoom, para não bloquear o deslizar entre fotos.
                if (scale > 1f) Modifier.pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                } else Modifier
            ),
    ) {
        AsyncImage(
            model = photo.displayUrl(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                },
        )
    }
}
