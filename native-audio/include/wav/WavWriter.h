#pragma once
#include <string>
#include <fstream>
#include <cstdint>
#include <mutex>

namespace roxstar {

/**
 * Standard RIFF WAV file writer for 16-bit PCM audio.
 * Formats chunks, writes PCM frames incrementally, and finalizes header on close.
 */
class WavWriter {
public:
    WavWriter();
    ~WavWriter();

    bool open(const std::string& filePath, int32_t sampleRate, int32_t numChannels, int32_t bitsPerSample = 16);
    void write(const int16_t* buffer, int32_t numFrames);
    void close();
    bool isOpen() const { return mIsOpen; }
    uint32_t getTotalBytesWritten() const { return mTotalDataBytes; }

private:
    std::ofstream mFile;
    bool mIsOpen{false};
    int32_t mSampleRate{48000};
    int32_t mNumChannels{1};
    int32_t mBitsPerSample{16};
    uint32_t mTotalDataBytes{0};
    std::mutex mMutex;

    void writeHeaderPlaceholder();
    void finalizeHeader();
};

} // namespace roxstar
