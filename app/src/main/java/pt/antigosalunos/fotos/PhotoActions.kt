package pt.antigosalunos.fotos

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Ações sobre fotos: partilhar (WhatsApp, etc.), guardar na galeria e abrir o álbum. */
object PhotoActions {

    private const val WHATSAPP = "com.whatsapp"
    private const val GOOGLE_PHOTOS = "com.google.android.apps.photos"

    suspend fun sharePhoto(context: Context, photo: Photo, onlyWhatsApp: Boolean) {
        val file = File(context.cacheDir, "shared/${photo.id.takeLast(24)}.jpg")
        val mime = if (file.exists()) "image/jpeg" else AlbumRepository.download(photo.downloadUrl, file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (onlyWhatsApp) {
            try {
                context.startActivity(Intent(send).setPackage(WHATSAPP))
                return
            } catch (_: ActivityNotFoundException) {
                // WhatsApp não instalado: cai para o seletor normal.
            }
        }
        context.startActivity(Intent.createChooser(send, "Partilhar foto"))
    }

    /** Guarda a foto original em Imagens/AntigosAlunos. */
    suspend fun savePhoto(context: Context, photo: Photo) = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "shared/${photo.id.takeLast(24)}.jpg")
        val mime = if (tmp.exists()) "image/jpeg" else AlbumRepository.download(photo.downloadUrl, tmp)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "antigos_alunos_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AntigosAlunos")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o ficheiro")
        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out)
            tmp.inputStream().use { it.copyTo(out) }
        }
    }

    /**
     * Abre o álbum na app Google Photos, onde os membros podem carregar em
     * "Adicionar fotos". (A API do Google Photos não permite a apps de terceiros
     * adicionar fotos a álbuns partilhados que não criaram.)
     */
    fun openAlbumInGooglePhotos(context: Context) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.ALBUM_URL))
        try {
            context.startActivity(Intent(view).setPackage(GOOGLE_PHOTOS))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(view)
        }
    }

    fun shareAlbumLink(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "📸 Álbum de fotos dos antigos alunos — vejam e acrescentem as vossas: ${BuildConfig.ALBUM_URL}"
            )
        }
        context.startActivity(Intent.createChooser(send, "Partilhar álbum"))
    }
}
