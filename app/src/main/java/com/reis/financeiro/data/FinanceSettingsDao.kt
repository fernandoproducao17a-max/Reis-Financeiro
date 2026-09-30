package com.reis.financeiro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceSettingsDao {
    @Query("SELECT * FROM finance_settings WHERE id = 1")
    fun observe(): Flow<FinanceSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: FinanceSettings)
}
