#pragma once
#include "AudioEffect.h"
#include <vector>
#include <mutex>

namespace roxstar {

/**
 * Freeverb-inspired Comb Filter for acoustic reverberation.
 */
class CombFilter {
public:
    CombFilter(size_t size = 1116, float feedback = 0.8f, float damp = 0.2f);
    float process(float input);
    void reset();
    void setFeedback(float feedback);
    void setDamp(float damp);

private:
    std::vector<float> mBuffer;
    size_t mIndex{0};
    float mFeedback{0.8f};
    float mDamp{0.2f};
    float mFilterStore{0.0f};
};

/**
 * All-pass filter to diffuse reflections and avoid coloration.
 */
class AllPassFilter {
public:
    AllPassFilter(size_t size = 225, float feedback = 0.5f);
    float process(float input);
    void reset();

private:
    std::vector<float> mBuffer;
    size_t mIndex{0};
    float mFeedback{0.5f};
};

/**
 * High-performance Schroeder / Freeverb reverb effect.
 */
class ReverbEffect : public AudioEffect {
public:
    ReverbEffect(int32_t sampleRate = 48000, float roomSize = 0.7f, float damping = 0.25f, float wetMix = 0.4f);
    ~ReverbEffect() override = default;

    void process(int16_t* buffer, int32_t numFrames, int32_t numChannels) override;
    void reset() override;

    void setRoomSize(float roomSize);
    void setDamping(float damping);
    void setWetMix(float wetMix);

private:
    int32_t mSampleRate;
    float mRoomSize;
    float mDamping;
    float mWetMix;

    std::vector<CombFilter> mCombs;
    std::vector<AllPassFilter> mAllPasses;
    std::mutex mMutex;

    void initFilters();
};

} // namespace roxstar
