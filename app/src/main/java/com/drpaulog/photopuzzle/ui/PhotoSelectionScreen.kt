package com.drpaulog.photopuzzle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.drpaulog.photopuzzle.PuzzleViewModel

/** Escolha das fotos que vão ser níveis. Nenhuma vem selecionada à partida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSelectionScreen(vm: PuzzleViewModel) {
    val context = LocalContext.current
    val firstTime = vm.levelIds == null
    val selected = vm.draftSelection

    LifecycleResumeEffect(Unit) {
        vm.refreshPhotos()
        onPauseOrDispose { }
    }
    if (!firstTime) BackHandler { vm.cancelEditLevelPhotos() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fotos para os puzzles") },
                navigationIcon = {
                    if (!firstTime) {
                        IconButton(onClick = vm::cancelEditLevelPhotos) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${selected.size} selecionadas", style = MaterialTheme.typography.titleSmall)
                    Button(
                        onClick = { vm.saveLevelPhotos() },
                        enabled = selected.isNotEmpty(),
                    ) { Text("Usar estas fotos") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Text(
                "Toca nas fotos que queres transformar em níveis de puzzle.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = {
                    selected.clear()
                    selected.addAll(vm.allPhotos.map { it.id })
                }) { Text("Selecionar todas") }
                TextButton(onClick = { selected.clear() }, enabled = selected.isNotEmpty()) {
                    Text("Limpar")
                }
            }

            when {
                !vm.photosLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                vm.allPhotos.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Não foram encontradas fotos neste dispositivo.", textAlign = TextAlign.Center)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(vm.allPhotos, key = { it.id }) { photo ->
                        val isSelected = photo.id in selected
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(4.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { if (isSelected) selected.remove(photo.id) else selected.add(photo.id) }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(photo.uri).size(300).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.25f))
                                )
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Selecionada",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .background(Color.White, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
