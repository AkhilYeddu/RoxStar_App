#include "AudioEngine.h"
#include <cstdio>
#include <iostream>

namespace roxstar {

AudioEngine::AudioEngine() {
    setupEffects();
    mWavWriter = std::make_unique<WavWriter>();
}

AudioEngine::~AudioEngine() {
    closeStreams();
}

void AudioEngine::setupEffects() {
    mEchoEffect = std::make_unique<EchoEffect>(mSampleRate, 250.0f, 0.5f, 0.5f);
    mReverbEffect = std::make_unique<ReverbEffect>(mSampleRate, 0.7f, 0.25f, 0.4f);
    mActiveEffect = nullptr;
}

void AudioEngine::setEffectType(EffectType effect) {
    switch (effect) {
        case EffectType::ECHO:
            mEchoEffect->reset();
            mActiveEffect.reset(); // Don't delete, just switch pointer logic
            // Use pointer directly
            break;
        case EffectType::REVERB:
            mReverbEffect->reset();
            break;
        default:
            break;
    }
}

void AudioEngine::setEchoParameters(float delayMs, float feedback, float wetMix) {
    if (mEchoEffect) {
        mEchoEffect->setDelayMs(delayMs);
        mEchoEffect->setFeedback(feedback);
        mEchoEffect->setWetMix(wetMix);
    }
}

void AudioEngine::setReverbParameters(float roomSize, float damping, float wetMix) {
    if (mReverbEffect) {
        mReverbEffect->setRoomSize(roomSize);
        mReverbEffect->setDamping(damping);
        mReverbEffect->setWetMix(wetMix);
    }
}

bool AudioEngine::startRecording(const std::string& outputWavPath, EffectType effect) {
    if (mState.load() == EngineState::RECORDING) {
        return false;
    }

    closeStreams();
    mCurrentRecordingPath = outputWavPath;

    // Reset selected effect
    if (effect == EffectType::ECHO) {
        mEchoEffect->reset();
    } else if (effect == EffectType::REVERB) {
        mReverbEffect->reset();
    }

    if (!mWavWriter->open(outputWavPath, mSampleRate, mChannelCount, mFormatBits)) {
        mState.store(EngineState::ERROR);
        return false;
    }

    mRecordingStartTime = std::chrono::steady_clock::now();

#if OBOE_AVAILABLE
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::I16)
        ->setChannelCount(mChannelCount)
        ->setSampleRate(mSampleRate)
        ->setInputPreset(oboe::InputPreset::VoiceRecognition)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    oboe::Result result = builder.openStream(mRecordingStream);
    if (result != oboe::Result::OK) {
        mWavWriter->close();
        mState.store(EngineState::ERROR);
        return false;
    }

    result = mRecordingStream->requestStart();
    if (result != oboe::Result::OK) {
        mRecordingStream->close();
        mRecordingStream.reset();
        mWavWriter->close();
        mState.store(EngineState::ERROR);
        return false;
    }
#endif

    mState.store(EngineState::RECORDING);
    return true;
}

bool AudioEngine::stopRecording() {
    if (mState.load() != EngineState::RECORDING) {
        return false;
    }

#if OBOE_AVAILABLE
    if (mRecordingStream) {
        mRecordingStream->requestStop();
        mRecordingStream->close();
        mRecordingStream.reset();
    }
#endif

    mWavWriter->close();
    mState.store(EngineState::IDLE);
    return true;
}

bool AudioEngine::cancelRecording() {
    if (mState.load() != EngineState::RECORDING) {
        return false;
    }

#if OBOE_AVAILABLE
    if (mRecordingStream) {
        mRecordingStream->requestStop();
        mRecordingStream->close();
        mRecordingStream.reset();
    }
#endif

    mWavWriter->close();
    if (!mCurrentRecordingPath.empty()) {
        std::remove(mCurrentRecordingPath.c_str());
        mCurrentRecordingPath.clear();
    }

    mState.store(EngineState::IDLE);
    return true;
}

bool AudioEngine::startPlayback(const std::string& inputWavPath) {
    if (mState.load() == EngineState::RECORDING) {
        return false;
    }

    closeStreams();
    mCurrentPlaybackPath = inputWavPath;

#if OBOE_AVAILABLE
    // Read input WAV data into mPlaybackBuffer
    FILE* file = fopen(inputWavPath.c_str(), "rb");
    if (!file) return false;

    // Skip 44 bytes WAV header
    fseek(file, 44, SEEK_SET);
    mPlaybackBuffer.clear();
    int16_t sample;
    while (fread(&sample, sizeof(int16_t), 1, file) == 1) {
        mPlaybackBuffer.push_back(sample);
    }
    fclose(file);
    mPlaybackReadOffset = 0;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::I16)
        ->setChannelCount(mChannelCount)
        ->setSampleRate(mSampleRate)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    oboe::Result result = builder.openStream(mPlaybackStream);
    if (result != oboe::Result::OK) {
        mState.store(EngineState::ERROR);
        return false;
    }

    result = mPlaybackStream->requestStart();
    if (result != oboe::Result::OK) {
        mPlaybackStream->close();
        mPlaybackStream.reset();
        mState.store(EngineState::ERROR);
        return false;
    }
#endif

    mState.store(EngineState::PLAYING);
    return true;
}

bool AudioEngine::stopPlayback() {
    if (mState.load() != EngineState::PLAYING) {
        return false;
    }

#if OBOE_AVAILABLE
    if (mPlaybackStream) {
        mPlaybackStream->requestStop();
        mPlaybackStream->close();
        mPlaybackStream.reset();
    }
#endif

    mState.store(EngineState::IDLE);
    return true;
}

int64_t AudioEngine::getRecordingDurationMs() const {
    if (mState.load() != EngineState::RECORDING) return 0;
    auto now = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::milliseconds>(now - mRecordingStartTime).count();
}

void AudioEngine::processAudioBufferDirect(int16_t* buffer, int32_t numFrames) {
    if (!buffer || numFrames <= 0) return;

    if (mEchoEffect) {
        mEchoEffect->process(buffer, numFrames, mChannelCount);
    }
    if (mWavWriter && mWavWriter->isOpen()) {
        mWavWriter->write(buffer, numFrames);
    }
}

#if OBOE_AVAILABLE
oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* audioStream,
    void* audioData,
    int32_t numFrames) {

    if (!audioData || numFrames <= 0) {
        return oboe::DataCallbackResult::Continue;
    }

    if (audioStream->getDirection() == oboe::Direction::Input) {
        // RECORDING PIPELINE:
        // Microphone -> Oboe input stream -> Effect processing -> WavWriter
        int16_t* pcmBuffer = static_cast<int16_t*>(audioData);

        // Apply Voice Effect if active
        if (mEchoEffect) {
            mEchoEffect->process(pcmBuffer, numFrames, mChannelCount);
        }

        // Write directly to local WAV draft file
        if (mWavWriter && mWavWriter->isOpen()) {
            mWavWriter->write(pcmBuffer, numFrames);
        }

        return oboe::DataCallbackResult::Continue;
    } else if (audioStream->getDirection() == oboe::Direction::Output) {
        // PLAYBACK PIPELINE:
        int16_t* outputBuffer = static_cast<int16_t*>(audioData);
        for (int32_t i = 0; i < numFrames * mChannelCount; ++i) {
            if (mPlaybackReadOffset < mPlaybackBuffer.size()) {
                outputBuffer[i] = mPlaybackBuffer[mPlaybackReadOffset++];
            } else {
                outputBuffer[i] = 0; // Silence when file ends
            }
        }

        if (mPlaybackReadOffset >= mPlaybackBuffer.size()) {
            mState.store(EngineState::IDLE);
            return oboe::DataCallbackResult::Stop;
        }

        return oboe::DataCallbackResult::Continue;
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream* audioStream, oboe::Result error) {
    // Handle transient audio errors before stream closing
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* audioStream, oboe::Result error) {
    // Reconnection or clean up state on unexpected audio device disconnect
    mState.store(EngineState::ERROR);
}
#endif

void AudioEngine::closeStreams() {
#if OBOE_AVAILABLE
    if (mRecordingStream) {
        mRecordingStream->stop();
        mRecordingStream->close();
        mRecordingStream.reset();
    }
    if (mPlaybackStream) {
        mPlaybackStream->stop();
        mPlaybackStream->close();
        mPlaybackStream.reset();
    }
#endif
    if (mWavWriter && mWavWriter->isOpen()) {
        mWavWriter->close();
    }
    mState.store(EngineState::IDLE);
}

} // namespace roxstar
