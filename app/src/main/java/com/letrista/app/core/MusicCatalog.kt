package com.letrista.app.core

/** Um grupo de estilos musicais (ex.: "Rock", "Música Portuguesa"). */
data class StyleGroup(val name: String, val styles: List<String>)

/** Catálogo fixo de estilos e temas apresentados ao utilizador. */
object MusicCatalog {

    val styleGroups: List<StyleGroup> = listOf(
        StyleGroup(
            "Música Portuguesa",
            listOf(
                "Fado", "Fado de Coimbra", "Música Popular Portuguesa", "Pimba",
                "Música Tradicional / Folclore", "Cante Alentejano", "Música de Intervenção",
                "Pop Português", "Rock Português", "Rap Tuga", "Kizomba", "Kuduro",
                "Morna", "Funaná", "Marrabenta",
            ),
        ),
        StyleGroup(
            "Música Brasileira",
            listOf(
                "MPB", "Samba", "Pagode", "Bossa Nova", "Sertanejo", "Sertanejo Universitário",
                "Forró", "Axé", "Funk Carioca", "Piseiro", "Brega", "Arrocha", "Frevo",
                "Choro", "Tropicália", "Baião",
            ),
        ),
        StyleGroup(
            "Pop",
            listOf(
                "Pop", "Dance Pop", "Synth-pop", "Electropop", "Indie Pop", "Dream Pop",
                "K-Pop", "J-Pop", "Teen Pop", "Power Pop", "Art Pop", "Bedroom Pop",
            ),
        ),
        StyleGroup(
            "Rock",
            listOf(
                "Rock", "Rock Clássico", "Hard Rock", "Rock Alternativo", "Indie Rock",
                "Punk Rock", "Pop Punk", "Grunge", "Rock Progressivo", "Psicadélico",
                "Garage Rock", "Post-Rock", "Emo", "Britpop", "Soft Rock", "Rockabilly",
            ),
        ),
        StyleGroup(
            "Metal",
            listOf(
                "Heavy Metal", "Thrash Metal", "Death Metal", "Black Metal", "Power Metal",
                "Metal Sinfónico", "Metalcore", "Nu Metal", "Doom Metal", "Metal Progressivo",
            ),
        ),
        StyleGroup(
            "Hip-Hop & R&B",
            listOf(
                "Hip-Hop", "Rap", "Trap", "Drill", "Boom Bap", "Rap Consciente", "Lo-fi Hip-Hop",
                "R&B", "R&B Contemporâneo", "Neo Soul", "Soul", "Funk", "Motown",
            ),
        ),
        StyleGroup(
            "Eletrónica",
            listOf(
                "EDM", "House", "Deep House", "Techno", "Trance", "Drum and Bass", "Dubstep",
                "Synthwave", "Disco", "Nu-Disco", "Eurodance", "Ambient", "Hyperpop",
            ),
        ),
        StyleGroup(
            "Latina",
            listOf(
                "Reggaeton", "Salsa", "Bachata", "Merengue", "Cumbia", "Tango", "Bolero",
                "Flamenco", "Rumba", "Ranchera", "Mariachi", "Corridos", "Latin Pop",
            ),
        ),
        StyleGroup(
            "Jazz & Blues",
            listOf(
                "Jazz", "Smooth Jazz", "Swing", "Bebop", "Jazz Vocal", "Blues", "Delta Blues",
                "Blues Rock", "Gospel", "Spirituals",
            ),
        ),
        StyleGroup(
            "Country & Folk",
            listOf(
                "Country", "Country Pop", "Bluegrass", "Americana", "Folk", "Indie Folk",
                "Folk Rock", "Música Celta", "Cantautor / Singer-songwriter",
            ),
        ),
        StyleGroup(
            "Mundo",
            listOf(
                "Reggae", "Dancehall", "Ska", "Afrobeat", "Afrobeats", "Amapiano", "Highlife",
                "Calypso", "Soca", "Chanson Française", "Música Italiana", "Bollywood",
            ),
        ),
        StyleGroup(
            "Outros",
            listOf(
                "Balada", "Música Infantil", "Musical / Broadway", "Banda Sonora", "Ópera",
                "Música Clássica com Letra", "Hino / Coral", "Música Cristã / Worship",
                "Canção de Natal", "Jingle Publicitário", "Canção de Embalar", "Paródia / Humor",
            ),
        ),
    )

    val allStyles: List<String> = styleGroups.flatMap { it.styles }

    val themes: List<String> = listOf(
        "Amor", "Paixão", "Desgosto amoroso", "Saudade", "Amizade", "Família", "Mãe", "Pai",
        "Filhos", "Infância", "Nostalgia", "Liberdade", "Esperança", "Superação",
        "Solidão", "Tristeza", "Alegria", "Festa", "Verão", "Mar", "Natureza", "Cidade",
        "Terra natal", "Emigração", "Viagem", "Estrada", "Noite", "Sonhos", "Fé",
        "Morte e perda", "Tempo que passa", "Juventude", "Rebeldia", "Injustiça social",
        "Política", "Guerra e paz", "Trabalho", "Dinheiro", "Fama", "Traição", "Ciúme",
        "Recomeço", "Autoestima", "Saúde mental", "Ansiedade", "Redes sociais",
        "Ambiente", "Futebol", "Animais", "Humor", "Natal", "Casamento",
    )

    val languages: List<String> = listOf(
        "Português (Portugal)", "Português (Brasil)", "Inglês", "Espanhol", "Francês", "Italiano",
    )

    val moods: List<String> = listOf(
        "Qualquer", "Alegre", "Melancólico", "Romântico", "Nostálgico", "Enérgico",
        "Sombrio", "Esperançoso", "Irónico", "Épico", "Calmo",
    )
}
