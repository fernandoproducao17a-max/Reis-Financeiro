package com.reis.financeiro.data

fun transactionsToJson(transactions: List<Transaction>, initialBalanceCents: Long): String {
    fun esc(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
    val items = transactions.joinToString(",") { t ->
        "{\"id\":" + t.id + ",\"type\":\"" + t.type.name + "\",\"amountCents\":" + t.amountCents + ",\"category\":\"" + esc(t.category) + "\",\"description\":\"" + esc(t.description) + "\",\"createdAt\":" + t.createdAt + "}"
    }
    return "{\"version\":1,\"initialBalanceCents\":" + initialBalanceCents + ",\"transactions\":[" + items + "]}";
}