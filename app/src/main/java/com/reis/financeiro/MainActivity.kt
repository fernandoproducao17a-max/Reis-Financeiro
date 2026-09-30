package com.reis.financeiro

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReisFinanceiroApp(
                onVoiceRequest = { audioPermission.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }
    }
}

@Composable
private fun ReisFinanceiroApp(onVoiceRequest: () -> Unit) {
    val background = Color(0xFF0A0A0B)
    val gold = Color(0xFFFFC72C)
    val green = Color(0xFF18D66B)

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = gold,
            secondary = green,
            background = background,
            surface = Color(0xFF151518)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .background(background),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("REIS FINANCEIRO", style = MaterialTheme.typography.headlineMedium, color = gold)
                Text("Sua vida financeira na sua voz", color = Color.LightGray)
                BalanceCard()
                Text("Comece dizendo, por exemplo:", color = Color.White)
                Text("“Gastei 100 reais de combustível”", color = green)
                Text("“Recebi 3 mil reais de salário”", color = green)
            }
        }
    }
}

@Composable
private fun BalanceCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17171A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Saldo", color = Color.LightGray)
            Text("R$ 0,00", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Entradas  R$ 0,00", color = Color(0xFF18D66B))
                Text("Saídas  R$ 0,00", color = Color(0xFFFF6B6B))
            }
        }
    }
}
