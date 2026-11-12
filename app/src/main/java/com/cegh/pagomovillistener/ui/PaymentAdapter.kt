package com.cegh.pagomovillistener.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cegh.pagomovillistener.R
import com.cegh.pagomovillistener.databinding.ItemPaymentBinding
import com.cegh.pagomovillistener.domain.model.OutboxStatus
import com.cegh.pagomovillistener.domain.model.PaymentNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentAdapter(
    private val onRetryClick: (PaymentNotification) -> Unit
) : ListAdapter<PaymentNotification, PaymentAdapter.PaymentViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val binding = ItemPaymentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PaymentViewHolder(binding, onRetryClick)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PaymentViewHolder(
        private val binding: ItemPaymentBinding,
        private val onRetryClick: (PaymentNotification) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        fun bind(item: PaymentNotification) {
            val context = binding.root.context

            binding.textBank.text = item.bank.displayName
            binding.textAmount.text = "Bs. ${item.rawAmount}"
            binding.textPhone.text = "Tel: ${item.phoneNumber}"
            binding.textReference.text = "Ref: ${item.reference ?: "N/A"}"
            binding.textDate.text = "Fecha: ${item.date} ${item.time ?: ""}".trim()
            binding.textReceivedAt.text = "Capturado: ${timeFormat.format(Date(item.receivedAt))}"

            when (item.syncStatus) {
                OutboxStatus.SYNCED -> {
                    binding.chipStatus.text = "SYNCED"
                    binding.chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_dark)
                    binding.textError.visibility = android.view.View.GONE
                    binding.btnRetry.visibility = android.view.View.GONE
                }
                OutboxStatus.PENDING -> {
                    binding.chipStatus.text = "PENDING"
                    binding.chipStatus.setChipBackgroundColorResource(android.R.color.holo_orange_dark)
                    binding.textError.visibility = android.view.View.GONE
                    binding.btnRetry.visibility = android.view.View.GONE
                }
                OutboxStatus.FAILED -> {
                    binding.chipStatus.text = "FAILED (${item.syncAttempts})"
                    binding.chipStatus.setChipBackgroundColorResource(android.R.color.holo_red_dark)
                    binding.textError.visibility = android.view.View.VISIBLE
                    binding.textError.text = item.lastSyncError ?: "Error al sincronizar"
                    binding.btnRetry.visibility = android.view.View.VISIBLE
                    binding.btnRetry.setOnClickListener { onRetryClick(item) }
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PaymentNotification>() {
        override fun areItemsTheSame(oldItem: PaymentNotification, newItem: PaymentNotification): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PaymentNotification, newItem: PaymentNotification): Boolean {
            return oldItem == newItem
        }
    }
}
