package com.roxstar.app.data.models

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Draft(
    val id: String = "",
    val title: String = "",
    val filePath: String = "",
    val durationMs: Long = 0L,
    val createdAt: Any? = null,
    val effectApplied: String = "NONE",
    val isUploaded: Boolean = false
) {
    val formattedDate: String
        get() {
            return try {
                val millis = when (createdAt) {
                    is Number -> createdAt.toLong()
                    is String -> System.currentTimeMillis()
                    else -> System.currentTimeMillis()
                }
                val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                sdf.format(Date(millis))
            } catch (_: Exception) {
                "Recent"
            }
        }

    val formattedDuration: String
        get() {
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / (1000 * 60)) % 60
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

    val existsLocally: Boolean
        get() = filePath.isNotBlank() && File(filePath).exists()
}

data class User(
    val id: String = "",
    val username: String = "",
    val avatar: String = ""
)

data class RoomMember(
    val userId: String = "",
    val username: String = "",
    val role: String = "MEMBER", // OWNER or MEMBER
    val connectionStatus: String = "CONNECTED"
)

data class Room(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val status: String = "IDLE", // IDLE, IN_SPIN
    val participants: List<RoomMember> = emptyList(),
    val sharedDrafts: List<Draft> = emptyList()
)

data class SpinState(
    val spinId: String = "",
    val roomId: String = "",
    val status: String = "WAITING", // WAITING, RUNNING, COMPLETED, ABORTED
    val round: Int = 0,
    val activeParticipants: List<String> = emptyList(),
    val eliminatedParticipants: List<String> = emptyList(),
    val winnerId: String? = null,
    val lastEliminatedId: String? = null,
    val nextEliminationCountdownSeconds: Int = 5
)
