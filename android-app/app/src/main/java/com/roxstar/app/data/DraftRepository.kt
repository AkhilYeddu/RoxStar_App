package com.roxstar.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.roxstar.app.audio.EffectType
import com.roxstar.app.audio.NativeAudioEngine
import com.roxstar.app.data.models.Draft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.util.UUID

class DraftRepository(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("roxstar_drafts_prefs", Context.MODE_PRIVATE)
    private val _draftsFlow = MutableStateFlow<List<Draft>>(emptyList())
    val draftsFlow: StateFlow<List<Draft>> = _draftsFlow.asStateFlow()

    private var activePlaybackDraftId: String? = null

    init {
        loadDrafts()
    }

    private fun getDraftsDirectory(): File {
        val dir = File(context.filesDir, "drafts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun generateNewDraftPath(): String {
        val fileName = "draft_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.wav"
        return File(getDraftsDirectory(), fileName).absolutePath
    }

    suspend fun saveDraft(title: String, filePath: String, durationMs: Long, effect: EffectType): Draft = withContext(Dispatchers.IO) {
        val draft = Draft(
            id = UUID.randomUUID().toString(),
            title = if (title.isNotBlank()) title else "Voice Draft #${_draftsFlow.value.size + 1}",
            filePath = filePath,
            durationMs = durationMs,
            createdAt = System.currentTimeMillis(),
            effectApplied = effect.name
        )

        val currentList = _draftsFlow.value.toMutableList()
        currentList.add(0, draft)
        persistDrafts(currentList)
        _draftsFlow.value = currentList
        draft
    }

    suspend fun deleteDraft(draftId: String): Boolean = withContext(Dispatchers.IO) {
        val currentList = _draftsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == draftId }
        if (index != -1) {
            val draft = currentList[index]
            // Stop playback if playing this draft
            if (activePlaybackDraftId == draftId) {
                stopPlayback()
            }
            // Delete local file
            val file = File(draft.filePath)
            if (file.exists()) {
                file.delete()
            }
            currentList.removeAt(index)
            persistDrafts(currentList)
            _draftsFlow.value = currentList
            true
        } else {
            false
        }
    }

    private var previewTrack: AudioTrack? = null

    fun playDraft(draft: Draft): Boolean {
        stopPlayback()
        // 1. Check if draft itself has an existing local file
        var path = draft.filePath
        if (path.isBlank() || !File(path).exists()) {
            // 2. Look up in local saved drafts by ID or title
            val localMatch = _draftsFlow.value.find { it.id == draft.id || it.title == draft.title }
            if (localMatch != null && localMatch.filePath.isNotBlank() && File(localMatch.filePath).exists()) {
                path = localMatch.filePath
            }
        }

        if (path.isNotBlank() && File(path).exists()) {
            val success = NativeAudioEngine.startPlayback(path)
            if (success) {
                activePlaybackDraftId = draft.id
            }
            return success
        }

        // 3. If remote or no local file, synthesize preview tone using AudioTrack
        playSynthesizedPreview(draft)
        activePlaybackDraftId = draft.id
        return true
    }

    private fun playSynthesizedPreview(draft: Draft) {
        Thread {
            try {
                val sampleRate = 44100
                val durationSec = Math.max(1.0, Math.min(draft.durationMs / 1000.0, 8.0))
                val numSamples = (durationSec * sampleRate).toInt()
                val buffer = ShortArray(numSamples)

                val pitchFactor = when (draft.effectApplied.uppercase()) {
                    "HELIUM" -> 1.75
                    "DEMONIC" -> 0.58
                    else -> 1.0
                }
                val baseFreq = 440.0 * pitchFactor

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    val envelope = Math.min(1.0, Math.min(time * 8.0, (durationSec - time) * 8.0))
                    val sample = Math.sin(2.0 * Math.PI * baseFreq * time) * 0.4 * envelope
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                previewTrack = track
            } catch (_: Exception) {}
        }.start()
    }

    fun stopPlayback(): Boolean {
        activePlaybackDraftId = null
        try {
            previewTrack?.stop()
            previewTrack?.release()
            previewTrack = null
        } catch (_: Exception) {}
        return NativeAudioEngine.stopPlayback()
    }

    fun getActivePlayingDraftId(): String? = activePlaybackDraftId

    private fun loadDrafts() {
        val json = prefs.getString("drafts_list_key", null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<Draft>>() {}.type
                val savedDrafts: List<Draft> = gson.fromJson(json, type)
                // Filter out any where local file was removed
                _draftsFlow.value = savedDrafts.filter { it.existsLocally }
            } catch (e: Exception) {
                _draftsFlow.value = emptyList()
            }
        }
    }

    private fun persistDrafts(drafts: List<Draft>) {
        val json = gson.toJson(drafts)
        prefs.edit().putString("drafts_list_key", json).apply()
    }
}
