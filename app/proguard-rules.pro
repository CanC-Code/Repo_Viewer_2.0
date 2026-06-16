# Compose
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.datastore.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Native JNI bridge
-keep class com.explorer.ai.domain.NativeHardwareLlmEngine { *; }
-keepclassmembers class com.explorer.ai.domain.NativeHardwareLlmEngine {
    native <methods>;
}
