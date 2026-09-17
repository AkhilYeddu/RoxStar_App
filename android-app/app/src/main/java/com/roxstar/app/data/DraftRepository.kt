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

    fun playDraft(draft: Draft): Boolean {
        if (!draft.existsLocally) return false
        stopPlayback()
        val success = NativeAudioEngine.startPlayback(draft.filePath)
        if (success) {
            activePlaybackDraftId = draft.id
        }
        return success
    }

    fun stopPlayback(): Boolean {
        activePlaybackDraftId = null
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
