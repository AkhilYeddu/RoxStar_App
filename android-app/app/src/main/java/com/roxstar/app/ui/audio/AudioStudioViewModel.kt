package com.roxstar.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roxstar.app.audio.EffectType
import com.roxstar.app.audio.EngineState
import com.roxstar.app.audio.NativeAudioEngine
import com.roxstar.app.data.DraftRepository
import com.roxstar.app.data.models.Draft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioStudioUiState(
    val engineState: EngineState = EngineState.IDLE,
    val selectedEffect: EffectType = EffectType.NONE,
    val recordingDurationMs: Long = 0L,
    val echoDelayMs: Float = 250.0f,
    val echoFeedback: Float = 0.5f,
    val echoWetMix: Float = 0.5f,
    val reverbRoomSize: Float = 0.7f,
    val activePlayingDraftId: String? = null,
    val statusMessage: String = "Ready to record voice draft"
)

class AudioStudioViewModel(private val repository: DraftRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioStudioUiState())
    val uiState: StateFlow<AudioStudioUiState> = _uiState.asStateFlow()

    val draftsList: StateFlow<List<Draft>> = repository.draftsFlow

    private var currentRecordingPath: String? = null
    private var durationTimerJob: Job? = null

    init {
        updateEngineState()
    }

    fun selectEffect(effect: EffectType) {
        _uiState.value = _uiState.value.copy(selectedEffect = effect)
        NativeAudioEngine.setEchoParams(
            _uiState.value.echoDelayMs,
            _uiState.value.echoFeedback,
            _uiState.value.echoWetMix
        )
        NativeAudioEngine.setReverbParams(
            _uiState.value.reverbRoomSize,
            0.25f,
            0.4f
        )
    }

    fun setEchoDelay(delayMs: Float) {
        _uiState.value = _uiState.value.copy(echoDelayMs = delayMs)
        NativeAudioEngine.setEchoParams(delayMs, _uiState.value.echoFeedback, _uiState.value.echoWetMix)
    }

    fun setEchoFeedback(feedback: Float) {
        _uiState.value = _uiState.value.copy(echoFeedback = feedback)
        NativeAudioEngine.setEchoParams(_uiState.value.echoDelayMs, feedback, _uiState.value.echoWetMix)
    }

    fun startRecording(draftTitle: String? = null): Boolean {
        if (_uiState.value.engineState == EngineState.RECORDING) return false

        val outputPath = repository.generateNewDraftPath()
        currentRecordingPath = outputPath

        val success = NativeAudioEngine.startRecording(outputPath, _uiState.value.selectedEffect)
        if (success) {
            _uiState.value = _uiState.value.copy(
                engineState = EngineState.RECORDING,
                recordingDurationMs = 0L,
                statusMessage = "Recording with ${_uiState.value.selectedEffect.name} effect..."
            )
            startDurationTimer()
        } else {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Failed to start recording. Check microphone permission or Oboe stream."
            )
        }
        return success
    }

    fun stopRecording(draftTitle: String = "") {
        if (_uiState.value.engineState != EngineState.RECORDING) return

        durationTimerJob?.cancel()
        val duration = NativeAudioEngine.getRecordingDuration()
        val success = NativeAudioEngine.stopRecording()

        if (success && currentRecordingPath != null) {
            val path = currentRecordingPath!!
            viewModelScope.launch {
                repository.saveDraft(
                    title = draftTitle,
                    filePath = path,
                    durationMs = duration,
                    effect = _uiState.value.selectedEffect
                )
                _uiState.value = _uiState.value.copy(
                    engineState = EngineState.IDLE,
                    recordingDurationMs = 0L,
                    statusMessage = "Draft saved successfully!"
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(
                engineState = EngineState.IDLE,
                statusMessage = "Recording stopped with error."
            )
        }
        currentRecordingPath = null
    }

    fun cancelRecording() {
        if (_uiState.value.engineState != EngineState.RECORDING) return

        durationTimerJob?.cancel()
        NativeAudioEngine.cancelRecording()
        currentRecordingPath = null
        _uiState.value = _uiState.value.copy(
            engineState = EngineState.IDLE,
            recordingDurationMs = 0L,
            statusMessage = "Recording cancelled and temporary file discarded."
        )
    }

    fun playDraft(draft: Draft) {
        val success = repository.playDraft(draft)
        if (success) {
            _uiState.value = _uiState.value.copy(
                engineState = EngineState.PLAYING,
                activePlayingDraftId = draft.id,
                statusMessage = "Playing: ${draft.title}"
            )
        } else {
            _uiState.value = _uiState.value.copy(statusMessage = "Failed to play draft file.")
        }
    }

    fun stopPlayback() {
        repository.stopPlayback()
        _uiState.value = _uiState.value.copy(
            engineState = EngineState.IDLE,
            activePlayingDraftId = null,
            statusMessage = "Playback stopped"
        )
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            repository.deleteDraft(draftId)
            if (_uiState.value.activePlayingDraftId == draftId) {
                stopPlayback()
            }
            _uiState.value = _uiState.value.copy(statusMessage = "Draft deleted.")
        }
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(200)
                val dur = NativeAudioEngine.getRecordingDuration()
                _uiState.value = _uiState.value.copy(recordingDurationMs = dur)
            }
        }
    }

    private fun updateEngineState() {
        val state = NativeAudioEngine.getState()
        _uiState.value = _uiState.value.copy(engineState = state)
    }
}
