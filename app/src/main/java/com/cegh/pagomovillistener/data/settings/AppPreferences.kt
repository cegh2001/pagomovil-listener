package com.cegh.pagomovillistener.data.settings

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pagomovil_prefs", Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "https://api.tuempresa.com/webhooks/pagomovil").orEmpty()
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    var webhookSecret: String
        get() = prefs.getString(KEY_WEBHOOK_SECRET, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_WEBHOOK_SECRET, value.trim()).apply()

    var isServiceActive: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ACTIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ACTIVE, value).apply()

    companion object {
        private const val KEY_WEBHOOK_URL = "key_webhook_url"
        private const val KEY_WEBHOOK_SECRET = "key_webhook_secret"
        private const val KEY_SERVICE_ACTIVE = "key_service_active"
    }
}
