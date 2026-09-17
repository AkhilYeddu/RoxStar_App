#include "effects/EchoEffect.h"
#include <algorithm>
#include <cmath>

namespace roxstar {

EchoEffect::EchoEffect(int32_t sampleRate, float delayMs, float feedback, float wetMix)
    : mSampleRate(sampleRate > 0 ? sampleRate : 48000),
      mDelayMs(delayMs > 0.0f ? delayMs : 250.0f),
      mFeedback(std::clamp(feedback, 0.0f, 0.95f)),
      mWetMix(std::clamp(wetMix, 0.0f, 1.0f)) {
    updateBufferSize();
}

void EchoEffect::updateBufferSize() {
    std::lock_guard<std::mutex> lock(mMutex);
    mDelaySamples = static_cast<size_t>((static_cast<double>(mSampleRate) * mDelayMs) / 1000.0);
    if (mDelaySamples < 1) mDelaySamples = 1;
    // Support stereo channels buffer length
    mDelayBuffer.assign(mDelaySamples * 2, 0.0f);
    mWriteIndex = 0;
}

void EchoEffect::setDelayMs(float delayMs) {
    if (delayMs <= 0.0f) return;
    mDelayMs = delayMs;
    updateBufferSize();
}

void EchoEffect::setFeedback(float feedback) {
    mFeedback = std::clamp(feedback, 0.0f, 0.95f);
}

void EchoEffect::setWetMix(float wetMix) {
    mWetMix = std::clamp(wetMix, 0.0f, 1.0f);
}

void EchoEffect::reset() {
    std::lock_guard<std::mutex> lock(mMutex);
    std::fill(mDelayBuffer.begin(), mDelayBuffer.end(), 0.0f);
    mWriteIndex = 0;
}

void EchoEffect::process(int16_t* buffer, int32_t numFrames, int32_t numChannels) {
    if (!buffer || numFrames <= 0 || numChannels <= 0) return;

    std::lock_guard<std::mutex> lock(mMutex);
    const size_t totalDelayBufferLength = mDelayBuffer.size();
    if (totalDelayBufferLength == 0) return;

    const size_t channelDelayLength = mDelaySamples;

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        for (int32_t ch = 0; ch < numChannels; ++ch) {
            const int32_t sampleIndex = frame * numChannels + ch;
            const float drySample = static_cast<float>(buffer[sampleIndex]);

            // Delay buffer index for this channel
            const size_t bufferOffset = ch * channelDelayLength;
            const size_t readIndex = bufferOffset + (mWriteIndex % channelDelayLength);

            const float delayedSample = mDelayBuffer[readIndex];

            // Calculate output with wet/dry mix and feedback
            const float processedSample = drySample + (delayedSample * mFeedback);
            mDelayBuffer[readIndex] = processedSample;

            const float mixedOutput = ((1.0f - mWetMix) * drySample) + (mWetMix * processedSample);

            // Hard clamp to prevent 16-bit signed integer clipping
            buffer[sampleIndex] = static_cast<int16_t>(std::clamp(mixedOutput, -32768.0f, 32767.0f));
        }

        mWriteIndex = (mWriteIndex + 1) % channelDelayLength;
    }
}

} // namespace roxstar
