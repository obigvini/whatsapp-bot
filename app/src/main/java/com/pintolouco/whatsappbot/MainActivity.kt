package com.pintolouco.whatsappbot

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pintolouco.whatsappbot.service.WhatsAppService
import com.pintolouco.whatsappbot.utils.Constants
import com.pintolouco.whatsappbot.utils.SupabaseClient
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var tvServiceStatus: TextView
    private lateinit var btnEnableAccessibility: Button
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button
    private lateinit var etSupabaseUrl: EditText
    private lateinit var etSupabaseKey: EditText
    private lateinit var btnSaveConfig: Button
    
    private lateinit var supabaseClient: SupabaseClient
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
        setupSupabaseClient()
        updateServiceStatus()
    }
    
    private fun initViews() {
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
        etSupabaseUrl = findViewById(R.id.etSupabaseUrl)
        etSupabaseKey = findViewById(R.id.etSupabaseKey)
        btnSaveConfig = findViewById(R.id.btnSaveConfig)
    }
    
    private fun setupClickListeners() {
        btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
        
        btnStartService.setOnClickListener {
            if (!supabaseClient.isConfigured()) {
                Toast.makeText(this, "Configure o Supabase primeiro!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val intent = Intent(this, WhatsAppService::class.java)
            intent.action = Constants.ACTION_START_SERVICE
            startForegroundService(intent)
            updateServiceStatus()
        }
        
        btnStopService.setOnClickListener {
            val intent = Intent(this, WhatsAppService::class.java)
            intent.action = Constants.ACTION_STOP_SERVICE
            stopService(intent)
            updateServiceStatus()
        }
        
        btnSaveConfig.setOnClickListener {
            saveSupabaseConfig()
        }
    }
    
    private fun setupSupabaseClient() {
        supabaseClient = SupabaseClient(this)
        
        // Carregar configurações salvas
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        etSupabaseUrl.setText(prefs.getString(Constants.SUPABASE_URL_KEY, ""))
        etSupabaseKey.setText(prefs.getString(Constants.SUPABASE_KEY_KEY, ""))
    }
    
    private fun saveSupabaseConfig() {
        val url = etSupabaseUrl.text.toString().trim()
        val key = etSupabaseKey.text.toString().trim()
        
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(key)) {
            Toast.makeText(this, "Preencha todos os campos!", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            supabaseClient.updateConfig(url, key)
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Configurações salvas!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateServiceStatus() {
        val isServiceRunning = WhatsAppService.isServiceRunning
        val statusText = if (isServiceRunning) "Em execução" else "Parado"
        tvServiceStatus.text = getString(R.string.service_status, statusText)
        
        btnStartService.isEnabled = !isServiceRunning && supabaseClient.isConfigured()
        btnStopService.isEnabled = isServiceRunning
    }
    
    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }
}