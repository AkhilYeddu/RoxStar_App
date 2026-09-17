package com.roxstar.app.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roxstar.app.data.models.Draft
import com.roxstar.app.data.models.Room
import com.roxstar.app.data.models.SpinState
import com.roxstar.app.network.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ── Room State ──────────────────────────────────────────────────────────────

data class RoomUiState(
    val currentRoom: Room? = null,
    val userId: String = "",
    val username: String = "",
    val isConnected: Boolean = false,
    val isOwner: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

// ── Spin State ───────────────────────────────────────────────────────────────

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

// ── Combined ViewModel ────────────────────────────────────────────────────────

class RoomSessionViewModel(
    private val apiService: ApiService,
    private val socketManager: SocketManager
) : ViewModel() {

    // Room
    private val _roomState = MutableStateFlow(RoomUiState())
    val roomState: StateFlow<RoomUiState> = _roomState.asStateFlow()

    // Spin
    private val _spinState = MutableStateFlow(SpinUiState())
    val spinState: StateFlow<SpinUiState> = _spinState.asStateFlow()

    /** Guard against race-condition: socket `room_state` event arriving after leave. */
    @Volatile private var isLeaving = false

    private var countdownJob: Job? = null

    init {
        observeSocketEvents()
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    fun setUserInfo(userId: String, username: String) {
        _roomState.value = _roomState.value.copy(userId = userId, username = username)
    }

    // ── Socket Event Observation ──────────────────────────────────────────────

    private fun observeSocketEvents() {
        viewModelScope.launch {
            socketManager.connectionState.collect { connected ->
                _roomState.value = _roomState.value.copy(isConnected = connected)
            }
        }

        viewModelScope.launch {
            socketManager.eventsFlow.collect { event ->
                when (event) {

                    // ── Room Events ───────────────────────────────────────────

                    is SocketEvent.UserJoined -> {
                        val room = _roomState.value.currentRoom ?: return@collect
                        val members = room.participants.toMutableList()
                        val idx = members.indexOfFirst { it.userId == event.user.userId }
                        if (idx >= 0) members[idx] = event.user else members.add(event.user)
                        _roomState.value = _roomState.value.copy(
                            currentRoom = room.copy(participants = members),
                            infoMessage = "${event.user.username} joined the room"
                        )
                    }

                    is SocketEvent.UserLeft -> {
                        val room = _roomState.value.currentRoom ?: return@collect
                        val members = room.participants.filter { it.userId != event.userId }
                        _roomState.value = _roomState.value.copy(
                            currentRoom = room.copy(participants = members),
                            infoMessage = "User left (${event.reason})"
                        )
                    }

                    is SocketEvent.DraftShared -> {
                        val room = _roomState.value.currentRoom ?: return@collect
                        val drafts = room.sharedDrafts.toMutableList()
                        if (drafts.none { it.id == event.draft.id }) {
                            drafts.add(0, event.draft)
                        }
                        _roomState.value = _roomState.value.copy(
                            currentRoom = room.copy(sharedDrafts = drafts),
                            infoMessage = "New draft shared: ${event.draft.title} by ${event.sharedBy}"
                        )
                    }

                    is SocketEvent.RoomStateUpdated -> {
                        // Ignore stale room_state events that arrive after we have left
                        if (isLeaving) return@collect
                        val isOwner = event.room.ownerId == _roomState.value.userId
                        _roomState.value = _roomState.value.copy(
                            currentRoom = event.room,
                            isOwner = isOwner,
                            infoMessage = "Room state synchronised"
                        )
                    }

                    is SocketEvent.ErrorOccurred -> {
                        _roomState.value = _roomState.value.copy(errorMessage = event.message)
                    }

                    // ── Spin Events ───────────────────────────────────────────

                    is SocketEvent.SpinStarted -> {
                        val userId = _roomState.value.userId
                        val isEligible = event.spinState.activeParticipants.contains(userId)
                        _spinState.value = _spinState.value.copy(
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
                        val userId = _roomState.value.userId
                        val eliminated = _spinState.value.eliminatedList.toMutableList()
                        if (!eliminated.contains(event.eliminatedUserId)) {
                            eliminated.add(event.eliminatedUserId)
                        }
                        val userStatus = if (event.eliminatedUserId == userId) {
                            "You have been eliminated!"
                        } else {
                            "Player ${event.eliminatedUserId} eliminated!"
                        }
                        _spinState.value = _spinState.value.copy(
                            round = event.round,
                            activeSurvivors = event.remainingUsers,
                            eliminatedList = eliminated,
                            countdownSeconds = 5,
                            statusBanner = "Round ${event.round}: $userStatus (${event.remainingUsers.size} remaining)"
                        )
                        startCountdownTimer()
                    }

                    is SocketEvent.WinnerAnnounced -> {
                        countdownJob?.cancel()
                        val userId = _roomState.value.userId
                        val isWinner = event.winnerId == userId
                        _spinState.value = _spinState.value.copy(
                            isSpinRunning = false,
                            winnerId = event.winnerId,
                            activeSurvivors = listOf(event.winnerId),
                            countdownSeconds = 0,
                            statusBanner = if (isWinner) "🏆 CONGRATULATIONS! YOU WON THE SPIN! 🏆"
                                          else "🏆 Winner: ${event.winnerId} 🏆"
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    // ── Room Operations ───────────────────────────────────────────────────────

    fun createRoom(serverUrl: String, roomName: String) {
        viewModelScope.launch {
            _roomState.value = _roomState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = apiService.createRoom(
                    CreateRoomRequest(name = roomName, ownerId = _roomState.value.userId)
                )
                if (response.isSuccessful && response.body()?.data != null) {
                    val room = response.body()!!.data!!
                    _roomState.value = _roomState.value.copy(
                        currentRoom = room,
                        isOwner = true,
                        isLoading = false,
                        infoMessage = "Room '${room.name}' created"
                    )
                    socketManager.connect(serverUrl, room.id, _roomState.value.userId, _roomState.value.username)
                } else {
                    _roomState.value = _roomState.value.copy(
                        isLoading = false,
                        errorMessage = response.body()?.message ?: "Failed to create room"
                    )
                }
            } catch (e: Exception) {
                _roomState.value = _roomState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Network error"
                )
            }
        }
    }

    fun joinRoom(serverUrl: String, roomId: String) {
        viewModelScope.launch {
            _roomState.value = _roomState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = apiService.joinRoom(
                    roomId,
                    JoinRoomRequest(userId = _roomState.value.userId, username = _roomState.value.username)
                )
                if (response.isSuccessful && response.body()?.data != null) {
                    val room = response.body()!!.data!!
                    val isOwner = room.ownerId == _roomState.value.userId
                    _roomState.value = _roomState.value.copy(
                        currentRoom = room,
                        isOwner = isOwner,
                        isLoading = false,
                        infoMessage = "Joined room '${room.name}'"
                    )
                    socketManager.connect(serverUrl, room.id, _roomState.value.userId, _roomState.value.username)
                } else {
                    _roomState.value = _roomState.value.copy(
                        isLoading = false,
                        errorMessage = response.body()?.message ?: "Failed to join room"
                    )
                }
            } catch (e: Exception) {
                _roomState.value = _roomState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Network error"
                )
            }
        }
    }

    /**
     * Leave the current room.
     *
     * We set [isLeaving] = true and call [socketManager.disconnect] BEFORE
     * the API leave request so that any in-flight `room_state` socket events
     * are ignored and don't restore [currentRoom] after we cleared it.
     */
    fun leaveRoom() {
        val room = _roomState.value.currentRoom ?: return
        isLeaving = true

        // Disconnect socket first — prevents stale room_state events
        socketManager.disconnect()

        // Immediately clear room from UI so the screen updates right away
        _roomState.value = _roomState.value.copy(
            currentRoom = null,
            isOwner = false,
            infoMessage = "Left room"
        )
        // Reset spin state too
        _spinState.value = SpinUiState()

        viewModelScope.launch {
            try {
                apiService.leaveRoom(room.id, LeaveRoomRequest(userId = _roomState.value.userId))
            } catch (_: Exception) {
                // Ignore API leave failures — we've already disconnected locally
            } finally {
                isLeaving = false
            }
        }
    }

    fun shareDraft(draft: Draft) {
        val room = _roomState.value.currentRoom
        if (room == null) {
            _roomState.value = _roomState.value.copy(
                errorMessage = "Please join or create a room first to share a voice draft!"
            )
            return
        }

        viewModelScope.launch {
            try {
                val req = ShareDraftRequest(
                    userId = _roomState.value.userId,
                    draftId = draft.id,
                    title = draft.title,
                    durationMs = draft.durationMs,
                    effectApplied = draft.effectApplied
                )
                val response = apiService.shareDraft(room.id, req)
                if (response.isSuccessful && response.body()?.success == true) {
                    val current = _roomState.value.currentRoom
                    if (current != null) {
                        val updatedDrafts = current.sharedDrafts.toMutableList()
                        if (updatedDrafts.none { it.id == draft.id }) {
                            updatedDrafts.add(0, draft)
                        }
                        _roomState.value = _roomState.value.copy(
                            currentRoom = current.copy(sharedDrafts = updatedDrafts),
                            infoMessage = "Draft '${draft.title}' shared with room!"
                        )
                    } else {
                        _roomState.value = _roomState.value.copy(
                            infoMessage = "Draft '${draft.title}' shared with room!"
                        )
                    }
                } else {
                    val errMsg = response.body()?.message ?: "Failed to share draft"
                    _roomState.value = _roomState.value.copy(errorMessage = errMsg)
                }
            } catch (e: Exception) {
                _roomState.value = _roomState.value.copy(errorMessage = e.localizedMessage ?: "Failed to share draft")
            }
        }
    }

    fun clearMessages() {
        _roomState.value = _roomState.value.copy(errorMessage = null, infoMessage = null)
    }

    // ── Spin Operations ───────────────────────────────────────────────────────

    fun triggerStartSpin() {
        val roomId = _roomState.value.currentRoom?.id
        if (roomId == null) {
            _spinState.value = _spinState.value.copy(errorMessage = "Must be inside an active room to spin")
            return
        }
        viewModelScope.launch {
            try {
                val response = apiService.startSpin(roomId, StartSpinRequest(userId = _roomState.value.userId))
                if (!response.isSuccessful) {
                    val err = response.errorBody()?.string() ?: "Failed to start spin"
                    _spinState.value = _spinState.value.copy(errorMessage = "Cannot start spin: $err")
                }
            } catch (e: Exception) {
                _spinState.value = _spinState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = 5
            while (isActive && remaining > 0) {
                _spinState.value = _spinState.value.copy(countdownSeconds = remaining)
                delay(1000)
                remaining--
            }
            if (isActive) {
                _spinState.value = _spinState.value.copy(countdownSeconds = 0)
            }
        }
    }
}
