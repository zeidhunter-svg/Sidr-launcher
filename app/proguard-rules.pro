# Phase 9 / Y2 release hardening. Keep rules are intentionally scoped to runtime/reflection-heavy
# libraries used by the app; app/domain code should still be shrinkable unless a library needs it.

# Hilt / Dagger generated graph and Hilt Workers.
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class **_HiltModules* { *; }
-keep class **_GeneratedInjector { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.hilt.work.HiltWorker { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.annotation.**

# Room database/entities/DAOs. Room ships consumer rules, these keep our schema surface explicit.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# kotlinx.serialization generated serializers and metadata.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keep class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# ONNX Runtime Java/JNI bridge.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Ktor client internals use generated serializers and platform engines.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
