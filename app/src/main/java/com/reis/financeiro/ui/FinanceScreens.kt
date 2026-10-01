package com.reis.financeiro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reis.financeiro.data.*
import com.reis.financeiro.util.toBrl
import com.reis.financeiro.util.toCentsOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

private val ReisGold = Color(0xFFFFC72C)
private fun hiddenValue(value: Long, visible: Boolean): String = if (visible) value.toBrl() else "••••••"

@Composable
fun DashboardScreen(
    transactions: List<Transaction>,
    initial: Long,
    income: Long,
    expense: Long,
    onInitial: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit
) {
    val balance = initial + income - expense
    var valuesVisible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("REIS FINANCEIRO", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFFFC72C), fontWeight = FontWeight.Bold)
        Text("Sua vida financeira na sua voz", color = Color.LightGray)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171A)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Saldo atual", color = Color.LightGray)
                    TextButton(onClick = onInitial) { Text("Valor inicial") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(hiddenValue(balance, valuesVisible), style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { valuesVisible = !valuesVisible }) { Text(if (valuesVisible) "Ocultar" else "Mostrar", color = ReisGold) }
                }
                Text("Inicial: " + hiddenValue(initial, valuesVisible), color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("↑ " + hiddenValue(income, valuesVisible), color = Color(0xFF18D66B))
                    Text("↓ " + hiddenValue(expense, valuesVisible), color = Color(0xFFFF6B6B))
                }
            }
        }
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("＋ Novo lançamento") }
        Text("Últimos lançamentos", color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (transactions.isEmpty()) Text("Nenhum lançamento ainda. Use o microfone ou adicione manualmente.", color = Color.Gray)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(transactions.take(8), key = { it.id }) { TransactionRow(it, onEdit, onDelete, valuesVisible) }
        }
    }
}

@Composable
fun HistoryScreen(transactions: List<Transaction>, onEdit: (Transaction) -> Unit, onDelete: (Transaction) -> Unit) {
    var valuesVisible by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("Todos") }
    var period by remember { mutableStateOf("Todos") }
    val filters = listOf("Todos", "Entradas", "Saídas", "Combustível", "Mercado", "Moradia")
    val periodStart = when (period) {
        "Hoje" -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        "7 dias" -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        "Este mês" -> Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        else -> 0L
    }
    val filtered = transactions.filter {
        val typeMatches = filter == "Todos" || (filter == "Entradas" && it.type == TransactionType.INCOME) ||
            (filter == "Saídas" && it.type == TransactionType.EXPENSE) || it.category == filter
        typeMatches && it.createdAt >= periodStart
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Lançamentos", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFFFC72C), fontWeight = FontWeight.Bold)
            TextButton(onClick = { valuesVisible = !valuesVisible }) { Text(if (valuesVisible) "Ocultar" else "Mostrar", color = Color(0xFFFFC72C)) }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Todos", "Hoje", "7 dias", "Este mês").forEach { label ->
                FilterChip(selected = period == label, onClick = { period = label }, label = { Text(label) })
            }
        }
        if (filtered.isEmpty()) Text("Nenhum lançamento encontrado.", color = Color.Gray)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(filtered, key = { it.id }) { TransactionRow(it, onEdit, onDelete, valuesVisible) } }
    }
}

@Composable
fun ReportsScreen(transactions: List<Transaction>, income: Long, expense: Long, onExportBackup: () -> Unit, onImportBackup: () -> Unit) {
    var valuesVisible by remember { mutableStateOf(false) }
    val byCategory = transactions.filter { it.type == TransactionType.EXPENSE }.groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amountCents } }.toList().sortedByDescending { it.second }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Relatórios", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFFFC72C), fontWeight = FontWeight.Bold)
            TextButton(onClick = { valuesVisible = !valuesVisible }) { Text(if (valuesVisible) "Ocultar" else "Mostrar", color = Color(0xFFFFC72C)) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171A)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Resumo", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Entradas: " + hiddenValue(income, valuesVisible), color = Color(0xFF18D66B))
                Text("Saídas: " + hiddenValue(expense, valuesVisible), color = Color(0xFFFF6B6B))
                Text("Movimentado: " + hiddenValue(income + expense, valuesVisible), color = Color.LightGray)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportBackup, modifier = Modifier.weight(1f)) { Text("Exportar") }
            OutlinedButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) { Text("Restaurar") }
        }
        Text("Gastos por categoria", color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (byCategory.isEmpty()) Text("Ainda não há despesas.", color = Color.Gray)
        else byCategory.forEach { (category, value) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(category, color = Color.White); Text(hiddenValue(value, valuesVisible), color = Color(0xFFFF6B6B)) } }
    }
}

@Composable
fun TransactionRow(transaction: Transaction, onEdit: (Transaction) -> Unit, onDelete: (Transaction) -> Unit, valuesVisible: Boolean = false) {
    var confirm by remember { mutableStateOf(false) }
    val positive = transaction.type == TransactionType.INCOME
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171A)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(transaction.category, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(transaction.description.ifBlank { "Sem descrição" }, color = Color.Gray, maxLines = 1)
                Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(transaction.createdAt)), color = Color.DarkGray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (positive) "+ " else "- ") + hiddenValue(transaction.amountCents, valuesVisible), color = if (positive) Color(0xFF18D66B) else Color(0xFFFF6B6B))
                Row { TextButton(onClick = { onEdit(transaction) }) { Text("Editar") }; TextButton(onClick = { confirm = true }) { Text("Excluir", color = Color(0xFFFF6B6B)) } }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Excluir lançamento?") }, text = { Text("Essa ação não pode ser desfeita.") }, confirmButton = { TextButton(onClick = { onDelete(transaction); confirm = false }) { Text("Excluir") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}

@Composable
fun TransactionDialog(existing: Transaction?, onDismiss: () -> Unit, onSave: (Transaction) -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var amount by remember { mutableStateOf(existing?.amountCents?.toBrl()?.replace("R$", "")?.trim() ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Outros") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Novo lançamento" else "Editar lançamento") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, label = { Text("Saída") })
                FilterChip(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, label = { Text("Entrada") })
            }
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Valor") }, prefix = { Text("R$ ") }, singleLine = true)
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Categoria") }, singleLine = true)
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descrição") }, singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { amount.toCentsOrNull()?.takeIf { it > 0 }?.let { cents -> onSave(Transaction(existing?.id ?: 0L, type, cents, category.ifBlank { "Outros" }, description, existing?.createdAt ?: System.currentTimeMillis())) } }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun InitialBalanceDialog(currentCents: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var value by remember { mutableStateOf(if (currentCents == 0L) "" else "%.2f".format(currentCents / 100.0)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Valor inicial") }, text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Com quanto você começou?") }, prefix = { Text("R$ ") }, singleLine = true) }, confirmButton = { TextButton(onClick = { value.toCentsOrNull()?.takeIf { it >= 0 }?.let(onSave) }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun PrivacyLockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF08090B)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("REIS", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
        Text("FINANCEIRO", color = ReisGold, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(22.dp))
        Text("Conteúdo protegido", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Use sua biometria ou o bloqueio do aparelho para entrar.", color = Color.Gray)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onUnlock, shape = RoundedCornerShape(16.dp)) { Text("Desbloquear") }
    }
}

@Composable
fun PrivacySettingsDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Boolean) -> Unit
) {
    var checked by remember(enabled) { mutableStateOf(enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacidade") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Proteja o REIS com a biometria ou o bloqueio de tela do aparelho.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bloquear ao abrir")
                    Switch(checked = checked, onCheckedChange = { checked = it })
                }
                Text("Os valores financeiros continuam ocultos por padrão.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(checked); onDismiss() }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
