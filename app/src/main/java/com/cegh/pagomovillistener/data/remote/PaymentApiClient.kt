package com.cegh.pagomovillistener.data.remote

import com.cegh.pagomovillistener.domain.model.PaymentNotification
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class PaymentApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun postPayment(
        targetUrl: String,
        secretToken: String?,
        payment: PaymentNotification
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (targetUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Target Webhook URL is empty"))
        }

        val payload = PaymentPayload.fromDomain(payment)
        val jsonString = gson.toJson(payload)
        val body = jsonString.toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .post(body)
            .addHeader("User-Agent", "PagoMovil-Listener/1.0")

        if (!secretToken.isNullOrBlank()) {
            requestBuilder.addHeader("X-Webhook-Secret", secretToken)
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            response.use {
                // Treat both 2xx and 409 Conflict (already recorded by backend) as successful delivery
                // to prevent WorkManager from entering an infinite retry loop for duplicates.
                if (it.isSuccessful || it.code == 409) {
                    Result.success(Unit)
                } else {
                    val errorBody = it.body?.string().orEmpty()
                    Result.failure(IOException("Server error ${it.code}: $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
