# Works around a native crash in JIT-compiled OkHttp hostname parsing by keeping the IDN table out of that method.
-keep,allowobfuscation class okhttp3.internal.idn.** { <fields>; <methods>; }

-keep class com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
