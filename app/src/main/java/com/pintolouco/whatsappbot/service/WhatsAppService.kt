package com.pintolouco.whatsappbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.pintolouco.whatsappbot.model.FluxoMensagem
import com.pintolouco.whatsappbot.utils.Constants
import com.pintolouco.whatsappbot.utils.SupabaseClient
import kotlinx.coroutines.*
import java.net.URLEncoder

class WhatsAppService : AccessibilityService() {
    
    companion object {
        var isServiceRunning = false
        private val mainHandler = Handler(Looper.getMainLooper())
        private var coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
    
    private lateinit var supabaseClient: SupabaseClient
    private var ultimoProcessamento = 0L
    private var processandoMensagem = false
    private var leadAtual: String? = null
    private var numeroAtendente: String? = null
    
    override fun onCreate() {
        super.onCreate()
        supabaseClient = SupabaseClient(this)
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED
            packageNames = arrayOf(Constants.WHATSAPP_PACKAGE)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        setServiceInfo(info)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Evitar processamento paralelo
        if (processandoMensagem) return
        
        // Evitar processar muitas vezes seguidas
        if (System.currentTimeMillis() - ultimoProcessamento < 2000) return
        
        if (event?.packageName == Constants.WHATSAPP_PACKAGE) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    verificarMensagemRecebida()
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    verificarMensagemRecebida()
                }
            }
        }
    }
    
    private fun verificarMensagemRecebida() {
        val root = rootInActiveWindow ?: return
        
        // Verificar se foi banido
        if (verificarBanimento(root)) {
            return
        }
        
        // Extrair número do lead e mensagem
        val leadTelefone = extrairNumeroLead(root) ?: return
        val mensagemLead = extrairMensagemRecebida(root) ?: return
        
        // Ignorar mensagens vazias
        if (mensagemLead.isBlank()) return
        
        leadAtual = leadTelefone
        numeroAtendente = extrairMeuNumero(root) ?: "desconhecido"
        
        // Marcar como processando
        processandoMensagem = true
        ultimoProcessamento = System.currentTimeMillis()
        
        // Processar em background
        coroutineScope.launch {
            try {
                // Buscar etapa atual do lead
                val estado = supabaseClient.getConversaEstado(leadTelefone)
                val etapaAtual = estado?.etapa_atual ?: 1
                
                // Buscar próxima mensagem do fluxo
                val fluxo = supabaseClient.getMensagemByEtapa(etapaAtual + 1)
                
                if (fluxo != null && !fluxo.mensagem.isNullOrEmpty()) {
                    // Enviar resposta
                    enviarResposta(leadTelefone, fluxo)
                    
                    // Atualizar etapa
                    supabaseClient.updateConversaEstado(leadTelefone, numeroAtendente!!, etapaAtual + 1)
                }
                
                // Pequeno delay antes de liberar
                delay(500)
                processandoMensagem = false
                
            } catch (e: Exception) {
                e.printStackTrace()
                processandoMensagem = false
            }
        }
    }
    
    private fun verificarBanimento(root: AccessibilityNodeInfo): Boolean {
        val texto = root.text?.toString()?.lowercase() ?: return false
        
        if (texto.contains("banido") || texto.contains("banned")) {
            coroutineScope.launch {
                numeroAtendente?.let { numero ->
                    supabaseClient.updateNumeroBanido(numero)
                    supabaseClient.sendBanWebhook(numero)
                }
            }
            return true
        }
        return false
    }
    
    private fun extrairNumeroLead(root: AccessibilityNodeInfo): String? {
        // Buscar por texto de contato
        val contatoNode = buscarNodePorTexto(root, listOf("contato", "contact", "número", "number"))
        val texto = contatoNode?.text?.toString() ?: return null
        
        // Extrair números do texto
        val regex = Regex("\\d{10,13}")
        return regex.find(texto)?.value
    }
    
    private fun extrairMeuNumero(root: AccessibilityNodeInfo): String? {
        // Buscar nas configurações ou no título
        val tituloNode = buscarNodePorTexto(root, listOf("(eu)", "(me)", "você", "you"))
        // Fallback: usar número configurado
        return "numero_configurado"
    }
    
    private fun extrairMensagemRecebida(root: AccessibilityNodeInfo): String? {
        // Buscar mensagem recente (última bolha)
        val mensagemNode = encontrarUltimaMensagem(root)
        return mensagemNode?.text?.toString()
    }
    
    private fun encontrarUltimaMensagem(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = mutableListOf(root)
        var ultimaMensagem: AccessibilityNodeInfo? = null
        
        while (stack.isNotEmpty()) {
            val node = stack.removeAt(stack.size - 1)
            
            // Verificar se é um balão de mensagem
            val className = node.className?.toString()?.lowercase() ?: ""
            val texto = node.text?.toString() ?: ""
            
            if ((className.contains("textview") || className.contains("message")) && texto.isNotBlank()) {
                ultimaMensagem = node
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
        
        return ultimaMensagem
    }
    
    private fun buscarNodePorTexto(node: AccessibilityNodeInfo, palavras: List<String>): AccessibilityNodeInfo? {
        val stack = mutableListOf(node)
        
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(stack.size - 1)
            val texto = current.text?.toString()?.lowercase() ?: ""
            val desc = current.contentDescription?.toString()?.lowercase() ?: ""
            
            if (palavras.any { texto.contains(it) || desc.contains(it) }) {
                return current
            }
            
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.add(it) }
            }
        }
        return null
    }
    
    private suspend fun enviarResposta(telefone: String, fluxo: FluxoMensagem) {
        withContext(Dispatchers.Main) {
            try {
                // Abrir WhatsApp via wa.me
                val intent = Intent(Intent.ACTION_VIEW)
                val mensagemCodificada = URLEncoder.encode(fluxo.mensagem, "UTF-8")
                intent.data = Uri.parse("https://wa.me/$telefone?text=$mensagemCodificada")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                
                // Aguardar carregar
                delay(2500)
                
                // Tentar clicar no botão enviar
                val root = rootInActiveWindow
                val enviarBtn = encontrarBotaoEnviar(root)
                enviarBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                
                // Aguardar e voltar
                delay(1000)
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(500)
                performGlobalAction(GLOBAL_ACTION_BACK)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun encontrarBotaoEnviar(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return buscarNodePorTexto(root, listOf("enviar", "send"))
    }
    
    override fun onInterrupt() {
        isServiceRunning = false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        coroutineScope.cancel()
    }
}