package com.reis.financeiro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    suspend fun getAll(): List<Transaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE createdAt BETWEEN :start AND :end ORDER BY createdAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) FROM transactions")
    fun observeIncomeCents(): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0) FROM transactions")
    fun observeExpenseCents(): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0) FROM transactions")
    fun observeNetCents(): Flow<Long>
}
