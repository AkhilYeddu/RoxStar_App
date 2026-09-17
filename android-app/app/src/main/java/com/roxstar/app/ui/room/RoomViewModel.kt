package com.roxstar.app.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roxstar.app.data.models.Draft
import com.roxstar.app.data.models.Room
import com.roxstar.app.data.models.RoomMember
import com.roxstar.app.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

class RoomViewModel(
    private val apiService: ApiService,
    private val socketManager: SocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    init {
        observeSocketEvents()
    }

    fun setUserInfo(userId: String, username: String) {
        _uiState.value = _uiState.value.copy(userId = userId, username = username)
    }

    private fun observeSocketEvents() {
        viewModelScope.launch {
            socketManager.connectionState.collect { connected ->
                _uiState.value = _uiState.value.copy(isConnected = connected)
            }
        }

        viewModelScope.launch {
            socketManager.eventsFlow.collect { event ->
                when (event) {
                    is SocketEvent.UserJoined -> {
                        val currentRoom = _uiState.value.currentRoom ?: return@collect
                        val updatedMembers = currentRoom.participants.toMutableList()
                        val existingIndex = updatedMembers.indexOfFirst { it.userId == event.user.userId }
                        if (existingIndex >= 0) {
                            updatedMembers[existingIndex] = event.user
                        } else {
                            updatedMembers.add(event.user)
                        }
                        _uiState.value = _uiState.value.copy(
                            currentRoom = currentRoom.copy(participants = updatedMembers),
                            infoMessage = "${event.user.username} joined the room"
                        )
                    }

                    is SocketEvent.UserLeft -> {
                        val currentRoom = _uiState.value.currentRoom ?: return@collect
                        val updatedMembers = currentRoom.participants.filter { it.userId != event.userId }
                        _uiState.value = _uiState.value.copy(
                            currentRoom = currentRoom.copy(participants = updatedMembers),
                            infoMessage = "User left (${event.reason})"
                        )
                    }

                    is SocketEvent.DraftShared -> {
                        val currentRoom = _uiState.value.currentRoom ?: return@collect
                        val updatedDrafts = currentRoom.sharedDrafts.toMutableList()
                        updatedDrafts.add(0, event.draft)
                        _uiState.value = _uiState.value.copy(
                            currentRoom = currentRoom.copy(sharedDrafts = updatedDrafts),
                            infoMessage = "New draft shared: ${event.draft.title} by ${event.sharedBy}"
                        )
                    }

                    is SocketEvent.RoomStateUpdated -> {
                        val isOwner = event.room.ownerId == _uiState.value.userId
                        _uiState.value = _uiState.value.copy(
                            currentRoom = event.room,
                            isOwner = isOwner,
                            infoMessage = "Room state synchronized"
                        )
                    }

                    is SocketEvent.ErrorOccurred -> {
                        _uiState.value = _uiState.value.copy(errorMessage = event.message)
                    }

                    else -> Unit
                }
            }
        }
    }

    fun createRoom(serverUrl: String, roomName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = apiService.createRoom(
                    CreateRoomRequest(
                        name = roomName,
                        ownerId = _uiState.value.userId
                    )
                )
                if (response.isSuccessful && response.body()?.data != null) {
                    val room = response.body()!!.data!!
                    _uiState.value = _uiState.value.copy(
                        currentRoom = room,
                        isOwner = true,
                        isLoading = false,
                        infoMessage = "Room '${room.name}' created"
                    )
                    socketManager.connect(serverUrl, room.id, _uiState.value.userId, _uiState.value.username)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.body()?.message ?: "Failed to create room"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Network error"
                )
            }
        }
    }

    fun joinRoom(serverUrl: String, roomId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = apiService.joinRoom(
                    roomId,
                    JoinRoomRequest(userId = _uiState.value.userId, username = _uiState.value.username)
                )
                if (response.isSuccessful && response.body()?.data != null) {
                    val room = response.body()!!.data!!
                    val isOwner = room.ownerId == _uiState.value.userId
                    _uiState.value = _uiState.value.copy(
                        currentRoom = room,
                        isOwner = isOwner,
                        isLoading = false,
                        infoMessage = "Joined room '${room.name}'"
                    )
                    socketManager.connect(serverUrl, room.id, _uiState.value.userId, _uiState.value.username)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.body()?.message ?: "Failed to join room"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Network error"
                )
            }
        }
    }

    fun leaveRoom() {
        val room = _uiState.value.currentRoom ?: return
        viewModelScope.launch {
            try {
                apiService.leaveRoom(room.id, LeaveRoomRequest(userId = _uiState.value.userId))
            } catch (e: Exception) {
                // Ignore API leave failures during exit
            } finally {
                socketManager.disconnect()
                _uiState.value = _uiState.value.copy(
                    currentRoom = null,
                    isOwner = false,
                    infoMessage = "Left room"
                )
            }
        }
    }

    fun shareDraft(draft: Draft) {
        val room = _uiState.value.currentRoom ?: return
        viewModelScope.launch {
            try {
                val req = ShareDraftRequest(
                    userId = _uiState.value.userId,
                    draftId = draft.id,
                    title = draft.title,
                    durationMs = draft.durationMs,
                    effectApplied = draft.effectApplied
                )
                val response = apiService.shareDraft(room.id, req)
                if (response.isSuccessful && response.body()?.data != null) {
                    _uiState.value = _uiState.value.copy(
                        currentRoom = response.body()!!.data,
                        infoMessage = "Draft '${draft.title}' shared with room!"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "Failed to share draft")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }
}
