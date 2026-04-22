# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Google Generative AI SDK classes
-keep class com.google.ai.client.generativeai.** { *; }
-keep class com.google.protobuf.** { *; }
