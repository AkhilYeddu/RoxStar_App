package com.roxstar.app.data

import android.content.Context
import android.media.MediaPlayer
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
import java.io.File
import java.util.UUID

class DraftRepository(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("roxstar_drafts_prefs", Context.MODE_PRIVATE)
    private val _draftsFlow = MutableStateFlow<List<Draft>>(emptyList())
    val draftsFlow: StateFlow<List<Draft>> = _draftsFlow.asStateFlow()

    private var activePlaybackDraftId: String? = null

    /** MediaPlayer used for downloaded/shared drafts to avoid sample-rate mismatch noise. */
    private var mediaPlayer: MediaPlayer? = null

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

    /**
     * Play a draft file.
     *
     * - Own (locally recorded) drafts → NativeAudioEngine (Oboe), preserving effects.
     * - Shared/downloaded drafts from other users → Android MediaPlayer, which reads
     *   the WAV header to determine the exact sample rate and channel count, preventing
     *   the white-noise artifact that occurs when the Oboe engine plays audio at a
     *   mismatched rate.
     */
    fun playDraft(draft: Draft, onStart: (() -> Unit)? = null): Boolean {
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

        // 3. Check if cached from an earlier download
        val cachedFile = File(getDraftsDirectory(), "shared_${draft.id}.wav")
        if ((path.isBlank() || !File(path).exists()) && cachedFile.exists() && cachedFile.length() > 44) {
            path = cachedFile.absolutePath
        }

        if (path.isNotBlank() && File(path).exists()) {
            val isOwnRecording = _draftsFlow.value.any { it.id == draft.id }
            return if (isOwnRecording) {
                // Use native Oboe engine for own locally-recorded drafts
                val success = NativeAudioEngine.startPlayback(path)
                if (success) {
                    activePlaybackDraftId = draft.id
                    onStart?.invoke()
                }
                success
            } else {
                // Use MediaPlayer for shared/downloaded drafts — avoids sample-rate mismatch noise
                playWithMediaPlayer(path, draft.id, onStart)
                true
            }
        }

        // 4. Not yet cached — download from server then play with MediaPlayer
        downloadAndPlayWithMediaPlayer(draft, onStart)
        activePlaybackDraftId = draft.id
        return true
    }

    /**
     * Play a WAV file with Android MediaPlayer. MediaPlayer reads the WAV header and
     * correctly configures its decoder, so there is no risk of sample-rate mismatch.
     */
    private fun playWithMediaPlayer(filePath: String, draftId: String, onStart: (() -> Unit)? = null) {
        try {
            releaseMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    activePlaybackDraftId = null
                    releaseMediaPlayer()
                }
                setOnErrorListener { _, _, _ ->
                    activePlaybackDraftId = null
                    releaseMediaPlayer()
                    false
                }
                start()
            }
            activePlaybackDraftId = draftId
            onStart?.invoke()
        } catch (_: Exception) {
            activePlaybackDraftId = null
            releaseMediaPlayer()
        }
    }

    /**
     * Download a shared draft audio file from the server and play it via MediaPlayer.
     * Runs the network request on a background thread, then switches to the main thread
     * for MediaPlayer initialization (required by Android).
     */
    private fun downloadAndPlayWithMediaPlayer(draft: Draft, onStart: (() -> Unit)? = null) {
        Thread {
            try {
                var urlStr = draft.filePath
                if (urlStr.isBlank()) {
                    urlStr = "/api/drafts/${draft.id}/audio"
                }
                if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                    val base = "https://roxstar-app.azurewebsites.net".trimEnd('/')
                    val sub = if (urlStr.startsWith("/")) urlStr else "/$urlStr"
                    urlStr = base + sub
                }

                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                conn.connect()

                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.readBytes()
                    if (bytes.size > 44) {
                        val cacheFile = File(getDraftsDirectory(), "shared_${draft.id}.wav")
                        cacheFile.writeBytes(bytes)

                        // MediaPlayer must be set up on the main thread
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            playWithMediaPlayer(cacheFile.absolutePath, draft.id, onStart)
                        }
                    }
                }
            } catch (_: Exception) {
                activePlaybackDraftId = null
            }
        }.start()
    }

    fun stopPlayback(): Boolean {
        activePlaybackDraftId = null
        releaseMediaPlayer()
        return NativeAudioEngine.stopPlayback()
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
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
