package com.reis.financeiro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
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
fun ReisDrawerHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(com.reis.financeiro.R.drawable.ic_reis_logo),
                contentDescription = "Logo REIS",
                modifier = Modifier.size(62.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("REIS", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("FINANCEIRO", color = ReisGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        Text("Controle suas entradas e saídas com rapidez, privacidade e voz.", color = Color(0xFF9B9BA3), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ReisDrawerItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        icon = { Text(icon, color = if (selected) ReisGold else Color.LightGray) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color(0xFF2A2515),
            selectedTextColor = Color.White,
            unselectedTextColor = Color(0xFFE5E5E8),
            unselectedIconColor = Color.LightGray
        )
    )
}

@Composable
@Composable
fun DashboardScreen(
    transactions: List<Transaction>, initial: Long, income: Long, expense: Long,
    onInitial: () -> Unit, onNew: () -> Unit, onEdit: (Transaction) -> Unit, onDelete: (Transaction) -> Unit
) {
    val balance = initial + income - expense
    var valuesVisible by remember { mutableStateOf(false) }
    val byCategory = transactions.filter { it.type == TransactionType.EXPENSE }.groupBy { it.category }
        .mapValues { it.value.sumOf { tx -> tx.amountCents } }.toList().sortedByDescending { it.second }.take(5)
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151519)), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(com.reis.financeiro.R.drawable.ic_reis_logo), "REIS", Modifier.size(68.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("REIS FINANCEIRO", color = ReisGold, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("Sua vida financeira na sua voz", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { valuesVisible = !valuesVisible }) { Text(if (valuesVisible) "Ocultar" else "Mostrar", color = ReisGold) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Visão geral", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Resumo financeiro", color = Color.Gray) }
            Text("● Protegido", color = Color(0xFF18D66B), style = MaterialTheme.typography.labelMedium)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1913)), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Saldo disponível", color = Color.LightGray)
                Text(hiddenValue(balance, valuesVisible), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                Text("Inicial: " + hiddenValue(initial, valuesVisible), color = Color.Gray)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceMetricCard("Entradas", income, Color(0xFF18D66B), valuesVisible, Modifier.weight(1f))
            FinanceMetricCard("Saídas", expense, Color(0xFFFF6B6B), valuesVisible, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNew, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("＋ Novo") }
            OutlinedButton(onClick = onInitial, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Saldo inicial") }
        }
        if (byCategory.isNotEmpty()) {
            Text("Onde seu dinheiro está saindo", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val maxValue = byCategory.maxOf { it.second }.coerceAtLeast(1L)
            byCategory.forEach { (category, value) ->
                Column(Modifier.fillMaxWidth().background(Color(0xFF141417), RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(hiddenValue(value, valuesVisible), color = Color(0xFFFF6B6B))
                    }
                    if (valuesVisible) LinearProgressIndicator(progress = { value.toFloat() / maxValue.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = ReisGold, trackColor = Color(0xFF303035))
                }
            }
        }
        Text("Últimos lançamentos", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (transactions.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151519)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎙️", style = MaterialTheme.typography.displaySmall)
                    Text("Seu primeiro lançamento começa aqui", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Use o botão de voz ou o menu para registrar uma entrada ou saída.", color = Color.Gray)
                }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(transactions.take(6), key = { it.id }) { TransactionRow(it, onEdit, onDelete, valuesVisible) }
        }
    }
}

@Composable
fun HistoryScreen(transactions: List<Transaction>, onEdit: (Transaction) -> Unit, onDelete: (Transaction) -> Unit) {
    var valuesVisible by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }
    var period by remember { mutableStateOf("Todos") }
    val periodStart = when (period) {
        "Hoje" -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }.timeInMillis
        "7 dias" -> System.currentTimeMillis() - 7L*24*60*60*1000
        "Este mês" -> Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }.timeInMillis
        else -> 0L
    }
    val filtered = transactions.filter {
        val query = search.trim().lowercase()
        val typeOk = filter == "Todos" || (filter == "Entradas" && it.type == TransactionType.INCOME) || (filter == "Saídas" && it.type == TransactionType.EXPENSE) || it.category == filter
        val textOk = query.isBlank() || it.category.lowercase().contains(query) || it.description.lowercase().contains(query)
        typeOk && textOk && it.createdAt >= periodStart
    }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Lançamentos", color = ReisGold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(filtered.size.toString() + " registros", color = Color.Gray) }
            TextButton(onClick = { valuesVisible = !valuesVisible }) { Text(if (valuesVisible) "Ocultar" else "Mostrar", color = ReisGold) }
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), label = { Text("Pesquisar por categoria ou descrição") }, singleLine = true)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
            listOf("Todos","Entradas","Saídas","Combustível","Mercado","Transporte","Alimentação").forEach { label ->
                FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 8.dp)) {
            listOf("Todos","Hoje","7 dias","Este mês").forEach { label ->
                FilterChip(selected = period == label, onClick = { period = label }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
            }
        }
        if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum lançamento encontrado.", color = Color.Gray) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(filtered, key = { it.id }) { TransactionRow(it, onEdit, onDelete, valuesVisible) } }
    }
}

@Composable
fun ReportsScreen(transactions: List<Transaction>, income: Long, expense: Long, onExportBackup: () -> Unit, onImportBackup: () -> Unit) {
    var valuesVisible by remember { mutableStateOf(false) }
    val byCategory = transactions.filter { it.type == TransactionType.EXPENSE }.groupBy { it.category }
        .mapValues { it.value.sumOf { tx -> tx.amountCents } }.toList().sortedByDescending { it.second }
    val maxValue = byCategory.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Relatórios", color = ReisGold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Visão do seu dinheiro", color = Color.Gray) }
            TextButton(onClick = { valuesVisible = !valuesVisible }) { Text(if (valuesVisible) "Ocultar" else "Mostrar", color = ReisGold) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171A)), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Resumo geral", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Entradas  " + hiddenValue(income, valuesVisible), color = Color(0xFF18D66B))
                Text("Saídas    " + hiddenValue(expense, valuesVisible), color = Color(0xFFFF6B6B))
                Text("Saldo     " + hiddenValue(income - expense, valuesVisible), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Text("Gastos por categoria", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (byCategory.isEmpty()) Text("Ainda não há despesas para analisar.", color = Color.Gray)
        else byCategory.forEach { (category, value) ->
            Column(Modifier.fillMaxWidth().background(Color(0xFF141417), RoundedCornerShape(14.dp)).padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(category, color = Color.White); Text(hiddenValue(value, valuesVisible), color = Color(0xFFFF6B6B)) }
                if (valuesVisible) LinearProgressIndicator(progress = { value.toFloat()/maxValue.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top=8.dp), color = ReisGold, trackColor = Color(0xFF303035))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportBackup, modifier = Modifier.weight(1f)) { Text("Exportar backup") }
            OutlinedButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) { Text("Restaurar") }
        }
    }
}

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
            Text("Sugestões", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DefaultCategories.all.take(6).forEach { item -> FilterChip(selected = category == item.name, onClick = { category = item.name }, label = { Text(item.emoji + " " + item.name) }) }
            }
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


@Composable
fun CategoryListDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorias") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultCategories.all.forEach { category ->
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF17171A), RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category.emoji, modifier = Modifier.width(34.dp))
                        Text(category.name, color = Color.White)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
