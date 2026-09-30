package com.reis.financeiro.voice

import com.reis.financeiro.data.Transaction
import com.reis.financeiro.data.TransactionType

data class ParsedVoiceCommand(
    val transaction: Transaction,
    val confirmationText: String
)

object VoiceCommandParser {
    private val amountRegex = Regex("""(\d+(?:[.,]\d{1,2})?)\s*(?:reais|real|r\$)?""", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = text.lowercase().trim()
        val match = amountRegex.find(normalized) ?: return null
        val raw = match.groupValues[1].replace(".", "").replace(",", ".")
        val amount = raw.toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val income = listOf("recebi", "ganhei", "entrou", "salário", "salario", "renda")
            .any { normalized.contains(it) }
        val expense = listOf("gastei", "paguei", "comprei", "saí", "sai", "despesa")
            .any { normalized.contains(it) }

        val type = when {
            income -> TransactionType.INCOME
            expense -> TransactionType.EXPENSE
            else -> return null
        }

        val category = when {
            listOf("combustível", "combustivel", "gasolina", "etanol", "diesel").any { normalized.contains(it) } -> "Combustível"
            listOf("salário", "salario").any { normalized.contains(it) } -> "Salário"
            normalized.contains("mercado") || normalized.contains("supermercado") -> "Mercado"
            normalized.contains("aluguel") -> "Moradia"
            else -> "Outros"
        }

        val transaction = Transaction(
            type = type,
            amountCents = (amount * 100).toLong(),
            category = category,
            description = text.trim()
        )

        val verb = if (type == TransactionType.INCOME) "Entrada" else "Saída"
        return ParsedVoiceCommand(transaction, "$verb de R$ %.2f em %s".format(amount, category))
    }
}
