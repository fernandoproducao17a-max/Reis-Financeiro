package com.reis.financeiro.voice

import com.reis.financeiro.data.Transaction
import com.reis.financeiro.data.TransactionType

data class ParsedVoiceCommand(
    val transaction: Transaction,
    val confirmationText: String
)

object VoiceCommandParser {
    private val spokenNumbers = mapOf(
        "zero" to 0.0, "um" to 1.0, "uma" to 1.0, "dois" to 2.0, "duas" to 2.0,
        "três" to 3.0, "tres" to 3.0, "quatro" to 4.0, "cinco" to 5.0,
        "seis" to 6.0, "sete" to 7.0, "oito" to 8.0, "nove" to 9.0, "dez" to 10.0,
        "onze" to 11.0, "doze" to 12.0, "treze" to 13.0, "quatorze" to 14.0, "quinze" to 15.0,
        "dezesseis" to 16.0, "dezessete" to 17.0, "dezoito" to 18.0, "dezenove" to 19.0,
        "vinte" to 20.0, "trinta" to 30.0, "quarenta" to 40.0, "cinquenta" to 50.0,
        "sessenta" to 60.0, "setenta" to 70.0, "oitenta" to 80.0, "noventa" to 90.0,
        "cem" to 100.0, "cento" to 100.0
    )

    private fun spokenAmount(text: String): Double? {
        val pattern = spokenNumbers.keys.joinToString("|") { Regex.escape(it) }
        val simple = Regex("\\b(" + pattern + ")\\s+mil\\b").find(text)
        if (simple != null) return (spokenNumbers[simple.groupValues[1]] ?: return null) * 1000.0
        if (text.contains("mil")) {
            val before = text.substringBefore("mil").trim().removeSuffix("e").trim()
            val base = before.split(" ").lastOrNull()?.let { spokenNumbers[it] }
            if (base != null) return base * 1000.0
            if (text.trim().startsWith("mil")) return 1000.0
        }
        return null
    }

    private val amountRegex = Regex(
        """(\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:,\d{1,2})?)\s*(?:reais|real|r\$)?""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = text.lowercase().trim()
        val thousandMatch = Regex("""(\d+(?:,\d+)?)\s*mil""").find(normalized)
        val match = amountRegex.find(normalized)
        val amount = if (spokenAmount(normalized) != null) {
            spokenAmount(normalized)!!
        } else if (thousandMatch != null) {
            (thousandMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null) * 1000.0
        } else {
            val raw = match?.groupValues?.get(1)?.replace(".", "")?.replace(",", ".") ?: return null
            raw.toDoubleOrNull() ?: return null
        }
        if (amount <= 0) return null

        val income = listOf("recebi", "ganhei", "entrou", "salário", "salario", "renda", "caiu")
            .any { normalized.contains(it) }
        val expense = listOf("gastei", "paguei", "comprei", "saí", "sai", "despesa", "gasto", "transferi", "enviei", "pix")
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
            normalized.contains("uber") || normalized.contains("ônibus") || normalized.contains("onibus") || normalized.contains("transporte") -> "Transporte"
            normalized.contains("cinema") || normalized.contains("jogo") || normalized.contains("lazer") -> "Lazer"
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
