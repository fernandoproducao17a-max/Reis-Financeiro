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
import android.app.Activity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    private var speechRecognizer: SpeechRecognizer? = null
    private var appLocked = false
    private var shouldRelock = false
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startVoiceInput() }
    private var pendingRestoreUri by mutableStateOf<android.net.Uri?>(null)

    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    private fun restoreBackup(uri: android.net.Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("Arquivo vazio")
                val backup = parseBackup(json)
                database.withTransaction {
                    database.transactionDao().deleteAll()
                    backup.transactions.forEach { database.transactionDao().insert(it) }
                    val currentLock = database.settingsDao().get()?.appLockEnabled ?: false
                    database.settingsDao().save(FinanceSettings(initialBalanceCents = backup.initialBalanceCents, appLockEnabled = currentLock))
                }
                runOnUiThread { pendingRestoreUri = null; Toast.makeText(this@MainActivity, "Backup restaurado: ${backup.transactions.size} lançamentos.", Toast.LENGTH_LONG).show() }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Backup inválido ou não foi possível restaurar.", Toast.LENGTH_LONG).show() }
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
        LaunchedEffect(appLocked) { if (appLocked && settings?.appLockEnabled == true) unlocked = false }
        LaunchedEffect(settings?.appLockEnabled, appLocked) {
            shouldRelock = settings?.appLockEnabled == true
            if (settings?.appLockEnabled == true && (!unlocked || appLocked)) requestBiometricUnlock { appLocked = false; unlocked = true } else if (settings?.appLockEnabled != true) unlocked = true
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
            pendingRestoreUri?.let { uri ->
                AlertDialog(
                    onDismissRequest = { pendingRestoreUri = null },
                    title = { Text("Restaurar backup?") },
                    text = { Text("A restauração substituirá os lançamentos atuais pelos dados do backup. O bloqueio de privacidade será preservado.") },
                    confirmButton = { TextButton(onClick = { restoreBackup(uri) }) { Text("Restaurar") } },
                    dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancelar") } }
                )
            }
            if (showPrivacy) PrivacySettingsDialog(settings?.appLockEnabled == true, { showPrivacy = false }) { enabled -> scope.launch { settingsDao.save(FinanceSettings(initialBalanceCents = initial, appLockEnabled = enabled)) } }
            if (showInitial) InitialBalanceDialog(initial, { showInitial = false }) { value -> scope.launch { settingsDao.save(FinanceSettings(initialBalanceCents = value, appLockEnabled = settings?.appLockEnabled == true)) }; showInitial = false }
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

    override fun onStop() {
        super.onStop()
        if (::database.isInitialized) {
            if (shouldRelock) appLocked = true
        }
    }

    override fun onDestroy() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        database.close()
        super.onDestroy()
    }

    private fun requestVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startVoiceInput()
        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoiceInput() {
        if (speechRecognizer != null) {
            Toast.makeText(this, "O REIS já está ouvindo. Fale agora.", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissão do microfone não concedida.", Toast.LENGTH_LONG).show()
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "O reconhecimento de voz não está disponível neste aparelho. Verifique o serviço de voz do Android.", Toast.LENGTH_LONG).show()
            return
        }
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(this)
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível iniciar o microfone. Tente novamente.", Toast.LENGTH_LONG).show()
            return
        }
        speechRecognizer = recognizer
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ex.: Gastei 100 reais de combustível")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            private fun finish() {
                if (speechRecognizer === recognizer) speechRecognizer = null
                recognizer.destroy()
            }
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "🎙️ Ouvindo… fale o lançamento.", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                val parsed = spoken?.let { com.reis.financeiro.voice.VoiceCommandParser.parse(it) }
                if (parsed == null) {
                    Toast.makeText(this@MainActivity, "Não entendi. Exemplo: Gastei 100 reais de combustível.", Toast.LENGTH_LONG).show()
                } else {
                    CoroutineScope(Dispatchers.IO).launch { database.transactionDao().insert(parsed.transaction) }
                    Toast.makeText(this@MainActivity, "✓ ${parsed.confirmationText}", Toast.LENGTH_LONG).show()
                }
                finish()
            }
            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Erro no áudio. Verifique se o microfone está funcionando e se outro app está usando-o."
                    SpeechRecognizer.ERROR_CLIENT -> "O reconhecimento foi interrompido. Toque no microfone e tente novamente."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "O REIS não tem permissão para usar o microfone."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "O serviço de voz está sem conexão. Verifique a internet."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Não consegui entender. Diga o valor e o tipo, por exemplo: paguei 50 reais de Uber."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O serviço de voz está ocupado. Aguarde alguns segundos e tente novamente."
                    SpeechRecognizer.ERROR_SERVER -> "O serviço de reconhecimento de voz apresentou uma falha. Tente novamente."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não detectei fala. Toque no microfone e fale logo depois."
                    else -> "Falha no reconhecimento de voz. Código: $error."
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            speechRecognizer = null
            recognizer.destroy()
            Toast.makeText(this, "Não foi possível iniciar a escuta do microfone.", Toast.LENGTH_LONG).show()
        }
    }
}