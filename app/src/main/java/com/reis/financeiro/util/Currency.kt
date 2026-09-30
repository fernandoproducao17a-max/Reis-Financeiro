package com.reis.financeiro.util

import java.text.NumberFormat
import java.util.Locale

fun Long.toBrl(): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(this / 100.0)

fun String.toCentsOrNull(): Long? =
    trim().replace("R$", "").replace(".", "").replace(",", ".")
        .toDoubleOrNull()?.let { (it * 100).toLong() }
