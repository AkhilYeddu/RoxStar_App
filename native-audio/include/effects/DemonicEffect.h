#pragma once
#include "effects/AudioEffect.h"
#include <vector>
#include <mutex>

namespace roxstar {

/**
 * Demonic Voice Effect (Pitch shift DOWN ~0.6x + sub-octave growl + low-pass filter)
 */
class DemonicEffect : public AudioEffect {
public:
    DemonicEffect(int32_t sampleRate = 48000, float pitchRatio = 0.6f);
    virtual ~DemonicEffect() = default;

    void process(int16_t* buffer, int32_t numFrames, int32_t numChannels) override;
    void reset() override;

    void setPitchRatio(float ratio);

private:
    int32_t mSampleRate;
    float mPitchRatio;
    std::vector<float> mRingBuffer;
    double mWriteIndex{0.0};
    double mReadIndex{0.0};
    float mLowPassStore{0.0f};
    std::mutex mMutex;
};

} // namespace roxstar
