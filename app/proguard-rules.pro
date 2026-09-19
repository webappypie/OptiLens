# OptiLens — ProGuard / R8 Rules

# ── Keep application entry points ─────────────────────────
-keep class com.webappypie.optilens.MainActivity { *; }

# ── Kotlin metadata ────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# ── Kotlin coroutines ──────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Compose (R8 handles most; keep for debugging) ──────────
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── Additional rules will be added per-feature as phases progress ──
