# Room, Compose і Coil постачають власні правила R8 — додаткових не потрібно.

# Vosk (офлайн-розпізнавання) працює через JNA — рефлексія й нативні виклики.
-dontwarn java.awt.**
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
