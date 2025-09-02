package com.matthewslade.gametimer

import android.annotation.SuppressLint
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
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
import androidx.compose.runtime.collectAsState
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
    val names: List<String> = listOf("Player 1", "Player 2", "Player 3"),
    val bankUnusedTime: Boolean = false
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
            "n" to s.names,
            "b" to s.bankUnusedTime
        )
    },
    restore = { map ->
        Settings(
            turnMinutes = map["t"] as Int,
            reserveMinutes = map["r"] as Int,
            playerCount = map["c"] as Int,
            names = (map["n"] as List<*>).filterIsInstance<String>(),
            bankUnusedTime = map["b"] as? Boolean ?: false
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(initial: Settings, onStart: (Settings) -> Unit) {
    var turn by rememberSaveable { mutableStateOf(initial.turnMinutes.toString()) }
    var reserve by rememberSaveable { mutableStateOf(initial.reserveMinutes.toString()) }
    var count by rememberSaveable { mutableStateOf(initial.playerCount) }
    var bankUnusedTime by rememberSaveable { mutableStateOf(initial.bankUnusedTime) }
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
        topBar = { TopAppBar(title = { Text("Board Game Timer – Settings") }) }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = bankUnusedTime,
                    onCheckedChange = { bankUnusedTime = it }
                )
                Text(
                    text = "Bank unused turn time to reserve",
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
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
                            names = names.toList(),
                            bankUnusedTime = bankUnusedTime
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

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val toneGenerator = remember { 
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME)
    }
    
    val viewModel = remember(settings, toneGenerator) {
        GameTimerViewModel(settings, toneGenerator)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    
    DisposableEffect(Unit) {
        // Keep screen on during game
        val activity = context as ComponentActivity
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        onDispose {
            // Remove keep screen on flag when leaving game screen
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    GameScreenScaffold(
        isLandscape = isLandscape,
        onBack = onBack,
        players = uiState.players,
        activeIndex = uiState.activeIndex,
        onReserve = uiState.onReserve,
        pickPlayer = viewModel::pickPlayer,
        turnRemainingMs = uiState.turnRemainingMs,
        running = uiState.running,
        onToggle = viewModel::toggleTimer,
        onResetTurn = viewModel::resetTurn
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScreenScaffold(
    isLandscape: Boolean,
    onBack: () -> Unit,
    players: List<PlayerState>,
    activeIndex: Int,
    onReserve: Boolean,
    pickPlayer: (Int) -> Unit,
    turnRemainingMs: Long,
    running: Boolean,
    onToggle: () -> Unit,
    onResetTurn: () -> Unit
) {
    val topBar = @Composable {
        TopAppBar(
            title = { Text("Board Game Timer") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            }
        )
    }
    
    if (isLandscape) {
        // Landscape layout: 3 columns with controls on the right
        Scaffold(topBar = topBar) { inner ->
            Row(
                modifier = Modifier.padding(inner).fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Player grid takes up 2/3 of the width
                PlayerGrid(
                    players = players,
                    activeIndex = activeIndex,
                    onReserve = onReserve,
                    pickPlayer = pickPlayer,
                    modifier = Modifier.weight(2f).fillMaxHeight()
                )
                
                // Controls column takes up 1/3 of the width
                ControlsColumn(
                    turnRemainingMs = turnRemainingMs,
                    onReserve = onReserve,
                    reserveRemainingMs = players[activeIndex].remainingReserveMs,
                    running = running,
                    onToggle = onToggle,
                    onResetTurn = onResetTurn,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    } else {
        // Portrait layout: original bottom bar design
        Scaffold(
            topBar = topBar,
            bottomBar = {
                ControlsBar(
                    turnRemainingMs = turnRemainingMs,
                    onReserve = onReserve,
                    reserveRemainingMs = players[activeIndex].remainingReserveMs,
                    running = running,
                    onToggle = onToggle,
                    onResetTurn = onResetTurn
                )
            }
        ) { inner ->
            PlayerGrid(
                players = players,
                activeIndex = activeIndex,
                onReserve = onReserve,
                pickPlayer = pickPlayer,
                modifier = Modifier.padding(inner).fillMaxSize().padding(8.dp)
            )
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun PlayerGrid(
    players: List<PlayerState>,
    activeIndex: Int,
    onReserve: Boolean,
    pickPlayer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val rows = (players.size + 1) / 2
        val cellHeight = (maxHeight - (8.dp * (rows - 1))) / rows
        
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
                    isUsingReserve = i == activeIndex && onReserve && p.remainingReserveMs > 0L,
                    onSelect = { pickPlayer(i) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cellHeight)
                )
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
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val label = if (!onReserve) "Turn Time" else "Reserve Time"
            val display = if (!onReserve) formatTime(turnRemainingMs) else formatTime(reserveRemainingMs)
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = display,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onToggle, 
                    modifier = Modifier.weight(1f).height(72.dp)
                ) {
                    Text(
                        text = if (running) "Pause" else "Start",
                        fontSize = 20.sp
                    )
                }
                OutlinedButton(
                    onClick = onResetTurn,
                    modifier = Modifier.height(72.dp)
                ) { 
                    Text(
                        text = "Reset Turn",
                        fontSize = 18.sp
                    ) 
                }
            }
        }
    }
}

@Composable
private fun ControlsColumn(
    turnRemainingMs: Long,
    onReserve: Boolean,
    reserveRemainingMs: Long,
    running: Boolean,
    onToggle: () -> Unit,
    onResetTurn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 3.dp
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val label = if (!onReserve) "Turn Time" else "Reserve Time"
            val display = if (!onReserve) formatTime(turnRemainingMs) else formatTime(reserveRemainingMs)
            
            Spacer(Modifier.weight(0.5f))
            
            Text(
                text = label,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = display,
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.weight(1f))
            
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    text = if (running) "Pause" else "Start",
                    fontSize = 18.sp
                )
            }
            
            OutlinedButton(
                onClick = onResetTurn,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    text = "Reset Turn",
                    fontSize = 16.sp
                )
            }
            
            Spacer(Modifier.weight(0.5f))
        }
    }
}

@Composable
private fun PlayerSlot(
    player: PlayerState,
    isActive: Boolean,
    isFlashing: Boolean,
    isUsingReserve: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine colors based on state
    val (baseColor, borderColor) = when {
        isActive && isUsingReserve -> Color(0xFFFFEB3B) to Color(0xFFFF8F00) // Bright yellow for reserve time
        isActive -> Color(0xFF4CAF50) to Color(0xFF2E7D32) // Bright green for active player
        else -> Color(0xFFF6F6F6) to Color(0xFFBDBDBD) // Default gray
    }
    
    val infinite = rememberInfiniteTransition(label = "flash")
    val animatedColor by if (isFlashing) infinite.animateColor(
        initialValue = Color(0xFFFF1744), // Bright red
        targetValue = Color(0xFFD50000), // Even brighter/darker red
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashColor"
    ) else remember { mutableStateOf(baseColor) }
    val bg = if (isFlashing) animatedColor else baseColor

    Box(
        modifier
            .background(bg)
            .border(2.dp, borderColor)
            .clickable(enabled = !isActive) { onSelect() }
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Centered player name and reserve time
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = player.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = formatTime(player.remainingReserveMs),
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
        
        // Total used time in bottom left corner
        Text(
            text = "Used: ${formatTime(player.totalUsedMs)}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.align(Alignment.BottomStart),
            color = Color.Gray
        )
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