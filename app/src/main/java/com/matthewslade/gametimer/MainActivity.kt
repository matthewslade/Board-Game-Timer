package com.matthewslade.gametimer

import android.annotation.SuppressLint
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                AppRoot()
            }
        }
    }
}

data class Settings(
    val turnMinutes: Int = 2,
    val reserveMinutes: Int = 1,
    val playerCount: Int = 3,
    val names: List<String> = listOf("Player 1", "Player 2", "Player 3")
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    var settings by rememberSaveable(stateSaver = SettingsSaver) {
        mutableStateOf(Settings())
    }

    NavHost(navController = nav, startDestination = "settings") {
        composable("settings") {
            SettingsScreen(
                initial = settings,
                onStart = { newSettings ->
                    settings = newSettings
                    nav.navigate("game")
                }
            )
        }
        composable("game") {
            GameScreen(settings = settings, onBack = { nav.popBackStack() })
        }
    }
}

private val SettingsSaver = mapSaver(
    save = { s ->
        mapOf(
            "t" to s.turnMinutes,
            "r" to s.reserveMinutes,
            "c" to s.playerCount,
            "n" to s.names
        )
    },
    restore = { map ->
        Settings(
            turnMinutes = map["t"] as Int,
            reserveMinutes = map["r"] as Int,
            playerCount = map["c"] as Int,
            names = (map["n"] as List<*>).filterIsInstance<String>()
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(initial: Settings, onStart: (Settings) -> Unit) {
    var turn by rememberSaveable { mutableStateOf(initial.turnMinutes.toString()) }
    var reserve by rememberSaveable { mutableStateOf(initial.reserveMinutes.toString()) }
    var count by rememberSaveable { mutableStateOf(initial.playerCount) }
    var names by rememberSaveable(stateSaver = listSaver(
        save = { it },
        restore = { it.toMutableList() }
    )) { mutableStateOf(initial.names.toMutableList()) }

    LaunchedEffect(count) {
        val clamped = count.coerceIn(3, 6)
        if (clamped != count) count = clamped
        when {
            names.size < count -> {
                val newNames = names.toMutableList()
                repeat(count - names.size) { newNames.add("Player ${newNames.size + 1}") }
                names = newNames
            }
            names.size > count -> {
                names = names.take(count).toMutableList()
            }
        }
    }

    val turnInt = turn.toIntOrNull() ?: -1
    val reserveInt = reserve.toIntOrNull() ?: -1
    val isValid = turnInt > 0 && reserveInt >= 0 && count in 3..6 && names.all { it.isNotBlank() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("BoardGame Timer – Settings") }) }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = turn,
                    onValueChange = { turn = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Turn time (minutes)", fontSize = 16.sp) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 18.sp)
                )
                OutlinedTextField(
                    value = reserve,
                    onValueChange = { reserve = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Reserve time (minutes)", fontSize = 16.sp) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 18.sp)
                )
            }

            Column {
                Text(
                    text = "Number of players: $count", 
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                Slider(
                    value = count.toFloat(),
                    onValueChange = { f -> count = f.toInt().coerceIn(3, 6) },
                    valueRange = 3f..6f,
                    steps = 2
                )
            }

            Divider()

            Text(
                text = "Player names", 
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(((names.size / 2 + names.size % 2) * 80).dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                itemsIndexed(names) { i, name ->
                    OutlinedTextField(
                        value = name,
                        onValueChange = { new -> 
                            names = names.toMutableList().also { it[i] = new }
                        },
                        label = { Text("Player ${i + 1} name", fontSize = 16.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 18.sp)
                    )
                }
            }

            Button(
                onClick = {
                    onStart(
                        Settings(
                            turnMinutes = (turn.toIntOrNull() ?: 1).coerceAtLeast(1),
                            reserveMinutes = (reserve.toIntOrNull() ?: 0).coerceAtLeast(0),
                            playerCount = count,
                            names = names.toList()
                        )
                    )
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Start Game",
                    fontSize = 18.sp
                )
            }
        }
    }
}

data class PlayerState(
    val name: String,
    val totalReserveMs: Long,
    var remainingReserveMs: Long,
    var isOut: Boolean = false,
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val toneGenerator = remember { 
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.release()
        }
    }
    
    val turnTotalMs = remember(settings.turnMinutes) { settings.turnMinutes * 60_000L }
    var players by remember {
        mutableStateOf(
            settings.names.map {
                PlayerState(
                    name = it,
                    totalReserveMs = settings.reserveMinutes * 60_000L,
                    remainingReserveMs = settings.reserveMinutes * 60_000L,
                )
            }
        )
    }

    var activeIndex by remember { mutableStateOf(0) }
    var turnRemainingMs by remember { mutableStateOf(turnTotalMs) }
    var running by remember { mutableStateOf(false) }
    var onReserve by remember { mutableStateOf(false) }

    LaunchedEffect(running, activeIndex, onReserve) {
        while (running) {
            delay(100L)
            if (!onReserve) {
                turnRemainingMs = (turnRemainingMs - 100L).coerceAtLeast(0L)
                if (turnRemainingMs == 0L) onReserve = true
            } else {
                val p = players[activeIndex]
                val newRes = (p.remainingReserveMs - 100L).coerceAtLeast(0L)
                players = players.toMutableList().also {
                    it[activeIndex] = it[activeIndex].copy(remainingReserveMs = newRes)
                }
                if (newRes == 0L) {
                    players = players.toMutableList().also {
                        it[activeIndex] = it[activeIndex].copy(isOut = true)
                    }
                    // Play sound when player runs out of time
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                    running = false
                }
            }
        }
    }

    fun pickPlayer(index: Int) {
        activeIndex = index
        turnRemainingMs = turnTotalMs
        onReserve = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BoardGame Timer – Game") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        bottomBar = {
            ControlsBar(
                turnRemainingMs = turnRemainingMs,
                onReserve = onReserve,
                reserveRemainingMs = players[activeIndex].remainingReserveMs,
                running = running,
                onToggle = { running = !running },
                onResetTurn = { turnRemainingMs = turnTotalMs; onReserve = false }
            )
        }
    ) { inner ->
        BoxWithConstraints(
            modifier = Modifier.padding(inner).fillMaxSize().padding(8.dp)
        ) {
            val rows = (players.size + 1) / 2 // Calculate number of rows needed
            val cellHeight = (maxHeight - (8.dp * (rows - 1))) / rows // Available height divided by rows
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                itemsIndexed(players) { i, p ->
                    PlayerSlot(
                        player = p,
                        isActive = i == activeIndex,
                        isFlashing = i == activeIndex && onReserve && p.remainingReserveMs == 0L,
                        onSelect = { pickPlayer(i) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cellHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsBar(
    turnRemainingMs: Long,
    onReserve: Boolean,
    reserveRemainingMs: Long,
    running: Boolean,
    onToggle: () -> Unit,
    onResetTurn: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val label = if (!onReserve) "Turn Time" else "Reserve Time"
            val display = if (!onReserve) formatTime(turnRemainingMs) else formatTime(reserveRemainingMs)
            Text(
                text = "$label: $display",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onToggle, 
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text(
                        text = if (running) "Pause" else "Start",
                        fontSize = 18.sp
                    )
                }
                OutlinedButton(
                    onClick = onResetTurn,
                    modifier = Modifier.height(56.dp).padding(start = 8.dp)
                ) { 
                    Text(
                        text = "Reset Turn",
                        fontSize = 16.sp
                    ) 
                }
            }
        }
    }
}

@Composable
private fun PlayerSlot(
    player: PlayerState,
    isActive: Boolean,
    isFlashing: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = if (isActive) Color(0xFFDFF6DD) else Color(0xFFF6F6F6)
    val borderColor = if (isActive) Color(0xFF2E7D32) else Color(0xFFBDBDBD)
    val infinite = rememberInfiniteTransition(label = "flash")
    val animatedColor by if (isFlashing) infinite.animateColor(
        initialValue = Color(0xFFFFCDD2), // Light red
        targetValue = Color(0xFFFF5252), // Bright red
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashColor"
    ) else remember { mutableStateOf(baseColor) }
    val bg = if (isFlashing) animatedColor else baseColor

    Column(
        modifier
            .background(bg)
            .border(2.dp, borderColor)
            .clickable(enabled = !isActive) { onSelect() }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = player.name, 
                fontSize = 24.sp, 
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Reserve: ${formatTime(player.remainingReserveMs)}", 
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    MaterialTheme { SettingsScreen(initial = Settings(), onStart = {}) }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
fun GamePreview() {
    MaterialTheme { GameScreen(Settings(names = listOf("A", "B", "C")), onBack = {}) }
}