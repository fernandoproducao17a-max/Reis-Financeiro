package com.reis.financeiro.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class ReisConverters {
    @TypeConverter fun fromType(type: TransactionType): String = type.name
    @TypeConverter fun toType(value: String): TransactionType = TransactionType.valueOf(value)
}

@Database(entities = [Transaction::class, FinanceSettings::class], version = 3, exportSchema = false)
@TypeConverters(ReisConverters::class)
abstract class ReisDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun settingsDao(): FinanceSettingsDao
}
