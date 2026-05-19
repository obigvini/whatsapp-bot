package com.pintolouco.whatsappbot.utils

import android.content.Context
import android.content.SharedPreferences
import io.supabase.postgrest.PostgrestClient
import io.supabase.storage.StorageClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class SupabaseClient(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private var supabaseUrl: String? = null
    private var supabaseKey: String? = null
    
    init {
        loadConfig()
    }
    
    private fun loadConfig() {
        supabaseUrl = prefs.getString(Constants.SUPABASE_URL_KEY, null)
        supabaseKey = prefs.getString(Constants.SUPABASE_KEY_KEY, null)
    }
    
    fun isConfigured(): Boolean {
        return !supabaseUrl.isNullOrEmpty() && !supabaseKey.isNullOrEmpty()
    }
    
    fun updateConfig(url: String, key: String) {
        supabaseUrl = url
        supabaseKey = key
        prefs.edit()
            .putString(Constants.SUPABASE_URL_KEY, url)
            .putString(Constants.SUPABASE_KEY_KEY, key)
            .apply()
    }
    
    private fun getPostgrestClient(): PostgrestClient? {
        return if (isConfigured()) {
            PostgrestClient(supabaseUrl!!, supabaseKey!!)
        } else null
    }
    
    private fun getStorageClient(): StorageClient? {
        return if (isConfigured()) {
            StorageClient(supabaseUrl!!, supabaseKey!!)
        } else null
    }
    
    // Obter mensagem da sequência
    suspend fun getMensagemByEtapa(etapa: Int): FluxoMensagem? = withContext(Dispatchers.IO) {
        try {
            val client = getPostgrestClient() ?: return@withContext null
            val response = client.from("fluxos")
                .select()
                .eq("etapa", etapa)
                .execute()
            
            if (response.isSuccessful) {
                val data = response.body?.string()
                if (!data.isNullOrEmpty() && data != "[]") {
                    val jsonArray = org.json.JSONArray(data)
                    if (jsonArray.length() > 0) {
                        val jsonObject = jsonArray.getJSONObject(0)
                        return@withContext FluxoMensagem(
                            id = jsonObject.getInt("id"),
                            etapa = jsonObject.getInt("etapa"),
                            mensagem = jsonObject.optString("mensagem", null),
                            tipo_midia = jsonObject.optString("tipo_midia", null),
                            url_midia = jsonObject.optString("url_midia", null)
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // Obter estado atual da conversa
    suspend fun getConversaEstado(leadTelefone: String): ConversaEstado? = withContext(Dispatchers.IO) {
        try {
            val client = getPostgrestClient() ?: return@withContext null
            val response = client.from("conversa_estado")
                .select()
                .eq("lead_telefone", leadTelefone)
                .execute()
            
            if (response.isSuccessful) {
                val data = response.body?.string()
                if (!data.isNullOrEmpty() && data != "[]") {
                    val jsonArray = org.json.JSONArray(data)
                    if (jsonArray.length() > 0) {
                        val jsonObject = jsonArray.getJSONObject(0)
                        return@withContext ConversaEstado(
                            id = jsonObject.getInt("id"),
                            lead_telefone = jsonObject.getString("lead_telefone"),
                            numero_atendente = jsonObject.getString("numero_atendente"),
                            etapa_atual = jsonObject.getInt("etapa_atual"),
                            ultima_mensagem = jsonObject.getString("ultima_mensagem")
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // Atualizar estado da conversa
    suspend fun updateConversaEstado(leadTelefone: String, numeroAtendente: String, etapaAtual: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = getPostgrestClient() ?: return@withContext false
            
            // Verificar se já existe
            val estadoAtual = getConversaEstado(leadTelefone)
            
            if (estadoAtual != null) {
                // Atualizar registro existente
                val updateData = JSONObject().apply {
                    put("etapa_atual", etapaAtual)
                    put("ultima_mensagem", System.currentTimeMillis().toString())
                }
                
                val response = client.from("conversa_estado")
                    .update(updateData.toString())
                    .eq("lead_telefone", leadTelefone)
                    .execute()
                
                return@withContext response.isSuccessful
            } else {
                // Criar novo registro
                val insertData = JSONObject().apply {
                    put("lead_telefone", leadTelefone)
                    put("numero_atendente", numeroAtendente)
                    put("etapa_atual", etapaAtual)
                    put("ultima_mensagem", System.currentTimeMillis().toString())
                }
                
                val response = client.from("conversa_estado")
                    .insert(insertData.toString())
                    .execute()
                
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // Atualizar status de número banido
    suspend fun updateNumeroBanido(numeroAtendente: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = getPostgrestClient() ?: return@withContext false
            
            val updateData = JSONObject().apply {
                put("banido", true)
                put("status", "vermelho")
            }
            
            val response = client.from("numeros")
                .update(updateData.toString())
                .eq("numero", numeroAtendente)
                .execute()
            
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // Enviar webhook
    suspend fun sendBanWebhook(numeroAtendente: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(Constants.BAN_WEBHOOK_URL)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonData = JSONObject().apply {
                put("numero", numeroAtendente)
                put("motivo", "banido_whatsapp")
            }
            
            val outputStream = connection.outputStream
            outputStream.write(jsonData.toString().toByteArray())
            outputStream.close()
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            return@withContext responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // Baixar mídia do Supabase Storage
    suspend fun downloadMedia(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val storageClient = getStorageClient() ?: return@withContext null
            
            // Extrair o path da URL completa
            val path = url.substringAfterLast("/storage/v1/object/public/")
            
            val response = storageClient.from(path).download()
            
            if (response.isSuccessful) {
                return@withContext response.body?.bytes()
            }
            
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}