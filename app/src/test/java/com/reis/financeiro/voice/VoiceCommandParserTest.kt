package com.reis.financeiro.voice

import com.reis.financeiro.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceCommandParserTest {
    @Test
    fun parsesFuelExpense() {
        val result = VoiceCommandParser.parse("Gastei 100 reais de combustível")
        assertNotNull(result)
        assertEquals(TransactionType.EXPENSE, result!!.transaction.type)
        assertEquals(10000L, result.transaction.amountCents)
        assertEquals("Combustível", result.transaction.category)
    }

    @Test
    fun parsesSalaryIncome() {
        val result = VoiceCommandParser.parse("Recebi 3000 reais de salário")
        assertNotNull(result)
        assertEquals(TransactionType.INCOME, result!!.transaction.type)
        assertEquals(300000L, result.transaction.amountCents)
        assertEquals("Salário", result.transaction.category)
    }

    @Test
    fun parsesSpokenThousands() {
        val result = VoiceCommandParser.parse("Recebi 3 mil reais de salário")
        assertNotNull(result)
        assertEquals(300000L, result!!.transaction.amountCents)
    }

    @Test
    fun parsesBrazilianThousands() {
        val result = VoiceCommandParser.parse("Paguei 1.250,50 de energia")
        assertNotNull(result)
        assertEquals(125050L, result!!.transaction.amountCents)
        assertEquals("Energia", result.transaction.category)
    }
}
