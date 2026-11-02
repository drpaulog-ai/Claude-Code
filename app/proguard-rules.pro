# Mantém as classes do SDK da Anthropic (usa Jackson com reflexão).
-keep class com.anthropic.** { *; }
-dontwarn com.anthropic.**
-dontwarn okhttp3.**
-dontwarn okio.**
