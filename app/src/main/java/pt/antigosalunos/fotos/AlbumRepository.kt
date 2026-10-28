package pt.antigosalunos.fotos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object AlbumRepository {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0 Mobile Safari/537.36"

    // Evita a página de consentimento de cookies que a Google mostra na UE.
    private const val CONSENT_COOKIE = "CONSENT=YES+cb; SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg"

    suspend fun loadAlbum(url: String): Album = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "pt-PT,pt;q=0.9")
            .header("Cookie", CONSENT_COOKIE)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            AlbumParser.parse(response.body?.string().orEmpty())
        }
    }

    /** Descarrega [url] para [target] e devolve o tipo MIME indicado pelo servidor. */
    suspend fun download(url: String, target: File): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Resposta vazia")
            target.parentFile?.mkdirs()
            target.outputStream().use { body.byteStream().copyTo(it) }
            body.contentType()?.let { "${it.type}/${it.subtype}" } ?: "image/jpeg"
        }
    }
}
