package pt.antigosalunos.fotos.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.antigosalunos.fotos.AlbumUiState
import pt.antigosalunos.fotos.PhotoActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    state: AlbumUiState,
    onRefresh: () -> Unit,
    onOpenPhoto: (Int) -> Unit,
    onOpenWeb: () -> Unit,
) {
    val context = LocalContext.current
    val photos = state.album?.photos.orEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.album?.title?.ifBlank { null } ?: "Antigos Alunos")
                        if (photos.isNotEmpty()) {
                            Text(
                                "${photos.size} fotos",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { PhotoActions.shareAlbumLink(context) }) {
                        Icon(Icons.Default.Share, "Partilhar álbum")
                    }
                    IconButton(onClick = onOpenWeb) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Ver versão web")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Atualizar")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { PhotoActions.openAlbumInGooglePhotos(context) },
                icon = { Icon(Icons.Default.AddPhotoAlternate, null) },
                text = { Text("Adicionar fotos") },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                photos.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(2.dp, 2.dp, 2.dp, 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(photos, key = { _, p -> p.id }) { i, photo ->
                        AsyncImage(
                            model = photo.thumbnailUrl(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpenPhoto(i) },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Faltam fotos? O Google pode mostrar só parte de álbuns grandes — " +
                                "use \"Ver versão web\" no topo para ver todas.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                !state.loading -> EmptyOrError(state.error, onRefresh, onOpenWeb)
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun EmptyOrError(error: String?, onRefresh: () -> Unit, onOpenWeb: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (error != null) "Não foi possível carregar o álbum.\n($error)"
            else "Não foram encontradas fotos neste álbum.",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRefresh) { Text("Tentar de novo") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenWeb) { Text("Abrir versão web do álbum") }
    }
}
