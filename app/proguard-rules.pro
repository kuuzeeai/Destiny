# Works around a native crash in JIT-compiled OkHttp hostname parsing by keeping the IDN table out of that method.
-keep,allowobfuscation class okhttp3.internal.idn.** { <fields>; <methods>; }

# Mi terminal (SSH): JSch carga algoritmos por nombre de clase (reflexion); R8 no debe quitarlos.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn com.sun.jna.**
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
-dontwarn javax.security.auth.kerberos.**
-dontwarn java.lang.management.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.misc.**
