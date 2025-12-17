# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to the global list.

# Keep ffmpeg-kit and smart-exception classes (reflection & native bindings)
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-keep class org.slf4j.** { *; }
-dontwarn com.arthenica.**
-dontwarn org.slf4j.**
