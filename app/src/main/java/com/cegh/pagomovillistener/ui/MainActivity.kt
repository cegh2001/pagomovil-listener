package com.cegh.pagomovillistener.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cegh.pagomovillistener.R
import com.cegh.pagomovillistener.data.repository.PaymentRepository
import com.cegh.pagomovillistener.data.settings.AppPreferences
import com.cegh.pagomovillistener.databinding.ActivityMainBinding
import com.cegh.pagomovillistener.domain.parser.CompositePaymentParser
import com.cegh.pagomovillistener.service.PaymentNotificationListenerService
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: PaymentRepository
    private lateinit var preferences: AppPreferences
    private lateinit var adapter: PaymentAdapter
    private val parser = CompositePaymentParser()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PaymentRepository(this)
        preferences = AppPreferences(this)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observePayments()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupRecyclerView() {
        adapter = PaymentAdapter { item ->
            lifecycleScope.launch {
                repository.scheduleOutboxWorker()
                Snackbar.make(binding.root, "Reintentando sincronización...", Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnGrantPermission.setOnClickListener {
            openNotificationListenerSettings()
        }

        binding.btnBatteryOptimization.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        binding.btnSimulateBdv.setOnClickListener {
            simulateNotification(
                title = "PagomovilBDV recibido",
                body = "Recibiste un PagomovilBDV por Bs.1,00 del 0412-9897838 Ref: 334023334023 en fecha 11-11-26 hora: 22:29."
            )
        }

        binding.btnSimulateBfc.setOnClickListener {
            simulateNotification(
                title = "Pago recibido",
                body = "Ha recibido un Pago por Bs. 35,00 del Celular 0414***9152 a traves del servicio TUBFC 11/11/2026"
            )
        }
    }

    private fun observePayments() {
        lifecycleScope.launch {
            repository.allPaymentsFlow.collectLatest { list ->
                adapter.submitList(list)
                binding.textEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updatePermissionStatus() {
        val isGranted = isNotificationServiceEnabled()
        if (isGranted) {
            binding.cardPermissionWarning.visibility = View.GONE
        } else {
            binding.cardPermissionWarning.visibility = View.VISIBLE
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
        binding.cardBatteryWarning.visibility = if (isIgnoringBattery) View.GONE else View.VISIBLE
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, PaymentNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir la configuración de notificaciones", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallbackIntent)
            }
        }
    }

    private fun simulateNotification(title: String, body: String) {
        val payment = parser.parse(title, body)
        if (payment != null) {
            lifecycleScope.launch {
                repository.saveAndProcess(payment)
                Snackbar.make(binding.root, "Notificación simulada guardada en Outbox", Snackbar.LENGTH_SHORT).show()
            }
        } else {
            Snackbar.make(binding.root, "El parser no pudo reconocer el formato", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            R.id.action_sync_now -> {
                repository.scheduleOutboxWorker()
                Snackbar.make(binding.root, "Sincronización forzada iniciada", Snackbar.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val editUrl = dialogView.findViewById<EditText>(R.id.editWebhookUrl)
        val editSecret = dialogView.findViewById<EditText>(R.id.editWebhookSecret)

        editUrl.setText(preferences.webhookUrl)
        editSecret.setText(preferences.webhookSecret)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                preferences.webhookUrl = editUrl.text.toString()
                preferences.webhookSecret = editSecret.text.toString()
                Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
