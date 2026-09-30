package com.reis.financeiro

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reis.financeiro.data.FinanceSettings
import com.reis.financeiro.data.FinanceSettingsDao
import com.reis.financeiro.data.ReisDatabase
import com.reis.financeiro.data.Transaction
import com.reis.financeiro.data.TransactionDao
import com.reis.financeiro.data.TransactionType
import com.reis.financeiro.voice.VoiceCommandParser
import kotlinx.coroutines.launch

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS finance_settings (id INTEGER NOT NULL PRIMARY KEY, initialBalanceCents INTEGER NOT NULL)"
        )
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var database: ReisDatabase

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startVoiceInput() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = Room.databaseBuilder(
            applicationContext, ReisDatabase::class.java, "reis-financeiro.db"
        ).addMigrations(MIGRATION_1_2).build()

        setContent {
            ReisFinanceiroApp(
                dao = database.transactionDao(),
                settingsDao = database.settingsDao(),
                onVoiceRequest = {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) startVoiceInput()
                    else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
        }
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua entrada ou saída")
        }
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { text ->
                        VoiceCommandParser.parse(text)?.let { parsed ->
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                database.transactionDao().insert(parsed.transaction)
                            }
                        }
                    }
                recognizer.destroy()
            }
            override fun onError(error: Int) { recognizer.destroy() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }
}

@Composable
private fun ReisFinanceiroApp(
    dao: TransactionDao,
    settingsDao: FinanceSettingsDao,
    onVoiceRequest: () -> Unit
) {
    val background = Color(0xFF0A0A0B)
    val gold = Color(0xFFFFC72C)
    val green = Color(0xFF18D66B)
    val scope = rememberCoroutineScope()
    val transactions by dao.observeAll().collectAsState(initial = emptyList())
    val incomeCents by dao.observeIncomeCents().collectAsState(initial = 0L)
    val expenseCents by dao.observeExpenseCents().collectAsState(initial = 0L)
    val settings by settingsDao.observe().collectAsState(initial = null)
    val initialBalanceCents = settings?.initialBalanceCents ?: 0L
    val balanceCents = initialBalanceCents + incomeCents - expenseCents
    var showInitialBalanceDialog by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = gold, secondary = green, background = background, surface = Color(0xFF151518)
        )
    ) {
        Scaffold(
            containerColor = background,
            topBar = {
                TopAppBar(
                    title = { Text("REIS", color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onVoiceRequest,
                    containerColor = gold,
                    contentColor = Color.Black,
                    shape = CircleShape
                ) { Text("🎙") }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("REIS FINANCEIRO", style = MaterialTheme.typography.headlineMedium, color = gold)
                Text("Sua vida financeira na sua voz", color = Color.LightGray)
                BalanceCard(
                    balance = balanceCents,
                    initial = initialBalanceCents,
                    income = incomeCents,
                    expense = expenseCents,
                    onEditInitialBalance = { showInitialBalanceDialog = true }
                )
                Text("Últimos lançamentos", color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (transactions.isEmpty()) {
                    Text(
                        "Nenhum lançamento ainda. Toque no microfone e fale um gasto ou uma entrada.",
                        color = Color.Gray
                    )
                } else {
                    transactions.take(8).forEach { TransactionRow(it) }
                }
            }
        }

        if (showInitialBalanceDialog) {
            InitialBalanceDialog(
                currentCents = initialBalanceCents,
                onDismiss = { showInitialBalanceDialog = false },
                onSave = { cents ->
                    scope.launch {
                        settingsDao.save(FinanceSettings(initialBalanceCents = cents))
                    }
                    showInitialBalanceDialog = false
                }
            )
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Long,
    initial: Long,
    income: Long,
    expense: Long,
    onEditInitialBalance: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17171A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saldo", color = Color.LightGray)
                TextButton(onClick = onEditInitialBalance) { Text("Editar") }
            }
            Text("R$ %.2f".format(balance / 100.0), style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text("Valor inicial: R$ %.2f".format(initial / 100.0), color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Entradas  R$ %.2f".format(income / 100.0), color = Color(0xFF18D66B))
                Text("Saídas  R$ %.2f".format(expense / 100.0), color = Color(0xFFFF6B6B))
            }
        }
    }
}

@Composable
private fun InitialBalanceDialog(
    currentCents: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var value by remember {
        mutableStateOf(if (currentCents == 0L) "" else "%.2f".format(currentCents / 100.0))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Valor inicial") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Com quanto você começou?") },
                prefix = { Text("R$ ") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = value.replace(".", "").replace(",", ".")
                    .toDoubleOrNull()?.let { (it * 100).toLong() }
                if (cents != null && cents >= 0) onSave(cents)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun TransactionRow(transaction: Transaction) {
    val positive = transaction.type == TransactionType.INCOME
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(transaction.category, color = Color.White)
            Text(transaction.description, color = Color.Gray, maxLines = 1)
        }
        Text(
            text = (if (positive) "+ " else "- ") +
                "R$ %.2f".format(transaction.amountCents / 100.0),
            color = if (positive) Color(0xFF18D66B) else Color(0xFFFF6B6B)
        )
    }
}
