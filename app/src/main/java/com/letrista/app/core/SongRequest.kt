package com.letrista.app.core

/** Tudo o que o utilizador escolheu no assistente. */
data class SongRequest(
    val style: String,
    val themes: List<String>,
    val referenceArtist: String? = null,
    val language: String = "Português (Portugal)",
    val mood: String = "Qualquer",
    val extraNotes: String = "",
    val includeChorus: Boolean = true,
)

/** Constrói os prompts enviados ao Claude. Puro Kotlin, sem dependências de Android. */
object PromptBuilder {

    val systemPrompt: String = """
        És um letrista profissional, versátil e criativo. Escreves letras originais de canções,
        prontas a cantar, respeitando as convenções do estilo musical pedido: métrica, rima,
        vocabulário, imagens e estrutura típicos desse género.

        Regras:
        - A letra tem de ser 100% original. Nunca copies nem parafraseies versos de canções existentes.
        - Quando for indicado um intérprete de referência, inspira-te apenas no estilo geral dele
          (tom, temas recorrentes, tipo de imagens, cadência e vocabulário). Não uses o nome dele
          na letra, não cites as suas canções e não imites letras concretas.
        - Mantém uma métrica cantável e consistente entre estrofes equivalentes.
        - Responde só com o resultado, no formato indicado, sem comentários antes nem depois.

        Formato da resposta (texto simples, sem markdown):
        Título: <título da canção>

        [Verso 1]
        ...
        (usa marcadores de secção entre parênteses retos, como [Refrão], [Pré-refrão], [Ponte], [Outro])
    """.trimIndent()

    fun userPrompt(request: SongRequest): String = buildString {
        appendLine("Escreve a letra de uma canção com as seguintes características:")
        appendLine("- Estilo musical: ${request.style}")
        appendLine("- Tema(s): ${request.themes.joinToString(", ")}")
        appendLine("- Idioma: ${request.language}")
        if (request.mood.isNotBlank() && request.mood != "Qualquer") {
            appendLine("- Ambiente / emoção: ${request.mood}")
        }
        val artist = request.referenceArtist?.trim().orEmpty()
        if (artist.isNotEmpty()) {
            appendLine("- Intérprete de referência (inspiração de estilo apenas): $artist")
        }
        if (request.includeChorus) {
            appendLine("- Inclui um refrão marcante que se repita.")
        } else {
            appendLine("- Não uses refrão; estrutura a canção apenas com versos/estrofes.")
        }
        val notes = request.extraNotes.trim()
        if (notes.isNotEmpty()) {
            appendLine()
            appendLine("Notas adicionais do autor:")
            appendLine(notes)
        }
    }.trimEnd()
}
