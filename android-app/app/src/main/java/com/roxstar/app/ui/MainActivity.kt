package com.roxstar.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.roxstar.app.RoxStarApplication
import com.roxstar.app.audio.EffectType
import com.roxstar.app.audio.EngineState
import com.roxstar.app.data.models.Draft
import com.roxstar.app.databinding.ActivityMainBinding
import com.roxstar.app.ui.audio.AudioStudioViewModel
import com.roxstar.app.ui.room.RoomSessionViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as RoxStarApplication

        audioViewModel = AudioStudioViewModel(app.draftRepository)
        roomSessionViewModel = RoomSessionViewModel(app.apiService, app.socketManager)

        roomSessionViewModel.setUserInfo(currentUserId, currentUsername)
        binding.tvUserInfo.text = "👤 User: $currentUsername (ID: $currentUserId)"

        setupListeners()
        checkMicrophonePermission()
        observeViewModels()
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun setupListeners() {
        // Effect selector
        binding.rgEffects.setOnCheckedChangeListener { _, checkedId ->
            val effect = when (checkedId) {
                binding.rbEcho.id -> EffectType.ECHO
                binding.rbReverb.id -> EffectType.REVERB
                binding.rbHelium.id -> EffectType.HELIUM
                binding.rbDemonic.id -> EffectType.DEMONIC
                else -> EffectType.NONE
            }
            audioViewModel.selectEffect(effect)
        }

        // Record controls
        binding.btnRecord.setOnClickListener {
            val title = binding.etDraftTitle.text.toString().trim()
            val effect = when (binding.rgEffects.checkedRadioButtonId) {
                binding.rbEcho.id -> EffectType.ECHO
                binding.rbReverb.id -> EffectType.REVERB
                binding.rbHelium.id -> EffectType.HELIUM
                binding.rbDemonic.id -> EffectType.DEMONIC
                else -> EffectType.NONE
            }
            onStartRecording(title, effect)
        }

        binding.btnStopRecord.setOnClickListener {
            val title = binding.etDraftTitle.text.toString().trim()
            onStopRecording(title)
        }

        // Room controls
        binding.btnCreateRoom.setOnClickListener {
            val name = binding.etRoomInput.text.toString().trim()
            if (name.isNotEmpty()) {
                onCreateRoom(name)
            } else {
                Toast.makeText(this, "Enter room name", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnJoinRoom.setOnClickListener {
            val roomId = binding.etRoomInput.text.toString().trim()
            if (roomId.isNotEmpty()) {
                onJoinRoom(roomId)
            } else {
                Toast.makeText(this, "Enter Room ID or Name", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLeaveRoom.setOnClickListener {
            onLeaveRoom()
        }

        // Spin control
        binding.btnStartSpin.setOnClickListener {
            onStartSpin()
        }
    }

    private fun observeViewModels() {
        lifecycleScope.launch {
            audioViewModel.uiState.collectLatest { state ->
                binding.tvAudioStatus.text = state.statusMessage
                val sec = state.recordingDurationMs / 1000.0
                binding.tvRecordTimer.text = String.format("%.1fs", sec)

                val isRecording = state.engineState == EngineState.RECORDING
                binding.btnRecord.isEnabled = !isRecording
                binding.btnStopRecord.isEnabled = isRecording
            }
        }

        lifecycleScope.launch {
            audioViewModel.draftsList.collectLatest { drafts ->
                renderDraftsList(drafts)
            }
        }

        lifecycleScope.launch {
            roomSessionViewModel.roomState.collectLatest { state ->
                binding.tvConnectionStatus.text = if (state.isConnected) "⚡ Connected" else "⚡ Offline"
                binding.tvConnectionStatus.setTextColor(
                    if (state.isConnected) android.graphics.Color.parseColor("#10B981")
                    else android.graphics.Color.parseColor("#94A3B8")
                )

                if (state.currentRoom != null) {
                    val room = state.currentRoom
                    binding.tvActiveRoomInfo.text = "🟢 Room: ${room.name} (${room.roomId})\nMembers: ${room.participants.size}/${room.maxParticipants} | Role: ${if (state.isOwner) "Owner 👑" else "Member"}"
                    binding.btnStartSpin.isEnabled = state.isOwner
                } else {
                    binding.tvActiveRoomInfo.text = "Not in any room. Enter room ID/name above to join."
                    binding.btnStartSpin.isEnabled = false
                }

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
                binding.tvSpinStatus.text = "🎡 Spin Status: ${state.statusBanner}"
                binding.tvSpinTimer.text = "⏱️ Countdown: ${state.countdownSeconds}s"
                binding.tvSurvivors.text = "👥 Active Contenders (${state.activeSurvivors.size}): ${state.activeSurvivors.joinToString(", ")}"

                state.errorMessage?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderDraftsList(drafts: List<Draft>) {
        binding.llDraftsContainer.removeAllViews()
        if (drafts.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No recorded voice drafts yet."
                setTextColor(android.graphics.Color.parseColor("#64748B"))
                textSize = 12f
                setPadding(0, 8, 0, 8)
            }
            binding.llDraftsContainer.addView(emptyTv)
            return
        }

        for (draft in drafts) {
            val cardView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }
            }

            val titleTv = TextView(this).apply {
                text = "${draft.title}\n(${draft.effectApplied} | ${(draft.durationMs / 1000.0).format(1)}s)"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val playBtn = Button(this).apply {
                text = "▶ Play"
                textSize = 11f
                setOnClickListener { onPlayDraft(draft) }
            }

            val shareBtn = Button(this).apply {
                text = "🚀 Share"
                textSize = 11f
                setOnClickListener { onShareDraftWithRoom(draft) }
            }

            val deleteBtn = Button(this).apply {
                text = "🗑️"
                textSize = 11f
                setOnClickListener { onDeleteDraft(draft.id) }
            }

            cardView.addView(titleTv)
            cardView.addView(playBtn)
            cardView.addView(shareBtn)
            cardView.addView(deleteBtn)
            binding.llDraftsContainer.addView(cardView)
        }
    }

    private fun Double.format(digits: Int) = String.format("%.${digits}f", this)

    // ── Audio Studio Operations ───────────────────────────────────────────────

    fun onStartRecording(title: String, effect: EffectType) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            audioViewModel.selectEffect(effect)
            val success = audioViewModel.startRecording(if (title.isEmpty()) "Voice Take" else title)
            if (success) {
                Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show()
            }
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun onStopRecording(title: String) {
        audioViewModel.stopRecording(if (title.isEmpty()) "Voice Take" else title)
        Toast.makeText(this, "Draft saved!", Toast.LENGTH_SHORT).show()
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
