package com.drpaulog.senhasporto

data class TicketLink(val label: String, val url: String)

data class Location(
    val name: String,
    val address: String,
    /** Página oficial onde aparecem as senhas em espera. */
    val statusUrl: String,
    /** Separadores a clicar antes de ler os números (ex.: "Balcões e senhas"). */
    val clickTexts: List<String> = emptyList(),
    val ticketLinks: List<TicketLink>,
)

/** Os mesmos locais e ligações que a versão Windows (config.json por omissão). */
val LOCATIONS = listOf(
    Location(
        name = "Loja do Cidadão do Porto",
        address = "Av. Fernão de Magalhães, 1862, Porto",
        statusUrl = "https://www2.gov.pt/locais-de-atendimento-de-servicos-publicos/loja-de-cidadao-do-porto",
        clickTexts = listOf("Balcões e senhas"),
        ticketLinks = listOf(
            TicketLink(
                "Tirar senha digital (gov.pt)",
                "https://www2.gov.pt/locais-de-atendimento-de-servicos-publicos/loja-de-cidadao-do-porto",
            ),
            TicketLink(
                "Como tirar senha online",
                "https://www2.gov.pt/locais-de-atendimento-de-servicos-publicos/tirar-senha-online-para-loja-de-cidadao",
            ),
        ),
    ),
    Location(
        name = "IEFP – Serviço de Emprego do Porto",
        address = "Rua Guedes de Azevedo, 212, Porto (Delegação Regional do Norte)",
        statusUrl = "https://www.iefp.pt/detalheservico?id=323659",
        ticketLinks = listOf(
            TicketLink("Tirar senha digital (IEFP / sigaApp)", "https://www.iefp.pt/detalheservico?id=323659"),
            TicketLink("Portal SIGA (gov.pt)", "https://www2.gov.pt/servicos/agendar-atendimento-pelo-sistema-siga"),
        ),
    ),
)

const val REFRESH_SECONDS = 120
