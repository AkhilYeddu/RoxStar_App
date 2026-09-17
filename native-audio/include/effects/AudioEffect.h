#pragma once
#include <cstdint>
#include <vector>

namespace roxstar {

enum class EffectType {
    NONE = 0,
    ECHO = 1,
    REVERB = 2,
    PITCH_SHIFT = 3
};

/**
 * Base interface for real-time PCM audio effects.
 * Operates on 16-bit signed integer or 32-bit float samples.
 */
class AudioEffect {
public:
    virtual ~AudioEffect() = default;

    /**
     * Process an interleaved buffer of 16-bit PCM audio samples in-place.
     * @param buffer Pointer to 16-bit PCM samples
     * @param numFrames Number of audio frames (numSamples = numFrames * numChannels)
     * @param numChannels Number of channels (1 = mono, 2 = stereo)
     */
    virtual void process(int16_t* buffer, int32_t numFrames, int32_t numChannels) = 0;

    /**
     * Reset internal state/buffers.
     */
    virtual void reset() = 0;
};

} // namespace roxstar
