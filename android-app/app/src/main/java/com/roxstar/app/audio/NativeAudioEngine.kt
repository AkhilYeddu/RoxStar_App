package com.roxstar.app.audio

enum class EffectType(val value: Int) {
    NONE(0),
    ECHO(1),
    REVERB(2),
    PITCH_SHIFT(3),
    HELIUM(4),
    DEMONIC(5)
}

enum class EngineState(val value: Int) {
    IDLE(0),
    RECORDING(1),
    PLAYING(2),
    ERROR(3);

    companion object {
        fun fromInt(value: Int) = entries.find { it.value == value } ?: IDLE
    }
}

object NativeAudioEngine {
    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("roxstar_audio_native")
            isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("Failed to load roxstar_audio_native: ${e.message}")
            isLibraryLoaded = false
        }
    }

    fun isNativeLoaded(): Boolean = isLibraryLoaded

    fun startRecording(outputPath: String, effect: EffectType = EffectType.NONE): Boolean {
        if (!isLibraryLoaded) return false
        return nativeStartRecording(outputPath, effect.value)
    }

    fun stopRecording(): Boolean {
        if (!isLibraryLoaded) return false
        return nativeStopRecording()
    }

    fun cancelRecording(): Boolean {
        if (!isLibraryLoaded) return false
        return nativeCancelRecording()
    }

    fun startPlayback(inputPath: String): Boolean {
        if (!isLibraryLoaded) return false
        return nativeStartPlayback(inputPath)
    }

    fun stopPlayback(): Boolean {
        if (!isLibraryLoaded) return false
        return nativeStopPlayback()
    }

    fun setEchoParams(delayMs: Float, feedback: Float, wetMix: Float) {
        if (isLibraryLoaded) {
            nativeSetEchoParams(delayMs, feedback, wetMix)
        }
    }

    fun setReverbParams(roomSize: Float, damping: Float, wetMix: Float) {
        if (isLibraryLoaded) {
            nativeSetReverbParams(roomSize, damping, wetMix)
        }
    }

    fun getState(): EngineState {
        if (!isLibraryLoaded) return EngineState.IDLE
        return EngineState.fromInt(nativeGetState())
    }

    fun getRecordingDuration(): Long {
        if (!isLibraryLoaded) return 0L
        return nativeGetRecordingDuration()
    }

    // JNI Native methods
    private external fun nativeStartRecording(outputPath: String, effectOrdinal: Int): Boolean
    private external fun nativeStopRecording(): Boolean
    private external fun nativeCancelRecording(): Boolean
    private external fun nativeStartPlayback(inputPath: String): Boolean
    private external fun nativeStopPlayback(): Boolean
    private external fun nativeSetEchoParams(delayMs: Float, feedback: Float, wetMix: Float)
    private external fun nativeSetReverbParams(roomSize: Float, damping: Float, wetMix: Float)
    private external fun nativeGetState(): Int
    private external fun nativeGetRecordingDuration(): Long
}
