#include "effects/ReverbEffect.h"
#include <algorithm>
#include <cmath>

namespace roxstar {

CombFilter::CombFilter(size_t size, float feedback, float damp)
    : mBuffer(size > 0 ? size : 1000, 0.0f), mFeedback(feedback), mDamp(damp) {}

float CombFilter::process(float input) {
    if (mBuffer.empty()) return input;
    float output = mBuffer[mIndex];
    mFilterStore = (output * (1.0f - mDamp)) + (mFilterStore * mDamp);
    mBuffer[mIndex] = input + (mFilterStore * mFeedback);
    mIndex = (mIndex + 1) % mBuffer.size();
    return output;
}

void CombFilter::reset() {
    std::fill(mBuffer.begin(), mBuffer.end(), 0.0f);
    mFilterStore = 0.0f;
    mIndex = 0;
}

void CombFilter::setFeedback(float feedback) {
    mFeedback = feedback;
}

void CombFilter::setDamp(float damp) {
    mDamp = damp;
}

AllPassFilter::AllPassFilter(size_t size, float feedback)
    : mBuffer(size > 0 ? size : 200, 0.0f), mFeedback(feedback) {}

float AllPassFilter::process(float input) {
    if (mBuffer.empty()) return input;
    float bufOut = mBuffer[mIndex];
    float output = -input + bufOut;
    mBuffer[mIndex] = input + (bufOut * mFeedback);
    mIndex = (mIndex + 1) % mBuffer.size();
    return output;
}

void AllPassFilter::reset() {
    std::fill(mBuffer.begin(), mBuffer.end(), 0.0f);
    mIndex = 0;
}

ReverbEffect::ReverbEffect(int32_t sampleRate, float roomSize, float damping, float wetMix)
    : mSampleRate(sampleRate > 0 ? sampleRate : 48000),
      mRoomSize(std::clamp(roomSize, 0.0f, 0.98f)),
      mDamping(std::clamp(damping, 0.0f, 1.0f)),
      mWetMix(std::clamp(wetMix, 0.0f, 1.0f)) {
    initFilters();
}

void ReverbEffect::initFilters() {
    std::lock_guard<std::mutex> lock(mMutex);
    mCombs.clear();
    mAllPasses.clear();

    const double scale = static_cast<double>(mSampleRate) / 44100.0;
    // Standard Freeverb delay tuned lengths
    const size_t combTunings[] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    const size_t allPassTunings[] = {556, 441, 341, 225};

    for (size_t tuning : combTunings) {
        size_t adjusted = static_cast<size_t>(static_cast<double>(tuning) * scale);
        mCombs.emplace_back(adjusted, mRoomSize, mDamping);
    }

    for (size_t tuning : allPassTunings) {
        size_t adjusted = static_cast<size_t>(static_cast<double>(tuning) * scale);
        mAllPasses.emplace_back(adjusted, 0.5f);
    }
}

void ReverbEffect::setRoomSize(float roomSize) {
    mRoomSize = std::clamp(roomSize, 0.0f, 0.98f);
    std::lock_guard<std::mutex> lock(mMutex);
    for (auto& comb : mCombs) {
        comb.setFeedback(mRoomSize);
    }
}

void ReverbEffect::setDamping(float damping) {
    mDamping = std::clamp(damping, 0.0f, 1.0f);
    std::lock_guard<std::mutex> lock(mMutex);
    for (auto& comb : mCombs) {
        comb.setDamp(mDamping);
    }
}

void ReverbEffect::setWetMix(float wetMix) {
    mWetMix = std::clamp(wetMix, 0.0f, 1.0f);
}

void ReverbEffect::reset() {
    std::lock_guard<std::mutex> lock(mMutex);
    for (auto& comb : mCombs) comb.reset();
    for (auto& ap : mAllPasses) ap.reset();
}

void ReverbEffect::process(int16_t* buffer, int32_t numFrames, int32_t numChannels) {
    if (!buffer || numFrames <= 0 || numChannels <= 0) return;

    std::lock_guard<std::mutex> lock(mMutex);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        // Average channels to mono for diffuse reverb tail
        float monoInput = 0.0f;
        for (int32_t ch = 0; ch < numChannels; ++ch) {
            monoInput += static_cast<float>(buffer[frame * numChannels + ch]);
        }
        monoInput /= static_cast<float>(numChannels);

        // Sum comb outputs in parallel
        float combSum = 0.0f;
        for (auto& comb : mCombs) {
            combSum += comb.process(monoInput);
        }

        // Pass through series all-pass filters for diffusion
        float reverbOut = combSum * 0.125f; // Scale down sum
        for (auto& ap : mAllPasses) {
            reverbOut = ap.process(reverbOut);
        }

        // Mix dry/wet into original channels
        for (int32_t ch = 0; ch < numChannels; ++ch) {
            const int32_t sampleIndex = frame * numChannels + ch;
            const float drySample = static_cast<float>(buffer[sampleIndex]);
            const float mixed = ((1.0f - mWetMix) * drySample) + (mWetMix * reverbOut);
            buffer[sampleIndex] = static_cast<int16_t>(std::clamp(mixed, -32768.0f, 32767.0f));
        }
    }
}

} // namespace roxstar
