#include "effects/HeliumEffect.h"
#include <algorithm>
#include <cmath>

namespace roxstar {

HeliumEffect::HeliumEffect(int32_t sampleRate, float pitchRatio)
    : mSampleRate(sampleRate > 0 ? sampleRate : 48000),
      mPitchRatio(std::clamp(pitchRatio, 1.2f, 2.2f)) {
    // 4096 frames ring buffer
    mRingBuffer.assign(4096 * 2, 0.0f);
}

void HeliumEffect::setPitchRatio(float ratio) {
    std::lock_guard<std::mutex> lock(mMutex);
    mPitchRatio = std::clamp(ratio, 1.2f, 2.2f);
}

void HeliumEffect::reset() {
    std::lock_guard<std::mutex> lock(mMutex);
    std::fill(mRingBuffer.begin(), mRingBuffer.end(), 0.0f);
    mWriteIndex = 0.0;
    mReadIndex = 0.0;
}

void HeliumEffect::process(int16_t* buffer, int32_t numFrames, int32_t numChannels) {
    if (!buffer || numFrames <= 0 || numChannels <= 0) return;

    std::lock_guard<std::mutex> lock(mMutex);
    const size_t bufferSize = mRingBuffer.size() / 2; // frames in ring buffer

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        size_t writePos = (static_cast<size_t>(mWriteIndex)) % bufferSize;
        
        for (int32_t ch = 0; ch < numChannels; ++ch) {
            float inputSample = static_cast<float>(buffer[frame * numChannels + ch]);
            mRingBuffer[writePos * 2 + (ch % 2)] = inputSample;
        }

        // Interpolated read at mReadIndex with mPitchRatio rate
        size_t readPosA = (static_cast<size_t>(mReadIndex)) % bufferSize;
        size_t readPosB = (readPosA + 1) % bufferSize;
        float frac = static_cast<float>(mReadIndex - std::floor(mReadIndex));

        for (int32_t ch = 0; ch < numChannels; ++ch) {
            float sampleA = mRingBuffer[readPosA * 2 + (ch % 2)];
            float sampleB = mRingBuffer[readPosB * 2 + (ch % 2)];
            float interpolated = sampleA + frac * (sampleB - sampleA);

            buffer[frame * numChannels + ch] = static_cast<int16_t>(
                std::clamp(interpolated, -32768.0f, 32767.0f)
            );
        }

        mWriteIndex += 1.0;
        mReadIndex += mPitchRatio;

        // Keep read pointer from drifting too far from write pointer
        if (mReadIndex > mWriteIndex + bufferSize) {
            mReadIndex = mWriteIndex;
        }
    }
}

} // namespace roxstar
