package com.reis.financeiro.data

data class BackupData(
    val version: Int,
    val initialBalanceCents: Long,
    val transactions: List<Transaction>
)

fun transactionsToJson(transactions: List<Transaction>, initialBalanceCents: Long): String {
    fun esc(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
    val items = transactions.joinToString(",") { t ->
        "{\"id\":" + t.id + ",\"type\":\"" + t.type.name + "\",\"amountCents\":" + t.amountCents +
            ",\"category\":\"" + esc(t.category) + "\",\"description\":\"" + esc(t.description) +
            "\",\"createdAt\":" + t.createdAt + "}"
    }
    return "{\"version\":1,\"initialBalanceCents\":" + initialBalanceCents + ",\"transactions\":[" + items + "]}"
}

fun parseBackup(json: String): BackupData {
    val root = org.json.JSONObject(json)
    val version = root.optInt("version", 1)
    val initial = root.optLong("initialBalanceCents", 0L)
    val array = root.optJSONArray("transactions") ?: org.json.JSONArray()
    val transactions = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            add(Transaction(
                id = 0L,
                type = TransactionType.valueOf(item.getString("type")),
                amountCents = item.getLong("amountCents"),
                category = item.optString("category", "Outros").ifBlank { "Outros" },
                description = item.optString("description", ""),
                createdAt = item.optLong("createdAt", System.currentTimeMillis())
            ))
        }
    }
    return BackupData(version, initial, transactions)
}
