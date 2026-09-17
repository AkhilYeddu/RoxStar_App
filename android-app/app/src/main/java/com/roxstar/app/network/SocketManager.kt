package com.roxstar.app.network

import com.google.gson.Gson
import com.roxstar.app.data.models.Draft
import com.roxstar.app.data.models.Room
import com.roxstar.app.data.models.RoomMember
import com.roxstar.app.data.models.SpinState
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

sealed class SocketEvent {
    data class UserJoined(val user: RoomMember) : SocketEvent()
    data class UserLeft(val userId: String, val reason: String?) : SocketEvent()
    data class DraftShared(val draft: Draft, val sharedBy: String) : SocketEvent()
    data class SpinStarted(val spinState: SpinState) : SocketEvent()
    data class UserEliminated(val eliminatedUserId: String, val remainingUsers: List<String>, val round: Int) : SocketEvent()
    data class WinnerAnnounced(val winnerId: String, val spinState: SpinState) : SocketEvent()
    data class RoomStateUpdated(val room: Room) : SocketEvent()
    data class ErrorOccurred(val message: String) : SocketEvent()
}

class SocketManager {
    private var socket: Socket? = null
    private val gson = Gson()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _eventsFlow = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val eventsFlow: SharedFlow<SocketEvent> = _eventsFlow.asSharedFlow()

    fun connect(serverUrl: String, roomId: String, userId: String, username: String) {
        disconnect()

        val options = IO.Options().apply {
            reconnection = true
            reconnectionAttempts = 10
            reconnectionDelay = 1000
            timeout = 10000
            query = "roomId=$roomId&userId=$userId&username=$username"
        }

        try {
            socket = IO.socket(serverUrl, options).apply {
                on(Socket.EVENT_CONNECT) {
                    _connectionState.value = true
                    // Request current authoritative room state upon connect/reconnect
                    val payload = JSONObject().apply {
                        put("roomId", roomId)
                        put("userId", userId)
                    }
                    emit("get_room_state", payload)
                }

                on(Socket.EVENT_DISCONNECT) {
                    _connectionState.value = false
                }

                on("user_joined") { args ->
                    if (args.isNotEmpty()) {
                        val member = gson.fromJson(args[0].toString(), RoomMember::class.java)
                        _eventsFlow.tryEmit(SocketEvent.UserJoined(member))
                    }
                }

                on("user_left") { args ->
                    if (args.isNotEmpty()) {
                        val json = JSONObject(args[0].toString())
                        val userIdLeft = json.optString("userId")
                        val reason = json.optString("reason", "left")
                        _eventsFlow.tryEmit(SocketEvent.UserLeft(userIdLeft, reason))
                    }
                }

                on("draft_shared") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val json = JSONObject(args[0].toString())
                            val draftObj = json.optJSONObject("draft")
                            val draft = if (draftObj != null) {
                                Draft(
                                    id = draftObj.optString("id", draftObj.optString("draftId", "")),
                                    title = draftObj.optString("title", "Voice Draft"),
                                    filePath = draftObj.optString("fileUrl", draftObj.optString("filePath", "")),
                                    durationMs = draftObj.optLong("durationMs", 0L),
                                    effectApplied = draftObj.optString("effectApplied", "NONE")
                                )
                            } else {
                                gson.fromJson(json.toString(), Draft::class.java)
                            }
                            val sharedBy = json.optString("sharedBy", "Anonymous")
                            _eventsFlow.tryEmit(SocketEvent.DraftShared(draft, sharedBy))
                        } catch (_: Exception) {}
                    }
                }

                on("spin_started") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val spin = gson.fromJson(args[0].toString(), SpinState::class.java)
                            _eventsFlow.tryEmit(SocketEvent.SpinStarted(spin))
                        } catch (_: Exception) {}
                    }
                }

                on("user_eliminated") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val json = JSONObject(args[0].toString())
                            val eliminatedId = json.optString("eliminatedUserId")
                            val remainingArray = json.optJSONArray("remainingUsers")
                            val remainingList = mutableListOf<String>()
                            if (remainingArray != null) {
                                for (i in 0 until remainingArray.length()) {
                                    remainingList.add(remainingArray.getString(i))
                                }
                            }
                            val round = json.optInt("round", 1)
                            _eventsFlow.tryEmit(SocketEvent.UserEliminated(eliminatedId, remainingList, round))
                        } catch (_: Exception) {}
                    }
                }

                on("winner_announced") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val json = JSONObject(args[0].toString())
                            val winnerId = json.optString("winnerId")
                            val spin = gson.fromJson(json.optJSONObject("spinState")?.toString() ?: "{}", SpinState::class.java)
                            _eventsFlow.tryEmit(SocketEvent.WinnerAnnounced(winnerId, spin))
                        } catch (_: Exception) {}
                    }
                }

                on("room_state") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val room = gson.fromJson(args[0].toString(), Room::class.java)
                            _eventsFlow.tryEmit(SocketEvent.RoomStateUpdated(room))
                        } catch (_: Exception) {}
                    }
                }

                on("error_event") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val json = JSONObject(args[0].toString())
                            val message = json.optString("message", "Unknown error")
                            _eventsFlow.tryEmit(SocketEvent.ErrorOccurred(message))
                        } catch (_: Exception) {}
                    }
                }

                connect()
            }
        } catch (e: Exception) {
            _connectionState.value = false
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionState.value = false
    }
}
