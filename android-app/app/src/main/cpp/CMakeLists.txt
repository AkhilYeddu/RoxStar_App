cmake_minimum_required(VERSION 3.22.1)
project("roxstar_audio_native")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Reference native-audio code
set(NATIVE_AUDIO_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../native-audio)

include_directories(
    ${NATIVE_AUDIO_DIR}/include
)

find_package(oboe REQUIRED CONFIG)

add_library(
    roxstar_audio_native
    SHARED
    native-lib.cpp
    ${NATIVE_AUDIO_DIR}/src/AudioEngine.cpp
    ${NATIVE_AUDIO_DIR}/src/effects/EchoEffect.cpp
    ${NATIVE_AUDIO_DIR}/src/effects/ReverbEffect.cpp
    ${NATIVE_AUDIO_DIR}/src/effects/HeliumEffect.cpp
    ${NATIVE_AUDIO_DIR}/src/effects/DemonicEffect.cpp
    ${NATIVE_AUDIO_DIR}/src/wav/WavWriter.cpp
)

target_link_libraries(
    roxstar_audio_native
    oboe::oboe
    log
    android
)
