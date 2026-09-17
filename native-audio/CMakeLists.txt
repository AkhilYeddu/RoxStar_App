cmake_minimum_required(VERSION 3.22.1)
project(roxstar_native_audio VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

set(AUDIO_SOURCES
    src/AudioEngine.cpp
    src/effects/EchoEffect.cpp
    src/effects/ReverbEffect.cpp
    src/wav/WavWriter.cpp
)

add_library(roxstar_audio STATIC ${AUDIO_SOURCES})

if (ANDROID)
    find_package(oboe REQUIRED CONFIG)
    target_link_libraries(roxstar_audio PUBLIC oboe::oboe log)
endif()
