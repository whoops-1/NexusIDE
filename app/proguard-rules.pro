# R8 / ProGuard rules for NexusIDE
-allowaccessmodification
-repackageclasses 'nx'
-optimizations !code/simplification/arithmetic
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep Compose runtime metadata
-keep class androidx.compose.runtime.** { *; }
-keep class kotlin.Metadata { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp / Okio reference optional TLS providers (Conscrypt, BouncyCastle,
# OpenJSSE) reflectively depending on what's on the classpath at runtime.
# These are OkHttp's own published R8 rules, not a guess.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okio.**

# JSch (com.github.mwiede fork, package unchanged from upstream) resolves
# cipher/kex/MAC implementations via Class.forName() against string names
# in its internal algorithm tables — R8 can't see those lookups statically,
# so the whole package needs to survive shrinking and obfuscation intact.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# NanoHTTPD (embedded dev web server) does MIME/method lookups by name.
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# compose-markdown wraps an Android TextView + Spanned-based renderer;
# keep it intact rather than risk obfuscating internals R8 can't trace
# through the AndroidView interop boundary.
-keep class dev.jeziellago.compose.markdowntext.** { *; }
-dontwarn dev.jeziellago.compose.markdowntext.**

# NOTE: these rules are written from each library's documented reflection
# behavior, not verified against an actual R8 "missing_rules.txt" output —
# this sandbox has no Android SDK/Gradle to run a real release build.
# Run `./gradlew assembleRelease` locally and check
# app/build/outputs/mapping/release/missing_rules.txt before shipping;
# treat this file as a strong starting point, not a guarantee.
