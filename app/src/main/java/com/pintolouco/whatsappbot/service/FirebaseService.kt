package com.pintolouco.whatsappbot.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

class FirebaseService : FirebaseMessagingService() {
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        val data = message.data
        val tipo = data["tipo"]
        
        when (tipo) {
            "disparar" -> {
                val lead = data["lead"]
                val mensagem = data["mensagem"]
                val etapaInicial = data["etapa_inicial"]?.toIntOrNull() ?: 1
                
                if (!lead.isNullOrEmpty() && !mensagem.isNullOrEmpty()) {
                    enviarMensagemDireta(lead, mensagem)
                }
            }
        }
    }
    
    private fun enviarMensagemDireta(telefone: String, mensagem: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://wa.me/$telefone?text=${URLEncoder.encode(mensagem, "UTF-8")}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Salvar token no Supabase
    }
}