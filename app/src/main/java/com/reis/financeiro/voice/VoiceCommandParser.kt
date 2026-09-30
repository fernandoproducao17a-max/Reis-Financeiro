package com.reis.financeiro.voice

import com.reis.financeiro.data.Transaction
import com.reis.financeiro.data.TransactionType

data class ParsedVoiceCommand(
    val transaction: Transaction,
    val confirmationText: String
)

object VoiceCommandParser {
    private val amountRegex = Regex(
        """(\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:,\d{1,2})?)\s*(?:reais|real|r\$)?""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = text.lowercase().trim()
        val match = amountRegex.find(normalized) ?: return null
        val raw = match.groupValues[1].replace(".", "").replace(",", ".")
        val amount = raw.toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val income = listOf("recebi", "ganhei", "entrou", "salário", "salario", "renda", "caiu")
            .any { normalized.contains(it) }
        val expense = listOf("gastei", "paguei", "comprei", "saí", "sai", "despesa", "gasto")
            .any { normalized.contains(it) }

        val type = when {
            income && !expense -> TransactionType.INCOME
            expense && !income -> TransactionType.EXPENSE
            else -> return null
        }

        val category = when {
            listOf("combustível", "combustivel", "gasolina", "etanol", "diesel", "posto").any { normalized.contains(it) } -> "Combustível"
            listOf("salário", "salario").any { normalized.contains(it) } -> "Salário"
            listOf("freelance", "freela").any { normalized.contains(it) } -> "Freelance"
            normalized.contains("mercado") || normalized.contains("supermercado") -> "Mercado"
            normalized.contains("aluguel") -> "Moradia"
            normalized.contains("energia") || normalized.contains("luz") -> "Energia"
            normalized.contains("água") || normalized.contains("agua") -> "Água"
            normalized.contains("internet") -> "Internet"
            normalized.contains("farmácia") || normalized.contains("farmacia") || normalized.contains("remédio") || normalized.contains("remedio") -> "Saúde"
            normalized.contains("escola") || normalized.contains("curso") -> "Educação"
            normalized.contains("restaurante") || normalized.contains("comida") || normalized.contains("lanche") -> "Alimentação"
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
