package com.cegh.pagomovillistener.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.cegh.pagomovillistener.data.repository.PaymentRepository
import com.cegh.pagomovillistener.data.settings.AppPreferences
import com.cegh.pagomovillistener.domain.parser.CompositePaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentNotificationListenerService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val parser = CompositePaymentParser()
    private lateinit var repository: PaymentRepository
    private lateinit var preferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        repository = PaymentRepository(applicationContext)
        preferences = AppPreferences(applicationContext)
        Log.i(TAG, "PaymentNotificationListenerService initialized")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || !preferences.isServiceActive) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE).orEmpty()
        
        // Android notifications can have text or bigText when expanded
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = if (bigText.isNotBlank()) bigText else text

        if (title.isBlank() && body.isBlank()) return

        Log.d(TAG, "Notification received from pkg: ${sbn.packageName} | Title: $title | Body: $body")

        val payment = parser.parse(title, body)
        if (payment != null) {
            Log.i(TAG, "Valid Pago Móvil detected: Bank=${payment.bank}, Amount=${payment.amountBs}, Ref=${payment.reference}")
            serviceScope.launch {
                try {
                    repository.saveAndProcess(payment)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing detected payment", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.i(TAG, "PaymentNotificationListenerService destroyed")
    }

    companion object {
        private const val TAG = "PagoMovilListener"
    }
}
