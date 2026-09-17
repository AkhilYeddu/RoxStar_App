#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>
#include "AudioEngine.h"

#define TAG "RoxStar_NativeAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::unique_ptr<roxstar::AudioEngine> sAudioEngine = nullptr;

static roxstar::AudioEngine* getAudioEngine() {
    if (!sAudioEngine) {
        sAudioEngine = std::make_unique<roxstar::AudioEngine>();
    }
    return sAudioEngine.get();
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_roxstar_app_audio_NativeAudioEngine_nativeStartRecording(
        JNIEnv* env,
        jobject /* this */,
        jstring outputPath,
        jint effectOrdinal) {
    if (!outputPath) return JNI_FALSE;

    const char* nativePath = env->GetStringUTFChars(outputPath, nullptr);
    std::string pathStr(nativePath);
    env->ReleaseStringUTFChars(outputPath, nativePath);

    roxstar::EffectType effect = static_cast<roxstar::EffectType>(effectOrdinal);
    LOGI("nativeStartRecording called: path=%s, effect=%d", pathStr.c_str(), effectOrdinal);

    bool success = getAudioEngine()->startRecording(pathStr, effect);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_roxstar_app_audio_NativeAudioEngine_nativeStopRecording(
        JNIEnv* /* env */,
        jobject /* this */) {
    LOGI("nativeStopRecording called");
    bool success = getAudioEngine()->stopRecording();
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_roxstar_app_audio_NativeAudioEngine_nativeCancelRecording(
        JNIEnv* /* env */,
        jobject /* this */) {
    LOGI("nativeCancelRecording called");
    bool success = getAudioEngine()->cancelRecording();
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_roxstar_app_audio_NativeAudioEngine_nativeStartPlayback(
        JNIEnv* env,
        jobject /* this */,
        jstring inputPath) {
    if (!inputPath) return JNI_FALSE;

    const char* nativePath = env->GetStringUTFChars(inputPath, nullptr);
    std::string pathStr(nativePath);
    env->ReleaseStringUTFChars(inputPath, nativePath);

    LOGI("nativeStartPlayback called: path=%s", pathStr.c_str());
    bool success = getAudioEngine()->startPlayback(pathStr);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_roxstar_app_audio_NativeAudioEngine_nativeStopPlayback(
        JNIEnv* /* env */,
        jobject /* this */) {
    LOGI("nativeStopPlayback called");
    bool success = getAudioEngine()->stopPlayback();
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_roxstar_app_audio_NativeAudioEngine_nativeSetEchoParams(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat delayMs,
        jfloat feedback,
        jfloat wetMix) {
    getAudioEngine()->setEchoParameters(delayMs, feedback, wetMix);
}

JNIEXPORT void JNICALL
Java_com_roxstar_app_audio_NativeAudioEngine_nativeSetReverbParams(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat roomSize,
        jfloat damping,
        jfloat wetMix) {
    getAudioEngine()->setReverbParameters(roomSize, damping, wetMix);
}

JNIEXPORT jint JNICALL
Java_com_roxstar_app_audio_NativeAudioEngine_nativeGetState(
        JNIEnv* /* env */,
        jobject /* this */) {
    return static_cast<jint>(getAudioEngine()->getState());
}

JNIEXPORT jlong JNICALL
Java_com_roxstar_app_audio_NativeAudioEngine_nativeGetRecordingDuration(
        JNIEnv* /* env */,
        jobject /* this */) {
    return static_cast<jlong>(getAudioEngine()->getRecordingDurationMs());
}

} // extern "C"
