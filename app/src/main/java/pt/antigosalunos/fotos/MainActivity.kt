package pt.antigosalunos.fotos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.antigosalunos.fotos.ui.AppTheme
import pt.antigosalunos.fotos.ui.GalleryScreen
import pt.antigosalunos.fotos.ui.ViewerScreen
import pt.antigosalunos.fotos.ui.WebAlbumScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val vm: AlbumViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                var openIndex by rememberSaveable { mutableStateOf<Int?>(null) }
                var showWeb by rememberSaveable { mutableStateOf(false) }
                val photos = state.album?.photos.orEmpty()

                val index = openIndex
                when {
                    showWeb -> {
                        BackHandler { showWeb = false }
                        WebAlbumScreen(onBack = { showWeb = false })
                    }
                    index != null && index in photos.indices -> {
                        BackHandler { openIndex = null }
                        ViewerScreen(photos, index, onBack = { openIndex = null })
                    }
                    else -> GalleryScreen(
                        state = state,
                        onRefresh = vm::refresh,
                        onOpenPhoto = { openIndex = it },
                        onOpenWeb = { showWeb = true },
                    )
                }
            }
        }
    }
}
