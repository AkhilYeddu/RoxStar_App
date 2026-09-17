#include "wav/WavWriter.h"
#include <iostream>

namespace roxstar {

WavWriter::WavWriter() = default;

WavWriter::~WavWriter() {
    close();
}

bool WavWriter::open(const std::string& filePath, int32_t sampleRate, int32_t numChannels, int32_t bitsPerSample) {
    std::lock_guard<std::mutex> lock(mMutex);
    close();

    mSampleRate = sampleRate;
    mNumChannels = numChannels;
    mBitsPerSample = bitsPerSample;
    mTotalDataBytes = 0;

    mFile.open(filePath, std::ios::binary | std::ios::out | std::ios::trunc);
    if (!mFile.is_open()) {
        return false;
    }

    writeHeaderPlaceholder();
    mIsOpen = true;
    return true;
}

void WavWriter::writeHeaderPlaceholder() {
    // 44-byte standard RIFF header
    char header[44] = {0};
    mFile.write(header, 44);
}

void WavWriter::write(const int16_t* buffer, int32_t numFrames) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mIsOpen || !buffer || numFrames <= 0) return;

    const size_t bytesToWrite = static_cast<size_t>(numFrames) * mNumChannels * (mBitsPerSample / 8);
    mFile.write(reinterpret_cast<const char*>(buffer), bytesToWrite);
    mTotalDataBytes += static_cast<uint32_t>(bytesToWrite);
}

void WavWriter::finalizeHeader() {
    if (!mFile.is_open()) return;

    // RIFF header format
    mFile.seekp(0, std::ios::beg);

    uint32_t byteRate = mSampleRate * mNumChannels * (mBitsPerSample / 8);
    uint16_t blockAlign = mNumChannels * (mBitsPerSample / 8);
    uint32_t chunkSize = 36 + mTotalDataBytes;

    // RIFF descriptor
    mFile.write("RIFF", 4);
    mFile.write(reinterpret_cast<const char*>(&chunkSize), 4);
    mFile.write("WAVE", 4);

    // fmt subchunk
    mFile.write("fmt ", 4);
    uint32_t subchunk1Size = 16; // 16 for PCM
    uint16_t audioFormat = 1;    // 1 for PCM
    uint16_t numChannels = static_cast<uint16_t>(mNumChannels);
    uint32_t sampleRate = static_cast<uint32_t>(mSampleRate);
    uint16_t bitsPerSample = static_cast<uint16_t>(mBitsPerSample);

    mFile.write(reinterpret_cast<const char*>(&subchunk1Size), 4);
    mFile.write(reinterpret_cast<const char*>(&audioFormat), 2);
    mFile.write(reinterpret_cast<const char*>(&numChannels), 2);
    mFile.write(reinterpret_cast<const char*>(&sampleRate), 4);
    mFile.write(reinterpret_cast<const char*>(&byteRate), 4);
    mFile.write(reinterpret_cast<const char*>(&blockAlign), 2);
    mFile.write(reinterpret_cast<const char*>(&bitsPerSample), 2);

    // data subchunk
    mFile.write("data", 4);
    mFile.write(reinterpret_cast<const char*>(&mTotalDataBytes), 4);

    mFile.flush();
}

void WavWriter::close() {
    if (mIsOpen) {
        finalizeHeader();
        mFile.close();
        mIsOpen = false;
    }
}

} // namespace roxstar
