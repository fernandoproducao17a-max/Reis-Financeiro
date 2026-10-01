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
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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

private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("ALTER TABLE finance_settings ADD COLUMN appLockEnabled INTEGER NOT NULL DEFAULT 0") } }

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS finance_settings (id INTEGER NOT NULL PRIMARY KEY, initialBalanceCents INTEGER NOT NULL)")
    }
}

class MainActivity : FragmentActivity() {
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
                        database.settingsDao().save(FinanceSettings(initialBalanceCents = backup.initialBalanceCents, appLockEnabled = database.settingsDao().get()?.appLockEnabled ?: false))
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
        val settings by settingsDao.observe().collectAsState(initial = null)
        var unlocked by remember { mutableStateOf(false) }
        LaunchedEffect(settings?.appLockEnabled) {
            if (settings?.appLockEnabled == true) requestBiometricUnlock { unlocked = true } else unlocked = true
        }
        val initial = settings?.initialBalanceCents ?: 0L
        val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
        val scope = rememberCoroutineScope()
        var tab by remember { mutableIntStateOf(0) }
        var showInitial by remember { mutableStateOf(false) }
        var showForm by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<Transaction?>(null) }
        var showPrivacy by remember { mutableStateOf(false) }
        if (!unlocked) { PrivacyLockScreen(onUnlock = { requestBiometricUnlock { unlocked = true } }); return }
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFC72C), secondary = Color(0xFF18D66B), background = Color(0xFF0A0A0B), surface = Color(0xFF17171A))) {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = Color(0xFF111114), drawerContentColor = Color.White) {
                        ReisDrawerHeader()
                        HorizontalDivider(color = Color(0xFF29292D))
                        ReisDrawerItem("⌂", "Início", tab == 0) { tab = 0; scope.launch { drawerState.close() } }
                        ReisDrawerItem("☷", "Lançamentos", tab == 1) { tab = 1; scope.launch { drawerState.close() } }
                        ReisDrawerItem("▥", "Relatórios", tab == 2) { tab = 2; scope.launch { drawerState.close() } }
                        HorizontalDivider(color = Color(0xFF29292D), modifier = Modifier.padding(vertical = 8.dp))
                        ReisDrawerItem("＋", "Novo lançamento", false) { editing = null; showForm = true; scope.launch { drawerState.close() } }
                        ReisDrawerItem("🎙", "Lançar por voz", false) { scope.launch { drawerState.close() }; requestVoice() }
                        ReisDrawerItem("🔒", "Privacidade e segurança", false) { showPrivacy = true; scope.launch { drawerState.close() } }
                        ReisDrawerItem("◉", "Saldo inicial", false) { showInitial = true; scope.launch { drawerState.close() } }
                        HorizontalDivider(color = Color(0xFF29292D), modifier = Modifier.padding(vertical = 8.dp))
                        Text("DADOS", color = Color(0xFF88888F), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                        ReisDrawerItem("⇧", "Exportar backup", false) { exportBackup.launch("reis-financeiro-backup.json"); scope.launch { drawerState.close() } }
                        ReisDrawerItem("⇩", "Restaurar backup", false) { importBackup.launch(arrayOf("application/json", "text/json", "text/plain")); scope.launch { drawerState.close() } }
                        Spacer(Modifier.weight(1f))
                        Text("REIS FINANCEIRO • 1.1.0", color = Color(0xFF66666D), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(20.dp))
                    }
                }
            ) {
                Scaffold(
                    containerColor = Color(0xFF0A0A0B),
                    topBar = {
                        TopAppBar(
                            navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Text("☰", color = Color.White, style = MaterialTheme.typography.titleLarge) } },
                            title = {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Image(painter = painterResource(com.reis.financeiro.R.drawable.ic_reis_logo), contentDescription = "REIS", modifier = Modifier.size(34.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("REIS", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("FINANCEIRO", color = Color(0xFFFFC72C), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            },
                            actions = { IconButton(onClick = { showPrivacy = true }) { Text("🔒", color = Color(0xFFFFC72C)) } },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0B))
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF121214)) {
                            val labels = listOf("⌂" to "Início", "☷" to "Lançamentos", "▥" to "Relatórios")
                            labels.forEachIndexed { index, item -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(item.first) }, label = { Text(item.second) }) }
                        }
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(onClick = { requestVoice() }, icon = { Text("🎙") }, text = { Text("Lançar por voz", fontWeight = FontWeight.Bold) }, containerColor = Color(0xFFFFC72C), contentColor = Color.Black)
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (tab) {
                            0 -> DashboardScreen(transactions, initial, income, expense, { showInitial = true }, { editing = null; showForm = true }, { editing = it; showForm = true }, { scope.launch { dao.delete(it) } })
                            1 -> HistoryScreen(transactions, { editing = it; showForm = true }, { scope.launch { dao.delete(it) } })
                            else -> ReportsScreen(transactions, income, expense, { exportBackup.launch("reis-financeiro-backup.json") }, { importBackup.launch(arrayOf("application/json", "text/json", "text/plain")) })
                        }
                    }
                }
            }
            if (showPrivacy) PrivacySettingsDialog(settings?.appLockEnabled == true, { showPrivacy = false }) { enabled -> scope.launch { settingsDao.save(FinanceSettings(initialBalanceCents = initial, appLockEnabled = enabled)) } }
            if (showInitial) InitialBalanceDialog(initial, { showInitial = false }) { value -> scope.launch { settingsDao.save(FinanceSettings(initialBalanceCents = value)) }; showInitial = false }
            if (showForm) TransactionDialog(editing, { showForm = false }) { transaction -> scope.launch { if (transaction.id == 0L) dao.insert(transaction) else dao.update(transaction) }; showForm = false }
        }
    }

    private fun requestBiometricUnlock(onSuccess: () -> Unit) {
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            })
            val info = BiometricPrompt.PromptInfo.Builder().setTitle("REIS protegido").setSubtitle("Confirme sua identidade para acessar suas finanças").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
            prompt.authenticate(info)
        } else onSuccess()
    }

    private fun requestVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startVoiceInput()
        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissão do microfone não concedida.", Toast.LENGTH_LONG).show()
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "O reconhecimento de voz não está disponível neste aparelho.", Toast.LENGTH_LONG).show()
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua entrada ou saída")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                val parsed = spoken?.let { com.reis.financeiro.voice.VoiceCommandParser.parse(it) }
                if (parsed == null) Toast.makeText(this@MainActivity, "Não entendi. Exemplo: Gastei 100 reais de combustível.", Toast.LENGTH_LONG).show()
                else { CoroutineScope(Dispatchers.IO).launch { database.transactionDao().insert(parsed.transaction) }; Toast.makeText(this@MainActivity, "Registrado: " + parsed.confirmationText, Toast.LENGTH_LONG).show() }
                recognizer.destroy()
            }
            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Erro ao acessar o áudio. Verifique o microfone."
                    SpeechRecognizer.ERROR_CLIENT -> "O reconhecimento foi interrompido. Tente novamente."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "O REIS não tem permissão para usar o microfone."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "O serviço de voz está sem conexão."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Não consegui entender. Fale o valor e o tipo de lançamento."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O serviço de voz está ocupado. Aguarde e tente novamente."
                    SpeechRecognizer.ERROR_SERVER -> "O serviço de reconhecimento de voz apresentou uma falha."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não detectei fala. Toque no microfone e fale em seguida."
                    else -> "Falha no reconhecimento de voz. Código: $error."
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                recognizer.destroy()
            }
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