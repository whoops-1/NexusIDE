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

# JNI: keep native bridge classes
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.nexus.ide.native.** { *; }
