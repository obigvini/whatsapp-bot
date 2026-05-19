package com.pintolouco.whatsappbot.model

data class FluxoMensagem(
    val id: Int,
    val etapa: Int,
    val mensagem: String?,
    val tipo_midia: String?,
    val url_midia: String?
)