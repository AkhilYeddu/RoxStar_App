package com.roxstar.app.ui.spin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roxstar.app.data.models.SpinState
import com.roxstar.app.network.ApiService
import com.roxstar.app.network.SocketEvent
import com.roxstar.app.network.SocketManager
import com.roxstar.app.network.StartSpinRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SpinUiState(
    val spinState: SpinState? = null,
    val isSpinRunning: Boolean = false,
    val round: Int = 0,
    val countdownSeconds: Int = 5,
    val activeSurvivors: List<String> = emptyList(),
    val eliminatedList: List<String> = emptyList(),
    val winnerId: String? = null,
    val isEligiblePlayer: Boolean = false,
    val statusBanner: String = "Spin wheel ready",
    val errorMessage: String? = null
)

class SpinWheelViewModel(
    private val apiService: ApiService,
    private val socketManager: SocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpinUiState())
    val uiState: StateFlow<SpinUiState> = _uiState.asStateFlow()

    private var currentUserId: String = ""
    private var countdownJob: Job? = null

    init {
        observeSocketSpinEvents()
    }

    fun setUserId(userId: String) {
        currentUserId = userId
    }

    private fun observeSocketSpinEvents() {
        viewModelScope.launch {
            socketManager.eventsFlow.collect { event ->
                when (event) {
                    is SocketEvent.SpinStarted -> {
                        val isEligible = event.spinState.activeParticipants.contains(currentUserId)
                        _uiState.value = _uiState.value.copy(
                            spinState = event.spinState,
                            isSpinRunning = true,
                            round = 1,
                            countdownSeconds = 5,
                            activeSurvivors = event.spinState.activeParticipants,
                            eliminatedList = emptyList(),
                            winnerId = null,
                            isEligiblePlayer = isEligible,
                            statusBanner = "Spin Wheel RUNNING! Eliminating every 5s...",
                            errorMessage = null
                        )
                        startCountdownTimer()
                    }

                    is SocketEvent.UserEliminated -> {
                        val updatedSurvivors = event.remainingUsers
                        val newlyEliminated = event.eliminatedUserId
                        val currentEliminated = _uiState.value.eliminatedList.toMutableList()
                        if (!currentEliminated.contains(newlyEliminated)) {
                            currentEliminated.add(newlyEliminated)
                        }

                        val userStatus = if (newlyEliminated == currentUserId) {
                            "You have been eliminated!"
                        } else {
                            "Player $newlyEliminated eliminated!"
                        }

                        _uiState.value = _uiState.value.copy(
                            round = event.round,
                            activeSurvivors = updatedSurvivors,
                            eliminatedList = currentEliminated,
                            countdownSeconds = 5,
                            statusBanner = "Round ${event.round}: $userStatus (${updatedSurvivors.size} remaining)"
                        )
                        startCountdownTimer()
                    }

                    is SocketEvent.WinnerAnnounced -> {
                        countdownJob?.cancel()
                        val isWinner = event.winnerId == currentUserId
                        _uiState.value = _uiState.value.copy(
                            isSpinRunning = false,
                            winnerId = event.winnerId,
                            activeSurvivors = listOf(event.winnerId),
                            countdownSeconds = 0,
                            statusBanner = if (isWinner) "🏆 CONGRATULATIONS! YOU WON THE SPIN! 🏆" else "🏆 Winner: ${event.winnerId} 🏆"
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    fun triggerStartSpin(roomId: String) {
        viewModelScope.launch {
            try {
                val response = apiService.startSpin(roomId, StartSpinRequest(userId = currentUserId))
                if (!response.isSuccessful) {
                    val err = response.errorBody()?.string() ?: "Failed to start spin"
                    _uiState.value = _uiState.value.copy(errorMessage = "Cannot start spin: $err")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = 5
            while (isActive && remaining > 0) {
                _uiState.value = _uiState.value.copy(countdownSeconds = remaining)
                delay(1000)
                remaining--
            }
            if (isActive) {
                _uiState.value = _uiState.value.copy(countdownSeconds = 0)
            }
        }
    }
}
