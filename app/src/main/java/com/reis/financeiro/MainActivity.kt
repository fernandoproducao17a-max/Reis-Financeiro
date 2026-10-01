package com.reis.financeiro

import android.Manifest
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reis.financeiro.data.*
import com.reis.financeiro.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("ALTER TABLE finance_settings ADD COLUMN appLockEnabled INTEGER NOT NULL DEFAULT 0") } }\n\nprivate val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS finance_settings (id INTEGER NOT NULL PRIMARY KEY, initialBalanceCents INTEGER NOT NULL)")
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var database: ReisDatabase
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startVoiceInput() }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("Arquivo vazio")
                    val backup = parseBackup(json)
                    database.withTransaction {
                        database.transactionDao().deleteAll()
                        backup.transactions.forEach { database.transactionDao().insert(it) }
                        database.settingsDao().save(FinanceSettings(initialBalanceCents = backup.initialBalanceCents))
                    }
                    runOnUiThread { Toast.makeText(this@MainActivity, "Backup restaurado: ${backup.transactions.size} lançamentos.", Toast.LENGTH_LONG).show() }
                } catch (_: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Backup inválido ou não foi possível restaurar.", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    private val exportBackup = registerForActivityResult(CreateDocument("application/json")) { uri ->
        if (uri != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val json = com.reis.financeiro.data.transactionsToJson(database.transactionDao().getAll(), database.settingsDao().get()?.initialBalanceCents ?: 0L)
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    runOnUiThread { Toast.makeText(this@MainActivity, "Backup exportado.", Toast.LENGTH_LONG).show() }
                } catch (_: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Não foi possível exportar o backup.", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = Room.databaseBuilder(applicationContext, ReisDatabase::class.java, "reis-financeiro.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        setContent { ReisApp(database) }
        if (intent.getStringExtra("reis_action") == "novo_lancamento") {
            window.decorView.post { requestVoice() }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ReisApp(database: ReisDatabase) {
        val dao = database.transactionDao()
        val settingsDao = database.settingsDao()
        val transactions by dao.observeAll().collectAsState(initial = emptyList())
        val settings by settingsDao.observe().collectAsState(initial = null)\n        var unlocked by remember { mutableStateOf(false) }\n        LaunchedEffect(settings?.appLockEnabled) {\n            if (settings?.appLockEnabled == true) requestBiometricUnlock { unlocked = true } else unlocked = true\n        }
        val initial = settings?.initialBalanceCents ?: 0L
        val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
        val scope = rememberCoroutineScope()
        var tab by remember { mutableIntStateOf(0) }
        var showInitial by remember { mutableStateOf(false) }
        var showForm by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<Transaction?>(null) }
        if (!unlocked) { PrivacyLockScreen(onUnlock = { requestBiometricUnlock { unlocked = true } }); return }\n        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFC72C), secondary = Color(0xFF18D66B), background = Color(0xFF0A0A0B), surface = Color(0xFF17171A))) {
            Scaffold(
                containerColor = Color(0xFF0A0A0B),
                topBar = { TopAppBar(title = { Text("REIS", color = Color.White) }, actions = { Text("Financeiro", color = Color(0xFFFFC72C), modifier = Modifier.padding(end = 16.dp)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0B))) },
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF121214)) {
                        val labels = listOf("⌂" to "Início", "☷" to "Lançamentos", "▥" to "Relatórios")
                        labels.forEachIndexed { index, item -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(item.first) }, label = { Text(item.second) }) }
                    }
                },
                floatingActionButton = { FloatingActionButton(onClick = { requestVoice() }, containerColor = Color(0xFFFFC72C), contentColor = Color.Black) { Text("🎙") } }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (tab) {
                        0 -> DashboardScreen(transactions, initial, income, expense, { showInitial = true }, { editing = null; showForm = true }, { editing = it; showForm = true }, { scope.launch { dao.delete(it) } })
                        1 -> HistoryScreen(transactions, { editing = it; showForm = true }, { scope.launch { dao.delete(it) } })
                        else -> ReportsScreen(transactions, income, expense, { exportBackup.launch("reis-financeiro-backup.json") }, { importBackup.launch(arrayOf("application/json", "text/json", "text/plain")) })
                    }
                }
            }
            if (showInitial) InitialBalanceDialog(initial, { showInitial = false }) { value -> scope.launch { settingsDao.save(FinanceSettings(initialBalanceCents = value)) }; showInitial = false }
            if (showForm) TransactionDialog(editing, { showForm = false }) { transaction -> scope.launch { if (transaction.id == 0L) dao.insert(transaction) else dao.update(transaction) }; showForm = false }
        }
    }

    private fun requestVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startVoiceInput()
        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { Toast.makeText(this, "Reconhecimento de voz indisponível.", Toast.LENGTH_LONG).show(); return }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua entrada ou saída")
        }
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                val parsed = spoken?.let { com.reis.financeiro.voice.VoiceCommandParser.parse(it) }
                if (parsed == null) Toast.makeText(this@MainActivity, "Não entendi. Exemplo: Gastei 100 reais de combustível.", Toast.LENGTH_LONG).show()
                else { CoroutineScope(Dispatchers.IO).launch { database.transactionDao().insert(parsed.transaction) }; Toast.makeText(this@MainActivity, "Registrado: " + parsed.confirmationText, Toast.LENGTH_LONG).show() }
                recognizer.destroy()
            }
            override fun onError(error: Int) { Toast.makeText(this@MainActivity, "Não consegui ouvir. Tente novamente.", Toast.LENGTH_SHORT).show(); recognizer.destroy() }
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