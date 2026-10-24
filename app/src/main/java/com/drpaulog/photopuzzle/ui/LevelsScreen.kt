package com.drpaulog.photopuzzle.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.drpaulog.photopuzzle.GRID_SIZES
import com.drpaulog.photopuzzle.PhotoRepository
import com.drpaulog.photopuzzle.PuzzleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelsScreen(vm: PuzzleViewModel) {
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        vm.refreshPhotos()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshPhotos() }
    // Depois de dar acesso a mais fotos (Android 14+), volta à escolha dos níveis.
    val morePhotosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.refreshPhotos()
        vm.editLevelPhotos()
    }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.startCustom(uri) }

    // Android 14+: o utilizador pode ter dado acesso só a algumas fotos.
    val partialAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        vm.hasAccess &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) !=
        PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Puzzle de Fotos") },
                actions = {
                    if (vm.hasAccess) {
                        TextButton(onClick = vm::editLevelPhotos) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Text("  Fotos")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    pickLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Escolher foto") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                GRID_SIZES.forEachIndexed { i, size ->
                    SegmentedButton(
                        selected = vm.gridSize == size,
                        onClick = { vm.selectGridSize(size) },
                        shape = SegmentedButtonDefaults.itemShape(i, GRID_SIZES.size),
                    ) { Text("$size×$size") }
                }
            }

            when {
                !vm.hasAccess -> AccessRequest {
                    permissionLauncher.launch(PhotoRepository.requiredPermissions())
                }
                vm.photosLoaded && vm.photos.isEmpty() -> CenteredMessage(
                    "Não há fotos escolhidas para níveis.\n" +
                        "Toca em \"Fotos\" para as escolher, ou usa \"Escolher foto\" para jogar com uma imagem."
                )
                else -> {
                    val done = vm.photos.count { it.id in vm.completed }
                    Text(
                        "$done de ${vm.photos.size} níveis completos em ${vm.gridSize}×${vm.gridSize}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (partialAccess) {
                        TextButton(
                            onClick = { morePhotosLauncher.launch(PhotoRepository.requiredPermissions()) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text("Dar acesso a mais fotos") }
                    }
                    LevelGrid(vm)
                }
            }
        }
    }
}

@Composable
private fun LevelGrid(vm: PuzzleViewModel) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(vm.photos, key = { _, photo -> photo.id }) { index, photo ->
            val completed = photo.id in vm.completed
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.startLevel(index) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(photo.uri).size(300).crossfade(true).build(),
                    contentDescription = "Nível ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Nível ${index + 1}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (completed) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Completo",
                        tint = Color(0xFF4CAF50),
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

@Composable
private fun AccessRequest(onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Cada foto do teu dispositivo torna-se um nível do puzzle.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Dá acesso às fotos para criar os níveis automaticamente.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Permitir acesso às fotos") }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, textAlign = TextAlign.Center)
    }
}
