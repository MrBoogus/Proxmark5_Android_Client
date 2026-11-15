# ProGuard / R8 rules for Proxmark5 Android Client
-keep class com.proxmark5.client.protocol.** { *; }
-keep class com.proxmark5.client.data.** { *; }
-dontwarn com.google.errorprone.annotations.**
