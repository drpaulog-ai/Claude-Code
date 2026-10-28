package pt.antigosalunos.fotos

/** Uma foto (ou vídeo) do álbum partilhado. */
data class Photo(
    val id: String,
    /** URL base em lh3.googleusercontent.com, sem parâmetros de tamanho. */
    val baseUrl: String,
    val width: Int,
    val height: Int,
) {
    fun thumbnailUrl(size: Int = 400) = "$baseUrl=w$size-h$size-c"
    fun displayUrl(size: Int = 2048) = "$baseUrl=w$size-h$size"
    /** Ficheiro original, em resolução máxima. */
    val downloadUrl get() = "$baseUrl=d"
}

data class Album(val title: String, val photos: List<Photo>)

/**
 * Extrai as fotos da página pública de um álbum partilhado do Google Photos.
 *
 * A página embute os dados num bloco `AF_initDataCallback(...)` com entradas do tipo
 * `["AF1Qip…",["https://lh3.googleusercontent.com/pw/…",largura,altura,…]`.
 * Não é uma API oficial, por isso o formato pode mudar; nesse caso a app recorre
 * à vista web do álbum.
 */
object AlbumParser {
    private val entryRegex = Regex(
        """\["(AF1Q[\w-]+)",\["(https://lh3\.googleusercontent\.com/[\w\-/]+)",(\d+),(\d+)"""
    )
    private val fallbackUrlRegex = Regex("""https://lh3\.googleusercontent\.com/pw/[\w-]+""")
    private val titleRegex = Regex("""<meta\s+property="og:title"\s+content="([^"]*)"""")

    fun parse(html: String): Album {
        val photos = entryRegex.findAll(html)
            .map { m ->
                val (id, url, w, h) = m.destructured
                Photo(id, url, w.toInt(), h.toInt())
            }
            .distinctBy { it.id }
            .toList()
            .ifEmpty {
                // Formato desconhecido: recolhe pelo menos os URLs das imagens.
                fallbackUrlRegex.findAll(html)
                    .map { it.value }
                    .distinct()
                    .map { Photo(it.substringAfterLast('/'), it, 0, 0) }
                    .toList()
            }
        val title = titleRegex.find(html)?.groupValues?.get(1)
            ?.let(::unescapeHtml)
            ?.substringBefore(" - Google Photos")
            ?.substringBefore(" – Google Photos")
            ?.trim()
            .orEmpty()
        return Album(title, photos)
    }

    private fun unescapeHtml(s: String) = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
