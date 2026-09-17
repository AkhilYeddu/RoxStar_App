#pragma once
#include "effects/AudioEffect.h"
#include <vector>
#include <mutex>

namespace roxstar {

/**
 * Helium Voice Effect (Pitch shift UP ~1.65x for high chipmunk sound)
 */
class HeliumEffect : public AudioEffect {
public:
    HeliumEffect(int32_t sampleRate = 48000, float pitchRatio = 1.65f);
    virtual ~HeliumEffect() = default;

    void process(int16_t* buffer, int32_t numFrames, int32_t numChannels) override;
    void reset() override;

    void setPitchRatio(float ratio);

private:
    int32_t mSampleRate;
    float mPitchRatio;
    std::vector<float> mRingBuffer;
    double mWriteIndex{0.0};
    double mReadIndex{0.0};
    std::mutex mMutex;
};

} // namespace roxstar
