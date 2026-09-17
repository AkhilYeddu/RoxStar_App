#pragma once

#include <string>
#include <memory>
#include <atomic>
#include <vector>
#include <chrono>

#if __ANDROID__ || defined(ROXSTAR_USE_OBOE)
#include <oboe/Oboe.h>
#define OBOE_AVAILABLE 1
#else
#define OBOE_AVAILABLE 0
#endif

#include "effects/AudioEffect.h"
#include "effects/EchoEffect.h"
#include "effects/ReverbEffect.h"
#include "effects/HeliumEffect.h"
#include "effects/DemonicEffect.h"
#include "wav/WavWriter.h"

namespace roxstar {

enum class EngineState {
    IDLE = 0,
    RECORDING = 1,
    PLAYING = 2,
    ERROR = 3
};

#if OBOE_AVAILABLE
class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
#else
class AudioEngine {
#endif
public:
    AudioEngine();
    virtual ~AudioEngine();

    // Lifecycle and Recording Controls
    bool startRecording(const std::string& outputWavPath, EffectType effect = EffectType::NONE);
    bool stopRecording();
    bool cancelRecording();

    // Playback Controls
    bool startPlayback(const std::string& inputWavPath);
    bool stopPlayback();

    // Configuration & Diagnostics
    void setEffectType(EffectType effect);
    void setEchoParameters(float delayMs, float feedback, float wetMix);
    void setReverbParameters(float roomSize, float damping, float wetMix);

    EngineState getState() const { return mState.load(); }
    int64_t getRecordingDurationMs() const;
    int32_t getSampleRate() const { return mSampleRate; }
    int32_t getChannelCount() const { return mChannelCount; }

#if OBOE_AVAILABLE
    // Oboe Callbacks
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorBeforeClose(oboe::AudioStream* audioStream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream* audioStream, oboe::Result error) override;
#endif

    // Host simulation / offline processor for testing outside Android
    void processAudioBufferDirect(int16_t* buffer, int32_t numFrames);

private:
    std::atomic<EngineState> mState{EngineState::IDLE};
    std::string mCurrentRecordingPath;
    std::string mCurrentPlaybackPath;

    int32_t mSampleRate{48000};
    int32_t mChannelCount{1}; // Mono recording optimal for voice
    int32_t mFormatBits{16};

    std::chrono::steady_clock::time_point mRecordingStartTime;

    EffectType mSelectedEffect{EffectType::NONE};
    std::unique_ptr<EchoEffect> mEchoEffect;
    std::unique_ptr<ReverbEffect> mReverbEffect;
    std::unique_ptr<HeliumEffect> mHeliumEffect;
    std::unique_ptr<DemonicEffect> mDemonicEffect;
    std::unique_ptr<WavWriter> mWavWriter;

#if OBOE_AVAILABLE
    std::shared_ptr<oboe::AudioStream> mRecordingStream;
    std::shared_ptr<oboe::AudioStream> mPlaybackStream;
    std::vector<int16_t> mPlaybackBuffer;
    size_t mPlaybackReadOffset{0};
#endif

    void setupEffects();
    void closeStreams();
};

} // namespace roxstar
