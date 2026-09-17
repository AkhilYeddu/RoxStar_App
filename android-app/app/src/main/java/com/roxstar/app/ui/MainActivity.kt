package com.roxstar.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.roxstar.app.RoxStarApplication
import com.roxstar.app.audio.EffectType
import com.roxstar.app.data.models.Draft
import com.roxstar.app.ui.audio.AudioStudioViewModel
import com.roxstar.app.ui.room.RoomSessionViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var app: RoxStarApplication
    private lateinit var audioViewModel: AudioStudioViewModel
    private lateinit var roomSessionViewModel: RoomSessionViewModel

    private var currentUserId: String = UUID.randomUUID().toString().take(6)
    private var currentUsername: String = "User_$currentUserId"

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted! Ready to record.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Microphone permission is required to record voice drafts.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as RoxStarApplication

        audioViewModel = AudioStudioViewModel(app.draftRepository)
        roomSessionViewModel = RoomSessionViewModel(app.apiService, app.socketManager)

        roomSessionViewModel.setUserInfo(currentUserId, currentUsername)

        checkMicrophonePermission()
        observeViewModels()
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun observeViewModels() {
        lifecycleScope.launch {
            audioViewModel.uiState.collectLatest { state ->
                // Handles UI updates for recording/playback state
            }
        }

        lifecycleScope.launch {
            audioViewModel.draftsList.collectLatest { drafts ->
                // Handles Draft list updates
            }
        }

        lifecycleScope.launch {
            roomSessionViewModel.roomState.collectLatest { state ->
                state.errorMessage?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    roomSessionViewModel.clearMessages()
                }
                state.infoMessage?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    roomSessionViewModel.clearMessages()
                }
            }
        }

        lifecycleScope.launch {
            roomSessionViewModel.spinState.collectLatest { state ->
                state.errorMessage?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Audio Studio Operations ───────────────────────────────────────────────

    fun onStartRecording(title: String, effect: EffectType) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            audioViewModel.selectEffect(effect)
            audioViewModel.startRecording(title)
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun onStopRecording(title: String) {
        audioViewModel.stopRecording(title)
    }

    fun onCancelRecording() {
        audioViewModel.cancelRecording()
    }

    fun onPlayDraft(draft: Draft) {
        audioViewModel.playDraft(draft)
    }

    fun onDeleteDraft(draftId: String) {
        audioViewModel.deleteDraft(draftId)
    }

    // ── Room Operations ───────────────────────────────────────────────────────

    fun onCreateRoom(roomName: String) {
        roomSessionViewModel.createRoom(app.serverBaseUrl, roomName)
    }

    fun onJoinRoom(roomId: String) {
        roomSessionViewModel.joinRoom(app.serverBaseUrl, roomId)
    }

    fun onLeaveRoom() {
        roomSessionViewModel.leaveRoom()
    }

    fun onShareDraftWithRoom(draft: Draft) {
        roomSessionViewModel.shareDraft(draft)
    }

    // ── Spin Wheel Operations ─────────────────────────────────────────────────

    fun onStartSpin() {
        roomSessionViewModel.triggerStartSpin()
    }
}
