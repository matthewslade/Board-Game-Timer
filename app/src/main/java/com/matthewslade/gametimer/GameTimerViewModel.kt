package com.matthewslade.gametimer

import android.media.ToneGenerator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerState(
    val name: String,
    val totalReserveMs: Long,
    var remainingReserveMs: Long,
    var totalUsedMs: Long = 0L,
    var reserveTimeUsedMs: Long = 0L,
    var isOut: Boolean = false,
)

data class GameTimerUiState(
    val players: List<PlayerState> = emptyList(),
    val activeIndex: Int = 0,
    val turnRemainingMs: Long = 0L,
    val running: Boolean = false,
    val onReserve: Boolean = false,
    val turnStartReserveUsedMs: Long = 0L,
    val turnTotalMs: Long = 0L
)

class GameTimerViewModel(
    private val settings: Settings,
    private val toneGenerator: ToneGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GameTimerUiState(
            players = settings.names.map { name ->
                PlayerState(
                    name = name,
                    totalReserveMs = settings.reserveMinutes * 60_000L,
                    remainingReserveMs = settings.reserveMinutes * 60_000L
                )
            },
            turnTotalMs = settings.turnMinutes * 60_000L,
            turnRemainingMs = settings.turnMinutes * 60_000L
        )
    )
    val uiState: StateFlow<GameTimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun toggleTimer() {
        val currentState = _uiState.value
        if (currentState.running) {
            stopTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        _uiState.value = _uiState.value.copy(running = true)
        timerJob = viewModelScope.launch {
            while (_uiState.value.running) {
                delay(100L)
                val state = _uiState.value

                if (!state.onReserve) {
                    val newTurnTime = (state.turnRemainingMs - 100L).coerceAtLeast(0L)
                    _uiState.value = state.copy(
                        turnRemainingMs = newTurnTime,
                        onReserve = newTurnTime == 0L
                    )
                } else {
                    val activePlayer = state.players[state.activeIndex]
                    val newReserveTime = (activePlayer.remainingReserveMs - 100L).coerceAtLeast(0L)
                    val reserveTimeConsumed = activePlayer.remainingReserveMs - newReserveTime

                    val updatedPlayers = state.players.toMutableList().also { players ->
                        players[state.activeIndex] = activePlayer.copy(
                            remainingReserveMs = newReserveTime,
                            reserveTimeUsedMs = activePlayer.reserveTimeUsedMs + reserveTimeConsumed
                        )
                    }

                    _uiState.value = state.copy(players = updatedPlayers)

                    if (newReserveTime == 0L) {
                        val finalPlayers = updatedPlayers.toMutableList().also { players ->
                            players[state.activeIndex] = players[state.activeIndex].copy(isOut = true)
                        }
                        _uiState.value = _uiState.value.copy(
                            players = finalPlayers,
                            running = false
                        )
                        // Play sound when player runs out of time
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                    }
                }
            }
        }
    }

    private fun stopTimer() {
        _uiState.value = _uiState.value.copy(running = false)
        timerJob?.cancel()
    }

    fun pickPlayer(index: Int) {
        val state = _uiState.value
        val currentPlayer = state.players[state.activeIndex]

        // Calculate time used in this turn by current player
        val timeUsedThisTurn = if (!state.onReserve) {
            // Time used from turn time only
            state.turnTotalMs - state.turnRemainingMs
        } else {
            // Full turn time was used, plus reserve time consumed during this turn
            val reserveTimeUsedThisTurn = currentPlayer.reserveTimeUsedMs - state.turnStartReserveUsedMs
            state.turnTotalMs + reserveTimeUsedThisTurn
        }

        // Update current player's total used time and handle banking
        val updatedPlayers = state.players.toMutableList().also { playerList ->
            val updatedPlayer = if (settings.bankUnusedTime && !state.onReserve && state.turnRemainingMs > 0) {
                // Bank unused time and update total used time
                currentPlayer.copy(
                    remainingReserveMs = currentPlayer.remainingReserveMs + state.turnRemainingMs,
                    totalUsedMs = currentPlayer.totalUsedMs + (state.turnTotalMs - state.turnRemainingMs)
                )
            } else {
                // Just update total used time
                currentPlayer.copy(
                    totalUsedMs = currentPlayer.totalUsedMs + timeUsedThisTurn
                )
            }
            playerList[state.activeIndex] = updatedPlayer
        }

        _uiState.value = state.copy(
            players = updatedPlayers,
            activeIndex = index,
            turnRemainingMs = state.turnTotalMs,
            onReserve = false,
            turnStartReserveUsedMs = updatedPlayers[index].reserveTimeUsedMs
        )
    }

    fun resetTurn() {
        val state = _uiState.value
        _uiState.value = state.copy(
            turnRemainingMs = state.turnTotalMs,
            onReserve = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        toneGenerator.release()
    }
}
