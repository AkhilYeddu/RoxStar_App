# RoxStar Studio Proguard Rules
-keep class com.roxstar.app.audio.NativeAudioEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
