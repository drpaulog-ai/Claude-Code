package pt.antigosalunos.fotos

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumParserTest {

    private val html = """
        <html><head>
        <meta property="og:title" content="Turma de 1995 &amp; amigos - Google Photos">
        </head><body><script>AF_initDataCallback({key: 'ds:1', data:[null,[
        ["AF1QipAAA_1",["https://lh3.googleusercontent.com/pw/AP1GczAAA-x_1",4032,3024,null,null,null,null,null,null,[1]],1690000000000],
        ["AF1QipBBB-2",["https://lh3.googleusercontent.com/pw/AP1GczBBB_y-2",1080,1920,null],1690000000001],
        ["AF1QipAAA_1",["https://lh3.googleusercontent.com/pw/AP1GczAAA-x_1",4032,3024,null],1690000000000]
        ]]});</script></body></html>
    """.trimIndent()

    @Test
    fun parsesPhotosAndTitle() {
        val album = AlbumParser.parse(html)
        assertEquals("Turma de 1995 & amigos", album.title)
        assertEquals(2, album.photos.size)
        val first = album.photos[0]
        assertEquals("AF1QipAAA_1", first.id)
        assertEquals("https://lh3.googleusercontent.com/pw/AP1GczAAA-x_1", first.baseUrl)
        assertEquals(4032, first.width)
        assertEquals("https://lh3.googleusercontent.com/pw/AP1GczAAA-x_1=d", first.downloadUrl)
    }

    @Test
    fun fallsBackToPlainUrls() {
        val album = AlbumParser.parse(
            """<img src="https://lh3.googleusercontent.com/pw/XYZ_1=w200"><img src="https://lh3.googleusercontent.com/pw/XYZ_1=w400">"""
        )
        assertEquals(1, album.photos.size)
        assertEquals("https://lh3.googleusercontent.com/pw/XYZ_1", album.photos[0].baseUrl)
    }
}
