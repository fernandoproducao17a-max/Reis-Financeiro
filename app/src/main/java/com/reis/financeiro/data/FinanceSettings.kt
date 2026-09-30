package com.reis.financeiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "finance_settings")
data class FinanceSettings(
    @PrimaryKey val id: Int = 1,
    val initialBalanceCents: Long = 0L
)
