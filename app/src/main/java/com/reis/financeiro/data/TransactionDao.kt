package com.reis.financeiro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) FROM transactions")
    fun observeIncomeCents(): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0) FROM transactions")
    fun observeExpenseCents(): Flow<Long>
}
