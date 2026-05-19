package com.pintolouco.whatsappbot.utils

object Constants {
    const val PREFS_NAME = "WhatsAppBotPrefs"
    const val SUPABASE_URL_KEY = "supabase_url"
    const val SUPABASE_KEY_KEY = "supabase_key"
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val BAN_WEBHOOK_URL = "https://meuservidor.com/webhook/ban"
    
    // Intent actions
    const val ACTION_START_SERVICE = "com.pintolouco.whatsappbot.START_SERVICE"
    const val ACTION_STOP_SERVICE = "com.pintolouco.whatsappbot.STOP_SERVICE"
    
    // Firebase
    const val FCM_CHANNEL_ID = "whatsapp_bot_channel"
    const val FCM_CHANNEL_NAME = "WhatsApp Bot Notifications"
    
    // Accessibility
    const val ACCESSIBILITY_SERVICE_ID = "com.pintolouco.whatsappbot/.service.WhatsAppService"
}