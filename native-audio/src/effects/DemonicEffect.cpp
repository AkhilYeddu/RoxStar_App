#include "effects/DemonicEffect.h"
#include <algorithm>
#include <cmath>

namespace roxstar {

DemonicEffect::DemonicEffect(int32_t sampleRate, float pitchRatio)
    : mSampleRate(sampleRate > 0 ? sampleRate : 48000),
      mPitchRatio(std::clamp(pitchRatio, 0.45f, 0.75f)) {
    // 4096 frames ring buffer
    mRingBuffer.assign(4096 * 2, 0.0f);
}

void DemonicEffect::setPitchRatio(float ratio) {
    std::lock_guard<std::mutex> lock(mMutex);
    mPitchRatio = std::clamp(ratio, 0.45f, 0.75f);
}

void DemonicEffect::reset() {
    std::lock_guard<std::mutex> lock(mMutex);
    std::fill(mRingBuffer.begin(), mRingBuffer.end(), 0.0f);
    mWriteIndex = 0.0;
    mReadIndex = 0.0;
    mLowPassStore = 0.0f;
}

void DemonicEffect::process(int16_t* buffer, int32_t numFrames, int32_t numChannels) {
    if (!buffer || numFrames <= 0 || numChannels <= 0) return;

    std::lock_guard<std::mutex> lock(mMutex);
    const size_t bufferSize = mRingBuffer.size() / 2;

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        size_t writePos = (static_cast<size_t>(mWriteIndex)) % bufferSize;
        
        for (int32_t ch = 0; ch < numChannels; ++ch) {
            float drySample = static_cast<float>(buffer[frame * numChannels + ch]);
            mRingBuffer[writePos * 2 + (ch % 2)] = drySample;
        }

        // Interpolated pitch-down read
        size_t readPosA = (static_cast<size_t>(mReadIndex)) % bufferSize;
        size_t readPosB = (readPosA + 1) % bufferSize;
        float frac = static_cast<float>(mReadIndex - std::floor(mReadIndex));

        for (int32_t ch = 0; ch < numChannels; ++ch) {
            float sampleA = mRingBuffer[readPosA * 2 + (ch % 2)];
            float sampleB = mRingBuffer[readPosB * 2 + (ch % 2)];
            float pitchedDown = sampleA + frac * (sampleB - sampleA);

            float dry = static_cast<float>(buffer[frame * numChannels + ch]);

            // Low-pass growl filter
            mLowPassStore = (0.75f * mLowPassStore) + (0.25f * pitchedDown);

            // Mix pitched down + deep sub growl + slight dry blend
            float demonicOut = (pitchedDown * 0.7f) + (mLowPassStore * 0.4f) + (dry * 0.2f);

            buffer[frame * numChannels + ch] = static_cast<int16_t>(
                std::clamp(demonicOut, -32768.0f, 32767.0f)
            );
        }

        mWriteIndex += 1.0;
        mReadIndex += mPitchRatio;

        // Keep read pointer bound
        if (mWriteIndex > mReadIndex + bufferSize) {
            mReadIndex = mWriteIndex - bufferSize / 2;
        }
    }
}

} // namespace roxstar
