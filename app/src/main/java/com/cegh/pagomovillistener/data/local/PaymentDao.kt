package com.cegh.pagomovillistener.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: PaymentEntity): Long

    @Update
    suspend fun update(payment: PaymentEntity)

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE reference = :reference LIMIT 1")
    suspend fun findByReference(reference: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE rawBody = :rawBody LIMIT 1")
    suspend fun findByRawBody(rawBody: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED' ORDER BY receivedAt ASC")
    suspend fun getPendingPayments(): List<PaymentEntity>

    @Query("SELECT * FROM payments ORDER BY receivedAt DESC")
    fun getAllPaymentsFlow(): Flow<List<PaymentEntity>>

    @Query("UPDATE payments SET syncStatus = 'SYNCED', lastSyncError = null WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("UPDATE payments SET syncStatus = 'FAILED', syncAttempts = syncAttempts + 1, lastSyncError = :error WHERE id = :id")
    suspend fun markAsFailed(id: Long, error: String)

    @Query("DELETE FROM payments WHERE syncStatus = 'SYNCED' AND receivedAt < :thresholdEpochMillis")
    suspend fun deleteSyncedOlderThan(thresholdEpochMillis: Long): Int
}
