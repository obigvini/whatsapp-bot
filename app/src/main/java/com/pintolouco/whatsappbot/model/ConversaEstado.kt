package com.pintolouco.whatsappbot.model

data class ConversaEstado(
    val id: Int,
    val lead_telefone: String,
    val numero_atendente: String,
    val etapa_atual: Int,
    val ultima_mensagem: String
)