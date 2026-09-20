# ==============================================================================
# OptiLens — ProGuard / R8 Release Hardening Rules
# ==============================================================================

# Single-pass optimization to ensure fast, deterministic release builds
-optimizationpasses 1

# ── Application Entry Points ──────────────────────────────────────────────────
-keep class com.webappypie.optilens.OptiLensApp { *; }
-keep class com.webappypie.optilens.MainActivity { *; }

# ── Kotlin Attributes & Metadata ──────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── Kotlinx Serialization (Type-Safe Navigation & Config) ────────────────────
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    *** Companion;
}
-keep class * implements kotlinx.serialization.KSerializer {
    <init>(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    *;
}
-keepclassmembers class * {
    static kotlinx.serialization.KSerializer serializer(...);
}

# ── Jetpack Compose ───────────────────────────────────────────────────────────
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-dontwarn androidx.compose.**

# ── Hilt / Dagger Dependency Injection ────────────────────────────────────────
-dontwarn com.google.errorprone.annotations.**
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class androidx.hilt.navigation.compose.** { *; }
-keep class dagger.hilt.** { *; }
-keepnames class * implements dagger.hilt.internal.GeneratedComponent

# ── CameraX & Camera2 ─────────────────────────────────────────────────────────
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-keep class androidx.camera.extensions.** { *; }
-dontwarn androidx.camera.**

# ── Google Play Billing Client ────────────────────────────────────────────────
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.**

# ── Google Mobile Ads (Safe Monetization) ─────────────────────────────────────
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }
-dontwarn com.google.android.gms.ads.**

# ── Firebase (Remote Config & Analytics) ──────────────────────────────────────
-keep class com.google.firebase.remoteconfig.** { *; }
-keep class com.google.firebase.analytics.** { *; }
-dontwarn com.google.firebase.**

# ── Google ML Kit (Face Detection) ────────────────────────────────────────────
-keep class com.google.mlkit.vision.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# ── DataStore Preferences ─────────────────────────────────────────────────────
-keepclassmembers class androidx.datastore.preferences.core.MutablePreferences { *; }
-dontwarn androidx.datastore.**

# ── Domain & Data Models (Config, Quirks, Pipeline State) ─────────────────────
-keep class com.webappypie.optilens.core.common.config.** { *; }
-keep class com.webappypie.optilens.core.common.model.** { *; }
-keep class com.webappypie.optilens.core.camera.model.** { *; }
-keep class com.webappypie.optilens.core.camera.quirks.** { *; }
-keep class com.webappypie.optilens.core.imaging.model.** { *; }
-keep class com.webappypie.optilens.core.settings.model.** { *; }

# ── Native JNI & OpenCV Methods ───────────────────────────────────────────────
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers class * {
    native <methods>;
}
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# ── Suppress Host JVM Testing ImageIO / AWT Warnings in Android R8 ───────────
-dontwarn java.awt.**
-dontwarn javax.imageio.**

