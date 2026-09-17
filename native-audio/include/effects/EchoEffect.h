#pragma once
#include "AudioEffect.h"
#include <vector>
#include <mutex>

namespace roxstar {

/**
 * High-performance circular buffer Echo effect.
 * Formula: y[n] = (1 - wetMix) * x[n] + wetMix * (x[n] + feedback * delayBuffer[readIndex])
 */
class EchoEffect : public AudioEffect {
public:
    /**
     * @param sampleRate Audio stream sample rate (e.g. 48000 Hz)
     * @param delayMs Echo delay time in milliseconds (e.g. 250ms)
     * @param feedback Echo decay feedback factor (0.0 to 0.85)
     * @param wetMix Wet/dry mix ratio (0.0 = dry, 1.0 = fully wet)
     */
    EchoEffect(int32_t sampleRate = 48000, float delayMs = 250.0f, float feedback = 0.5f, float wetMix = 0.5f);
    ~EchoEffect() override = default;

    void process(int16_t* buffer, int32_t numFrames, int32_t numChannels) override;
    void reset() override;

    void setDelayMs(float delayMs);
    void setFeedback(float feedback);
    void setWetMix(float wetMix);

private:
    int32_t mSampleRate;
    float mDelayMs;
    float mFeedback;
    float mWetMix;

    std::vector<float> mDelayBuffer;
    size_t mWriteIndex{0};
    size_t mDelaySamples{0};
    std::mutex mMutex;

    void updateBufferSize();
};

} // namespace roxstar
